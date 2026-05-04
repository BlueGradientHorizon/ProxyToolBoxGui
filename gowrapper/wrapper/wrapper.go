package wrapper

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/bluegradienthorizon/proxytoolbox/parsers"
	"github.com/bluegradienthorizon/proxytoolbox/registry"
	"github.com/bluegradienthorizon/proxytoolbox/runner"
)

type Status int

const (
	StatusIdle Status = iota
	StatusDownloading
	StatusParsing
	StatusValidating
	StatusTesting
	StatusCompleted
	StatusError
)

type TestPhase int

const (
	TestPhaseNone TestPhase = iota
	TestPhaseValidation
	TestPhaseLatency
)

type WorkerInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Path    string `json:"path"`
}

type ConfigStats struct {
	Found      int `json:"found"`
	Duplicated int `json:"duplicated"`
	ParseErr   int `json:"parse_err"`
	ValidErr   int `json:"valid_err"`
	Working    int `json:"working"`
}

type BatchProgress struct {
	BatchNum  int `json:"batch_num"`
	RoundNum  int `json:"round_num"`
	Total     int `json:"total"`
	Running   int `json:"running"`
	Failed    int `json:"failed"`
	Succeeded int `json:"succeeded"`
}

type TestProgress struct {
	Phase           TestPhase       `json:"phase"`
	CurrentBatch    int             `json:"current_batch"`
	TotalBatches    int             `json:"total_batches"`
	CurrentRound    int             `json:"current_round"`
	TotalRounds     int             `json:"total_rounds"`
	BatchProgresses []BatchProgress `json:"batch_progresses"`
	ElapsedSeconds  int             `json:"elapsed_seconds"`
	TotalSeconds    int             `json:"total_seconds"`
	IsRunning       bool            `json:"is_running"`
}

type DownloadProgress struct {
	Total     int  `json:"total"`
	Succeeded int  `json:"succeeded"`
	Failed    int  `json:"failed"`
	IsRunning bool `json:"is_running"`
}

type ProxyConfig struct {
	Tag     string `json:"tag"`
	Type    string `json:"type"`
	Server  string `json:"server"`
	Port    int    `json:"port"`
	ConnURI string `json:"conn_uri"`
}

type WrapperSettings struct {
	DownloadTimeout    int  `json:"download_timeout"`
	PerformDedup       bool `json:"perform_dedup"`
	LatencyRounds      int  `json:"latency_rounds"`
	RoundTimeout       int  `json:"round_timeout"`
	TestByBatches      bool `json:"test_by_batches"`
	BatchSize          int  `json:"batch_size"`
	AutoStartWebServer bool `json:"auto_start_web_server"`
	WebServerPort      int  `json:"web_server_port"`
	WebServerLocalhost bool `json:"web_server_localhost"`
}

func DefaultSettings() WrapperSettings {
	return WrapperSettings{
		DownloadTimeout:    10,
		PerformDedup:       true,
		LatencyRounds:      3,
		RoundTimeout:       10,
		TestByBatches:      true,
		BatchSize:          5000,
		AutoStartWebServer: true,
		WebServerPort:      35240,
		WebServerLocalhost: true,
	}
}

type TestCallback interface {
	OnRoundStarted(batchNum, roundNum, total int)
	OnProgress(tag string, delay int64, failed bool)
	OnRoundEnded(batchNum, roundNum int)
	OnBatchCompleted(batchNum, succeeded, failed int)
}

type Wrapper struct {
	mu               sync.RWMutex
	status           Status
	workers          []WorkerInfo
	selectedWorker   string
	configs          []parsers.ProxyConfig
	workingConfigs   []parsers.ProxyConfig
	stats            ConfigStats
	testProgress     TestProgress
	downloadProgress DownloadProgress
	settings         WrapperSettings
}

func NewWrapper() *Wrapper {
	return &Wrapper{
		status:   StatusIdle,
		settings: DefaultSettings(),
	}
}

