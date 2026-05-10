include ../common.mk

OUT_DIR := bin
ANDROID_OUT_DIR := $(OUT_DIR)/android

ifeq ($(OS),Windows_NT)
    # 1) Allow explicit override from CI/environment
    ifneq ($(MINGW_BIN_DIR),)
        MSYS2_DIR := $(MINGW_BIN_DIR)
    else
        # 2) Try to find the compiler directly in PATH
        GCC_PATH := $(subst \,/,$(shell where.exe x86_64-w64-mingw32-gcc.exe 2>NUL))
        ifneq ($(GCC_PATH),)
            MSYS2_DIR := $(dir $(firstword $(GCC_PATH)))
        else
            # 3) Derive from msys2.exe if available
            MSYS2_BIN := $(subst \,/,$(shell where.exe msys2.exe 2>NUL))
            ifneq ($(MSYS2_BIN),)
                MSYS2_DIR := $(dir $(firstword $(MSYS2_BIN)))mingw64/bin
            else
                # 4) Check common installation locations
                ifneq ($(wildcard C:/msys64/mingw64/bin/x86_64-w64-mingw32-gcc.exe),)
                    MSYS2_DIR := C:/msys64/mingw64/bin
                else ifneq ($(wildcard D:/msys64/mingw64/bin/x86_64-w64-mingw32-gcc.exe),)
                    MSYS2_DIR := D:/msys64/mingw64/bin
                else ifneq ($(wildcard C:/mingw64/bin/x86_64-w64-mingw32-gcc.exe),)
                    MSYS2_DIR := C:/mingw64/bin
                else
                    $(error MinGW x86_64-w64-mingw32-gcc.exe not found. Install mingw-w64-x86_64-gcc, add it to PATH, or set MINGW_BIN_DIR.)
                endif
            endif
        endif
    endif
endif

.PHONY: build windows_x64 linux_x64 android clean

ifeq ($(OS),Windows_NT)
build: windows_x64
else
build: linux_x64
endif

windows_x64:
	@cmd.exe /c "if not exist "$(subst /,\,$(MSYS2_DIR))" ( echo MSYS2/MinGW64 not found at $(MSYS2_DIR) && exit 1 )"
	$(call ENV_SET,PATH,%PATH%;$(subst /,\,$(MSYS2_DIR))) \
	$(call ENV_SET,GOOS,windows) \
	$(call ENV_SET,GOARCH,amd64) \
	$(call ENV_SET,CGO_ENABLED,1) \
	$(call ENV_SET,CC,$(MSYS2_DIR)/x86_64-w64-mingw32-gcc.exe) \
	$(call ENV_SET,C_INCLUDE_PATH,$(subst \,/,$(JAVA_HOME))/include$(JNI_SEP)$(subst \,/,$(JAVA_HOME))/include/win32) \
	go build -buildmode=c-shared -o $(OUT_DIR)/windows/x64/libwrapper$(LIB)

linux_x64:
	$(call ENV_SET,GOOS,linux) \
	$(call ENV_SET,GOARCH,amd64) \
	$(call ENV_SET,CGO_ENABLED,1) \
	$(call ENV_SET,C_INCLUDE_PATH,$(JAVA_HOME)/include$(JNI_SEP)$(JAVA_HOME)/include/linux) \
	go build -buildmode=c-shared -o $(OUT_DIR)/linux/x64/libwrapper.so

android:
	$(call build_arch,arm64,,aarch64-linux-android,arm64-v8a) \
	$(call build_arch,arm,7,armv7a-linux-androideabi,armeabi-v7a) \
	$(call build_arch,amd64,,x86_64-linux-android,x86_64) \
	$(call build_arch,386,,i686-linux-android,x86) \
	cd .

# $(call ANDROID_GO_BUILD,GOARCH,GOARM,CC_PREFIX,OUT_PATH,C_INCLUDE_PATH,GO_BUILD_MODE,SOURCE_PATH)
define build_arch
	$(call ANDROID_GO_BUILD,$(1),$(2),$(3),$(ANDROID_OUT_DIR)/$(4)/libwrapper.so,$(NDK_SYSROOT_INCLUDE),-buildmode=c-shared,.) &&
endef

clean:
	$(call RMDIR,$(OUT_DIR))
	$(call RM,*.aar)
	$(call RM,*.so)
	$(call RM,*.h)
