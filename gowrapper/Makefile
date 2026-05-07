ifeq ($(OS),Windows_NT)
    LIB := .dll
    MKDIR = if not exist "$(subst /,\,$(1))" mkdir "$(subst /,\,$(1))"
    RM = if exist "$(subst /,\,$(1))" del /Q /F "$(subst /,\,$(1))"
    RMDIR = if exist "$(subst /,\,$(1))" rmdir /S /Q "$(subst /,\,$(1))"
    ENV_SET = set $(1)=$(2)&&
    NDK_HOST_TAG := windows-x86_64
    CLANG_EXT := .cmd
    MSYS2_DIR := $(dir $(firstword $(shell where msys2.exe 2>NUL)))/mingw64/bin
else
    LIB := .so
    MKDIR = mkdir -p $(1)
    RM = rm -f $(1)
    RMDIR = rm -rf $(1)
    ENV_SET = $(1)=$(2)
    UNAME_S := $(shell uname -s)
    ifeq ($(UNAME_S),Linux)
        NDK_HOST_TAG := linux-x86_64
    endif
    ifeq ($(UNAME_S),Darwin)
        NDK_HOST_TAG := darwin-x86_64
    endif
    CLANG_EXT :=
endif

ANDROID_API ?= 24
NDK_BIN := $(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/$(NDK_HOST_TAG)/bin
OUT_DIR := bin
ANDROID_OUT_DIR := $(OUT_DIR)/android

.PHONY: build windows_x64 linux_x64 android clean

build:
	$(call ENV_SET,CGO_ENABLED,1) go build -buildmode=c-shared -o $(OUT_DIR)/libwrapper$(LIB) ./wrapper

windows_x64:
	$(call ENV_SET,PATH,%PATH%;$(MSYS2_DIR)) $(call ENV_SET,GOOS,windows) $(call ENV_SET,GOARCH,amd64) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(MSYS2_DIR)\x86_64-w64-mingw32-gcc.exe) go build -buildmode=c-shared -o $(OUT_DIR)/windows/x64/libwrapper.dll ./wrapper

linux_x64:
	$(call ENV_SET,GOOS,linux) $(call ENV_SET,GOARCH,amd64) $(call ENV_SET,CGO_ENABLED,1) go build -buildmode=c-shared -o $(OUT_DIR)/linux/x64/libwrapper.so ./wrapper

android:
	$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,arm64) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/aarch64-linux-android$(ANDROID_API)-clang$(CLANG_EXT)) go build -buildmode=c-shared -o $(ANDROID_OUT_DIR)/arm64-v8a/libwrapper.so ./wrapper
	$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,arm) $(call ENV_SET,GOARM,7) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/armv7a-linux-androideabi$(ANDROID_API)-clang$(CLANG_EXT)) go build -buildmode=c-shared -o $(ANDROID_OUT_DIR)/armeabi-v7a/libwrapper.so ./wrapper
	$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,amd64) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/x86_64-linux-android$(ANDROID_API)-clang$(CLANG_EXT)) go build -buildmode=c-shared -o $(ANDROID_OUT_DIR)/x86_64/libwrapper.so ./wrapper
	$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,386) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/i686-linux-android$(ANDROID_API)-clang$(CLANG_EXT)) go build -buildmode=c-shared -o $(ANDROID_OUT_DIR)/x86/libwrapper.so ./wrapper

clean:
	$(call RMDIR,$(OUT_DIR))
	$(call RM,*.aar)
	$(call RM,*.so)
	$(call RM,*.h)
