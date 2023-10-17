/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.util;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility class to convert an {@link Image} from YUV to RGB.
 *
 */
public final class ImageProcessingUtil {

    private static final String TAG = "ImageProcessingUtil";
    private static int sImageCount = 0;

    static {
        System.loadLibrary("image_processing_util_jni");
    }

    enum Result {
        UNKNOWN,
        SUCCESS,
        ERROR_CONVERSION,  // Native conversion error.
    }

    private ImageProcessingUtil() {
    }

    /**
     * Wraps a JPEG byte array with an {@link Image}.
     *
     * <p>This methods wraps the given byte array with an {@link Image} via the help of the
     * given ImageReader. The image format of the ImageReader has to be JPEG, and the JPEG image
     * size has to match the size of the ImageReader.
     */
    @Nullable
    public static Image convertJpegBytesToImage(
            @NonNull ImageReader jpegImageReader,
            @NonNull byte[] jpegBytes) {
        if (jpegImageReader.getImageFormat() != ImageFormat.JPEG) {
            throw new IllegalArgumentException("Only " +
                    "ImageFormat.JPEG is supported, found " + jpegImageReader.getImageFormat());
        }
        if (jpegBytes == null) {
            throw new IllegalArgumentException("Jpeg bytes must not be null");
        }

        Surface surface = jpegImageReader.getSurface();
        if (surface == null) {
            throw new IllegalStateException("Could not acquire surface from jpegImageReader");
        }

        if (nativeWriteJpegToSurface(jpegBytes, surface) != 0) {
            Log.e(TAG, "Failed to enqueue JPEG image.");
            return null;
        }

        final Image image = jpegImageReader.acquireLatestImage();
        if (image == null) {
            Log.e(TAG, "Failed to get acquire JPEG image.");
        }
        return image;
    }


    /**
     * Copies information from a given Bitmap to the address of the ByteBuffer
     *
     * @param bitmap            source bitmap
     * @param byteBuffer        destination ByteBuffer
     * @param bufferStride      the stride of the ByteBuffer
     */
    public static void copyBitmapToByteBuffer(@NonNull Bitmap bitmap,
                                              @NonNull ByteBuffer byteBuffer, int bufferStride) {
        int bitmapStride = bitmap.getRowBytes();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        nativeCopyBetweenByteBufferAndBitmap(bitmap, byteBuffer, bitmapStride, bufferStride, width,
                height, false);
    }

    /**
     * Copies information from a ByteBuffer to the address of the Bitmap
     *
     * @param bitmap            destination Bitmap
     * @param byteBuffer        source ByteBuffer
     * @param bufferStride      the stride of the ByteBuffer
     *
     */
    public static void copyByteBufferToBitmap(@NonNull Bitmap bitmap,
                                              @NonNull ByteBuffer byteBuffer, int bufferStride) {
        int bitmapStride = bitmap.getRowBytes();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        nativeCopyBetweenByteBufferAndBitmap(bitmap, byteBuffer, bufferStride, bitmapStride, width,
                height, true);
    }

    /**
     * Writes a JPEG bytes data as an Image into the Surface. Returns true if it succeeds and false
     * otherwise.
     */
    public static boolean writeJpegBytesToSurface(
            @NonNull Surface surface,
            @NonNull byte[] jpegBytes) {
        Objects.requireNonNull(jpegBytes);
        Objects.requireNonNull(surface);

        if (nativeWriteJpegToSurface(jpegBytes, surface) != 0) {
            Log.e(TAG, "Failed to enqueue JPEG image.");
            return false;
        }
        return true;
    }

    /**
     * Convert a YUV_420_888 Image to a JPEG bytes data as an Image into the Surface.
     *
     * <p>Returns true if it succeeds and false otherwise.
     */
    public static boolean convertYuvToJpegBytesIntoSurface(
            @NonNull Image image,
            @IntRange(from = 1, to = 100) int jpegQuality,
            int rotationDegrees,
            @NonNull Surface outputSurface) {
        try {
            byte[] jpegBytes =
                    ImageUtils.yuvImageToJpegByteArray(
                            image, null, jpegQuality, rotationDegrees);
            return writeJpegBytesToSurface(outputSurface,
                    jpegBytes);
        } catch (ImageUtils.CodecFailedException e) {
            Log.e(TAG, "Failed to encode YUV to JPEG", e);
            return false;
        }
    }

