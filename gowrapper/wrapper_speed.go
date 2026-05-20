package main

import (
"context"
"fmt"
"time"

"github.com/bluegradienthorizon/proxytoolbox/presets"
"github.com/bluegradienthorizon/proxytoolbox/runner"
"github.com/bluegradienthorizon/proxytoolbox/worker"
)

type SpeedTestCallbacks struct {
OnRoundStarted func(batch int, round int, total int)
OnProgress     func(tag string, speed float64, failed bool)
OnRoundEnded   func(batch int, round int)
}

type ProxySpeedConfig struct {
Tag     string  `json:"tag"`
ConnURI string  `json:"conn_uri"`
Speed   float64 `json:"speed"`
}

func RunSpeedTests(
provider string,
modeStr string,
targetBytes int64,
speedRounds int,
roundTimeout int,
testByBatches bool,
batchSize int,
targetTags []string,
callbacks SpeedTestCallbacks,
) ([]ProxySpeedConfig, error) {
testMu.Lock()
validConfigs := currentValidConfigs
workerPath := currentWorkerPath
testMu.Unlock()

if len(validConfigs) == 0 {
return nil, fmt.Errorf("No valid configs to test")
}

tagMap := make(map[string]bool)
for _, t := range targetTags {
tagMap[t] = true
}

var batchTags []string
// keep targetTags order
for _, t := range targetTags {
if tagMap[t] {
batchTags = append(batchTags, t)
}
}

if len(batchTags) == 0 {
return nil, fmt.Errorf("No matching configs found for speed test")
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
goBatchSize = len(batchTags)
}

var allResults []runner.SpeedTestResult

var stProvider worker.SpeedTestProvider
if provider == "cloudflare" {
stProvider = presets.CloudflareProvider
} else {
stProvider = presets.CloudflareProvider
}

stMode := runner.SpeedTestModeDownload
if modeStr == "upload" {
stMode = runner.SpeedTestModeUpload
}

for batchStart := 0; batchStart < len(batchTags); batchStart += goBatchSize {
if ctx.Err() != nil {
break
}

batchEnd := batchStart + goBatchSize
        if batchEnd > len(batchTags) {
            batchEnd = len(batchTags)
        }
currentBatchTags := batchTags[batchStart:batchEnd]
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

stSettings := runner.SpeedTestRunnerSettings{
BaseTestRunnerSettings: runner.BaseTestRunnerSettings{
SortResults:  true,
FilterFailed: true,
Timeout:      time.Duration(roundTimeout) * time.Second,
Rounds:       speedRounds,
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
Provider:    stProvider,
Mode:        stMode,
TargetBytes: targetBytes,
}

testResults, err := batchRunner.RunSpeedTests(ctx, currentBatchTags, stSettings)
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

passedTags := make(map[string]float64)
for _, result := range allResults {
if result.Error == nil {
passedTags[result.Tag] = result.Speed
}
}

workingConfigs := []ProxySpeedConfig{}
for _, cfg := range validConfigs {
if speed, ok := passedTags[cfg.Config.Tag]; ok {
workingConfigs = append(workingConfigs, ProxySpeedConfig{
Tag:     cfg.Config.Tag,
ConnURI: cfg.ConnURI,
Speed:   speed,
})
}
}

return workingConfigs, nil
}
