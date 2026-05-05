package wrapper

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
    Type    string `json:"type"`
    Server  string `json:"server"`
    Port    int    `json:"port"`
    ConnURI string `json:"conn_uri"`
}

type LatencyTestSettings struct {
    PerformDedup  bool
    LatencyRounds int
    RoundTimeout  time.Duration
    TestByBatches bool
    BatchSize     int
}

type TestCallback interface {
    OnRoundStarted(batchNum, roundNum, total int)
    OnProgress(tag string, delay int64, failed bool)
    OnRoundEnded(batchNum, roundNum int)
}

// DiscoverWorkers finds all valid worker programs in libraryPath.
// Returns JSON []WorkerInfo.
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

// ParseConfigs parses connection URIs into ProxyConfig structs.
// Returns (json []ProxyConfig, duplicatedCount, parseErrorCount, error).
func ParseConfigs(configStrings []string, performDedup bool) (string, int, int, error) {
    seen := make(map[string]bool)
    var configs []parsers.ProxyConfig
    dupCount := 0
    parseErrs := 0

    for _, connURI := range configStrings {
        connURI = strings.TrimSpace(connURI)
        if connURI == "" {
            continue
        }
        if performDedup {
            if seen[connURI] {
                dupCount++
                continue
            }
            seen[connURI] = true
        }
        p, err := parsers.ParseConfig(connURI)
        if err != nil {
            parseErrs++
            continue
        }
        configs = append(configs, *p)
    }

    var out []ProxyConfig
    for _, c := range configs {
        if c.Config != nil {
            out = append(out, ProxyConfig{
                Tag:     c.Config.Tag,
                Type:    c.Config.Type,
                Server:  c.Config.Server,
                Port:    int(c.Config.Port),
                ConnURI: c.ConnURI,
            })
        }
    }

    b, err := json.Marshal(out)
    return string(b), dupCount, parseErrs, err
}

// ValidateConfigs validates configs against a worker program.
// Returns (json []ProxyConfig, validationErrorCount, error).
func ValidateConfigs(workerPath string, configsJson string) (string, int, error) {
    var configs []parsers.ProxyConfig
    if err := json.Unmarshal([]byte(configsJson), &configs); err != nil {
        return "", 0, err
    }

    ctx := context.Background()
    tr, err := runner.NewTestRunner(runner.RunnerSettings{
        WorkerPath: workerPath,
    })
    if err != nil {
        return "", 0, err
    }
    defer tr.Close()

    for i := range configs {
        if configs[i].Config != nil && configs[i].Config.Tag == "" {
            configs[i].Config.Tag = fmt.Sprintf("outbound-%d", i)
        }
    }

    taggedConfigs, validationErrors, err := tr.Validate(ctx, configs, runner.DefaultConfigTaggerFunc)
    if err != nil {
        return "", 0, err
    }

    var out []ProxyConfig
    for _, c := range taggedConfigs {
        if c.Config != nil {
            out = append(out, ProxyConfig{
                Tag:     c.Config.Tag,
                Type:    c.Config.Type,
                Server:  c.Config.Server,
                Port:    int(c.Config.Port),
                ConnURI: c.ConnURI,
            })
        }
    }

    b, err := json.Marshal(out)
    return string(b), len(validationErrors), err
}

// RunLatencyTests performs batched latency tests.
// Returns (json []ProxyConfig workingConfigs, error).
func RunLatencyTests(workerPath string, configsJson string, settings LatencyTestSettings, callback TestCallback) (string, error) {
    var configs []parsers.ProxyConfig
    if err := json.Unmarshal([]byte(configsJson), &configs); err != nil {
        return "", err
    }

    ctx := context.Background()

    // Ensure tags
    for i := range configs {
        if configs[i].Config != nil && configs[i].Config.Tag == "" {
            configs[i].Config.Tag = fmt.Sprintf("outbound-%d", i)
        }
    }

    // Initial validation
    tr, err := runner.NewTestRunner(runner.RunnerSettings{
        WorkerPath: workerPath,
    })
    if err != nil {
        return "", err
    }

    taggedConfigs, validationErrors, err := tr.Validate(ctx, configs, runner.DefaultConfigTaggerFunc)
    tr.Close()
    if err != nil {
        return "", err
    }

    errMap := make(map[string]bool)
    for _, ve := range validationErrors {
        errMap[ve.Tag] = true
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

    batchSize := settings.BatchSize
    if !settings.TestByBatches {
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
                Timeout:      settings.RoundTimeout,
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

    passedTags := make(map[string]struct{})
    for _, result := range allResults {
        if result.Error == nil {
            passedTags[result.Tag] = struct{}{}
        }
    }

    var workingConfigs []ProxyConfig
    for _, cfg := range validConfigs {
        if _, ok := passedTags[cfg.Config.Tag]; ok {
            workingConfigs = append(workingConfigs, ProxyConfig{
                Tag:     cfg.Config.Tag,
                Type:    cfg.Config.Type,
                Server:  cfg.Config.Server,
                Port:    int(cfg.Config.Port),
                ConnURI: cfg.ConnURI,
            })
        }
    }

    b, err := json.Marshal(workingConfigs)
    return string(b), err
}

func min(a, b int) int {
    if a < b {
        return a
    }
    return b
}
