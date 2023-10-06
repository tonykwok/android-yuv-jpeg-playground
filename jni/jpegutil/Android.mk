
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

LOCAL_LDLIBS += -llog \
                -ljnigraphics \

LOCAL_SHARED_LIBRARIES := libjpeg

LOCAL_SRC_FILES += jpegutil.cpp \
                   jpegutilnative.cpp

LOCAL_SDK_VERSION := 17

LOCAL_MODULE := libjni_jpegutil

include $(BUILD_SHARED_LIBRARY)
