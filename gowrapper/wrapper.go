package main

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/bluegradienthorizon/proxytoolbox/parsers"
	"github.com/bluegradienthorizon/proxytoolbox/presets"
	"github.com/bluegradienthorizon/proxytoolbox/registry"
	"github.com/bluegradienthorizon/proxytoolbox/runner"
	"github.com/bluegradienthorizon/proxytoolbox/worker"
)

type WorkerInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Path    string `json:"path"`
}

type ProxyConfig struct {
	Tag     string  `json:"tag"`
	ConnURI string  `json:"conn_uri"`
	Delay   int64   `json:"delay"`
	Speed   float64 `json:"speed"`
}

type LatencyTestCallbacks struct {
	OnRoundStarted func(batch int, round int, total int)
	OnProgress     func(tag string, delay int64, failed bool)
	OnRoundEnded   func(batch int, round int)
}

type SpeedTestCallbacks struct {
	OnRoundStarted func(batch int, round int, total int)
	OnProgress     func(tag string, speed float64, failed bool)
	OnRoundEnded   func(batch int, round int)
}

type SpeedTestPreset struct {
	Id   string `json:"id"`
	Name string `json:"name"`
}

var (
	testMu     sync.Mutex
	testCancel context.CancelFunc
	testRunner *runner.TestRunner

	currentWorkerPath    string
	currentParsedConfigs []parsers.ProxyConfig
	currentValidConfigs  []parsers.ProxyConfig
)

func DiscoverWorkers(libraryPath string) ([]WorkerInfo, error) {
	reg := registry.NewRegistry()
	reg.Discover(libraryPath)

	workersMap := reg.All()
	workers := make([]WorkerInfo, 0)
	for _, list := range workersMap {
		for _, info := range list {
			workers = append(workers, WorkerInfo{
				Name:    info.Name,
				Version: info.Version,
				Path:    info.Path,
			})
		}
	}
	return workers, nil
}

func DiscoverSpeedTestPresets() ([]SpeedTestPreset, error) {
	return []SpeedTestPreset{
		{Id: "cloudflare", Name: "Cloudflare"},
	}, nil
}

func StopTests() {
	testMu.Lock()
	defer testMu.Unlock()
	if testCancel != nil {
		testCancel()
	}
	if testRunner != nil {
		testRunner.Close()
	}
}

func InitializeRunner(workerPath string) error {
	testMu.Lock()
	defer testMu.Unlock()
	currentWorkerPath = workerPath

	// Verify if we can create a runner
	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: currentWorkerPath,
	})
	if err != nil {
		return fmt.Errorf("Failed to initialize test runner: %v", err)
	}
	tr.Close()
	return nil
}

func ParseConfigs(inputConfigs []ProxyConfig) (map[string]string, error) {
	testMu.Lock()
	defer testMu.Unlock()

	for _, c := range inputConfigs {
		if strings.TrimSpace(c.Tag) == "" {
			return nil, fmt.Errorf("Empty tag found in config")
		}
	}

	var parsedConfigs []parsers.ProxyConfig
	parseErrors := make(map[string]string)

	for _, c := range inputConfigs {
		connURI := strings.TrimSpace(c.ConnURI)
		if connURI == "" {
			parseErrors[c.Tag] = "empty connection URI"
			continue
		}

		p, err := parsers.ParseConfig(connURI)
		if err != nil {
			parseErrors[c.Tag] = err.Error()
			continue
		}
		if p.Config == nil {
			parseErrors[c.Tag] = "parsed config is nil"
			continue
		}

		// Apply the specified tag instead of auto-generating
		p.Config.Tag = c.Tag
		parsedConfigs = append(parsedConfigs, *p)
	}

	if len(parsedConfigs) == 0 {
		return nil, fmt.Errorf("No valid configs after parsing")
	}

	currentParsedConfigs = parsedConfigs
	return parseErrors, nil
}

func ValidateConfigs() (map[string]string, error) {
	testMu.Lock()
	defer testMu.Unlock()

	if len(currentParsedConfigs) == 0 {
		return nil, fmt.Errorf("No configs to validate")
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: currentWorkerPath,
	})
	if err != nil {
		return nil, fmt.Errorf("Failed to create test runner for validation: %v", err)
	}
	defer tr.Close()

	taggedConfigs, validationErrors, err := tr.Validate(ctx, currentParsedConfigs, runner.DefaultConfigTaggerFunc)
	if err != nil {
		return nil, fmt.Errorf("Validation error: %v", err)
	}

	validateErrors := make(map[string]string)
	errMap := make(map[string]bool)
	for _, ve := range validationErrors {
		validateErrors[ve.Tag] = ve.Error
		errMap[ve.Tag] = true
	}

	validConfigs := make([]parsers.ProxyConfig, 0)
	for _, c := range taggedConfigs {
		if c.Config != nil && !errMap[c.Config.Tag] {
			validConfigs = append(validConfigs, c)
		}
	}

	if len(validConfigs) == 0 {
		return nil, fmt.Errorf("No valid configs after validation")
	}

	currentValidConfigs = validConfigs
	return validateErrors, nil
}

