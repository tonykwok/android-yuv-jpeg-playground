/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @hide
 */
public class ImageUtils {
    private static final String TAG = "ImageUtils";

    /**
     * Converts JPEG {@link Image} to JPEG byte array.
     */
    @NonNull
    public static byte[] jpegImageToJpegByteArray(@NonNull Image image) {
        if (image.getFormat() != ImageFormat.JPEG) {
            throw new IllegalArgumentException(
                    "Incorrect image format of the input image proxy: " + image.getFormat());
        }

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.rewind();
        buffer.get(data);

        return data;
    }

    /**
     * Converts RGBA_8888 {@link Image} to RGBA_8888 byte array.
     */
    @NonNull
    public static byte[] rgbaImageToRgbaByteArray(@NonNull Image image) {
        if (image.getFormat() != PixelFormat.RGBA_8888) {
            throw new IllegalArgumentException(
                    "Incorrect image format of the input image proxy: " + image.getFormat());
        }
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int pixelStride = planes[0].getPixelStride();
        final int rowStride = planes[0].getRowStride();
        final int rowPadding = rowStride - pixelStride * width;

        final byte[] imageBuffer = new byte[buffer.remaining()];
        buffer.get(imageBuffer);
        if (rowPadding == 0) {
            return imageBuffer;
        }

        final int actualSize = width * height * pixelStride;
        final byte[] result = new byte[actualSize];

        int srcPos = 0;
        int dstPos = 0;
        for (int i = 0; i < height ; ++i) {
            System.arraycopy(imageBuffer, srcPos, result, dstPos, width * pixelStride);
            srcPos += rowStride;
            dstPos += width * pixelStride;
        }
        return result;
    }

    /**
     * Converts JPEG {@link Image} to JPEG byte array. The input JPEG image will be cropped
     * by the specified crop rectangle and compressed by the specified quality value.
     */
    @NonNull
    public static byte[] jpegImageToJpegByteArray(@NonNull Image image,
                                                  @NonNull Rect cropRect, @IntRange(from = 1, to = 100) int jpegQuality)
            throws CodecFailedException {
        if (image.getFormat() != ImageFormat.JPEG) {
            throw new IllegalArgumentException(
                    "Incorrect image format of the input image proxy: " + image.getFormat());
        }

        byte[] data = jpegImageToJpegByteArray(image);
        data = cropJpegByteArray(data, cropRect, jpegQuality);

        return data;
    }

    /**
     * Converts YUV_420_888 {@link Image} to JPEG byte array. The input YUV_420_888 image
     * will be cropped if a non-null crop rectangle is specified. The output JPEG byte array will
     * be compressed by the specified quality value. The rotationDegrees is set to the EXIF of
     * the JPEG if it is not 0.
     */
    @NonNull
    public static byte[] yuvImageToJpegByteArray(@NonNull Image image,
                                                 @Nullable Rect cropRect,
                                                 @IntRange(from = 1, to = 100)
                                                 int jpegQuality,
                                                 int rotationDegrees) throws CodecFailedException {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException(
                    "Incorrect image format of the input image proxy: " + image.getFormat());
        }

        byte[] yuvBytes = yuv_420_888toNv21(image);
        YuvImage yuv = new YuvImage(yuvBytes, ImageFormat.NV21, image.getWidth(), image.getHeight(),
                null);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        if (cropRect == null) {
            cropRect = new Rect(0, 0, image.getWidth(), image.getHeight());
        }
        boolean success =
                yuv.compressToJpeg(cropRect, jpegQuality, byteArrayOutputStream);
        if (!success) {
            throw new CodecFailedException("YuvImage failed to encode jpeg.",
                    CodecFailedException.FailureType.ENCODE_FAILED);
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Generate a direct NV21 {@link ByteBuffer} from a YUV420_888 {@link Image}.
     */
    @NonNull
    public static byte[] yuv_420_888toNv21(@NonNull Image image) {
        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();
        yBuffer.rewind();
        uBuffer.rewind();
        vBuffer.rewind();

        int ySize = yBuffer.remaining();

        int position = 0;
        // TODO(b/115743986): Pull these bytes from a pool instead of allocating for every image.
        byte[] nv21 = new byte[ySize + (image.getWidth() * image.getHeight() / 2)];

        // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
        for (int row = 0; row < image.getHeight(); row++) {
            yBuffer.get(nv21, position, image.getWidth());
            position += image.getWidth();
            yBuffer.position(
                    Math.min(ySize, yBuffer.position() - image.getWidth() + yPlane.getRowStride()));
        }

        int chromaHeight = image.getHeight() / 2;
        int chromaWidth = image.getWidth() / 2;
        int vRowStride = vPlane.getRowStride();
        int uRowStride = uPlane.getRowStride();
        int vPixelStride = vPlane.getPixelStride();
        int uPixelStride = uPlane.getPixelStride();

        // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
        // perform faster bulk gets from the byte buffers.
        byte[] vLineBuffer = new byte[vRowStride];
        byte[] uLineBuffer = new byte[uRowStride];
        for (int row = 0; row < chromaHeight; row++) {
            vBuffer.get(vLineBuffer, 0, Math.min(vRowStride, vBuffer.remaining()));
            uBuffer.get(uLineBuffer, 0, Math.min(uRowStride, uBuffer.remaining()));
            int vLineBufferPosition = 0;
            int uLineBufferPosition = 0;
            for (int col = 0; col < chromaWidth; col++) {
                nv21[position++] = vLineBuffer[vLineBufferPosition];
                nv21[position++] = uLineBuffer[uLineBufferPosition];
                vLineBufferPosition += vPixelStride;
                uLineBufferPosition += uPixelStride;
            }
        }

        return nv21;
    }

    /** Crops JPEG byte array with given {@link Rect}. */
    @NonNull
    @SuppressWarnings("deprecation")
    private static byte[] cropJpegByteArray(@NonNull byte[] data, @NonNull Rect cropRect,
                                            @IntRange(from = 1, to = 100) int jpegQuality) throws CodecFailedException {
        Bitmap bitmap;
        try {
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length,
                    false);
            bitmap = decoder.decodeRegion(cropRect, new BitmapFactory.Options());
            decoder.recycle();
        } catch (IllegalArgumentException e) {
            throw new CodecFailedException("Decode byte array failed with illegal argument." + e,
                    CodecFailedException.FailureType.DECODE_FAILED);
        } catch (IOException e) {
            throw new CodecFailedException("Decode byte array failed.",
                    CodecFailedException.FailureType.DECODE_FAILED);
        }

        if (bitmap == null) {
            throw new CodecFailedException("Decode byte array failed.",
                    CodecFailedException.FailureType.DECODE_FAILED);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
        if (!success) {
            throw new CodecFailedException("Encode bitmap failed.",
                    CodecFailedException.FailureType.ENCODE_FAILED);
        }
        bitmap.recycle();

        return out.toByteArray();
    }

    private static final int BYTES_PER_RGB_PIX = 3; // bytes per pixel

    /**
     * Convert a single YUV pixel to RGB.
     */
    private static void yuvToRgb(byte[] yuvData, int outOffset, /*out*/byte[] rgbOut) {
        final int COLOR_MAX = 255;

        float y = yuvData[0] & 0xFF;  // Y channel
        float cb = yuvData[1] & 0xFF; // U channel
        float cr = yuvData[2] & 0xFF; // V channel

        // convert YUV -> RGB (from JFIF's "Conversion to and from RGB" section)
        float r = y + 1.402f * (cr - 128);
        float g = y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128);
        float b = y + 1.772f * (cb - 128);

        // clamp to [0,255]
        rgbOut[outOffset] = (byte) Math.max(0, Math.min(COLOR_MAX, r));
        rgbOut[outOffset + 1] = (byte) Math.max(0, Math.min(COLOR_MAX, g));
        rgbOut[outOffset + 2] = (byte) Math.max(0, Math.min(COLOR_MAX, b));
    }

