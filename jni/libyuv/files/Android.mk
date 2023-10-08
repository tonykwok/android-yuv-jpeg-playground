
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# By default, the build system generates ARM target binaries in thumb mode,
# where each instruction is 16 bits wide.  Defining this variable as arm
# forces the build system to generate object files in 32-bit arm mode.  This
# is the same setting previously used by libjpeg.
# TODO (msarett): Run performance tests to determine whether arm mode is still
#                 preferred to thumb mode for libjpeg-turbo.
LOCAL_ARM_MODE := arm

LOCAL_CFLAGS := -Wall \
                -Werror \
                -Wno-unused-parameter \
                -fexceptions \
                -DHAVE_JPEG \
                -DLIBYUV_UNLIMITED_DATA

# If we are certain that the ARM v7 device has NEON (and there is no need for
# a runtime check), we can indicate that with a flag.
ifeq ($(strip $(TARGET_ARCH_ABI)),armeabi-v7a)
    LOCAL_CFLAGS += -mfpu=neon
endif

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := \
    source/compare.cc \
    source/compare_common.cc \
    source/compare_gcc.cc \
    source/compare_msa.cc \
    source/compare_neon.cc \
    source/compare_neon64.cc \
    source/convert.cc \
    source/convert_argb.cc \
    source/convert_from.cc \
    source/convert_from_argb.cc \
    source/convert_jpeg.cc \
    source/convert_to_argb.cc \
    source/convert_to_i420.cc \
    source/cpu_id.cc \
    source/mjpeg_decoder.cc \
    source/mjpeg_validate.cc \
    source/planar_functions.cc \
    source/rotate.cc \
    source/rotate_any.cc \
    source/rotate_argb.cc \
    source/rotate_common.cc \
    source/rotate_gcc.cc \
    source/rotate_msa.cc \
    source/rotate_neon.cc \
    source/rotate_neon64.cc \
    source/row_any.cc \
    source/row_common.cc \
    source/row_gcc.cc \
    source/row_msa.cc \
    source/row_neon.cc \
    source/row_neon64.cc \
    source/scale.cc \
    source/scale_any.cc \
    source/scale_argb.cc \
    source/scale_common.cc \
    source/scale_gcc.cc \
    source/scale_msa.cc \
    source/scale_neon.cc \
    source/scale_neon64.cc \
    source/scale_rgb.cc \
    source/scale_uv.cc \
    source/video_common.cc

LOCAL_SHARED_LIBRARIES := libjpeg

# LOCAL_EXPORT_C_INCLUDE_DIRS is used by the platform build system
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

# LOCAL_EXPORT_C_INCLUDES is used by the ndk build system
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SDK_VERSION := 29

LOCAL_MODULE := libyuv

include $(BUILD_SHARED_LIBRARY)


