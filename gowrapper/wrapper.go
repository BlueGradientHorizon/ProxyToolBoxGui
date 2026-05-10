package main

/*
#include <jni.h>
#include <stdlib.h>
#include <stdbool.h>

static jstring NewString(JNIEnv *env, const jchar *chars, jsize len) {
    return (*env)->NewString(env, chars, len);
}

static jsize GetStringLength(JNIEnv *env, jstring str) {
    return (*env)->GetStringLength(env, str);
}

static const jchar* GetStringChars(JNIEnv *env, jstring str) {
    return (*env)->GetStringChars(env, str, NULL);
}

static void ReleaseStringChars(JNIEnv *env, jstring str, const jchar *chars) {
    (*env)->ReleaseStringChars(env, str, chars);
}

static jclass GetObjectClass(JNIEnv *env, jobject obj) {
    return (*env)->GetObjectClass(env, obj);
}

static jmethodID GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
    return (*env)->GetMethodID(env, clazz, name, sig);
}

static void callOnParseFailed(JNIEnv *env, jobject cb, jmethodID mid, jstring json) {
    (*env)->CallVoidMethod(env, cb, mid, json);
}

static void callOnValidateFailed(JNIEnv *env, jobject cb, jmethodID mid, jstring json) {
    (*env)->CallVoidMethod(env, cb, mid, json);
}

static void callOnRoundStarted(JNIEnv *env, jobject cb, jmethodID mid, jlong batch, jlong round, jlong total) {
    (*env)->CallVoidMethod(env, cb, mid, batch, round, total);
}

static void callOnProgress(JNIEnv *env, jobject cb, jmethodID mid, jstring tag, jlong delay, jboolean failed) {
    (*env)->CallVoidMethod(env, cb, mid, tag, delay, failed);
}

static void callOnRoundEnded(JNIEnv *env, jobject cb, jmethodID mid, jlong batch, jlong round) {
    (*env)->CallVoidMethod(env, cb, mid, batch, round);
}

static void callOnError(JNIEnv *env, jobject cb, jmethodID mid, jstring msg) {
    (*env)->CallVoidMethod(env, cb, mid, msg);
}

static void DeleteLocalRef(JNIEnv *env, jobject obj) {
    (*env)->DeleteLocalRef(env, obj);
}
*/
import "C"
import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"
	"unicode/utf16"
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

func JStringToString(env *C.JNIEnv, s C.jstring) string {
	if s == 0 {
		return ""
	}
	n := C.GetStringLength(env, s)
	if n == 0 {
		return ""
	}
	chars := C.GetStringChars(env, s)
	defer C.ReleaseStringChars(env, s, chars)
	u16s := make([]uint16, int(n))
	ptr := unsafe.Pointer(chars)
	size := unsafe.Sizeof(C.jchar(0))
	for i := 0; i < int(n); i++ {
		u16s[i] = *(*uint16)(unsafe.Pointer(uintptr(ptr) + uintptr(i)*size))
	}
	runes := utf16.Decode(u16s)
	return string(runes)
}

func StringToJString(env *C.JNIEnv, s string) C.jstring {
	r := []rune(s)
	u16 := utf16.Encode(r)
	if len(u16) == 0 {
		var empty C.jchar
		return C.NewString(env, &empty, 0)
	}
	carr := make([]C.jchar, len(u16))
	for i, v := range u16 {
		carr[i] = C.jchar(v)
	}
	return C.NewString(env, &carr[0], C.jsize(len(u16)))
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
//
//export Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeDiscoverWorkers
func Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeDiscoverWorkers(env *C.JNIEnv, clazz C.jclass, libraryPath C.jstring) C.jstring {
	goLibraryPath := JStringToString(env, libraryPath)
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
	return StringToJString(env, string(b))
}

// StopTests cancels any running latency tests and closes the active worker process.
//
//export Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeStopTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeStopTests(env *C.JNIEnv, clazz C.jclass) {
	testMu.Lock()
	defer testMu.Unlock()
	if testCancel != nil {
		testCancel()
	}
	if testRunner != nil {
		testRunner.Close()
	}
}

