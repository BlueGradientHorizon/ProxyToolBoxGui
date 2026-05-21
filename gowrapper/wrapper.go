package main

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/bluegradienthorizon/proxytoolbox/parsers"
	"github.com/bluegradienthorizon/proxytoolbox/registry"
	"github.com/bluegradienthorizon/proxytoolbox/runner"
)

type WorkerInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Path    string `json:"path"`
}

type ProxyConfig struct {
	Tag     string `json:"tag"`
	ConnURI string `json:"conn_uri"`
	Delay   int64  `json:"delay"`
}

type TestCallbacks struct {
	OnRoundStarted func(batch int, round int, total int)
	OnProgress     func(tag string, delay int64, failed bool)
	OnRoundEnded   func(batch int, round int)
}

var (
	testMu     sync.Mutex
	testCancel context.CancelFunc
	testRunner *runner.TestRunner

	lowMemMode           bool
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

func StopTests() {
	testMu.Lock()
	defer testMu.Unlock()
	if testCancel != nil {
		testCancel()
	}
	if testRunner != nil {
		testRunner.Close()
		testRunner = nil
	}
}

func InitializeRunner(workerPath string, lowMem bool) error {
	testMu.Lock()
	defer testMu.Unlock()

	if testRunner != nil {
		testRunner.Close()
		testRunner = nil
	}

	currentWorkerPath = workerPath
	lowMemMode = lowMem

	// Verify if we can create a runner
	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: currentWorkerPath,
	})
	if err != nil {
		return fmt.Errorf("Failed to initialize test runner: %v", err)
	}

	if lowMemMode {
		tr.Close()
	} else {
		testRunner = tr
	}
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
	tr := testRunner
	testMu.Unlock()

	if lowMemMode {
		var err error
		tr, err = runner.NewTestRunner(runner.RunnerSettings{
			WorkerPath: currentWorkerPath,
		})
		if err != nil {
			return nil, fmt.Errorf("Failed to initialize temporary runner: %v", err)
		}
		defer tr.Close()
	} else if tr == nil {
		return nil, fmt.Errorf("Test runner not initialized")
	}

	if len(currentParsedConfigs) == 0 {
		return nil, fmt.Errorf("No configs to validate")
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

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

	testMu.Lock()
	currentValidConfigs = validConfigs
	testMu.Unlock()

	return validateErrors, nil
}

func RunLatencyTests(
	testUrl string,
	latencyRounds int,
	roundTimeout int,
	testByBatches bool,
	batchSize int,
	callbacks TestCallbacks,
) ([]ProxyConfig, error) {
	testMu.Lock()
	validConfigs := currentValidConfigs
	globalTR := testRunner
	testMu.Unlock()

	if !lowMemMode && globalTR == nil {
		return nil, fmt.Errorf("Test runner not initialized")
	}

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

		var batchTags []string
		for _, c := range batchConfigs {
			if c.Config != nil {
				batchTags = append(batchTags, c.Config.Tag)
			}
		}

		if len(batchTags) == 0 {
			continue
		}

		var tr *runner.TestRunner
		if lowMemMode {
			var err error
			tr, err = runner.NewTestRunner(runner.RunnerSettings{
				WorkerPath: currentWorkerPath,
			})
			if err != nil {
				return nil, fmt.Errorf("Failed to initialize temporary runner for batch: %v", err)
			}
			// mandatory to call validate
			_, _, _ = tr.Validate(ctx, batchConfigs, runner.DefaultConfigTaggerFunc)
		} else {
			tr = globalTR
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

		testResults, err := tr.RunLatencyTests(ctx, batchTags, ltSettings)
		if err == nil {
			allResults = append(allResults, testResults.Results...)
		}

		if lowMemMode && tr != nil {
			tr.Close()
		}

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