    /**
     * Convert a single {@link Color} pixel to RGB.
     */
    private static void colorToRgb(int color, int outOffset, /*out*/byte[] rgbOut) {
        rgbOut[outOffset] = (byte) Color.red(color);
        rgbOut[outOffset + 1] = (byte) Color.green(color);
        rgbOut[outOffset + 2] = (byte) Color.blue(color);
        // Discards Alpha
    }

    /**
     * Generate a direct RGB {@link ByteBuffer} from a YUV420_888 {@link Image}.
     */
    private static ByteBuffer convertToRGB(Image yuvImage) {
        int width = yuvImage.getWidth();
        int height = yuvImage.getHeight();
        ByteBuffer buf = ByteBuffer.allocateDirect(BYTES_PER_RGB_PIX * width * height);

        Image.Plane yPlane = yuvImage.getPlanes()[0];
        Image.Plane uPlane = yuvImage.getPlanes()[1];
        Image.Plane vPlane = yuvImage.getPlanes()[2];

        ByteBuffer yBuf = yPlane.getBuffer();
        ByteBuffer uBuf = uPlane.getBuffer();
        ByteBuffer vBuf = vPlane.getBuffer();

        yBuf.rewind();
        uBuf.rewind();
        vBuf.rewind();

        int yRowStride = yPlane.getRowStride();
        int vRowStride = vPlane.getRowStride();
        int uRowStride = uPlane.getRowStride();

        int yPixStride = yPlane.getPixelStride();
        int vPixStride = vPlane.getPixelStride();
        int uPixStride = uPlane.getPixelStride();

        byte[] yuvPixel = { 0, 0, 0 };
        byte[] yFullRow = new byte[yPixStride * (width - 1) + 1];
        byte[] uFullRow = new byte[uPixStride * (width / 2 - 1) + 1];
        byte[] vFullRow = new byte[vPixStride * (width / 2 - 1) + 1];
        byte[] finalRow = new byte[BYTES_PER_RGB_PIX * width];
        for (int i = 0; i < height; i++) {
            int halfH = i / 2;
            yBuf.position(yRowStride * i);
            yBuf.get(yFullRow);
            uBuf.position(uRowStride * halfH);
            uBuf.get(uFullRow);
            vBuf.position(vRowStride * halfH);
            vBuf.get(vFullRow);
            for (int j = 0; j < width; j++) {
                int halfW = j / 2;
                yuvPixel[0] = yFullRow[yPixStride * j];
                yuvPixel[1] = uFullRow[uPixStride * halfW];
                yuvPixel[2] = vFullRow[vPixStride * halfW];
                yuvToRgb(yuvPixel, j * BYTES_PER_RGB_PIX, /*out*/finalRow);
            }
            buf.put(finalRow);
        }

        yBuf.rewind();
        uBuf.rewind();
        vBuf.rewind();
        buf.rewind();
        return buf;
    }

    /** Exception for error during transcoding image. */
    public static final class CodecFailedException extends Exception {
        public enum FailureType {
            ENCODE_FAILED,
            DECODE_FAILED,
            UNKNOWN
        }

        private final FailureType mFailureType;

        CodecFailedException(@NonNull String message) {
            super(message);
            mFailureType = FailureType.UNKNOWN;
        }

        CodecFailedException(@NonNull String message, @NonNull FailureType failureType) {
            super(message);
            mFailureType = failureType;
        }

        @NonNull
        public FailureType getFailureType() {
            return mFailureType;
        }
    }
}
