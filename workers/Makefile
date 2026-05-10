include ../common.mk

WORKERS_REPO := github.com/bluegradienthorizon/proxytoolbox
WORKERS_VERSION := v0.0.1
WORKERS := xraycore singbox
REPO_DIR := proxytoolbox
OUT_DIR := bin

.PHONY: all build android clean clone windows_x64 linux_x64

all: build

clone:
	$(if $(wildcard $(REPO_DIR)),,git clone --depth 1 --branch $(WORKERS_VERSION) https://$(WORKERS_REPO) $(REPO_DIR))

ifeq ($(OS),Windows_NT)
build: windows_x64
else
build: linux_x64
endif

windows_x64: clone
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,windows) \
		$(call ENV_SET,GOARCH,amd64) \
		$(call ENV_SET,CGO_ENABLED,1) \
		go build -o $(CURDIR)/$(OUT_DIR)/windows/x64/$(w)-worker$(EXE) . && \
		cd $(CURDIR) && \
	) cd .

linux_x64: clone
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ENV_SET,GOOS,linux) \
		$(call ENV_SET,GOARCH,amd64) \
		$(call ENV_SET,CGO_ENABLED,1) \
		go build -o $(CURDIR)/$(OUT_DIR)/linux/x64/$(w)-worker . && \
		cd $(CURDIR) && \
	) cd .

android: clone
	$(call build_all_workers_arch,arm64,,aarch64-linux-android,arm64-v8a) \
	$(call build_all_workers_arch,arm,7,armv7a-linux-androideabi,armeabi-v7a) \
	$(call build_all_workers_arch,amd64,,x86_64-linux-android,x86_64) \
	$(call build_all_workers_arch,386,,i686-linux-android,x86) \
	cd .

# $(call build_all_workers_arch,GOARCH,GOARM,CC_PREFIX,OUT_SUBDIR)
define build_all_workers_arch
	$(foreach w,$(WORKERS), \
		cd $(REPO_DIR)/internal/workers/$(w) && \
		$(call ANDROID_GO_BUILD,$(1),$(2),$(3),$(CURDIR)/$(OUT_DIR)/android/$(4)/lib$(w)-worker.so,, ,.) && \
		cd $(CURDIR) && \
	)
endef

clean:
	$(call RMDIR,$(OUT_DIR))
	$(call RMDIR,$(REPO_DIR))
	$(call RM,*.so)
	$(call RM,*.h)
