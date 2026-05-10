package main

import (
	"context"
	"encoding/json"
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
}

type TestCallbacks struct {
	OnParseFailed    func(tags []string)
	OnValidateFailed func(tags []string)
	OnRoundStarted   func(batch int, round int, total int)
	OnProgress       func(tag string, delay int64, failed bool)
	OnRoundEnded     func(batch int, round int)
	OnError          func(msg string)
}

var (
	testMu     sync.Mutex
	testCancel context.CancelFunc
	testRunner *runner.TestRunner
)

func DiscoverWorkers(libraryPath string) []WorkerInfo {
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
	return workers
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

func RunLatencyTests(
	workerPath string,
	connUrisJson string,
	latencyRounds int,
	roundTimeout int,
	testByBatches bool,
	batchSize int,
	callbacks TestCallbacks,
) []ProxyConfig {
	var inputConfigs []ProxyConfig
	if err := json.Unmarshal([]byte(connUrisJson), &inputConfigs); err != nil {
		if callbacks.OnError != nil {
			callbacks.OnError(fmt.Sprintf("Unmarshal input error: %v", err))
		}
		return []ProxyConfig{}
	}

	for _, c := range inputConfigs {
		if strings.TrimSpace(c.Tag) == "" {
			if callbacks.OnError != nil {
				callbacks.OnError("Empty tag found in config")
			}
			return []ProxyConfig{}
		}
	}

	// 1. Parse configs
	var parsedConfigs []parsers.ProxyConfig
	parseFailedTags := []string{}

	for _, c := range inputConfigs {
		connURI := strings.TrimSpace(c.ConnURI)
		if connURI == "" {
			parseFailedTags = append(parseFailedTags, c.Tag)
			continue
		}

		p, err := parsers.ParseConfig(connURI)
		if err != nil || p.Config == nil {
			parseFailedTags = append(parseFailedTags, c.Tag)
			continue
		}

		// Apply the specified tag instead of auto-generating
		p.Config.Tag = c.Tag
		parsedConfigs = append(parsedConfigs, *p)
	}

	if callbacks.OnParseFailed != nil {
		callbacks.OnParseFailed(parseFailedTags)
	}

	if len(parsedConfigs) == 0 {
		if callbacks.OnError != nil {
			callbacks.OnError("No valid configs after parsing")
		}
		return []ProxyConfig{}
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

	// 2. Initial Validation
	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: workerPath,
	})
	if err != nil {
		if callbacks.OnError != nil {
			callbacks.OnError(fmt.Sprintf("Failed to create test runner: %v", err))
		}
		return []ProxyConfig{}
	}

	testMu.Lock()
	testRunner = tr
	testMu.Unlock()

	taggedConfigs, validationErrors, err := tr.Validate(ctx, parsedConfigs, runner.DefaultConfigTaggerFunc)

	tr.Close()
	testMu.Lock()
	testRunner = nil
	testMu.Unlock()

	if err != nil {
		if callbacks.OnError != nil {
			callbacks.OnError(fmt.Sprintf("Validation error: %v", err))
		}
		return []ProxyConfig{}
	}

	validateFailedTags := []string{}
	errMap := make(map[string]bool)
	for _, ve := range validationErrors {
		validateFailedTags = append(validateFailedTags, ve.Tag)
		errMap[ve.Tag] = true
	}

	if callbacks.OnValidateFailed != nil {
		callbacks.OnValidateFailed(validateFailedTags)
	}

	validConfigs := make([]parsers.ProxyConfig, 0)
	for _, c := range taggedConfigs {
		if c.Config != nil && !errMap[c.Config.Tag] {
			validConfigs = append(validConfigs, c)
		}
	}

	if len(validConfigs) == 0 {
		if callbacks.OnError != nil {
			callbacks.OnError("No valid configs after validation")
		}
		return []ProxyConfig{}
	}

	// 3. Batched Latency Tests
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
			TestURL: "https://www.google.com/generate_204",
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

	// 4. Wrap up working configs
	passedTags := make(map[string]struct{})
	for _, result := range allResults {
		if result.Error == nil {
			passedTags[result.Tag] = struct{}{}
		}
	}

	workingConfigs := []ProxyConfig{}
	for _, cfg := range validConfigs {
		if _, ok := passedTags[cfg.Config.Tag]; ok {
			workingConfigs = append(workingConfigs, ProxyConfig{
				Tag:     cfg.Config.Tag,
				ConnURI: cfg.ConnURI,
			})
		}
	}

	return workingConfigs
}
