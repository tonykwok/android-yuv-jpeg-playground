
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# By default, the build system generates ARM target binaries in thumb mode,
# where each instruction is 16 bits wide.  Defining this variable as arm
# forces the build system to generate object files in 32-bit arm mode.  This
# is the same setting previously used by libjpeg.
# TODO (msarett): Run performance tests to determine whether arm mode is still
#                 preferred to thumb mode for libjpeg-turbo.
LOCAL_ARM_MODE := arm

LOCAL_CFLAGS := -DWITH_SIMD \
                -DNO_GETENV \
                -O3 \
                -fstrict-aliasing \
                -Werror \
                -Wno-sign-compare \
                -Wno-unused-parameter

LOCAL_C_INCLUDES := $(LOCAL_PATH) \
                    $(LOCAL_PATH)/simd/arm

LOCAL_SRC_FILES := \
    jaricom.c \
    jcapimin.c \
    jcapistd.c \
    jcarith.c \
    jccoefct.c \
    jccolor.c \
    jcdctmgr.c \
    jchuff.c \
    jcicc.c \
    jcinit.c \
    jcmainct.c \
    jcmarker.c \
    jcmaster.c \
    jcomapi.c \
    jcparam.c \
    jcphuff.c \
    jcprepct.c \
    jcsample.c \
    jctrans.c \
    jdapimin.c \
    jdapistd.c \
    jdarith.c \
    jdatadst.c \
    jdatasrc.c \
    jdcoefct.c \
    jdcolor.c \
    jddctmgr.c \
    jdhuff.c \
    jdicc.c \
    jdinput.c \
    jdmainct.c \
    jdmarker.c \
    jdmaster.c \
    jdmerge.c \
    jdphuff.c \
    jdpostct.c \
    jdsample.c \
    jdtrans.c \
    jerror.c \
    jfdctflt.c \
    jfdctfst.c \
    jfdctint.c \
    jidctflt.c \
    jidctfst.c \
    jidctint.c \
    jidctred.c \
    jmemmgr.c \
    jmemnobs.c \
    jpeg_nbits_table.c \
    jquant1.c \
    jquant2.c \
    jutils.c

# If we are certain that the ARM v7 device has NEON (and there is no need for
# a runtime check), we can indicate that with a flag.
ifeq ($(strip $(TARGET_ARCH_ABI)),armeabi-v7a)
    LOCAL_CFLAGS += -D__ARM_HAVE_NEON__ \
                    -DNEON_INTRINSICS \
                    -mfpu=neon

    # ARM v7 NEON
    LOCAL_SRC_FILES += \
        simd/arm/aarch32/jchuff-neon.c \
        simd/arm/aarch32/jsimd.c \
        simd/arm/jccolor-neon.c \
        simd/arm/jcgray-neon.c \
        simd/arm/jcphuff-neon.c \
        simd/arm/jcsample-neon.c \
        simd/arm/jdcolor-neon.c \
        simd/arm/jdmerge-neon.c \
        simd/arm/jdsample-neon.c \
        simd/arm/jfdctfst-neon.c \
        simd/arm/jfdctint-neon.c \
        simd/arm/jidctfst-neon.c \
        simd/arm/jidctint-neon.c \
        simd/arm/jidctred-neon.c \
        simd/arm/jquanti-neon.c
endif

ifeq ($(strip $(TARGET_ARCH)),arm64)
    # ARM v8 64-bit NEON
    LOCAL_SRC_FILES += \
        simd/arm/aarch64/jchuff-neon.c \
        simd/arm/aarch64/jsimd.c \
        simd/arm/jccolor-neon.c \
        simd/arm/jcgray-neon.c \
        simd/arm/jcphuff-neon.c \
        simd/arm/jcsample-neon.c \
        simd/arm/jdcolor-neon.c \
        simd/arm/jdmerge-neon.c \
        simd/arm/jdsample-neon.c \
        simd/arm/jfdctfst-neon.c \
        simd/arm/jfdctint-neon.c \
        simd/arm/jidctfst-neon.c \
        simd/arm/jidctint-neon.c \
        simd/arm/jidctred-neon.c \
        simd/arm/jquanti-neon.c

    LOCAL_CFLAGS += -DNEON_INTRINSICS
endif

# LOCAL_EXPORT_C_INCLUDE_DIRS is used by the platform build system
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)

# LOCAL_EXPORT_C_INCLUDES is used by the ndk build system
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

LOCAL_SDK_VERSION := 17

LOCAL_MODULE := libjpeg

include $(BUILD_SHARED_LIBRARY)

