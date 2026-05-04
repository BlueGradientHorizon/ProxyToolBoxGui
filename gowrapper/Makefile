.PHONY: build android clean

EXT :=
ifeq ($(OS),Windows_NT)
EXT := .exe
endif

GOOS := $(shell go env GOOS)
GOARCH := $(shell go env GOARCH)

build:
	go build -o bin/wrapper$(EXT) ./wrapper

android:
	mkdir -p ../composeApp/src/androidMain/jniLibs
	gomobile bind -target=android/arm64,android/amd64 -androidapi 24 -o ../composeApp/src/androidMain/jniLibs/wrapper.aar ./wrapper

clean:
ifeq ($(OS),Windows_NT)
	if exist bin rmdir /s /q bin
	if exist *.aar del /q *.aar
else
	rm -rf bin/
	rm -f *.aar
endif