func (w *Wrapper) DiscoverWorkers(libraryPath string) string {
	w.mu.Lock()
	defer w.mu.Unlock()

	reg := registry.NewRegistry()
	reg.Discover(libraryPath)

	workersMap := reg.All()
	w.workers = make([]WorkerInfo, 0)

	for _, list := range workersMap {
		for _, info := range list {
			w.workers = append(w.workers, WorkerInfo{
				Name:    info.Name,
				Version: info.Version,
				Path:    info.Path,
			})
		}
	}

	if len(w.workers) > 0 {
		w.selectedWorker = w.workers[0].Path
	}

	b, _ := json.Marshal(w.workers)
	return string(b)
}

func (w *Wrapper) GetWorkers() string {
	w.mu.RLock()
	defer w.mu.RUnlock()
	b, _ := json.Marshal(w.workers)
	return string(b)
}

func (w *Wrapper) SelectWorker(path string) bool {
	w.mu.Lock()
	defer w.mu.Unlock()

	for _, wk := range w.workers {
		if wk.Path == path {
			w.selectedWorker = path
			return true
		}
	}
	return false
}

func (w *Wrapper) GetSelectedWorker() string {
	w.mu.RLock()
	defer w.mu.RUnlock()
	return w.selectedWorker
}

func (w *Wrapper) SetSettings(jsonSettings string) string {
	var settings WrapperSettings
	if err := json.Unmarshal([]byte(jsonSettings), &settings); err != nil {
		return fmt.Sprintf(`{"error":"%s"}`, err.Error())
	}
	w.mu.Lock()
	w.settings = settings
	w.mu.Unlock()
	return `{"success":true}`
}

func (w *Wrapper) GetSettings() string {
	w.mu.RLock()
	defer w.mu.RUnlock()
	b, _ := json.Marshal(w.settings)
	return string(b)
}

func (w *Wrapper) ParseConfigs(configStrings []string) string {
	w.mu.Lock()
	defer w.mu.Unlock()

	w.status = StatusParsing
	w.stats = ConfigStats{Found: len(configStrings)}

	var configs []parsers.ProxyConfig
	parseErrors := 0

	for _, connURI := range configStrings {
		connURI = strings.TrimSpace(connURI)
		if connURI == "" {
			continue
		}
		p, err := parsers.ParseConfig(connURI)
		if err != nil {
			parseErrors++
			continue
		}
		configs = append(configs, *p)
	}

	w.stats.ParseErr = parseErrors
	w.configs = configs
	w.stats.Found = len(configs)

	result := map[string]any{
		"success": len(configs),
		"errors":  parseErrors,
	}
	b, _ := json.Marshal(result)
	return string(b)
}

func (w *Wrapper) GetConfigs() string {
	w.mu.RLock()
	defer w.mu.RUnlock()

	configs := make([]ProxyConfig, 0, len(w.configs))
	for _, c := range w.configs {
		if c.Config != nil {
			configs = append(configs, ProxyConfig{
				Tag:     c.Config.Tag,
				Type:    c.Config.Type,
				Server:  c.Config.Server,
				Port:    int(c.Config.Port),
				ConnURI: c.ConnURI,
			})
		}
	}
	b, _ := json.Marshal(configs)
	return string(b)
}

func (w *Wrapper) GetWorkingConfigs() string {
	w.mu.RLock()
	defer w.mu.RUnlock()

	configs := make([]ProxyConfig, 0, len(w.workingConfigs))
	for _, c := range w.workingConfigs {
		if c.Config != nil {
			configs = append(configs, ProxyConfig{
				Tag:     c.Config.Tag,
				Type:    c.Config.Type,
				Server:  c.Config.Server,
				Port:    int(c.Config.Port),
				ConnURI: c.ConnURI,
			})
		}
	}
	b, _ := json.Marshal(configs)
	return string(b)
}

