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

static void callLatencyOnProgress(JNIEnv *env, jobject cb, jmethodID mid, jstring tag, jlong delay, jboolean failed) {
    (*env)->CallVoidMethod(env, cb, mid, tag, delay, failed);
}

static void callSpeedOnProgress(JNIEnv *env, jobject cb, jmethodID mid, jstring tag, jdouble speed, jboolean failed) {
    (*env)->CallVoidMethod(env, cb, mid, tag, speed, failed);
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

/*
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
		u16s[i] = *(*uint16)(unsafe.Add(ptr, uintptr(i)*size))
	}
	runes := utf16.Decode(u16s)
	return string(runes)
}
*/

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

func MarshalProto(m proto.Message) []byte {
	b, _ := proto.Marshal(m)
	return b
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverWorkers
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverWorkers(env *C.JNIEnv, clazz C.jclass, requestProto C.jbyteArray) C.jbyteArray {
	requestBytes := JByteArrayToBytes(env, requestProto)

	var req pb.PBDiscoverWorkersRequest
	if err := proto.Unmarshal(requestBytes, &req); err != nil {
		errStr := err.Error()
		resp := &pb.PBDiscoverWorkersResponse{Error: &errStr}
		return BytesToJByteArray(env, MarshalProto(resp))
	}

	workers, err := DiscoverWorkers(req.LibraryPath)

	resp := &pb.PBDiscoverWorkersResponse{}
	if err != nil {
		errStr := err.Error()
		resp.Error = &errStr
	} else {
		workerList := &pb.PBWorkerInfoList{}
		for _, w := range workers {
			workerList.Workers = append(workerList.Workers, &pb.PBWorkerInfo{
				Name:    w.Name,
				Version: w.Version,
				Path:    w.Path,
			})
		}
		resp.Workers = workerList
	}
	return BytesToJByteArray(env, MarshalProto(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverSpeedTestPresets
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeDiscoverSpeedTestPresets(
	env *C.JNIEnv,
	clazz C.jclass,
) C.jbyteArray {
	presets := DiscoverSpeedTestPresets()
	resp := &pb.PBDiscoverSpeedTestPresetsResponse{
		Presets: &pb.PBStringMap{Items: presets},
	}
	return BytesToJByteArray(env, MarshalProto(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeStopTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeStopTests(env *C.JNIEnv, clazz C.jclass) {
	StopTests()
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeInitializeRunner
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeInitializeRunner(
	env *C.JNIEnv,
	clazz C.jclass,
	requestProto C.jbyteArray,
) C.jbyteArray {
	requestBytes := JByteArrayToBytes(env, requestProto)

	var req pb.PBInitializeRunnerRequest
	if err := proto.Unmarshal(requestBytes, &req); err != nil {
		errStr := err.Error()
		resp := &pb.PBInitializeRunnerResponse{Error: &errStr}
		return BytesToJByteArray(env, MarshalProto(resp))
	}

	err := InitializeRunner(req.WorkerPath, req.LowMemMode)

	resp := &pb.PBInitializeRunnerResponse{}
	if err != nil {
		errStr := err.Error()
		resp.Error = &errStr
	} else {
		resp.Success = true
	}
	return BytesToJByteArray(env, MarshalProto(resp))
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
		errStr := err.Error()
		resp := &pb.PBParseConfigsResponse{Error: &errStr}
		return BytesToJByteArray(env, MarshalProto(resp))
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
	resp := &pb.PBParseConfigsResponse{}
	if err != nil {
		errStr := err.Error()
		resp.Error = &errStr
	} else {
		resp.ParseErrors = &pb.PBStringMap{Items: parseErrors}
	}
	return BytesToJByteArray(env, MarshalProto(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeValidateConfigs
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeValidateConfigs(
	env *C.JNIEnv,
	clazz C.jclass,
) C.jbyteArray {
	validateErrors, err := ValidateConfigs()
	resp := &pb.PBValidateConfigsResponse{}
	if err != nil {
		errStr := err.Error()
		resp.Error = &errStr
	} else {
		resp.ValidateErrors = &pb.PBStringMap{Items: validateErrors}
	}
	return BytesToJByteArray(env, MarshalProto(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunLatencyTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunLatencyTests(
	env *C.JNIEnv,
	clazz C.jclass,
	requestProto C.jbyteArray,
	callback C.jobject,
) C.jbyteArray {
	requestBytes := JByteArrayToBytes(env, requestProto)

	var req pb.PBRunLatencyTestsRequest
	if err := proto.Unmarshal(requestBytes, &req); err != nil {
		errStr := err.Error()
		resp := &pb.PBRunLatencyTestsResponse{Error: &errStr}
		return BytesToJByteArray(env, MarshalProto(resp))
	}

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

	callbacks := LatencyTestCallbacks{
		OnRoundStarted: func(batch int, round int, total int) {
			C.callOnRoundStarted(env, callback, midRoundStarted, C.jlong(batch), C.jlong(round), C.jlong(total))
		},
		OnProgress: func(tag string, delay int64, failed bool) {
			jTag := StringToJString(env, tag)
			var cFailed C.jboolean = 0
			if failed {
				cFailed = 1
			}
			C.callLatencyOnProgress(env, callback, midProgress, jTag, C.jlong(delay), cFailed)
			C.DeleteLocalRef(env, C.jobject(jTag))
		},
		OnRoundEnded: func(batch int, round int) {
			C.callOnRoundEnded(env, callback, midRoundEnded, C.jlong(batch), C.jlong(round))
		},
	}

	workingConfigs, err := RunLatencyTests(
		req.TestUrl,
		int(req.LatencyRounds),
		int(req.RoundTimeout),
		req.TestByBatches,
		int(req.BatchSize),
		callbacks,
	)

	resp := &pb.PBRunLatencyTestsResponse{}
	if err != nil {
		errStr := err.Error()
		resp.Error = &errStr
	} else {
		configsList := &pb.PBProxyConfigList{}
		for _, c := range workingConfigs {
			configsList.Configs = append(configsList.Configs, &pb.PBProxyConfig{
				Tag:     c.Tag,
				ConnUri: c.ConnURI,
				Delay:   c.Delay,
			})
		}
		resp.Configs = configsList
	}
	return BytesToJByteArray(env, MarshalProto(resp))
}

//export Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunSpeedTests
func Java_com_bghorizon_proxytoolboxgui_data_GoBridgeNative_nativeRunSpeedTests(
	env *C.JNIEnv,
	clazz C.jclass,
	requestProto C.jbyteArray,
	callback C.jobject,
) C.jbyteArray {
	requestBytes := JByteArrayToBytes(env, requestProto)

	var req pb.PBRunSpeedTestsRequest
	if err := proto.Unmarshal(requestBytes, &req); err != nil {
		errStr := err.Error()
		resp := &pb.PBRunSpeedTestsResponse{Error: &errStr}
		return BytesToJByteArray(env, MarshalProto(resp))
	}

	cbClass := C.GetObjectClass(env, callback)
	defer C.DeleteLocalRef(env, C.jobject(cbClass))

	cOnRoundStarted := C.CString("onRoundStarted")
	cOnProgress := C.CString("onProgress")
	cOnRoundEnded := C.CString("onRoundEnded")

	cSigVJJJ := C.CString("(JJJ)V")
	cSigVJSZ := C.CString("(Ljava/lang/String;DZ)V")
	cSigVJJ := C.CString("(JJ)V")

	midRoundStarted := C.GetMethodID(env, cbClass, cOnRoundStarted, cSigVJJJ)
	midProgress := C.GetMethodID(env, cbClass, cOnProgress, cSigVJSZ)
	midRoundEnded := C.GetMethodID(env, cbClass, cOnRoundEnded, cSigVJJ)

	defer func() {
		C.free(unsafe.Pointer(cOnRoundStarted))
		C.free(unsafe.Pointer(cOnProgress))
		C.free(unsafe.Pointer(cOnRoundEnded))
		C.free(unsafe.Pointer(cSigVJJJ))
		C.free(unsafe.Pointer(cSigVJSZ))
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
			C.callSpeedOnProgress(env, callback, midProgress, jTag, C.jdouble(speed), cFailed)
			C.DeleteLocalRef(env, C.jobject(jTag))
		},
		OnRoundEnded: func(batch int, round int) {
			C.callOnRoundEnded(env, callback, midRoundEnded, C.jlong(batch), C.jlong(round))
		},
	}

	mode := "download"
	if req.Mode == pb.PBSpeedTestMode_UPLOAD {
		mode = "upload"
	}

	results, err := RunSpeedTests(
		req.Tags,
		req.ProviderId,
		mode,
		int(req.Rounds),
		int(req.Timeout),
		req.TargetBytes,
		callbacks,
	)

	resp := &pb.PBRunSpeedTestsResponse{}
	if err != nil {
		errStr := err.Error()
		resp.Error = &errStr
	} else {
		for _, r := range results {
			var errStr *string
			if r.Error != nil {
				s := r.Error.Error()
				errStr = &s
			}
			resp.Results = append(resp.Results, &pb.PBSpeedTestResult{
				Tag:   r.Tag,
				Speed: r.Speed,
				Error: errStr,
			})
		}
	}
	return BytesToJByteArray(env, MarshalProto(resp))
}

func main() {}
