# Shared Makefile configuration for ProxyToolBoxGui

ifeq ($(OS),Windows_NT)
    SHELL := cmd.exe
    EXE := .exe
    LIB := .dll
    MKDIR = if not exist "$(subst /,\,$(1))" mkdir "$(subst /,\,$(1))"
    RM = if exist "$(subst /,\,$(1))" del /Q /F "$(subst /,\,$(1))"
    RMDIR = if exist "$(subst /,\,$(1))" rmdir /S /Q "$(subst /,\,$(1))"
    CP = copy /Y "$(subst /,\,$(1))" "$(subst /,\,$(2))"
    ENV_SET = set "$(1)=$(2)"&&
    NDK_HOST_TAG := windows-x86_64
    CLANG_EXT := .cmd
    JNI_SEP := ;
else
    EXE :=
    LIB := .so
    MKDIR = mkdir -p $(1)
    RM = rm -f $(1)
    RMDIR = rm -rf $(1)
    CP = cp $(1) $(2)
    ENV_SET = $(1)=$(2)
    UNAME_S := $(shell uname -s)
    ifeq ($(UNAME_S),Linux)
        NDK_HOST_TAG := linux-x86_64
    endif
    ifeq ($(UNAME_S),Darwin)
        NDK_HOST_TAG := darwin-x86_64
    endif
    CLANG_EXT :=
    JNI_SEP := :
endif

ANDROID_API ?= 24

ifneq ($(ANDROID_NDK_HOME),)
    NDK_ROOT := $(subst \,/,$(ANDROID_NDK_HOME))
    NDK_PREBUILT := $(NDK_ROOT)/toolchains/llvm/prebuilt/$(NDK_HOST_TAG)
    NDK_BIN := $(NDK_PREBUILT)/bin
    NDK_SYSROOT_INCLUDE := $(NDK_PREBUILT)/sysroot/usr/include
endif

# Helper for Android builds
# $(call ANDROID_GO_BUILD,GOARCH,GOARM,CC_PREFIX,OUT_PATH,C_INCLUDE_PATH,GO_BUILD_MODE,SOURCE_PATH)
define ANDROID_GO_BUILD
	$(call ENV_SET,GOOS,android) \
	$(call ENV_SET,GOARCH,$(1)) \
	$(if $(2),$(call ENV_SET,GOARM,$(2))) \
	$(call ENV_SET,CGO_ENABLED,1) \
	$(call ENV_SET,CC,$(NDK_BIN)/$(3)$(ANDROID_API)-clang$(CLANG_EXT)) \
	$(if $(5),$(call ENV_SET,C_INCLUDE_PATH,$(5))) \
	go build $(6) -o $(4) $(7)
endef

# Android Architectures: GOARCH, GOARM, CC_PREFIX, OUT_DIR_NAME
ARCH_arm64 := arm64,,aarch64-linux-android,arm64-v8a
ARCH_arm   := arm,7,armv7a-linux-androideabi,armeabi-v7a
ARCH_x64   := amd64,,x86_64-linux-android,x86_64
ARCH_x86   := 386,,i686-linux-android,x86

ANDROID_ARCHS := arm64 arm x64 x86
