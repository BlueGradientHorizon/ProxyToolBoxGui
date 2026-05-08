package main

/*
#include <stdlib.h>
#include <stdbool.h>

typedef void (*cb_parse_failed_t)(const char* tagsJson);
typedef void (*cb_validate_failed_t)(const char* tagsJson);
typedef void (*cb_round_started_t)(int batchNum, int roundNum, int total);
typedef void (*cb_progress_t)(const char* tag, long long delay, int failed);
typedef void (*cb_round_ended_t)(int batchNum, int roundNum);

static inline void invoke_parse_failed(cb_parse_failed_t cb, const char* tagsJson) { cb(tagsJson); }
static inline void invoke_validate_failed(cb_validate_failed_t cb, const char* tagsJson) { cb(tagsJson); }
static inline void invoke_round_started(cb_round_started_t cb, int batchNum, int roundNum, int total) { cb(batchNum, roundNum, total); }
static inline void invoke_progress(cb_progress_t cb, const char* tag, long long delay, int failed) { cb(tag, delay, failed); }
static inline void invoke_round_ended(cb_round_ended_t cb, int batchNum, int roundNum) { cb(batchNum, roundNum); }
*/
import "C"
import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"
	"unsafe"

	"github.com/bluegradienthorizon/proxytoolbox/parsers"
	"github.com/bluegradienthorizon/proxytoolbox/registry"
	"github.com/bluegradienthorizon/proxytoolbox/runner"
)

var (
	testMu     sync.Mutex
	testCancel context.CancelFunc
	testRunner *runner.TestRunner
)

// StopTests cancels any running latency tests and closes the active worker process.
//export StopTests
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

type WorkerInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Path    string `json:"path"`
}

type ProxyConfig struct {
	Tag     string `json:"tag"`
	ConnURI string `json:"conn_uri"`
}

// DiscoverWorkers finds all valid worker programs in libraryPath.
// Returns JSON[]WorkerInfo.
//export DiscoverWorkers
func DiscoverWorkers(libraryPath *C.char) *C.char {
	goLibraryPath := C.GoString(libraryPath)
	reg := registry.NewRegistry()
	reg.Discover(goLibraryPath)

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

	b, _ := json.Marshal(workers)
	return C.CString(string(b))
}

// RunLatencyTests parses, validates, and performs batched latency tests.
// Returns (json[]ProxyConfig workingConfigs, error).
//export RunLatencyTests
func RunLatencyTests(
	workerPath *C.char,
	connUrisJson *C.char,
	latencyRounds C.int,
	roundTimeout C.int,
	testByBatches C.int,
	batchSize C.int,
	cbParseFailed C.cb_parse_failed_t,
	cbValidateFailed C.cb_validate_failed_t,
	cbRoundStarted C.cb_round_started_t,
	cbProgress C.cb_progress_t,
	cbRoundEnded C.cb_round_ended_t,
) *C.char {
	goWorkerPath := C.GoString(workerPath)
	goConnUrisJson := C.GoString(connUrisJson)
	goTestByBatches := testByBatches != 0

	var inputConfigs []ProxyConfig
	if err := json.Unmarshal([]byte(goConnUrisJson), &inputConfigs); err != nil {
		return C.CString(fmt.Sprintf("[]"))
	}

	// Ensure tags are present
	for _, c := range inputConfigs {
		if strings.TrimSpace(c.Tag) == "" {
			return C.CString("[]") // TODO: should return "empty tag found" error
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
		// p.ConnURI = c.ConnURI // wrong, p.ConnURI returned from ParseConfig may be different (fixed)
		parsedConfigs = append(parsedConfigs, *p)
	}

	// Emit parse error callback
	{
		b, _ := json.Marshal(parseFailedTags)
		cTagsJson := C.CString(string(b))
		C.invoke_parse_failed(cbParseFailed, cTagsJson)
		C.free(unsafe.Pointer(cTagsJson))
	}

	if len(parsedConfigs) == 0 {
		return C.CString("[]") // TODO: should return "no valid configs" error
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
		WorkerPath: goWorkerPath,
	})
	if err != nil {
		return C.CString("[]")
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
		return C.CString("[]") // TODO: should return err
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
		cTagsJson := C.CString(string(b))
		C.invoke_validate_failed(cbValidateFailed, cTagsJson)
		C.free(unsafe.Pointer(cTagsJson))
	}

	validConfigs := make([]parsers.ProxyConfig, 0)
	for _, c := range taggedConfigs {
		if c.Config != nil && !errMap[c.Config.Tag] {
			validConfigs = append(validConfigs, c)
		}
	}

	if len(validConfigs) == 0 {
		return C.CString("[]") // TODO: should return "no valid configs" error
	}

	// 3. Batched Latency Tests
	goBatchSize := int(batchSize)
	if !goTestByBatches || goBatchSize <= 0 {
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
			WorkerPath: goWorkerPath,
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
				Timeout:      time.Duration(int(roundTimeout)) * time.Second,
				Rounds:       int(latencyRounds),
				RoundStartedCallback: func(round int, outboundsLen int) {
					C.invoke_round_started(cbRoundStarted, C.int(batchNum), C.int(round+1), C.int(outboundsLen))
				},
				ProgressCallback: func(result runner.LatencyTestResult) {
					cTag := C.CString(result.Tag)
					cFailed := 0
					if result.Error != nil {
						cFailed = 1
					}
					C.invoke_progress(cbProgress, cTag, C.longlong(result.Delay), C.int(cFailed))
					C.free(unsafe.Pointer(cTag))
				},
				RoundEndedCallback: func(round int) {
					C.invoke_round_ended(cbRoundEnded, C.int(batchNum), C.int(round+1))
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

	b, _ := json.Marshal(workingConfigs)
	return C.CString(string(b))
}

//export FreeString
func FreeString(ptr *C.char) {
	C.free(unsafe.Pointer(ptr))
}

func main() {}
