ifeq ($(OS),Windows_NT)
    EXE := .exe
    SHELL := cmd.exe
    MKDIR = if not exist "$(subst /,\,$(1))" mkdir "$(subst /,\,$(1))"
    RM = if exist "$(subst /,\,$(1))" del /Q /F "$(subst /,\,$(1))"
    RMDIR = if exist "$(subst /,\,$(1))" rmdir /S /Q "$(subst /,\,$(1))"
    CP = copy /Y "$(subst /,\,$(1))" "$(subst /,\,$(2))"
    TEMP_DIR := $(TEMP)\proxy_build
else
    EXE :=
    MKDIR = mkdir -p $(1)
    RM = rm -f $(1)
    RMDIR = rm -rf $(1)
    CP = cp $(1) $(2)
    TEMP_DIR := /tmp/proxy_build
endif

WORKERS_REPO := github.com/bluegradienthorizon/proxytoolbox
WORKERS_VERSION := v0.0.1
WORKERS := xraycore singbox
ANDROID_OUT := ../composeApp/libs

.PHONY: build android clean

build:
# 	go build -o bin/wrapper$(EXE) ./wrapper
# 	$(foreach w,$(WORKERS), \
# 		$(if $(wildcard bin/$(w)$(EXE)),, \
# 			git clone --depth 1 --branch $(WORKERS_VERSION) https://$(WORKERS_REPO) $(TEMP_DIR) && \
# 			cd $(TEMP_DIR)/internal/workers/$(w) && \
# 			go build -o $(CURDIR)/bin/$(w)$(EXE) . && \
# 			cd $(CURDIR) && $(call RMDIR,$(TEMP_DIR)) \
# 		) \
# 	)

android:
	$(call MKDIR,$(ANDROID_OUT))
	gomobile init
	gomobile bind -target=android/arm64,android/amd64 -androidapi 24 -o $(ANDROID_OUT)/wrapper.aar ./wrapper
	$(foreach w,$(WORKERS), \
		$(if $(wildcard bin/$(w)), $(call CP,bin/$(w),$(ANDROID_OUT)/lib$(w).so), ) \
	)

clean:
	$(call RMDIR,bin)
	$(call RM,*.aar)
	$(call RMDIR,$(TEMP_DIR))