func RunLatencyTests(
	testUrl string,
	latencyRounds int,
	roundTimeout int,
	testByBatches bool,
	batchSize int,
	callbacks LatencyTestCallbacks,
) ([]ProxyConfig, error) {
	testMu.Lock()
	validConfigs := currentValidConfigs
	workerPath := currentWorkerPath
	testMu.Unlock()

	if len(validConfigs) == 0 {
		return nil, fmt.Errorf("No valid configs to test")
	}

	ctx, cancel := context.WithCancel(context.Background())
	testMu.Lock()
	testCancel = cancel
	testMu.Unlock()
	defer func() {
		testMu.Lock()
		testCancel = nil
		testRunner = nil
		testMu.Unlock()
		cancel()
	}()

	// Batched Latency Tests
	goBatchSize := batchSize
	if !testByBatches || goBatchSize <= 0 {
		goBatchSize = len(validConfigs)
	}

	var allResults []runner.LatencyTestResult

	for batchStart := 0; batchStart < len(validConfigs); batchStart += goBatchSize {
		if ctx.Err() != nil {
			break
		}

		batchEnd := min(batchStart+goBatchSize, len(validConfigs))
		batchConfigs := validConfigs[batchStart:batchEnd]
		batchNum := batchStart/goBatchSize + 1

		batchRunner, err := runner.NewTestRunner(runner.RunnerSettings{
			WorkerPath: workerPath,
		})
		if err != nil {
			continue
		}

		testMu.Lock()
		testRunner = batchRunner
		testMu.Unlock()

		_, batchValidationErrors, err := batchRunner.Validate(ctx, batchConfigs, runner.DefaultConfigTaggerFunc)
		if err != nil {
			batchRunner.Close()
			testMu.Lock()
			testRunner = nil
			testMu.Unlock()
			if ctx.Err() != nil {
				break
			}
			continue
		}

		batchErrMap := make(map[string]bool)
		for _, ve := range batchValidationErrors {
			batchErrMap[ve.Tag] = true
		}

		var batchTags []string
		for _, c := range batchConfigs {
			if c.Config != nil && !batchErrMap[c.Config.Tag] {
				batchTags = append(batchTags, c.Config.Tag)
			}
		}

		if len(batchTags) == 0 {
			batchRunner.Close()
			testMu.Lock()
			testRunner = nil
			testMu.Unlock()
			continue
		}

		ltSettings := runner.LatencyTestRunnerSettings{
			BaseTestRunnerSettings: runner.BaseTestRunnerSettings{
				SortResults:  true,
				FilterFailed: true,
				Timeout:      time.Duration(roundTimeout) * time.Second,
				Rounds:       latencyRounds,
				RoundStartedCallback: func(round int, outboundsLen int) {
					if callbacks.OnRoundStarted != nil {
						callbacks.OnRoundStarted(batchNum, round+1, outboundsLen)
					}
				},
				ProgressCallback: func(result runner.LatencyTestResult) {
					if callbacks.OnProgress != nil {
						callbacks.OnProgress(result.Tag, result.Delay, result.Error != nil)
					}
				},
				RoundEndedCallback: func(round int) {
					if callbacks.OnRoundEnded != nil {
						callbacks.OnRoundEnded(batchNum, round+1)
					}
				},
			},
			TestURL: testUrl,
		}

		testResults, err := batchRunner.RunLatencyTests(ctx, batchTags, ltSettings)
		if err == nil {
			allResults = append(allResults, testResults.Results...)
		}

		batchRunner.Close()
		testMu.Lock()
		testRunner = nil
		testMu.Unlock()

		if ctx.Err() != nil {
			break
		}
	}

	// Wrap up working configs
	passedTags := make(map[string]int64)
	for _, result := range allResults {
		if result.Error == nil {
			passedTags[result.Tag] = result.Delay
		}
	}

	workingConfigs := []ProxyConfig{}
	for _, cfg := range validConfigs {
		if delay, ok := passedTags[cfg.Config.Tag]; ok {
			workingConfigs = append(workingConfigs, ProxyConfig{
				Tag:     cfg.Config.Tag,
				ConnURI: cfg.ConnURI,
				Delay:   delay,
			})
		}
	}

	return workingConfigs, nil
}