    /**
     * Converts image in YUV to RGB.
     *
     * Currently this config supports the devices which generated NV21, NV12, I420 YUV layout,
     * otherwise the input YUV layout will be converted to NV12 first and then to RGBA_8888 as a
     * fallback.
     *
     * @param image                input image in YUV.
     * @param rgbImageReader       output image reader in RGB.
     * @param rgbConvertedBuffer   intermediate image buffer for format conversion.
     * @param rotationDegrees      output image rotation degrees.
     * @param onePixelShiftEnabled true if one pixel shift should be applied, otherwise false.
     * @return output image in RGB.
     */
    @Nullable
    public static Image convertYUVToRGB(
            @NonNull Image image,
            @NonNull ImageReader rgbImageReader,
            @Nullable ByteBuffer rgbConvertedBuffer,
            @IntRange(from = 0, to = 359) int rotationDegrees,
            boolean onePixelShiftEnabled) {
        if (!isSupportedYUVFormat(image)) {
            Log.e(TAG, "Unsupported format for YUV to RGB");
            return null;
        }
        long startTimeMillis = System.currentTimeMillis();

        if (!isSupportedRotationDegrees(rotationDegrees)) {
            Log.e(TAG, "Unsupported rotation degrees for rotate RGB");
            return null;
        }

        // Convert YUV To RGB and write data to surface
        Result result = convertYUVToRGBInternal(
                image,
                rgbImageReader.getSurface(),
                rgbConvertedBuffer,
                rotationDegrees,
                onePixelShiftEnabled);

        if (result == Result.ERROR_CONVERSION) {
            Log.e(TAG, "YUV to RGB conversion failure");
            return null;
        }
        if (Log.isLoggable("MH", Log.DEBUG)) {
            // The log is used to profile the ImageProcessing performance and only shows in the
            // mobile harness tests.
            Log.d(TAG, String.format(Locale.US,
                    "Image processing performance profiling, duration: [%d], image count: %d",
                    (System.currentTimeMillis() - startTimeMillis), sImageCount));
            sImageCount++;
        }

        // Retrieve Image in RGB
        final Image rgbImage = rgbImageReader.acquireLatestImage();
        if (rgbImage == null) {
            Log.e(TAG, "YUV to RGB acquireLatestImage failure");
            return null;
        }

        return rgbImage;
    }

