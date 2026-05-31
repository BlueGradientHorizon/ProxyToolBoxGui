# Root Makefile for ProxyToolBoxGui
include common.mk

# Directories
COMPOSE_APP_DIR := composeApp
GOWRAPPER_DIR   := gowrapper
WORKERS_DIR     := workers
ANDROID_BIN_DIR := bin/android

# Target directories for binaries
JVM_RESOURCES := $(COMPOSE_APP_DIR)/src/jvmMain/resources
ANDROID_JNI   := $(COMPOSE_APP_DIR)/src/androidMain/jniLibs

# Desktop target detection
ifeq ($(OS),Windows_NT)
    DESKTOP_TARGET  := windows_x64
    DESKTOP_BIN_DIR := bin/windows/x64
else
    DESKTOP_TARGET  := linux_x64
    DESKTOP_BIN_DIR := bin/linux/x64
endif

# Android arch mapping (arch tag -> jniLibs directory)
ARCH_TAG_arm64 := arm64-v8a
ARCH_TAG_arm   := armeabi-v7a
ARCH_TAG_x64   := x86_64
ARCH_TAG_x86   := x86

.PHONY: all desktop android clean gowrapper-desktop gowrapper-android workers-desktop workers-android

all: desktop android

desktop: gowrapper-desktop workers-desktop

android: gowrapper-android workers-android

# Go Wrapper
gowrapper-desktop:
	$(MAKE) -C $(GOWRAPPER_DIR) $(DESKTOP_TARGET)
	$(call MKDIR,$(JVM_RESOURCES))
	$(call CP,$(GOWRAPPER_DIR)/$(DESKTOP_BIN_DIR)/*$(LIB),$(JVM_RESOURCES))

gowrapper-android:
	$(MAKE) -C $(GOWRAPPER_DIR) android
	$(foreach arch,$(ANDROID_ARCHS),\
		$(call MKDIR,$(ANDROID_JNI)/$(ARCH_TAG_$(arch))) && \
		$(call CP,$(GOWRAPPER_DIR)/$(ANDROID_BIN_DIR)/$(ARCH_TAG_$(arch))/*.so,$(ANDROID_JNI)/$(ARCH_TAG_$(arch))) && \
	) cd .

# Workers
workers-desktop:
	$(MAKE) -C $(WORKERS_DIR) $(DESKTOP_TARGET)
	$(call MKDIR,$(JVM_RESOURCES))
	$(call CP,$(WORKERS_DIR)/$(DESKTOP_BIN_DIR)/*,$(JVM_RESOURCES))

workers-android:
	$(MAKE) -C $(WORKERS_DIR) android
	$(foreach arch,$(ANDROID_ARCHS),\
		$(call MKDIR,$(ANDROID_JNI)/$(ARCH_TAG_$(arch))) && \
		$(call CP,$(WORKERS_DIR)/$(ANDROID_BIN_DIR)/$(ARCH_TAG_$(arch))/*,$(ANDROID_JNI)/$(ARCH_TAG_$(arch))) && \
	) cd .

clean:
	$(MAKE) -C $(GOWRAPPER_DIR) clean
	$(MAKE) -C $(WORKERS_DIR) clean
	$(foreach arch,$(ANDROID_ARCHS),$(call RMDIR,$(ANDROID_JNI)/$(ARCH_TAG_$(arch))) && ) cd .
	# We don't remove JVM_RESOURCES because it contains icons and other assets.
	# We could remove specific binaries if needed, but sub-module clean should be enough for them.
