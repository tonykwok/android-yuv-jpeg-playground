
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(LOCAL_PATH)

LOCAL_CFLAGS := -ffast-math \
                -O3 \
                -funroll-loops \
                -Wextra

# LOCAL_SHARED_LIBRARIES := liblog \
#                           libdl \
#                           libjnigraphics

LOCAL_LDLIBS := -llog \
                -ljnigraphics \

LOCAL_SRC_FILES := stackblur.cpp

LOCAL_SDK_VERSION := 17

LOCAL_MODULE := libjni_stackblur

include $(BUILD_SHARED_LIBRARY)
