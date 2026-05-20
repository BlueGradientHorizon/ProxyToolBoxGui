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

static void DeleteLocalRef(JNIEnv *env, jobject obj) {
    (*env)->DeleteLocalRef(env, obj);
}

static jbyteArray NewByteArray(JNIEnv *env, jsize len) {
    return (*env)->NewByteArray(env, len);
}

static void SetByteArrayRegion(JNIEnv *env, jbyteArray array, jsize start, jsize len, const jbyte *buf) {
    (*env)->SetByteArrayRegion(env, array, start, len, buf);
}

static jsize GetArrayLength(JNIEnv *env, jarray array) {
    return (*env)->GetArrayLength(env, array);
}

static void GetByteArrayRegion(JNIEnv *env, jbyteArray array, jsize start, jsize len, jbyte *buf) {
    (*env)->GetByteArrayRegion(env, array, start, len, buf);
}
*/
import "C"
import (
	"unicode/utf16"
	"unsafe"

	pb "github.com/bluegradienthorizon/proxytoolboxgui/gowrapper/proto"
	"google.golang.org/protobuf/proto"
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

func BytesToJByteArray(env *C.JNIEnv, b []byte) C.jbyteArray {
	size := C.jsize(len(b))
	arr := C.NewByteArray(env, size)
	if size > 0 {
		C.SetByteArrayRegion(env, arr, 0, size, (*C.jbyte)(unsafe.Pointer(&b[0])))
	}
	return arr
}

func JByteArrayToBytes(env *C.JNIEnv, arr C.jbyteArray) []byte {
	if arr == 0 {
		return nil
	}
	n := C.GetArrayLength(env, C.jarray(arr))
	if n == 0 {
		return []byte{}
	}
	b := make([]byte, int(n))
	C.GetByteArrayRegion(env, arr, 0, n, (*C.jbyte)(unsafe.Pointer(&b[0])))
	return b
}

func MarshalResponse(resp *pb.PBNativeResponse) []byte {
	b, _ := proto.Marshal(resp)
	return b
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverWorkers
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverWorkers(env *C.JNIEnv, clazz C.jclass, libraryPath C.jstring) C.jbyteArray {
	goLibraryPath := JStringToString(env, libraryPath)
	workers, err := DiscoverWorkers(goLibraryPath)

	resp := &pb.PBNativeResponse{}
	if err != nil {
		resp.Error = err.Error()
	} else {
		workerList := &pb.PBWorkerInfoList{}
		for _, w := range workers {
			workerList.Workers = append(workerList.Workers, &pb.PBWorkerInfo{
				Name:    w.Name,
				Version: w.Version,
				Path:    w.Path,
			})
		}
		resp.Payload = &pb.PBNativeResponse_Workers{Workers: workerList}
	}
	return BytesToJByteArray(env, MarshalResponse(resp))
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
) C.jbyteArray {
	goWorkerPath := JStringToString(env, workerPath)
	err := InitializeRunner(goWorkerPath)

	resp := &pb.PBNativeResponse{}
	if err != nil {
		resp.Error = err.Error()
	} else {
		resp.Payload = &pb.PBNativeResponse_Success{Success: true}
	}
	return BytesToJByteArray(env, MarshalResponse(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeParseConfigs
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeParseConfigs(
	env *C.JNIEnv,
	clazz C.jclass,
	configsProto C.jbyteArray,
) C.jbyteArray {
	configsBytes := JByteArrayToBytes(env, configsProto)

	var inputConfigsProto pb.PBProxyConfigList
	if err := proto.Unmarshal(configsBytes, &inputConfigsProto); err != nil {
		resp := &pb.PBNativeResponse{Error: err.Error()}
		return BytesToJByteArray(env, MarshalResponse(resp))
	}

	var inputConfigs []ProxyConfig
	for _, c := range inputConfigsProto.Configs {
		inputConfigs = append(inputConfigs, ProxyConfig{
			Tag:     c.Tag,
			ConnURI: c.ConnUri,
			Delay:   c.Delay,
		})
	}

	parseErrors, err := ParseConfigs(inputConfigs)
	resp := &pb.PBNativeResponse{}
	if err != nil {
		resp.Error = err.Error()
	} else {
		stringMap := &pb.PBStringMap{Items: parseErrors}
		resp.Payload = &pb.PBNativeResponse_StringMap{StringMap: stringMap}
	}
	return BytesToJByteArray(env, MarshalResponse(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeValidateConfigs
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeValidateConfigs(
	env *C.JNIEnv,
	clazz C.jclass,
) C.jbyteArray {
	validateErrors, err := ValidateConfigs()
	resp := &pb.PBNativeResponse{}
	if err != nil {
		resp.Error = err.Error()
	} else {
		stringMap := &pb.PBStringMap{Items: validateErrors}
		resp.Payload = &pb.PBNativeResponse_StringMap{StringMap: stringMap}
	}
	return BytesToJByteArray(env, MarshalResponse(resp))
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
) C.jbyteArray {
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

	resp := &pb.PBNativeResponse{}
	if err != nil {
		resp.Error = err.Error()
	} else {
		configsList := &pb.PBProxyConfigList{}
		for _, c := range workingConfigs {
			configsList.Configs = append(configsList.Configs, &pb.PBProxyConfig{
				Tag:     c.Tag,
				ConnUri: c.ConnURI,
				Delay:   c.Delay,
			})
		}
		resp.Payload = &pb.PBNativeResponse_Configs{Configs: configsList}
	}
	return BytesToJByteArray(env, MarshalResponse(resp))
}

func main() {}
