include ../common.mk

# ==============================================================================
# REPOSITORY CONFIGURATION
# ==============================================================================
# For each repo <name> in REPOS define:
#   <name>_URL     – clone URL
#   <name>_BRANCH  – (optional) branch or tag to shallow-clone
#   <name>_COMMIT  – (optional) exact commit hash to checkout.
#                    Only used when <name>_BRANCH is empty / not set.
#   <name>_WORKERS – space-separated paths relative to repo root that contain
#                    a Makefile with a `build` target.
# ==============================================================================

REPOS := proxytoolbox

proxytoolbox_URL     := https://github.com/bluegradienthorizon/proxytoolbox
#proxytoolbox_BRANCH  := v0.0.1
proxytoolbox_COMMIT  := d7c91930d4aec32f658b6ec3f2b7d7165dd1afca
proxytoolbox_WORKERS := internal/workers/xraycore internal/workers/singbox

# REPOS += otherrepo
# otherrepo_URL     := https://github.com/example/otherrepo
# otherrepo_BRANCH  := main
# otherrepo_COMMIT  := deadbeef
# otherrepo_WORKERS := workers/impl1 workers/impl2

# ==============================================================================
# INTERNAL SETUP
# ==============================================================================

OUT_DIR := bin

# Derive $(repo)_DIR for each registered repo
define REPO_INIT
$(1)_DIR := $(1)
endef
$(foreach r,$(REPOS),$(eval $(call REPO_INIT,$(r))))

# ==============================================================================
# CLONE
# ==============================================================================

# Pick the first applicable clone strategy.
# Priority: BRANCH → COMMIT → default shallow clone.
define REPO_CLONE_CMD
$(or \
	$(and $($(1)_BRANCH),\
		git clone --depth 1 --branch $($(1)_BRANCH) $($(1)_URL) $($(1)_DIR) \
	),\
	$(and $($(1)_COMMIT),\
		git init $($(1)_DIR) && \
		cd $($(1)_DIR) && \
		git remote add origin $($(1)_URL) && \
		git fetch --depth 1 origin $($(1)_COMMIT) && \
		git checkout FETCH_HEAD \
	),\
	git clone --depth 1 $($(1)_URL) $($(1)_DIR) \
)
endef

define REPO_CLONE
.PHONY: clone_$(1)
clone_$(1):
	$(if $(wildcard $($(1)_DIR)),,$(call REPO_CLONE_CMD,$(1)))
endef
$(foreach r,$(REPOS),$(eval $(call REPO_CLONE,$(r))))

clone: $(foreach r,$(REPOS),clone_$(r))

# ==============================================================================
# HOST BUILDS
# ==============================================================================

.PHONY: all build windows_x64 linux_x64 android clean

.DEFAULT_GOAL := all
all: build

ifeq ($(OS),Windows_NT)
build: windows_x64
else
build: linux_x64
endif

windows_x64: clone
	$(foreach r,$(REPOS),\
		$(foreach w,$($(r)_WORKERS),\
			$(call MKDIR,$(CURDIR)/$(OUT_DIR)/windows/x64) && \
			cd $($(r)_DIR)/$(w) && \
			$(call ENV_SET,GOOS,windows) \
			$(call ENV_SET,GOARCH,amd64) \
			$(call ENV_SET,CGO_ENABLED,1) \
			$(MAKE) build OUTDIR=$(subst \,/,$(CURDIR)/$(OUT_DIR)/windows/x64) && \
			cd $(CURDIR) && \
		)\
	) cd .

linux_x64: clone
	$(foreach r,$(REPOS),\
		$(foreach w,$($(r)_WORKERS),\
			$(call MKDIR,$(CURDIR)/$(OUT_DIR)/linux/x64) && \
			cd $($(r)_DIR)/$(w) && \
			$(call ENV_SET,GOOS,linux) \
			$(call ENV_SET,GOARCH,amd64) \
			$(call ENV_SET,CGO_ENABLED,1) \
			$(MAKE) build OUTDIR=$(subst \,/,$(CURDIR)/$(OUT_DIR)/linux/x64) && \
			cd $(CURDIR) && \
		)\
	) cd .

# ==============================================================================
# ANDROID BUILDS
# ==============================================================================

# $(call build_android_arch,GOARCH,GOARM,CC_PREFIX,OUT_SUBDIR)
define build_android_arch
	$(foreach r,$(REPOS),\
		$(foreach w,$($(r)_WORKERS),\
			$(call MKDIR,$(CURDIR)/$(OUT_DIR)/android/$(4)) && \
			cd $($(r)_DIR)/$(w) && \
			$(call ENV_SET,GOOS,android) \
			$(call ENV_SET,GOARCH,$(1)) \
			$(if $(2),$(call ENV_SET,GOARM,$(2))) \
			$(call ENV_SET,CGO_ENABLED,1) \
			$(call ENV_SET,CC,$(NDK_BIN)/$(3)$(ANDROID_API)-clang$(CLANG_EXT)) \
			$(MAKE) build OUTDIR=$(subst \,/,$(CURDIR)/$(OUT_DIR)/android/$(4)) && \
			cd $(CURDIR) && \
		)\
	) cd .
endef

android: clone
	$(call build_android_arch,arm64,,aarch64-linux-android,arm64-v8a)
	$(call build_android_arch,arm,7,armv7a-linux-androideabi,armeabi-v7a)
	$(call build_android_arch,amd64,,x86_64-linux-android,x86_64)
	$(call build_android_arch,386,,i686-linux-android,x86)
	cd .

# ==============================================================================
# CLEAN
# ==============================================================================

clean:
	$(foreach r,$(REPOS),\
		$(call RMDIR,$($(r)_DIR)) && \
	)
	$(call RMDIR,$(OUT_DIR))
	cd .