    /**
     * Converts image in YUV to {@link Bitmap}.
     *
     * <p> Different from {@link ImageProcessingUtil#convertYUVToRGB(
     * Image, ImageReader, ByteBuffer, int, boolean)}, this function converts to
     * {@link Bitmap} in RGBA directly. If input format is invalid,
     * {@link IllegalArgumentException} will be thrown. If the conversion to bitmap failed,
     * {@link UnsupportedOperationException} will be thrown.
     *
     * @param image input image in YUV.
     * @return bitmap output bitmap in RGBA.
     */
    @NonNull
    public static Bitmap convertYUVToBitmap(@NonNull Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Input image format must be YUV_420_888");
        }

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int srcStrideY = image.getPlanes()[0].getRowStride();
        int srcStrideU = image.getPlanes()[1].getRowStride();
        int srcStrideV = image.getPlanes()[2].getRowStride();
        int srcPixelStrideY = image.getPlanes()[0].getPixelStride();
        int srcPixelStrideUV = image.getPlanes()[1].getPixelStride();

        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(),
                image.getHeight(), Bitmap.Config.ARGB_8888);
        int bitmapStride = bitmap.getRowBytes();

        int result = nativeConvertAndroid420ToBitmap(
                image.getPlanes()[0].getBuffer(),
                srcStrideY,
                image.getPlanes()[1].getBuffer(),
                srcStrideU,
                image.getPlanes()[2].getBuffer(),
                srcStrideV,
                srcPixelStrideY,
                srcPixelStrideUV,
                bitmap,
                bitmapStride,
                imageWidth,
                imageHeight);
        if (result != 0) {
            throw new UnsupportedOperationException("YUV to RGB conversion failed");
        }
        return bitmap;
    }

    /**
     * Applies one pixel shift workaround for YUV image
     *
     * @param image input image in YUV.
     * @return true if one pixel shift is applied successfully, otherwise false.
     */
    public static boolean applyPixelShiftForYUV(@NonNull Image image) {
        if (!isSupportedYUVFormat(image)) {
            Log.e(TAG, "Unsupported format for YUV to RGB");
            return false;
        }

        Result result = applyPixelShiftInternal(image);

        if (result == Result.ERROR_CONVERSION) {
            Log.e(TAG, "One pixel shift for YUV failure");
            return false;
        }
        return true;
    }

    /**
     * Rotates YUV image.
     *
     * @param image                   input image.
     * @param rotatedImageReader      input image reader.
     * @param rotatedImageWriter      output image writer.
     * @param yRotatedBuffer          intermediate image buffer for y plane rotation.
     * @param uRotatedBuffer          intermediate image buffer for u plane rotation.
     * @param vRotatedBuffer          intermediate image buffer for v plane rotation.
     * @param rotationDegrees         output image rotation degrees.
     * @return rotated image or null if rotation fails or format is not supported.
     */
    @Nullable
    public static Image rotateYUV(
            @NonNull Image image,
            @NonNull ImageReader rotatedImageReader,
            @NonNull ImageWriter rotatedImageWriter,
            @NonNull ByteBuffer yRotatedBuffer,
            @NonNull ByteBuffer uRotatedBuffer,
            @NonNull ByteBuffer vRotatedBuffer,
            @IntRange(from = 0, to = 359) int rotationDegrees) {
        if (!isSupportedYUVFormat(image)) {
            Log.e(TAG, "Unsupported format for rotate YUV");
            return null;
        }

        if (!isSupportedRotationDegrees(rotationDegrees)) {
            Log.e(TAG, "Unsupported rotation degrees for rotate YUV");
            return null;
        }

        Result result = Result.ERROR_CONVERSION;

        // YUV rotation is checking non-zero rotation degrees in java layer to avoid unnecessary
        // overhead, while RGB rotation is checking in c++ layer.
        if (Build.VERSION.SDK_INT >= 23 && rotationDegrees > 0) {
            result = rotateYUVInternal(
                    image,
                    rotatedImageWriter,
                    yRotatedBuffer,
                    uRotatedBuffer,
                    vRotatedBuffer,
                    rotationDegrees);
        }

        if (result == Result.ERROR_CONVERSION) {
            Log.e(TAG, "rotate YUV failure");
            return null;
        }

        // Retrieve Image in rotated YUV
        Image rotatedImage = rotatedImageReader.acquireLatestImage();
        if (rotatedImage == null) {
            Log.e(TAG, "YUV rotation acquireLatestImage failure");
            return null;
        }

        return rotatedImage;
    }

    private static boolean isSupportedYUVFormat(@NonNull Image image) {
        return image.getFormat() == ImageFormat.YUV_420_888
                && image.getPlanes().length == 3;
    }

    private static boolean isSupportedRotationDegrees(
            @IntRange(from = 0, to = 359) int rotationDegrees) {
        return rotationDegrees == 0
                || rotationDegrees == 90
                || rotationDegrees == 180
                || rotationDegrees == 270;
    }

    @NonNull
    private static Result convertYUVToRGBInternal(
            @NonNull Image image,
            @NonNull Surface surface,
            @Nullable ByteBuffer rgbConvertedBuffer,
            int rotation,
            boolean onePixelShiftEnabled) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int srcStrideY = image.getPlanes()[0].getRowStride();
        int srcStrideU = image.getPlanes()[1].getRowStride();
        int srcStrideV = image.getPlanes()[2].getRowStride();
        int srcPixelStrideY = image.getPlanes()[0].getPixelStride();
        int srcPixelStrideUV = image.getPlanes()[1].getPixelStride();

        int startOffsetY = onePixelShiftEnabled ? srcPixelStrideY : 0;
        int startOffsetU = onePixelShiftEnabled ? srcPixelStrideUV : 0;
        int startOffsetV = onePixelShiftEnabled ? srcPixelStrideUV : 0;

        int result = nativeConvertAndroid420ToABGR(
                image.getPlanes()[0].getBuffer(),
                srcStrideY,
                image.getPlanes()[1].getBuffer(),
                srcStrideU,
                image.getPlanes()[2].getBuffer(),
                srcStrideV,
                srcPixelStrideY,
                srcPixelStrideUV,
                surface,
                rgbConvertedBuffer,
                imageWidth,
                imageHeight,
                startOffsetY,
                startOffsetU,
                startOffsetV,
                rotation);
        if (result != 0) {
            return Result.ERROR_CONVERSION;
        }
        return Result.SUCCESS;
    }

    @NonNull
    private static Result applyPixelShiftInternal(@NonNull Image image) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int srcStrideY = image.getPlanes()[0].getRowStride();
        int srcStrideU = image.getPlanes()[1].getRowStride();
        int srcStrideV = image.getPlanes()[2].getRowStride();
        int srcPixelStrideY = image.getPlanes()[0].getPixelStride();
        int srcPixelStrideUV = image.getPlanes()[1].getPixelStride();

        int startOffsetY = srcPixelStrideY;
        int startOffsetU = srcPixelStrideUV;
        int startOffsetV = srcPixelStrideUV;

        int result = nativeShiftPixel(
                image.getPlanes()[0].getBuffer(),
                srcStrideY,
                image.getPlanes()[1].getBuffer(),
                srcStrideU,
                image.getPlanes()[2].getBuffer(),
                srcStrideV,
                srcPixelStrideY,
                srcPixelStrideUV,
                imageWidth,
                imageHeight,
                startOffsetY,
                startOffsetU,
                startOffsetV);
        if (result != 0) {
            return Result.ERROR_CONVERSION;
        }
        return Result.SUCCESS;
    }

    @RequiresApi(23)
    @Nullable
    private static Result rotateYUVInternal(
            @NonNull Image image,
            @NonNull ImageWriter rotatedImageWriter,
            @NonNull ByteBuffer yRotatedBuffer,
            @NonNull ByteBuffer uRotatedBuffer,
            @NonNull ByteBuffer vRotatedBuffer,
            int rotationDegrees) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int srcStrideY = image.getPlanes()[0].getRowStride();
        int srcStrideU = image.getPlanes()[1].getRowStride();
        int srcStrideV = image.getPlanes()[2].getRowStride();
        int srcPixelStrideUV = image.getPlanes()[1].getPixelStride();

        Image rotatedImage = rotatedImageWriter.dequeueInputImage();
        if (rotatedImage == null) {
            return Result.ERROR_CONVERSION;
        }

        int result = nativeRotateYUV(
                image.getPlanes()[0].getBuffer(),
                srcStrideY,
                image.getPlanes()[1].getBuffer(),
                srcStrideU,
                image.getPlanes()[2].getBuffer(),
                srcStrideV,
                srcPixelStrideUV,
                rotatedImage.getPlanes()[0].getBuffer(),
                rotatedImage.getPlanes()[0].getRowStride(),
                rotatedImage.getPlanes()[0].getPixelStride(),
                rotatedImage.getPlanes()[1].getBuffer(),
                rotatedImage.getPlanes()[1].getRowStride(),
                rotatedImage.getPlanes()[1].getPixelStride(),
                rotatedImage.getPlanes()[2].getBuffer(),
                rotatedImage.getPlanes()[2].getRowStride(),
                rotatedImage.getPlanes()[2].getPixelStride(),
                yRotatedBuffer,
                uRotatedBuffer,
                vRotatedBuffer,
                imageWidth,
                imageHeight,
                rotationDegrees);

        if (result != 0) {
            return Result.ERROR_CONVERSION;
        }

        rotatedImageWriter.queueInputImage(rotatedImage);
        return Result.SUCCESS;
    }


    private static native int nativeCopyBetweenByteBufferAndBitmap(Bitmap bitmap,
                                                                   ByteBuffer byteBuffer,
                                                                   int sourceStride, int destinationStride, int width, int height,
                                                                   boolean isCopyBufferToBitmap);


    private static native int nativeWriteJpegToSurface(@NonNull byte[] jpegArray,
                                                       @NonNull Surface surface);

    private static native int nativeConvertAndroid420ToABGR(
            @NonNull ByteBuffer srcByteBufferY,
            int srcStrideY,
            @NonNull ByteBuffer srcByteBufferU,
            int srcStrideU,
            @NonNull ByteBuffer srcByteBufferV,
            int srcStrideV,
            int srcPixelStrideY,
            int srcPixelStrideUV,
            @Nullable Surface surface,
            @Nullable ByteBuffer convertedByteBufferRGB,
            int width,
            int height,
            int startOffsetY,
            int startOffsetU,
            int startOffsetV,
            int rotationDegrees);

    private static native int nativeConvertAndroid420ToBitmap(
            @NonNull ByteBuffer srcByteBufferY,
            int srcStrideY,
            @NonNull ByteBuffer srcByteBufferU,
            int srcStrideU,
            @NonNull ByteBuffer srcByteBufferV,
            int srcStrideV,
            int srcPixelStrideY,
            int srcPixelStrideUV,
            @NonNull Bitmap bitmap,
            int bitmapStride,
            int width,
            int height);

    private static native int nativeShiftPixel(
            @NonNull ByteBuffer srcByteBufferY,
            int srcStrideY,
            @NonNull ByteBuffer srcByteBufferU,
            int srcStrideU,
            @NonNull ByteBuffer srcByteBufferV,
            int srcStrideV,
            int srcPixelStrideY,
            int srcPixelStrideUV,
            int width,
            int height,
            int startOffsetY,
            int startOffsetU,
            int startOffsetV);

    private static native int nativeRotateYUV(
            @NonNull ByteBuffer srcByteBufferY,
            int srcStrideY,
            @NonNull ByteBuffer srcByteBufferU,
            int srcStrideU,
            @NonNull ByteBuffer srcByteBufferV,
            int srcStrideV,
            int srcPixelStrideUV,
            @NonNull ByteBuffer dstByteBufferY,
            int dstStrideY,
            int dstPixelStrideY,
            @NonNull ByteBuffer dstByteBufferU,
            int dstStrideU,
            int dstPixelStrideU,
            @NonNull ByteBuffer dstByteBufferV,
            int dstStrideV,
            int dstPixelStrideV,
            @NonNull ByteBuffer rotatedByteBufferY,
            @NonNull ByteBuffer rotatedByteBufferU,
            @NonNull ByteBuffer rotatedByteBufferV,
            int width,
            int height,
            int rotationDegrees);
}

