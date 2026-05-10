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
	"encoding/json"
	"unicode/utf16"
	"unsafe"
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

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeDiscoverWorkers
func Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeDiscoverWorkers(env *C.JNIEnv, clazz C.jclass, libraryPath C.jstring) C.jstring {
	goLibraryPath := JStringToString(env, libraryPath)
	workers := DiscoverWorkers(goLibraryPath)
	b, _ := json.Marshal(workers)
	return StringToJString(env, string(b))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeStopTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeStopTests(env *C.JNIEnv, clazz C.jclass) {
	StopTests()
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeRunLatencyTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridge_nativeRunLatencyTests(
	env *C.JNIEnv,
	clazz C.jclass,
	workerPath C.jstring,
	testUrl C.jstring,
	connUrisJson C.jstring,
	latencyRounds C.jint,
	roundTimeout C.jint,
	testByBatches C.jboolean,
	batchSize C.jint,
	callback C.jobject,
) C.jstring {
	goWorkerPath := JStringToString(env, workerPath)
	goTestUrl := JStringToString(env, testUrl)
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

	callbacks := TestCallbacks{
		OnParseFailed: func(tags []string) {
			b, _ := json.Marshal(tags)
			jTagsJson := StringToJString(env, string(b))
			C.callOnParseFailed(env, callback, midParseFailed, jTagsJson)
			C.DeleteLocalRef(env, C.jobject(jTagsJson))
		},
		OnValidateFailed: func(tags []string) {
			b, _ := json.Marshal(tags)
			jTagsJson := StringToJString(env, string(b))
			C.callOnValidateFailed(env, callback, midValidateFailed, jTagsJson)
			C.DeleteLocalRef(env, C.jobject(jTagsJson))
		},
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
		OnError: func(msg string) {
			jMsg := StringToJString(env, msg)
			C.callOnError(env, callback, midError, jMsg)
			C.DeleteLocalRef(env, C.jobject(jMsg))
		},
	}

	workingConfigs := RunLatencyTests(
		goWorkerPath,
		goTestUrl,
		goConnUrisJson,
		int(latencyRounds),
		int(roundTimeout),
		goTestByBatches,
		int(batchSize),
		callbacks,
	)

	b, _ := json.Marshal(workingConfigs)
	return StringToJString(env, string(b))
}

func main() {}
