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

static void callOnRoundStarted(JNIEnv *env, jobject cb, jmethodID mid, jlong batch, jlong round, jlong total) {
    (*env)->CallVoidMethod(env, cb, mid, batch, round, total);
}

static void callOnProgress(JNIEnv *env, jobject cb, jmethodID mid, jstring tag, jlong delay, jboolean failed) {
    (*env)->CallVoidMethod(env, cb, mid, tag, delay, failed);
}

static void callOnRoundEnded(JNIEnv *env, jobject cb, jmethodID mid, jlong batch, jlong round) {
    (*env)->CallVoidMethod(env, cb, mid, batch, round);
}

static void callOnSpeedProgress(JNIEnv *env, jobject cb, jmethodID mid, jstring tag, jdouble speed, jboolean failed) {
    (*env)->CallVoidMethod(env, cb, mid, tag, speed, failed);
}

static void DeleteLocalRef(JNIEnv *env, jobject obj) {
    (*env)->DeleteLocalRef(env, obj);
}
*/
import "C"
import (
	"encoding/json"
	"fmt"
	"unicode/utf16"
	"unsafe"
)

type NativeResponse struct {
	Data  string `json:"data"`
	Error string `json:"error"`
}

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

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverWorkers
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverWorkers(env *C.JNIEnv, clazz C.jclass, libraryPath C.jstring) C.jstring {
	goLibraryPath := JStringToString(env, libraryPath)
	workers, err := DiscoverWorkers(goLibraryPath)
	var resp NativeResponse
	if err != nil {
		resp = NativeResponse{Error: err.Error()}
	} else {
		b, _ := json.Marshal(workers)
		resp = NativeResponse{Data: string(b)}
	}
	b, _ := json.Marshal(resp)
	return StringToJString(env, string(b))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeStopTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeStopTests(env *C.JNIEnv, clazz C.jclass) {
	StopTests()
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeInitializeRunner
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeInitializeRunner(
	env *C.JNIEnv,
	clazz C.jclass,
	workerPath C.jstring,
) C.jstring {
	goWorkerPath := JStringToString(env, workerPath)
	err := InitializeRunner(goWorkerPath)
	var resp NativeResponse
	if err != nil {
		resp = NativeResponse{Error: err.Error()}
	} else {
		resp = NativeResponse{Data: "{}"}
	}
	b, _ := json.Marshal(resp)
	return StringToJString(env, string(b))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeParseConfigs
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeParseConfigs(
	env *C.JNIEnv,
	clazz C.jclass,
	connUrisJson C.jstring,
) C.jstring {
	goConnUrisJson := JStringToString(env, connUrisJson)

	var inputConfigs []ProxyConfig
	if err := json.Unmarshal([]byte(goConnUrisJson), &inputConfigs); err != nil {
		resp := NativeResponse{Error: fmt.Sprintf("Unmarshal input error: %v", err)}
		b, _ := json.Marshal(resp)
		return StringToJString(env, string(b))
	}

	parseErrors, err := ParseConfigs(inputConfigs)
	var resp NativeResponse
	if err != nil {
		resp = NativeResponse{Error: err.Error()}
	} else {
		b, _ := json.Marshal(parseErrors)
		resp = NativeResponse{Data: string(b)}
	}
	b, _ := json.Marshal(resp)
	return StringToJString(env, string(b))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeValidateConfigs
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeValidateConfigs(
	env *C.JNIEnv,
	clazz C.jclass,
) C.jstring {
	validateErrors, err := ValidateConfigs()
	var resp NativeResponse
	if err != nil {
		resp = NativeResponse{Error: err.Error()}
	} else {
		b, _ := json.Marshal(validateErrors)
		resp = NativeResponse{Data: string(b)}
	}
	b, _ := json.Marshal(resp)
	return StringToJString(env, string(b))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunLatencyTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunLatencyTests(
	env *C.JNIEnv,
	clazz C.jclass,
	testUrl C.jstring,
	latencyRounds C.jint,
	roundTimeout C.jint,
	testByBatches C.jboolean,
	batchSize C.jint,
	callback C.jobject,
) C.jstring {
	goTestUrl := JStringToString(env, testUrl)
	goTestByBatches := testByBatches != 0

	cbClass := C.GetObjectClass(env, callback)
	defer C.DeleteLocalRef(env, C.jobject(cbClass))

	cOnRoundStarted := C.CString("onRoundStarted")
	cOnProgress := C.CString("onProgress")
	cOnRoundEnded := C.CString("onRoundEnded")

	cSigVJJJ := C.CString("(JJJ)V")
	cSigVSJZ := C.CString("(Ljava/lang/String;JZ)V")
	cSigVJJ := C.CString("(JJ)V")

	midRoundStarted := C.GetMethodID(env, cbClass, cOnRoundStarted, cSigVJJJ)
	midProgress := C.GetMethodID(env, cbClass, cOnProgress, cSigVSJZ)
	midRoundEnded := C.GetMethodID(env, cbClass, cOnRoundEnded, cSigVJJ)

	defer func() {
		C.free(unsafe.Pointer(cOnRoundStarted))
		C.free(unsafe.Pointer(cOnProgress))
		C.free(unsafe.Pointer(cOnRoundEnded))
		C.free(unsafe.Pointer(cSigVJJJ))
		C.free(unsafe.Pointer(cSigVSJZ))
		C.free(unsafe.Pointer(cSigVJJ))
	}()

	callbacks := TestCallbacks{
		OnRoundStarted: func(batch int, round int, total int) {
			C.callOnRoundStarted(env, callback, midRoundStarted, C.jlong(batch), C.jlong(round), C.jlong(total))
		},
		OnProgress: func(tag string, delay int64, failed bool) {
			jTag := StringToJString(env, tag)
			var cFailed C.jboolean = 0
			if failed {
				cFailed = 1
			}
			C.callOnProgress(env, callback, midProgress, jTag, C.jlong(delay), cFailed)
			C.DeleteLocalRef(env, C.jobject(jTag))
		},
		OnRoundEnded: func(batch int, round int) {
			C.callOnRoundEnded(env, callback, midRoundEnded, C.jlong(batch), C.jlong(round))
		},
	}

	workingConfigs, err := RunLatencyTests(
		goTestUrl,
		int(latencyRounds),
		int(roundTimeout),
		goTestByBatches,
		int(batchSize),
		callbacks,
	)

	var resp NativeResponse
	if err != nil {
		resp = NativeResponse{Error: err.Error()}
	} else {
		b, _ := json.Marshal(workingConfigs)
		resp = NativeResponse{Data: string(b)}
	}
	b, _ := json.Marshal(resp)
	return StringToJString(env, string(b))
}

func main() {}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunSpeedTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunSpeedTests(
env *C.JNIEnv,
clazz C.jclass,
provider C.jstring,
modeStr C.jstring,
targetBytes C.jlong,
speedRounds C.jint,
roundTimeout C.jint,
testByBatches C.jboolean,
batchSize C.jint,
tagsJson C.jstring,
callback C.jobject,
) C.jstring {
goProvider := JStringToString(env, provider)
goModeStr := JStringToString(env, modeStr)
goTagsJson := JStringToString(env, tagsJson)
goTestByBatches := testByBatches != 0

var targetTags []string
if err := json.Unmarshal([]byte(goTagsJson), &targetTags); err != nil {
resp := NativeResponse{Error: fmt.Sprintf("Unmarshal tags error: %v", err)}
b, _ := json.Marshal(resp)
return StringToJString(env, string(b))
}

cbClass := C.GetObjectClass(env, callback)
defer C.DeleteLocalRef(env, C.jobject(cbClass))

cOnRoundStarted := C.CString("onRoundStarted")
cOnProgress := C.CString("onProgress")
cOnRoundEnded := C.CString("onRoundEnded")

cSigVJJJ := C.CString("(JJJ)V")
cSigVSFZ := C.CString("(Ljava/lang/String;DZ)V")
cSigVJJ := C.CString("(JJ)V")

midRoundStarted := C.GetMethodID(env, cbClass, cOnRoundStarted, cSigVJJJ)
midProgress := C.GetMethodID(env, cbClass, cOnProgress, cSigVSFZ)
midRoundEnded := C.GetMethodID(env, cbClass, cOnRoundEnded, cSigVJJ)

defer func() {
C.free(unsafe.Pointer(cOnRoundStarted))
C.free(unsafe.Pointer(cOnProgress))
C.free(unsafe.Pointer(cOnRoundEnded))
C.free(unsafe.Pointer(cSigVJJJ))
C.free(unsafe.Pointer(cSigVSFZ))
C.free(unsafe.Pointer(cSigVJJ))
}()

callbacks := SpeedTestCallbacks{
OnRoundStarted: func(batch int, round int, total int) {
C.callOnRoundStarted(env, callback, midRoundStarted, C.jlong(batch), C.jlong(round), C.jlong(total))
},
OnProgress: func(tag string, speed float64, failed bool) {
jTag := StringToJString(env, tag)
var cFailed C.jboolean = 0
if failed {
cFailed = 1
}
// Wait! We need a new C helper for Double
C.callOnSpeedProgress(env, callback, midProgress, jTag, C.jdouble(speed), cFailed)
C.DeleteLocalRef(env, C.jobject(jTag))
},
OnRoundEnded: func(batch int, round int) {
C.callOnRoundEnded(env, callback, midRoundEnded, C.jlong(batch), C.jlong(round))
},
}

workingConfigs, err := RunSpeedTests(
goProvider,
goModeStr,
int64(targetBytes),
int(speedRounds),
int(roundTimeout),
goTestByBatches,
int(batchSize),
targetTags,
callbacks,
)

var resp NativeResponse
if err != nil {
resp = NativeResponse{Error: err.Error()}
} else {
b, _ := json.Marshal(workingConfigs)
resp = NativeResponse{Data: string(b)}
}
b, _ := json.Marshal(resp)
return StringToJString(env, string(b))
}