// RunLatencyTests parses, validates, and performs batched latency tests.
// Returns (json[]ProxyConfig workingConfigs, error).
//
//export Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeRunLatencyTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeRunLatencyTests(
	env *C.JNIEnv,
	clazz C.jclass,
	workerPath C.jstring,
	connUrisJson C.jstring,
	latencyRounds C.jint,
	roundTimeout C.jint,
	testByBatches C.jboolean,
	batchSize C.jint,
	callback C.jobject,
) C.jstring {
	goWorkerPath := JStringToString(env, workerPath)
	goConnUrisJson := JStringToString(env, connUrisJson)
	goTestByBatches := testByBatches != 0

	cbClass := C.GetObjectClass(env, callback)
	defer C.DeleteLocalRef(env, C.jobject(cbClass))

	cOnParseFailedJson := C.CString("onParseFailedJson")
	cOnValidateFailedJson := C.CString("onValidateFailedJson")
	cOnRoundStarted := C.CString("onRoundStarted")
	cOnProgress := C.CString("onProgress")
	cOnRoundEnded := C.CString("onRoundEnded")
	cOnError := C.CString("onError")

	cSigVString := C.CString("(Ljava/lang/String;)V")
	cSigVJJJ := C.CString("(JJJ)V")
	cSigVSJZ := C.CString("(Ljava/lang/String;JZ)V")
	cSigVJJ := C.CString("(JJ)V")

	midParseFailed := C.GetMethodID(env, cbClass, cOnParseFailedJson, cSigVString)
	midValidateFailed := C.GetMethodID(env, cbClass, cOnValidateFailedJson, cSigVString)
	midRoundStarted := C.GetMethodID(env, cbClass, cOnRoundStarted, cSigVJJJ)
	midProgress := C.GetMethodID(env, cbClass, cOnProgress, cSigVSJZ)
	midRoundEnded := C.GetMethodID(env, cbClass, cOnRoundEnded, cSigVJJ)
	midError := C.GetMethodID(env, cbClass, cOnError, cSigVString)

	defer func() {
		C.free(unsafe.Pointer(cOnParseFailedJson))
		C.free(unsafe.Pointer(cOnValidateFailedJson))
		C.free(unsafe.Pointer(cOnRoundStarted))
		C.free(unsafe.Pointer(cOnProgress))
		C.free(unsafe.Pointer(cOnRoundEnded))
		C.free(unsafe.Pointer(cOnError))
		C.free(unsafe.Pointer(cSigVString))
		C.free(unsafe.Pointer(cSigVJJJ))
		C.free(unsafe.Pointer(cSigVSJZ))
		C.free(unsafe.Pointer(cSigVJJ))
	}()

	invokeError := func(msg string) {
		jMsg := StringToJString(env, msg)
		C.callOnError(env, callback, midError, jMsg)
		C.DeleteLocalRef(env, C.jobject(jMsg))
	}

	var inputConfigs []ProxyConfig
	if err := json.Unmarshal([]byte(goConnUrisJson), &inputConfigs); err != nil {
		invokeError(fmt.Sprintf("Unmarshal input error: %v", err))
		return StringToJString(env, "[]")
	}

	for _, c := range inputConfigs {
		if strings.TrimSpace(c.Tag) == "" {
			invokeError("Empty tag found in config")
			return StringToJString(env, "[]")
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
		jTagsJson := StringToJString(env, string(b))
		C.callOnParseFailed(env, callback, midParseFailed, jTagsJson)
		C.DeleteLocalRef(env, C.jobject(jTagsJson))
	}

	if len(parsedConfigs) == 0 {
		invokeError("No valid configs after parsing")
		return StringToJString(env, "[]")
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
		invokeError(fmt.Sprintf("Failed to create test runner: %v", err))
		return StringToJString(env, "[]")
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
		invokeError(fmt.Sprintf("Validation error: %v", err))
		return StringToJString(env, "[]")
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
		jTagsJson := StringToJString(env, string(b))
		C.callOnValidateFailed(env, callback, midValidateFailed, jTagsJson)
		C.DeleteLocalRef(env, C.jobject(jTagsJson))
	}

	validConfigs := make([]parsers.ProxyConfig, 0)
	for _, c := range taggedConfigs {
		if c.Config != nil && !errMap[c.Config.Tag] {
			validConfigs = append(validConfigs, c)
		}
	}

	if len(validConfigs) == 0 {
		invokeError("No valid configs after validation")
		return StringToJString(env, "[]")
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
					C.callOnRoundStarted(env, callback, midRoundStarted, C.jlong(batchNum), C.jlong(round+1), C.jlong(outboundsLen))
				},
				ProgressCallback: func(result runner.LatencyTestResult) {
					jTag := StringToJString(env, result.Tag)
					var cFailed C.jboolean = 0
					if result.Error != nil {
						cFailed = 1
					}
					C.callOnProgress(env, callback, midProgress, jTag, C.jlong(result.Delay), cFailed)
					C.DeleteLocalRef(env, C.jobject(jTag))
				},
				RoundEndedCallback: func(round int) {
					C.callOnRoundEnded(env, callback, midRoundEnded, C.jlong(batchNum), C.jlong(round+1))
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
	return StringToJString(env, string(b))
}

func main() {}
