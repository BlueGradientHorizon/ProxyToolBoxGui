package main

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
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

type LatencyTestSettings struct {
	LatencyRounds int
	RoundTimeout  int
	TestByBatches bool
	BatchSize     int
}

type TestCallback interface {
	OnParseFailed(tagsJson string)
	OnValidateFailed(tagsJson string)
	OnRoundStarted(batchNum, roundNum, total int)
	OnProgress(tag string, delay int64, failed bool)
	OnRoundEnded(batchNum, roundNum int)
}

// DiscoverWorkers finds all valid worker programs in libraryPath.
// Returns JSON[]WorkerInfo.
func DiscoverWorkers(libraryPath string) (string, error) {
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

	b, err := json.Marshal(workers)
	return string(b), err
}

// RunLatencyTests parses, validates, and performs batched latency tests.
// Returns (json[]ProxyConfig workingConfigs, error).
func RunLatencyTests(workerPath string, connUrisJson string, performDedup bool, settings *LatencyTestSettings, callback TestCallback) (string, error) {
	var inputConfigs []ProxyConfig
	if err := json.Unmarshal([]byte(connUrisJson), &inputConfigs); err != nil {
		return "", err
	}

	// Ensure tags are present
	for _, c := range inputConfigs {
		if strings.TrimSpace(c.Tag) == "" {
			return "", fmt.Errorf("empty tag found")
		}
	}

	// 1. Parse configs and deduplicate
	seen := make(map[string]bool)
	var parsedConfigs []parsers.ProxyConfig
	parseFailedTags := []string{}

	for _, c := range inputConfigs {
		connURI := strings.TrimSpace(c.ConnURI)
		if connURI == "" {
			parseFailedTags = append(parseFailedTags, c.Tag)
			continue
		}
		if performDedup {
			if seen[connURI] {
				continue
			}
			seen[connURI] = true
		}

		p, err := parsers.ParseConfig(connURI)
		if err != nil || p.Config == nil {
			parseFailedTags = append(parseFailedTags, c.Tag)
			continue
		}

		// Apply the specified tag instead of auto-generating
		p.Config.Tag = c.Tag
		// p.ConnURI = c.ConnURI // wrong, p.ConnURI returned from ParseConfig may be different (fixed)
		parsedConfigs = append(parsedConfigs, *p)
	}

	// Emit parse error callback
	{
		b, _ := json.Marshal(parseFailedTags)
		callback.OnParseFailed(string(b))
	}

	if len(parsedConfigs) == 0 {
		return "", fmt.Errorf("no_valid_configs")
	}

	ctx := context.Background()

	// 2. Initial Validation
	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: workerPath,
	})
	if err != nil {
		return "", err
	}

	taggedConfigs, validationErrors, err := tr.Validate(ctx, parsedConfigs, runner.DefaultConfigTaggerFunc)
	tr.Close()
	if err != nil {
		return "", err
	}

	validateFailedTags := []string{}
	errMap := make(map[string]bool)
	for _, ve := range validationErrors {
		validateFailedTags = append(validateFailedTags, ve.Tag)
		errMap[ve.Tag] = true
	}

	// Emit validate error callback
	{
		b, _ := json.Marshal(validateFailedTags)
		callback.OnValidateFailed(string(b))
	}

	validConfigs := make([]parsers.ProxyConfig, 0)
	for _, c := range taggedConfigs {
		if c.Config != nil && !errMap[c.Config.Tag] {
			validConfigs = append(validConfigs, c)
		}
	}

	if len(validConfigs) == 0 {
		return "", fmt.Errorf("no_valid_configs")
	}

	// 3. Batched Latency Tests
	batchSize := settings.BatchSize
	if !settings.TestByBatches || batchSize <= 0 {
		batchSize = len(validConfigs)
	}

	var allResults []runner.LatencyTestResult

	for batchStart := 0; batchStart < len(validConfigs); batchStart += batchSize {
		batchEnd := min(batchStart+batchSize, len(validConfigs))
		batchConfigs := validConfigs[batchStart:batchEnd]
		batchNum := batchStart/batchSize + 1

		batchRunner, err := runner.NewTestRunner(runner.RunnerSettings{
			WorkerPath: workerPath,
		})
		if err != nil {
			continue
		}

		_, batchValidationErrors, err := batchRunner.Validate(ctx, batchConfigs, runner.DefaultConfigTaggerFunc)
		if err != nil {
			batchRunner.Close()
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
			continue
		}

		ltSettings := runner.LatencyTestRunnerSettings{
			BaseTestRunnerSettings: runner.BaseTestRunnerSettings{
				SortResults:  true,
				FilterFailed: true,
				Timeout:      time.Duration(settings.RoundTimeout) * time.Second,
				Rounds:       settings.LatencyRounds,
				RoundStartedCallback: func(round int, outboundsLen int) {
					callback.OnRoundStarted(batchNum, round+1, outboundsLen)
				},
				ProgressCallback: func(result runner.LatencyTestResult) {
					callback.OnProgress(result.Tag, result.Delay, result.Error != nil)
				},
				RoundEndedCallback: func(round int) {
					callback.OnRoundEnded(batchNum, round+1)
				},
			},
			TestURL: "https://www.google.com/generate_204",
		}

		testResults, err := batchRunner.RunLatencyTests(ctx, batchTags, ltSettings)
		if err == nil {
			allResults = append(allResults, testResults.Results...)
		}

		batchRunner.Close()
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

	b, err := json.Marshal(workingConfigs)
	return string(b), err
}

func main() {}