func (w *Wrapper) GetStats() string {
	w.mu.RLock()
	defer w.mu.RUnlock()
	b, _ := json.Marshal(w.stats)
	return string(b)
}

func (w *Wrapper) GetTestProgress() string {
	w.mu.RLock()
	defer w.mu.RUnlock()
	b, _ := json.Marshal(w.testProgress)
	return string(b)
}

func (w *Wrapper) GetDownloadProgress() string {
	w.mu.RLock()
	defer w.mu.RUnlock()
	b, _ := json.Marshal(w.downloadProgress)
	return string(b)
}

func (w *Wrapper) GetStatus() int {
	w.mu.RLock()
	defer w.mu.RUnlock()
	return int(w.status)
}

func (w *Wrapper) ValidateConfigs() string {
	w.mu.Lock()
	if w.selectedWorker == "" {
		w.mu.Unlock()
		return `{"error":"no_worker"}`
	}
	w.status = StatusValidating
	w.testProgress = TestProgress{
		Phase:     TestPhaseValidation,
		IsRunning: true,
	}
	w.mu.Unlock()

	ctx := context.Background()
	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: w.selectedWorker,
	})
	if err != nil {
		w.mu.Lock()
		w.status = StatusError
		w.mu.Unlock()
		return fmt.Sprintf(`{"error":"%s"}`, err.Error())
	}
	defer tr.Close()

	w.mu.Lock()
	configs := make([]parsers.ProxyConfig, len(w.configs))
	copy(configs, w.configs)
	w.mu.Unlock()

	for i := range configs {
		if configs[i].Config != nil && configs[i].Config.Tag == "" {
			configs[i].Config.Tag = fmt.Sprintf("outbound-%d", i)
		}
	}

	_, validationErrors, err := tr.Validate(ctx, configs, runner.DefaultConfigTaggerFunc)
	if err != nil {
		w.mu.Lock()
		w.status = StatusError
		w.mu.Unlock()
		return fmt.Sprintf(`{"error":"%s"}`, err.Error())
	}

	validCount := 0
	for _, c := range configs {
		if c.Config != nil {
			validCount++
		}
	}

	w.mu.Lock()
	w.stats.ValidErr = len(validationErrors)
	w.stats.Working = validCount - len(validationErrors)
	w.status = StatusIdle
	w.testProgress.IsRunning = false
	w.mu.Unlock()

	result := map[string]any{
		"valid":  validCount - len(validationErrors),
		"errors": len(validationErrors),
	}
	b, _ := json.Marshal(result)
	return string(b)
}