func RunSpeedTests(
	connUrisJson string,
	providerId string,
	mode string,
	targetBytes int64,
	rounds int,
	roundTimeout int,
	testByBatches bool,
	batchSize int,
	callbacks SpeedTestCallbacks,
) ([]ProxyConfig, error) {
	testMu.Lock()
	workerPath := currentWorkerPath
	testMu.Unlock()

	if workerPath == "" {
		return nil, fmt.Errorf("worker path is empty")
	}

	var inputConfigs []ProxyConfig
	if err := json.Unmarshal([]byte(connUrisJson), &inputConfigs); err != nil {
		return nil, err
	}

	if len(inputConfigs) == 0 {
		return nil, fmt.Errorf("No valid configs to test")
	}

	var validConfigs []parsers.ProxyConfig
	for _, c := range inputConfigs {
		p, err := parsers.ParseConfig(c.ConnURI)
		if err == nil && p.Config != nil {
			p.Config.Tag = c.Tag
			validConfigs = append(validConfigs, *p)
		}
	}

	if len(validConfigs) == 0 {
		return nil, fmt.Errorf("no valid parsed configs")
	}

	ctx, cancel := context.WithCancel(context.Background())
	testMu.Lock()
	testCancel = cancel
	testMu.Unlock()
	defer func() {
		testMu.Lock()
		testCancel = nil
		testRunner = nil
		testMu.Unlock()
		cancel()
	}()

	goBatchSize := batchSize
	if !testByBatches || goBatchSize <= 0 {
		goBatchSize = len(validConfigs)
	}

	var allResults []runner.SpeedTestResult
	
	var provider worker.SpeedTestProvider
	if providerId == "cloudflare" {
		provider = presets.CloudflareProvider
	} else {
		provider = presets.CloudflareProvider // fallback
	}

	var testMode = worker.SpeedTestModeDownload
	if mode == "upload" {
		testMode = worker.SpeedTestModeUpload
	}

	for batchStart := 0; batchStart < len(validConfigs); batchStart += goBatchSize {
		if ctx.Err() != nil {
			break
		}
		batchEnd := min(batchStart+goBatchSize, len(validConfigs))
		batchConfigs := validConfigs[batchStart:batchEnd]
		batchNum := batchStart/goBatchSize + 1

		batchRunner, err := runner.NewTestRunner(runner.RunnerSettings{
			WorkerPath: workerPath,
		})
		if err != nil {
			continue
		}

		testMu.Lock()
		testRunner = batchRunner
		testMu.Unlock()

		_, batchValidationErrors, err := batchRunner.Validate(ctx, batchConfigs, runner.DefaultConfigTaggerFunc)
		if err != nil {
			batchRunner.Close()
			testMu.Lock()
			testRunner = nil
			testMu.Unlock()
			if ctx.Err() != nil {
				break
			}
			continue
		}

		batchErrMap := make(map[string]bool)
		for _, ve := range batchValidationErrors {
			batchErrMap[ve.Tag] = true
		}

		var batchTags []string
		for _, c := range batchConfigs {
			if c.Config != nil && !batchErrMap[c.Config.Tag] {
				batchTags = append(batchTags, c.Config.Tag)
			}
		}

		if len(batchTags) == 0 {
			batchRunner.Close()
			testMu.Lock()
			testRunner = nil
			testMu.Unlock()
			continue
		}

		stSettings := runner.SpeedTestRunnerSettings{
			BaseTestRunnerSettings: runner.BaseTestRunnerSettings{
				SortResults:  true,
				FilterFailed: true,
				Timeout:      time.Duration(roundTimeout) * time.Second,
				Rounds:       rounds,
				RoundStartedCallback: func(round int, outboundsLen int) {
					if callbacks.OnRoundStarted != nil {
						callbacks.OnRoundStarted(batchNum, round+1, outboundsLen)
					}
				},
				ProgressCallback: func(result runner.SpeedTestResult) {
					if callbacks.OnProgress != nil {
						callbacks.OnProgress(result.Tag, result.Speed, result.Error != nil)
					}
				},
				RoundEndedCallback: func(round int) {
					if callbacks.OnRoundEnded != nil {
						callbacks.OnRoundEnded(batchNum, round+1)
					}
				},
			},
			TargetBytes: targetBytes,
			Mode:        testMode,
			Provider:    provider,
		}

		testResults, err := batchRunner.RunSpeedTests(ctx, batchTags, stSettings)
		if err == nil {
			allResults = append(allResults, testResults.Results...)
		}

		batchRunner.Close()
		testMu.Lock()
		testRunner = nil
		testMu.Unlock()
	}

	passedTags := make(map[string]float64)
	for _, r := range allResults {
		if r.Error == nil {
			passedTags[r.Tag] = r.Speed
		}
	}

	var workingConfigs []ProxyConfig
	for _, cfg := range inputConfigs {
		if speed, ok := passedTags[cfg.Tag]; ok {
			workingConfigs = append(workingConfigs, ProxyConfig{
				Tag:     cfg.Tag,
				ConnURI: cfg.ConnURI,
				Speed:   speed,
				Delay:   cfg.Delay,
			})
		}
	}

	return workingConfigs, nil
}
