.PHONY: build android clean

EXT :=
ifeq ($(OS),Windows_NT)
EXT := .exe
endif

GOOS := $(shell go env GOOS)
GOARCH := $(shell go env GOARCH)

WORKERS_REPO := github.com/bluegradienthorizon/proxytoolbox
WORKERS_VERSION := v0.0.1
WORKERS := xraycore singbox

ifeq ($(findstring Windows,$(OS)),)
TEMP_DIR := $(shell mktemp -d 2>/dev/null || mktemp -d -t 'tmp')
else
TEMP_DIR := $(shell echo %TEMP%\tmp_%RANDOM%)
endif

build:
	go build -o bin/wrapper$(EXT) ./wrapper
	@bash -c 'for w in $(WORKERS); do \
		if [ ! -f bin/$$w$(EXT) ]; then \
			git clone --depth 1 --branch $(WORKERS_VERSION) https://$(WORKERS_REPO) $(TEMP_DIR) && \
			cd $(TEMP_DIR)/internal/workers/$$w && \
			go build -o $(CURDIR)/bin/$$w$(EXT) . && \
			cd $(CURDIR) && rm -rf $(TEMP_DIR); \
		fi; \
	done'

ifeq ($(OS),Windows_NT)
    SHELL := cmd
    MKDIR := if not exist "$(1)" mkdir "$(1)"
    CP := copy /Y "$(1)" "$(2)"
    RM := if exist "$(1)" del /F /Q "$(1)"
else
    MKDIR := mkdir -p $(1)
    CP := cp $(1) $(2)
    RM := rm -f $(1)
endif

ANDROID_OUT := ../composeApp/src/androidMain/jniLibs

android:
	$(call MKDIR,$(ANDROID_OUT))
	gomobile bind -target=android/arm64,android/amd64 -androidapi 24 -o $(ANDROID_OUT)/wrapper.aar ./wrapper
	@bash -c 'for w in $(WORKERS); do \
		if [ -f bin/$$w ]; then cp bin/$$w $(ANDROID_OUT)/lib$$w.so; fi; \
	done'

clean:
ifeq ($(OS),Windows_NT)
	if exist bin rmdir /s /q bin
	if exist *.aar del /q *.aar
else
	rm -rf bin/
	rm -f *.aar
endif