func (w *Wrapper) RunLatencyTests(callback TestCallback) string {
	w.mu.Lock()
	if w.selectedWorker == "" {
		w.mu.Unlock()
		return `{"error":"no_worker"}`
	}
	w.status = StatusTesting
	settings := w.settings
	configs := make([]parsers.ProxyConfig, len(w.configs))
	copy(configs, w.configs)
	w.mu.Unlock()

	ctx := context.Background()
	tr, err := runner.NewTestRunner(runner.RunnerSettings{
		WorkerPath: w.selectedWorker,
	})
	if err != nil {
		w.mu.Lock()
		w.status = StatusError
		w.mu.Unlock()
		return fmt.Sprintf(`{"error":"%s"}`, err.Error())
	}
	defer tr.Close()

	for i := range configs {
		if configs[i].Config != nil && configs[i].Config.Tag == "" {
			configs[i].Config.Tag = fmt.Sprintf("outbound-%d", i)
		}
	}

	_, validationErrors, err := tr.Validate(ctx, configs, runner.DefaultConfigTaggerFunc)
	if err != nil {
		w.mu.Lock()
		w.status = StatusError
		w.mu.Unlock()
		return fmt.Sprintf(`{"error":"%s"}`, err.Error())
	}

	validConfigs := make([]parsers.ProxyConfig, 0)
	errMap := make(map[string]bool)
	for _, ve := range validationErrors {
		errMap[ve.Tag] = true
	}
	for _, c := range configs {
		if c.Config != nil && !errMap[c.Config.Tag] {
			validConfigs = append(validConfigs, c)
		}
	}

	if len(validConfigs) == 0 {
		w.mu.Lock()
		w.status = StatusIdle
		w.mu.Unlock()
		return `{"error":"no_valid_configs"}`
	}

	batchSize := settings.BatchSize
	if !settings.TestByBatches {
		batchSize = len(validConfigs)
	}

	totalBatches := (len(validConfigs) + batchSize - 1) / batchSize

	var allResults []runner.LatencyTestResult

	for batchStart := 0; batchStart < len(validConfigs); batchStart += batchSize {
		batchEnd := min(batchStart+batchSize, len(validConfigs))
		batchConfigs := validConfigs[batchStart:batchEnd]
		batchNum := batchStart/batchSize + 1

		w.mu.Lock()
		w.testProgress = TestProgress{
			Phase:        TestPhaseLatency,
			CurrentBatch: batchNum,
			TotalBatches: totalBatches,
			IsRunning:    true,
		}
		w.mu.Unlock()

		batchRunner, err := runner.NewTestRunner(runner.RunnerSettings{
			WorkerPath: w.selectedWorker,
		})
		if err != nil {
			continue
		}

		_, validTags, err := batchRunner.Validate(ctx, batchConfigs, runner.DefaultConfigTaggerFunc)
		if err != nil {
			batchRunner.Close()
			continue
		}

		if len(validTags) == 0 {
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
					w.mu.Lock()
					w.testProgress.CurrentRound = round + 1
					w.testProgress.TotalRounds = settings.LatencyRounds
					w.testProgress.ElapsedSeconds = 0
					w.testProgress.TotalSeconds = settings.RoundTimeout * outboundsLen
					w.mu.Unlock()
					callback.OnRoundStarted(batchNum, round+1, outboundsLen)
				},
				ProgressCallback: func(result runner.LatencyTestResult) {
					w.mu.Lock()
					w.testProgress.ElapsedSeconds++
					w.mu.Unlock()
					callback.OnProgress(result.Tag, result.Delay, result.Error != nil)
				},
				RoundEndedCallback: func(round int) {
					callback.OnRoundEnded(batchNum, round+1)
				},
			},
			TestURL: "https://www.google.com/generate_204",
		}

		testResults, err := batchRunner.RunLatencyTests(ctx, validTags, ltSettings)
		if err != nil {
			batchRunner.Close()
			continue
		}

		allResults = append(allResults, testResults.Results...)
		batchRunner.Close()
	}

	w.mu.Lock()
	w.workingConfigs = make([]parsers.ProxyConfig, 0)
	passedTags := make(map[string]struct{})
	for _, result := range allResults {
		if result.Error == nil {
			passedTags[result.Tag] = struct{}{}
		}
	}
	for _, cfg := range validConfigs {
		if _, ok := passedTags[cfg.Config.Tag]; ok {
			w.workingConfigs = append(w.workingConfigs, cfg)
		}
	}
	w.stats.Working = len(w.workingConfigs)
	w.status = StatusCompleted
	w.testProgress.IsRunning = false
	w.mu.Unlock()

	result := map[string]any{
		"working": len(w.workingConfigs),
		"total":   len(validConfigs),
	}
	b, _ := json.Marshal(result)
	return string(b)
}

func (w *Wrapper) GetWorkingProfileURIs() []string {
	w.mu.RLock()
	defer w.mu.RUnlock()

	uris := make([]string, 0, len(w.workingConfigs))
	for _, c := range w.workingConfigs {
		uris = append(uris, c.ConnURI)
	}
	return uris
}

func GetLibraryPath() string {
	e, _ := os.Executable()
	dir := filepath.Dir(e)

	if runtime.GOOS == "android" {
		return filepath.Join(dir, "lib")
	}
	return dir
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}