
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(LOCAL_PATH)

# LOCAL_C_INCLUDES += $(LOCAL_PATH)/../libjpeg-turbo

LOCAL_CFLAGS := -ffast-math \
                -O3 \
                -funroll-loops \
                -Wextra

# LOCAL_SHARED_LIBRARIES := liblog \
#                           libdl \
#                           libjnigraphics

LOCAL_LDLIBS := -landroid \
                -llog \
                -ljnigraphics

LOCAL_SHARED_LIBRARIES := libyuv

LOCAL_SRC_FILES := image_processing_util_jni.cc

LOCAL_SDK_VERSION := 17

LOCAL_MODULE := libimage_processing_util_jni

include $(BUILD_SHARED_LIBRARY)
