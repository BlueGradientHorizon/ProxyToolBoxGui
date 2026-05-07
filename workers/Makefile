ifeq ($(OS),Windows_NT)
    EXE := .exe
    SHELL := cmd.exe
    MKDIR = if not exist "$(subst /,\,$(1))" mkdir "$(subst /,\,$(1))"
    RM = if exist "$(subst /,\,$(1))" del /Q /F "$(subst /,\,$(1))"
    RMDIR = if exist "$(subst /,\,$(1))" rmdir /S /Q "$(subst /,\,$(1))"
    CP = copy /Y "$(subst /,\,$(1))" "$(subst /,\,$(2))"
    ENV_SET = set $(1)=$(2)&&
    NDK_HOST_TAG := windows-x86_64
    CLANG_EXT := .cmd
else
    EXE :=
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
endif

ANDROID_API ?= 24
NDK_BIN := $(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt/$(NDK_HOST_TAG)/bin

WORKERS_REPO := github.com/bluegradienthorizon/proxytoolbox
WORKERS_VERSION := v0.0.1
WORKERS := xraycore singbox
REPO_DIR := proxytoolbox
OUT_DIR := bin

.PHONY: all build android clean clone windows_x64 linux_x64

all: build

clone:
	$(if $(wildcard $(REPO_DIR)),,git clone --depth 1 --branch $(WORKERS_VERSION) https://$(WORKERS_REPO) $(REPO_DIR))

build: clone
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,CGO_ENABLED,1) go build -o $(CURDIR)/$(OUT_DIR)/$(w)-worker$(EXE) . && \
		cd $(CURDIR) && \
	) cd .

windows_x64: clone
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,windows) $(call ENV_SET,GOARCH,amd64) $(call ENV_SET,CGO_ENABLED,1) go build -o $(CURDIR)/$(OUT_DIR)/windows/x64/$(w)-worker.exe . && \
		cd $(CURDIR) && \
	) cd .

linux_x64: clone
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,linux) $(call ENV_SET,GOARCH,amd64) $(call ENV_SET,CGO_ENABLED,1) go build -o $(CURDIR)/$(OUT_DIR)/linux/x64/$(w)-worker . && \
		cd $(CURDIR) && \
	) cd .

android: clone
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,arm64) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/aarch64-linux-android$(ANDROID_API)-clang$(CLANG_EXT)) go build -o $(CURDIR)/$(OUT_DIR)/android/arm64-v8a/lib$(w)-worker.so . && \
		cd $(CURDIR) && \
	) cd .
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,arm) $(call ENV_SET,GOARM,7) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/armv7a-linux-androideabi$(ANDROID_API)-clang$(CLANG_EXT)) go build -o $(CURDIR)/$(OUT_DIR)/android/armeabi-v7a/lib$(w)-worker.so . && \
		cd $(CURDIR) && \
	) cd .
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,amd64) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/x86_64-linux-android$(ANDROID_API)-clang$(CLANG_EXT)) go build -o $(CURDIR)/$(OUT_DIR)/android/x86_64/lib$(w)-worker.so . && \
		cd $(CURDIR) && \
	) cd .
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,android) $(call ENV_SET,GOARCH,386) $(call ENV_SET,CGO_ENABLED,1) $(call ENV_SET,CC,$(NDK_BIN)/i686-linux-android$(ANDROID_API)-clang$(CLANG_EXT)) go build -o $(CURDIR)/$(OUT_DIR)/android/x86/lib$(w)-worker.so . && \
		cd $(CURDIR) && \
	) cd .

clean:
	$(call RMDIR,$(OUT_DIR))
	$(call RMDIR,$(REPO_DIR))
	$(call RM,*.so)
	$(call RM,*.h)