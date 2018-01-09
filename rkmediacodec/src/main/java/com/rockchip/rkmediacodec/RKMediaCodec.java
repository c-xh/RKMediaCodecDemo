package com.rockchip.rkmediacodec;

import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Surface;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final public class RKMediaCodec {

    static {
        System.loadLibrary("native-lib");
    }
    /**
     * Per buffer metadata includes an offset and size specifying
     * the range of valid data in the associated codec (output) buffer.
     */
    public final static class BufferInfo {
        /**
         * Update the buffer metadata information.
         *
         * @param newOffset the start-offset of the data in the buffer.
         * @param newSize   the amount of data (in bytes) in the buffer.
         * @param newTimeUs the presentation timestamp in microseconds.
         * @param newFlags  buffer flags associated with the buffer.  This
         * should be a combination of  {@link #BUFFER_FLAG_KEY_FRAME} and
         * {@link #BUFFER_FLAG_END_OF_STREAM}.
         */
        public void set( int newOffset, int newSize, long newTimeUs, @BufferFlag int newFlags) {
            offset = newOffset;
            size = newSize;
            presentationTimeUs = newTimeUs;
            flags = newFlags;
        }

        /**
         * The start-offset of the data in the buffer.
         */
        public int offset;

        /**
         * The amount of data (in bytes) in the buffer.  If this is {@code 0},
         * the buffer has no data in it and can be discarded.  The only
         * use of a 0-size buffer is to carry the end-of-stream marker.
         */
        public int size;

        /**
         * The presentation timestamp in microseconds for the buffer.
         * This is derived from the presentation timestamp passed in
         * with the corresponding input buffer.  This should be ignored for
         * a 0-sized buffer.
         */
        public long presentationTimeUs;

        /**
         * Buffer flags associated with the buffer.  A combination of
         * {@link #BUFFER_FLAG_KEY_FRAME} and {@link #BUFFER_FLAG_END_OF_STREAM}.
         *
         *Encoded buffers that are key frames are marked with
         * {@link #BUFFER_FLAG_KEY_FRAME}.
         *
         *The last output buffer corresponding to the input buffer
         * marked with {@link #BUFFER_FLAG_END_OF_STREAM} will also be marked
         * with {@link #BUFFER_FLAG_END_OF_STREAM}. In some cases this could
         * be an empty buffer, whose sole purpose is to carry the end-of-stream
         * marker.
         */
        @BufferFlag
        public int flags;

        /** @hide */
        @NonNull
        public BufferInfo dup() {
            throw new RuntimeException("Not Support!");
//            BufferInfo copy = new BufferInfo();
//            copy.set(offset, size, presentationTimeUs, flags);
//            return copy;
        }
    };

    // The follow flag constants MUST stay in sync with their equivalents
    // in MediaCodec.h !

    /**
     * This indicates that the (encoded) buffer marked as such contains
     * the data for a key frame.
     *
     * @deprecated Use {@link #BUFFER_FLAG_KEY_FRAME} instead.
     */
    public static final int BUFFER_FLAG_SYNC_FRAME = 1;

    /**
     * This indicates that the (encoded) buffer marked as such contains
     * the data for a key frame.
     */
    public static final int BUFFER_FLAG_KEY_FRAME = 1;

    /**
     * This indicated that the buffer marked as such contains codec
     * initialization / codec specific data instead of media data.
     */
    public static final int BUFFER_FLAG_CODEC_CONFIG = 2;

    /**
     * This signals the end of stream, i.e. no buffers will be available
     * after this, unless of course, {@link #flush} follows.
     */
    public static final int BUFFER_FLAG_END_OF_STREAM = 4;

//    /** @hide */
//    @SuppressLint("UniqueConstants")
//    @IntDef(
//            flag = true,
//            value = {
//                    BUFFER_FLAG_SYNC_FRAME,
//                    BUFFER_FLAG_KEY_FRAME,
//                    BUFFER_FLAG_CODEC_CONFIG,
//                    BUFFER_FLAG_END_OF_STREAM,
//            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BufferFlag {}

    private EventHandler mEventHandler;
    private EventHandler mOnFrameRenderedHandler;
    private EventHandler mCallbackHandler;
    private Callback mCallback;
    private OnFrameRenderedListener mOnFrameRenderedListener;
    private Object mListenerLock = new Object();

    private static final int EVENT_CALLBACK = 1;
    private static final int EVENT_SET_CALLBACK = 2;
    private static final int EVENT_FRAME_RENDERED = 3;

    private static final int CB_INPUT_AVAILABLE = 1;
    private static final int CB_OUTPUT_AVAILABLE = 2;
    private static final int CB_ERROR = 3;
    private static final int CB_OUTPUT_FORMAT_CHANGE = 4;

    private class EventHandler extends Handler {
        private RKMediaCodec mCodec;

        public EventHandler(@NonNull RKMediaCodec codec, @NonNull Looper looper) {
            super(looper);
            mCodec = codec;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVENT_CALLBACK: {
                    handleCallback(msg);
                    break;
                }
                case EVENT_SET_CALLBACK: {
                    mCallback = (RKMediaCodec.Callback) msg.obj;
                    break;
                }
                case EVENT_FRAME_RENDERED:
                    synchronized (mListenerLock) {
                        Map<String, Object> map = (Map<String, Object>) msg.obj;
                        for (int i = 0; ; ++i) {
                            Object mediaTimeUs = map.get(i + "-media-time-us");
                            Object systemNano = map.get(i + "-system-nano");
                            if (mediaTimeUs == null || systemNano == null
                                    || mOnFrameRenderedListener == null) {
                                break;
                            }
                            mOnFrameRenderedListener.onFrameRendered(
                                    mCodec, (long) mediaTimeUs, (long) systemNano);
                        }
                        break;
                    }
                default: {
                    break;
                }
            }
        }

        private void handleCallback(@NonNull Message msg) {
            if (mCallback == null) {
                return;
            }

            switch (msg.arg1) {
                case CB_INPUT_AVAILABLE: {
                    int index = msg.arg2;
                    synchronized (mBufferLock) {
                        validateInputByteBuffer(mCachedInputBuffers, index);
                    }
                    mCallback.onInputBufferAvailable(mCodec, index);
                    break;
                }

                case CB_OUTPUT_AVAILABLE: {
                    int index = msg.arg2;
                    BufferInfo info = (RKMediaCodec.BufferInfo) msg.obj;
                    synchronized (mBufferLock) {
                        validateOutputByteBuffer(mCachedOutputBuffers, index, info);
                    }
                    mCallback.onOutputBufferAvailable(mCodec, index, info);
                    break;
                }

                case CB_ERROR: {
                    mCallback.onError(mCodec, (RKMediaCodec.CodecException) msg.obj);
                    break;
                }

                case CB_OUTPUT_FORMAT_CHANGE: {
                    throw new RuntimeException("Not Support!");
//                    mCallback.onOutputFormatChanged(mCodec, new MediaFormat((Map<String, Object>) msg.obj));
//                    break;
                }

                default: {
                    break;
                }
            }
        }
    }

    private boolean mHasSurface = false;

    /**
     * Instantiate the preferred decoder supporting input data of the given mime type.
     *
     * The following is a partial list of defined mime types and their semantics:
     * <ul>
     * <li>"video/x-vnd.on2.vp8" - VP8 video (i.e. video in .webm)
     * <li>"video/x-vnd.on2.vp9" - VP9 video (i.e. video in .webm)
     * <li>"video/avc" - H.264/AVC video
     * <li>"video/hevc" - H.265/HEVC video
     * <li>"video/mp4v-es" - MPEG4 video
     * <li>"video/3gpp" - H.263 video
     * <li>"audio/3gpp" - AMR narrowband audio
     * <li>"audio/amr-wb" - AMR wideband audio
     * <li>"audio/mpeg" - MPEG1/2 audio layer III
     * <li>"audio/mp4a-latm" - AAC audio (note, this is raw AAC packets, not packaged in LATM!)
     * <li>"audio/vorbis" - vorbis audio
     * <li>"audio/g711-alaw" - G.711 alaw audio
     * <li>"audio/g711-mlaw" - G.711 ulaw audio
     * </ul>
     *
     * <strong>Note:</strong> It is preferred to use {@link MediaCodecList#findDecoderForFormat}
     * and {@link #createByCodecName} to ensure that the resulting codec can handle a
     * given format.
     *
     * @param type The mime type of the input data.
     * @throws IOException if the codec cannot be created.
     * @throws IllegalArgumentException if type is not a valid mime type.
     * @throws NullPointerException if type is null.
     */
    @NonNull
    public static RKMediaCodec createDecoderByType(@NonNull String type) throws IOException {
        return new RKMediaCodec(type, true /* nameIsType */, false /* encoder */);
    }

    /**
     * Instantiate the preferred encoder supporting output data of the given mime type.
     *
     * <strong>Note:</strong> It is preferred to use {@link MediaCodecList#findEncoderForFormat}
     * and {@link #createByCodecName} to ensure that the resulting codec can handle a
     * given format.
     *
     * @param type The desired mime type of the output data.
     * @throws IOException if the codec cannot be created.
     * @throws IllegalArgumentException if type is not a valid mime type.
     * @throws NullPointerException if type is null.
     */
    @NonNull
    public static RKMediaCodec createEncoderByType(@NonNull String type) throws IOException {
        return new RKMediaCodec(type, true /* nameIsType */, true /* encoder */);
    }

    /**
     * If you know the exact name of the component you want to instantiate
     * use this method to instantiate it. Use with caution.
     * Likely to be used with information obtained from {@link MediaCodecList}
     * @param name The name of the codec to be instantiated.
     * @throws IOException if the codec cannot be created.
     * @throws IllegalArgumentException if name is not valid.
     * @throws NullPointerException if name is null.
     */
    @NonNull
    public static RKMediaCodec createByCodecName(@NonNull String name) throws IOException {
        return new RKMediaCodec(name, false /* nameIsType */, false /* unused */);
    }

    private RKMediaCodec(@NonNull String name, boolean nameIsType, boolean encoder) {
        throw new RuntimeException("Not Support!");
//        Looper looper;
//        if ((looper = Looper.myLooper()) != null) {
//            mEventHandler = new EventHandler(this, looper);
//        } else if ((looper = Looper.getMainLooper()) != null) {
//            mEventHandler = new EventHandler(this, looper);
//        } else {
//            mEventHandler = null;
//        }
//        mCallbackHandler = mEventHandler;
//        mOnFrameRenderedHandler = mEventHandler;
//
//        mBufferLock = new Object();
//
//        native_setup(name, nameIsType, encoder);
    }

    @Override
    protected void finalize() {
        throw new RuntimeException("Not Support!");
//        native_finalize();
    }

    /**
     * Returns the codec to its initial (Uninitialized) state.
     *
     * Call this if an {@link RKMediaCodec.CodecException#isRecoverable unrecoverable}
     * error has occured to reset the codec to its initial state after creation.
     *
     * @throws CodecException if an unrecoverable error has occured and the codec
     * could not be reset.
     * @throws IllegalStateException if in the Released state.
     */
    public final void reset() {
        throw new RuntimeException("Not Support!");
//        freeAllTrackedBuffers(); // free buffers first
//        native_reset();
    }

//    private native final void native_reset();

    /**
     * Free up resources used by the codec instance.
     *
     * Make sure you call this when you're done to free up any opened
     * component instance instead of relying on the garbage collector
     * to do this for you at some point in the future.
     */
    public final void release() {
        throw new RuntimeException("Not Support!");
//        freeAllTrackedBuffers(); // free buffers first
//        native_release();
    }

//    private native final void native_release();

    /**
     * If this codec is to be used as an encoder, pass this flag.
     */
    public static final int CONFIGURE_FLAG_ENCODE = 1;

    /** @hide */
    @IntDef(flag = true, value = { CONFIGURE_FLAG_ENCODE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConfigureFlag {}

    /**
     * Configures a component.
     *
     * @param format The format of the input data (decoder) or the desired
     *               format of the output data (encoder). Passing {@code null}
     *               as {@code format} is equivalent to passing an
     *               {@link MediaFormat#MediaFormat an empty mediaformat}.
     * @param surface Specify a surface on which to render the output of this
     *                decoder. Pass {@code null} as {@code surface} if the
     *                codec does not generate raw video output (e.g. not a video
     *                decoder) and/or if you want to configure the codec for
     *                {@link ByteBuffer} output.
     * @param crypto  Specify a crypto object to facilitate secure decryption
     *                of the media data. Pass {@code null} as {@code crypto} for
     *                non-secure codecs.
     * @param flags   Specify {@link #CONFIGURE_FLAG_ENCODE} to configure the
     *                component as an encoder.
     * @throws IllegalArgumentException if the surface has been released (or is invalid),
     * or the format is unacceptable (e.g. missing a mandatory key),
     * or the flags are not set properly
     * (e.g. missing {@link #CONFIGURE_FLAG_ENCODE} for an encoder).
     * @throws IllegalStateException if not in the Uninitialized state.
     * @throws CryptoException upon DRM error.
     * @throws CodecException upon codec error.
     */
    public void configure(
            @Nullable MediaFormat format,
            @Nullable Surface surface, @Nullable MediaCrypto crypto,
            @ConfigureFlag int flags) {
        throw new RuntimeException("Not Support!");
//        String[] keys = null;
//        Object[] values = null;
//
//        if (format != null) {
//            Map<String, Object> formatMap = format.getMap();
//            keys = new String[formatMap.size()];
//            values = new Object[formatMap.size()];
//
//            int i = 0;
//            for (Map.Entry<String, Object> entry: formatMap.entrySet()) {
//                if (entry.getKey().equals(MediaFormat.KEY_AUDIO_SESSION_ID)) {
//                    int sessionId = 0;
//                    try {
//                        sessionId = (Integer)entry.getValue();
//                    }
//                    catch (Exception e) {
//                        throw new IllegalArgumentException("Wrong Session ID Parameter!");
//                    }
//                    keys[i] = "audio-hw-sync";
//                    values[i] = AudioSystem.getAudioHwSyncForSession(sessionId);
//                } else {
//                    keys[i] = entry.getKey();
//                    values[i] = entry.getValue();
//                }
//                ++i;
//            }
//        }
//
//        mHasSurface = surface != null;
//
//        native_configure(keys, values, surface, crypto, flags);
    }

    /**
     *  Dynamically sets the output surface of a codec.
     *
     *  This can only be used if the codec was configured with an output surface.  The
     *  new output surface should have a compatible usage type to the original output surface.
     *  E.g. codecs may not support switching from a SurfaceTexture (GPU readable) output
     *  to ImageReader (software readable) output.
     *  @param surface the output surface to use. It must not be {@code null}.
     *  @throws IllegalStateException if the codec does not support setting the output
     *            surface in the current state.
     *  @throws IllegalArgumentException if the new surface is not of a suitable type for the codec.
     */
    public void setOutputSurface(@NonNull Surface surface) {
        throw new RuntimeException("Not Support!");
//        if (!mHasSurface) {
//            throw new IllegalStateException("codec was not configured for an output surface");
//        }
//        native_setSurface(surface);
    }

//    private native void native_setSurface(@NonNull Surface surface);

    /**
     * Create a persistent input surface that can be used with codecs that normally have an input
     * surface, such as video encoders. A persistent input can be reused by subsequent
     * {@link RKMediaCodec}  instances, but can only be used by at
     * most one codec or recorder instance concurrently.
     *
     * The application is responsible for calling release() on the Surface when done.
     *
     * @return an input surface that can be used with {@link #setInputSurface}.
     */
    @NonNull
    public static Surface createPersistentInputSurface() {
        throw new RuntimeException("Not Support!");
//        return native_createPersistentInputSurface();
    }

    static class PersistentSurface extends Surface {
        public PersistentSurface(SurfaceTexture surfaceTexture) {
            super(surfaceTexture);
        }

//        @SuppressWarnings("unused")
//        PersistentSurface() {} // used by native

        @Override
        public void release() {
            throw new RuntimeException("Not Support!");
//            native_releasePersistentInputSurface(this);
//            super.release();
        }

        private long mPersistentObject;
    };

    /**
     * Configures the codec (e.g. encoder) to use a persistent input surface in place of input
     * buffers.  This may only be called after {@link #configure} and before {@link #start}, in
     * @param surface a persistent input surface created by {@link #createPersistentInputSurface}
     * @throws IllegalStateException if not in the Configured state or does not require an input
     *           surface.
     * @throws IllegalArgumentException if the surface was not created by
     *           {@link #createPersistentInputSurface}.
     */
    public void setInputSurface(@NonNull Surface surface) {
        throw new RuntimeException("Not Support!");
//        if (!(surface instanceof PersistentSurface)) {
//            throw new IllegalArgumentException("not a PersistentSurface");
//        }
//        native_setInputSurface(surface);
    }

//    @NonNull
//    private static native final PersistentSurface native_createPersistentInputSurface();
//    private static native final void native_releasePersistentInputSurface(@NonNull Surface surface);
//    private native final void native_setInputSurface(@NonNull Surface surface);
//
//    private native final void native_setCallback(@Nullable Callback cb);
//
//    private native final void native_configure(
//            @Nullable String[] keys, @Nullable Object[] values,
//            @Nullable Surface surface, @Nullable MediaCrypto crypto, @ConfigureFlag int flags);

    /**
     * Requests a Surface to use as the input to an encoder, in place of input buffers.  This
     * may only be called after {@link #configure} and before {@link #start}.
     *
     * The application is responsible for calling release() on the Surface when
     * done.
     *
     * The Surface must be rendered with a hardware-accelerated API, such as OpenGL ES.
     * {@link Surface#lockCanvas(android.graphics.Rect)} may fail or produce
     * unexpected results.
     * @throws IllegalStateException if not in the Configured state.
     */
//    @NonNull
//    public native final Surface createInputSurface();

    /**
     * After successfully configuring the component, call {@code start}.
     *
     * Call {@code start} also if the codec is configured in asynchronous mode,
     * and it has just been flushed, to resume requesting input buffers.
     * @throws IllegalStateException if not in the Configured state
     *         or just after {@link #flush} for a codec that is configured
     *         in asynchronous mode.
     * @throws RKMediaCodec.CodecException upon codec error. Note that some codec errors
     * for start may be attributed to future method calls.
     */
    public final void start() {
        throw new RuntimeException("Not Support!");
//        native_start();
//        synchronized(mBufferLock) {
//            cacheBuffers(true /* input */);
//            cacheBuffers(false /* input */);
//        }
    }
//    private native final void native_start();

    /**
     * Finish the decode/encode session, note that the codec instance
     * remains active and ready to be {@link #start}ed again.
     * To ensure that it is available to other client call {@link #release}
     * and don't just rely on garbage collection to eventually do this for you.
     * @throws IllegalStateException if in the Released state.
     */
    public final void stop() {
        throw new RuntimeException("Not Support!");
//        native_stop();
//        freeAllTrackedBuffers();
//
//        synchronized (mListenerLock) {
//            if (mCallbackHandler != null) {
//                mCallbackHandler.removeMessages(EVENT_SET_CALLBACK);
//                mCallbackHandler.removeMessages(EVENT_CALLBACK);
//            }
//            if (mOnFrameRenderedHandler != null) {
//                mOnFrameRenderedHandler.removeMessages(EVENT_FRAME_RENDERED);
//            }
//        }
    }

//    private native final void native_stop();

    /**
     * Flush both input and output ports of the component.
     *
     * Upon return, all indices previously returned in calls to {@link #dequeueInputBuffer
     * dequeueInputBuffer} and {@link #dequeueOutputBuffer dequeueOutputBuffer} &mdash; or obtained
     * via {@link Callback#onInputBufferAvailable onInputBufferAvailable} or
     * {@link Callback#onOutputBufferAvailable onOutputBufferAvailable} callbacks &mdash; become
     * invalid, and all buffers are owned by the codec.
     *
     * If the codec is configured in asynchronous mode, call {@link #start}
     * after {@code flush} has returned to resume codec operations. The codec
     * will not request input buffers until this has happened.
     * <strong>Note, however, that there may still be outstanding {@code onOutputBufferAvailable}
     * callbacks that were not handled prior to calling {@code flush}.
     * The indices returned via these callbacks also become invalid upon calling {@code flush} and
     * should be discarded.</strong>
     *
     * If the codec is configured in synchronous mode, codec will resume
     * automatically if it is configured with an input surface.  Otherwise, it
     * will resume when {@link #dequeueInputBuffer dequeueInputBuffer} is called.
     *
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    public final void flush() {
        throw new RuntimeException("Not Support!");
//        synchronized(mBufferLock) {
//            invalidateByteBuffers(mCachedInputBuffers);
//            invalidateByteBuffers(mCachedOutputBuffers);
//            mDequeuedInputBuffers.clear();
//            mDequeuedOutputBuffers.clear();
//        }
//        native_flush();
    }

    private native final void native_flush();

    /**
     * Thrown when an internal codec error occurs.
     */
    public final static class CodecException extends IllegalStateException {
        CodecException(int errorCode, int actionCode, @Nullable String detailMessage) {
            super(detailMessage);
            mErrorCode = errorCode;
            mActionCode = actionCode;

            // TODO get this from codec
            final String sign = errorCode < 0 ? "neg_" : "";
            mDiagnosticInfo = "android.media.RKMediaCodec.error_" + sign + Math.abs(errorCode);
        }

        /**
         * Returns true if the codec exception is a transient issue,
         * perhaps due to resource constraints, and that the method
         * (or encoding/decoding) may be retried at a later time.
         */
        public boolean isTransient() {
            return mActionCode == ACTION_TRANSIENT;
        }

        /**
         * Returns true if the codec cannot proceed further,
         * but can be recovered by stopping, configuring,
         * and starting again.
         */
        public boolean isRecoverable() {
            return mActionCode == ACTION_RECOVERABLE;
        }

        /**
         * Retrieve the error code associated with a CodecException
         */
        public int getErrorCode() {
            return mErrorCode;
        }

        /**
         * Retrieve a developer-readable diagnostic information string
         * associated with the exception. Do not show this to end-users,
         * since this string will not be localized or generally
         * comprehensible to end-users.
         */
        public @NonNull String getDiagnosticInfo() {
            return mDiagnosticInfo;
        }

        /**
         * This indicates required resource was not able to be allocated.
         */
        public static final int ERROR_INSUFFICIENT_RESOURCE = 1100;

        /**
         * This indicates the resource manager reclaimed the media resource used by the codec.
         *
         * With this exception, the codec must be released, as it has moved to terminal state.
         */
        public static final int ERROR_RECLAIMED = 1101;

        /** @hide */
        @IntDef({
                ERROR_INSUFFICIENT_RESOURCE,
                ERROR_RECLAIMED,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ReasonCode {}

        /* Must be in sync with android_media_MediaCodec.cpp */
        private final static int ACTION_TRANSIENT = 1;
        private final static int ACTION_RECOVERABLE = 2;

        private final String mDiagnosticInfo;
        private final int mErrorCode;
        private final int mActionCode;
    }

    /**
     * Thrown when a crypto error occurs while queueing a secure input buffer.
     */
    public final static class CryptoException extends RuntimeException {
        public CryptoException(int errorCode, @Nullable String detailMessage) {
            super(detailMessage);
            mErrorCode = errorCode;
        }

        /**
         * This indicates that the requested key was not found when trying to
         * perform a decrypt operation.  The operation can be retried after adding
         * the correct decryption key.
         */
        public static final int ERROR_NO_KEY = 1;

        /**
         * This indicates that the key used for decryption is no longer
         * valid due to license term expiration.  The operation can be retried
         * after updating the expired keys.
         */
        public static final int ERROR_KEY_EXPIRED = 2;

        /**
         * This indicates that a required crypto resource was not able to be
         * allocated while attempting the requested operation.  The operation
         * can be retried if the app is able to release resources.
         */
        public static final int ERROR_RESOURCE_BUSY = 3;

        /**
         * This indicates that the output protection levels supported by the
         * device are not sufficient to meet the requirements set by the
         * content owner in the license policy.
         */
        public static final int ERROR_INSUFFICIENT_OUTPUT_PROTECTION = 4;

        /**
         * This indicates that decryption was attempted on a session that is
         * not opened, which could be due to a failure to open the session,
         * closing the session prematurely, or the session being reclaimed
         * by the resource manager.
         */
        public static final int ERROR_SESSION_NOT_OPENED = 5;

        /**
         * This indicates that an operation was attempted that could not be
         * supported by the crypto system of the device in its current
         * configuration.  It may occur when the license policy requires
         * device security features that aren't supported by the device,
         * or due to an internal error in the crypto system that prevents
         * the specified security policy from being met.
         */
        public static final int ERROR_UNSUPPORTED_OPERATION = 6;

        /** @hide */
        @IntDef({
                ERROR_NO_KEY,
                ERROR_KEY_EXPIRED,
                ERROR_RESOURCE_BUSY,
                ERROR_INSUFFICIENT_OUTPUT_PROTECTION,
                ERROR_SESSION_NOT_OPENED,
                ERROR_UNSUPPORTED_OPERATION
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface CryptoErrorCode {}

        /**
         * Retrieve the error code associated with a CryptoException
         */
        @CryptoErrorCode
        public int getErrorCode() {
            return mErrorCode;
        }

        private int mErrorCode;
    }

    /**
     * After filling a range of the input buffer at the specified index
     * submit it to the component. Once an input buffer is queued to
     * the codec, it MUST NOT be used until it is later retrieved by
     * {@link #getInputBuffer} in response to a {@link #dequeueInputBuffer}
     * return value or a {@link Callback#onInputBufferAvailable}
     * callback.
     *
     * Many decoders require the actual compressed data stream to be
     * preceded by "codec specific data", i.e. setup data used to initialize
     * the codec such as PPS/SPS in the case of AVC video or code tables
     * in the case of vorbis audio.
     * The class {@link android.media.MediaExtractor} provides codec
     * specific data as part of
     * the returned track format in entries named "csd-0", "csd-1" ...
     *
     * These buffers can be submitted directly after {@link #start} or
     * {@link #flush} by specifying the flag {@link
     * #BUFFER_FLAG_CODEC_CONFIG}.  However, if you configure the
     * codec with a {@link MediaFormat} containing these keys, they
     * will be automatically submitted by RKMediaCodec directly after
     * start.  Therefore, the use of {@link
     * #BUFFER_FLAG_CODEC_CONFIG} flag is discouraged and is
     * recommended only for advanced users.
     *
     * To indicate that this is the final piece of input data (or rather that
     * no more input data follows unless the decoder is subsequently flushed)
     * specify the flag {@link #BUFFER_FLAG_END_OF_STREAM}.
     * <p class=note>
     * <strong>Note:</strong> Prior to {@link android.os.Build.VERSION_CODES#M},
     * {@code presentationTimeUs} was not propagated to the frame timestamp of (rendered)
     * Surface output buffers, and the resulting frame timestamp was undefined.
     * Use {@link #releaseOutputBuffer(int, long)} to ensure a specific frame timestamp is set.
     * Similarly, since frame timestamps can be used by the destination surface for rendering
     * synchronization, <strong>care must be taken to normalize presentationTimeUs so as to not be
     * mistaken for a system time. (See {@linkplain #releaseOutputBuffer(int, long)
     * SurfaceView specifics}).</strong>
     *
     * @param index The index of a client-owned input buffer previously returned
     *              in a call to {@link #dequeueInputBuffer}.
     * @param offset The byte offset into the input buffer at which the data starts.
     * @param size The number of bytes of valid input data.
     * @param presentationTimeUs The presentation timestamp in microseconds for this
     *                           buffer. This is normally the media time at which this
     *                           buffer should be presented (rendered). When using an output
     *                           surface, this will be propagated as the {@link
     *                           SurfaceTexture#getTimestamp timestamp} for the frame (after
     *                           conversion to nanoseconds).
     * @param flags A bitmask of flags
     *              {@link #BUFFER_FLAG_CODEC_CONFIG} and {@link #BUFFER_FLAG_END_OF_STREAM}.
     *              While not prohibited, most codecs do not use the
     *              {@link #BUFFER_FLAG_KEY_FRAME} flag for input buffers.
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     * @throws CryptoException if a crypto object has been specified in
     *         {@link #configure}
     */
    public final void queueInputBuffer(
            int index,
            int offset, int size, long presentationTimeUs, int flags)
            throws CryptoException {
        throw new RuntimeException("Not Support!");
//        synchronized (mBufferLock) {
//            invalidateByteBuffer(mCachedInputBuffers, index);
//            mDequeuedInputBuffers.remove(index);
//        }
//        try {
//            native_queueInputBuffer(
//                    index, offset, size, presentationTimeUs, flags);
//        } catch (CryptoException | IllegalStateException e) {
//            revalidateByteBuffer(mCachedInputBuffers, index);
//            throw e;
//        }
    }

//    private native final void native_queueInputBuffer(
//            int index,
//            int offset, int size, long presentationTimeUs, int flags)
//            throws CryptoException;

    public static final int CRYPTO_MODE_UNENCRYPTED = 0;
    public static final int CRYPTO_MODE_AES_CTR     = 1;
    public static final int CRYPTO_MODE_AES_CBC     = 2;

    /**
     * Metadata describing the structure of a (at least partially) encrypted
     * input sample.
     * A buffer's data is considered to be partitioned into "subSamples",
     * each subSample starts with a (potentially empty) run of plain,
     * unencrypted bytes followed by a (also potentially empty) run of
     * encrypted bytes. If pattern encryption applies, each of the latter runs
     * is encrypted only partly, according to a repeating pattern of "encrypt"
     * and "skip" blocks. numBytesOfClearData can be null to indicate that all
     * data is encrypted. This information encapsulates per-sample metadata as
     * outlined in ISO/IEC FDIS 23001-7:2011 "Common encryption in ISO base
     * media file format files".
     */
    public final static class CryptoInfo {
        /**
         * The number of subSamples that make up the buffer's contents.
         */
        public int numSubSamples;
        /**
         * The number of leading unencrypted bytes in each subSample.
         */
        public int[] numBytesOfClearData;
        /**
         * The number of trailing encrypted bytes in each subSample.
         */
        public int[] numBytesOfEncryptedData;
        /**
         * A 16-byte key id
         */
        public byte[] key;
        /**
         * A 16-byte initialization vector
         */
        public byte[] iv;
        /**
         * The type of encryption that has been applied,
         * see {@link #CRYPTO_MODE_UNENCRYPTED}, {@link #CRYPTO_MODE_AES_CTR}
         * and {@link #CRYPTO_MODE_AES_CBC}
         */
        public int mode;

        /**
         * Metadata describing an encryption pattern for the protected bytes in
         * a subsample.  An encryption pattern consists of a repeating sequence
         * of crypto blocks comprised of a number of encrypted blocks followed
         * by a number of unencrypted, or skipped, blocks.
         */
        public final static class Pattern {
            /**
             * Number of blocks to be encrypted in the pattern. If zero, pattern
             * encryption is inoperative.
             */
            private int mEncryptBlocks;

            /**
             * Number of blocks to be skipped (left clear) in the pattern. If zero,
             * pattern encryption is inoperative.
             */
            private int mSkipBlocks;

            /**
             * Construct a sample encryption pattern given the number of blocks to
             * encrypt and skip in the pattern.
             */
            public Pattern(int blocksToEncrypt, int blocksToSkip) {
                set(blocksToEncrypt, blocksToSkip);
            }

            /**
             * Set the number of blocks to encrypt and skip in a sample encryption
             * pattern.
             */
            public void set(int blocksToEncrypt, int blocksToSkip) {
                mEncryptBlocks = blocksToEncrypt;
                mSkipBlocks = blocksToSkip;
            }

            /**
             * Return the number of blocks to skip in a sample encryption pattern.
             */
            public int getSkipBlocks() {
                return mSkipBlocks;
            }

            /**
             * Return the number of blocks to encrypt in a sample encryption pattern.
             */
            public int getEncryptBlocks() {
                return mEncryptBlocks;
            }
        };

        /**
         * The pattern applicable to the protected data in each subsample.
         */
        private Pattern pattern;

        /**
         * Set the subsample count, clear/encrypted sizes, key, IV and mode fields of
         * a {@link RKMediaCodec.CryptoInfo} instance.
         */
        public void set(
                int newNumSubSamples,
                @NonNull int[] newNumBytesOfClearData,
                @NonNull int[] newNumBytesOfEncryptedData,
                @NonNull byte[] newKey,
                @NonNull byte[] newIV,
                int newMode) {
            numSubSamples = newNumSubSamples;
            numBytesOfClearData = newNumBytesOfClearData;
            numBytesOfEncryptedData = newNumBytesOfEncryptedData;
            key = newKey;
            iv = newIV;
            mode = newMode;
            pattern = new Pattern(0, 0);
        }

        /**
         * Set the encryption pattern on a {@link RKMediaCodec.CryptoInfo} instance.
         * See {@link RKMediaCodec.CryptoInfo.Pattern}.
         */
        public void setPattern(Pattern newPattern) {
            pattern = newPattern;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(numSubSamples + " subsamples, key [");
            String hexdigits = "0123456789abcdef";
            for (int i = 0; i < key.length; i++) {
                builder.append(hexdigits.charAt((key[i] & 0xf0) >> 4));
                builder.append(hexdigits.charAt(key[i] & 0x0f));
            }
            builder.append("], iv [");
            for (int i = 0; i < key.length; i++) {
                builder.append(hexdigits.charAt((iv[i] & 0xf0) >> 4));
                builder.append(hexdigits.charAt(iv[i] & 0x0f));
            }
            builder.append("], clear ");
            builder.append(Arrays.toString(numBytesOfClearData));
            builder.append(", encrypted ");
            builder.append(Arrays.toString(numBytesOfEncryptedData));
            return builder.toString();
        }
    };

    /**
     * Similar to {@link #queueInputBuffer queueInputBuffer} but submits a buffer that is
     * potentially encrypted.
     * <strong>Check out further notes at {@link #queueInputBuffer queueInputBuffer}.</strong>
     *
     * @param index The index of a client-owned input buffer previously returned
     *              in a call to {@link #dequeueInputBuffer}.
     * @param offset The byte offset into the input buffer at which the data starts.
     * @param info Metadata required to facilitate decryption, the object can be
     *             reused immediately after this call returns.
     * @param presentationTimeUs The presentation timestamp in microseconds for this
     *                           buffer. This is normally the media time at which this
     *                           buffer should be presented (rendered).
     * @param flags A bitmask of flags
     *              {@link #BUFFER_FLAG_CODEC_CONFIG} and {@link #BUFFER_FLAG_END_OF_STREAM}.
     *              While not prohibited, most codecs do not use the
     *              {@link #BUFFER_FLAG_KEY_FRAME} flag for input buffers.
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     * @throws CryptoException if an error occurs while attempting to decrypt the buffer.
     *              An error code associated with the exception helps identify the
     *              reason for the failure.
     */
    public final void queueSecureInputBuffer(
            int index,
            int offset,
            @NonNull CryptoInfo info,
            long presentationTimeUs,
            int flags) throws CryptoException {
        throw new RuntimeException("Not Support!");
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedInputBuffers, index);
//            mDequeuedInputBuffers.remove(index);
//        }
//        try {
//            native_queueSecureInputBuffer(
//                    index, offset, info, presentationTimeUs, flags);
//        } catch (CryptoException | IllegalStateException e) {
//            revalidateByteBuffer(mCachedInputBuffers, index);
//            throw e;
//        }
    }

//    private native final void native_queueSecureInputBuffer(
//            int index,
//            int offset,
//            @NonNull CryptoInfo info,
//            long presentationTimeUs,
//            int flags) throws CryptoException;

    /**
     * Returns the index of an input buffer to be filled with valid data
     * or -1 if no such buffer is currently available.
     * This method will return immediately if timeoutUs == 0, wait indefinitely
     * for the availability of an input buffer if timeoutUs &lt; 0 or wait up
     * to "timeoutUs" microseconds if timeoutUs &gt; 0.
     * @param timeoutUs The timeout in microseconds, a negative timeout indicates "infinite".
     * @throws IllegalStateException if not in the Executing state,
     *         or codec is configured in asynchronous mode.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    public final int dequeueInputBuffer(long timeoutUs) {
        throw new RuntimeException("Not Support!");
//        int res = native_dequeueInputBuffer(timeoutUs);
//        if (res >= 0) {
//            synchronized(mBufferLock) {
//                validateInputByteBuffer(mCachedInputBuffers, res);
//            }
//        }
//        return res;
    }

//    private native final int native_dequeueInputBuffer(long timeoutUs);

    /**
     * If a non-negative timeout had been specified in the call
     * to {@link #dequeueOutputBuffer}, indicates that the call timed out.
     */
    public static final int INFO_TRY_AGAIN_LATER        = -1;

    /**
     * The output format has changed, subsequent data will follow the new
     * format. {@link #getOutputFormat()} returns the new format.  Note, that
     * you can also use the new {@link #getOutputFormat(int)} method to
     * get the format for a specific output buffer.  This frees you from
     * having to track output format changes.
     */
    public static final int INFO_OUTPUT_FORMAT_CHANGED  = -2;

    /**
     * The output buffers have changed, the client must refer to the new
     * set of output buffers returned by {@link #getOutputBuffers} from
     * this point on.
     *
     *Additionally, this event signals that the video scaling mode
     * may have been reset to the default.</p>
     *
     * @deprecated This return value can be ignored as {@link
     * #getOutputBuffers} has been deprecated.  Client should
     * request a current buffer using on of the get-buffer or
     * get-image methods each time one has been dequeued.
     */
    public static final int INFO_OUTPUT_BUFFERS_CHANGED = -3;

    /** @hide */
    @IntDef({
            INFO_TRY_AGAIN_LATER,
            INFO_OUTPUT_FORMAT_CHANGED,
            INFO_OUTPUT_BUFFERS_CHANGED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OutputBufferInfo {}

    /**
     * Dequeue an output buffer, block at most "timeoutUs" microseconds.
     * Returns the index of an output buffer that has been successfully
     * decoded or one of the INFO_* constants.
     * @param info Will be filled with buffer meta data.
     * @param timeoutUs The timeout in microseconds, a negative timeout indicates "infinite".
     * @throws IllegalStateException if not in the Executing state,
     *         or codec is configured in asynchronous mode.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @OutputBufferInfo
    public final int dequeueOutputBuffer(
            @NonNull BufferInfo info, long timeoutUs) {
        throw new RuntimeException("Not Support!");
//        int res = native_dequeueOutputBuffer(info, timeoutUs);
//        synchronized(mBufferLock) {
//            if (res == INFO_OUTPUT_BUFFERS_CHANGED) {
//                cacheBuffers(false /* input */);
//            } else if (res >= 0) {
//                validateOutputByteBuffer(mCachedOutputBuffers, res, info);
//                if (mHasSurface) {
//                    mDequeuedOutputInfos.put(res, info.dup());
//                }
//            }
//        }
//        return res;
    }

//    private native final int native_dequeueOutputBuffer(@NonNull BufferInfo info, long timeoutUs);

    /**
     * If you are done with a buffer, use this call to return the buffer to the codec
     * or to render it on the output surface. If you configured the codec with an
     * output surface, setting {@code render} to {@code true} will first send the buffer
     * to that output surface. The surface will release the buffer back to the codec once
     * it is no longer used/displayed.
     *
     * Once an output buffer is released to the codec, it MUST NOT
     * be used until it is later retrieved by {@link #getOutputBuffer} in response
     * to a {@link #dequeueOutputBuffer} return value or a
     * {@link Callback#onOutputBufferAvailable} callback.
     *
     * @param index The index of a client-owned output buffer previously returned
     *              from a call to {@link #dequeueOutputBuffer}.
     * @param render If a valid surface was specified when configuring the codec,
     *               passing true renders this output buffer to the surface.
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    public final void releaseOutputBuffer(int index, boolean render) {
        throw new RuntimeException("Not Support!");
//        BufferInfo info = null;
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedOutputBuffers, index);
//            mDequeuedOutputBuffers.remove(index);
//            if (mHasSurface) {
//                info = mDequeuedOutputInfos.remove(index);
//            }
//        }
//        releaseOutputBuffer(index, render, false /* updatePTS */, 0 /* dummy */);
    }

    /**
     * If you are done with a buffer, use this call to update its surface timestamp
     * and return it to the codec to render it on the output surface. If you
     * have not specified an output surface when configuring this video codec,
     * this call will simply return the buffer to the codec.<p>
     *
     * The timestamp may have special meaning depending on the destination surface.
     *
     *
     * <tr>SurfaceView specifics
     * <tr><td>
     * If you render your buffer on a {@link android.view.SurfaceView},
     * you can use the timestamp to render the buffer at a specific time (at the
     * VSYNC at or after the buffer timestamp).  For this to work, the timestamp
     * needs to be <i>reasonably close</i> to the current {@link System#nanoTime}.
     * Currently, this is set as within one (1) second. A few notes:
     *
     * <ul>
     * <li>the buffer will not be returned to the codec until the timestamp
     * has passed and the buffer is no longer used by the {@link Surface}.
     * <li>buffers are processed sequentially, so you may block subsequent buffers to
     * be displayed on the {@link Surface}.  This is important if you
     * want to react to user action, e.g. stop the video or seek.
     * <li>if multiple buffers are sent to the {@link Surface} to be
     * rendered at the same VSYNC, the last one will be shown, and the other ones
     * will be dropped.
     * <li>if the timestamp is <em>not</em> "reasonably close" to the current system
     * time, the {@link Surface} will ignore the timestamp, and
     * display the buffer at the earliest feasible time.  In this mode it will not
     * drop frames.
     * <li>for best performance and quality, call this method when you are about
     * two VSYNCs' time before the desired render time.  For 60Hz displays, this is
     * about 33 msec.
     * </ul>
     *
     * </table>
     *
     * Once an output buffer is released to the codec, it MUST NOT
     * be used until it is later retrieved by {@link #getOutputBuffer} in response
     * to a {@link #dequeueOutputBuffer} return value or a
     * {@link Callback#onOutputBufferAvailable} callback.
     *
     * @param index The index of a client-owned output buffer previously returned
     *              from a call to {@link #dequeueOutputBuffer}.
     * @param renderTimestampNs The timestamp to associate with this buffer when
     *              it is sent to the Surface.
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    public final void releaseOutputBuffer(int index, long renderTimestampNs) {
        throw new RuntimeException("Not Support!");
//        BufferInfo info = null;
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedOutputBuffers, index);
//            mDequeuedOutputBuffers.remove(index);
//            if (mHasSurface) {
//                info = mDequeuedOutputInfos.remove(index);
//            }
//        }
//        releaseOutputBuffer(index, true /* render */, true /* updatePTS */, renderTimestampNs);
    }

//    private native final void releaseOutputBuffer(
//            int index, boolean render, boolean updatePTS, long timeNs);

    /**
     * Signals end-of-stream on input.  Equivalent to submitting an empty buffer with
     * {@link #BUFFER_FLAG_END_OF_STREAM} set.  This may only be used with
     * encoders receiving input from a Surface created by {@link #createInputSurface}.
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
//    public native final void signalEndOfInputStream();

    /**
     * Call this after dequeueOutputBuffer signals a format change by returning
     * {@link #INFO_OUTPUT_FORMAT_CHANGED}.
     * You can also call this after {@link #configure} returns
     * successfully to get the output format initially configured
     * for the codec.  Do this to determine what optional
     * configuration parameters were supported by the codec.
     *
     * @throws IllegalStateException if not in the Executing or
     *                               Configured state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @NonNull
    public final MediaFormat getOutputFormat() {
        throw new RuntimeException("Not Support!");
//        return new MediaFormat(getFormatNative(false /* input */));
    }

    /**
     * Call this after {@link #configure} returns successfully to
     * get the input format accepted by the codec. Do this to
     * determine what optional configuration parameters were
     * supported by the codec.
     *
     * @throws IllegalStateException if not in the Executing or
     *                               Configured state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @NonNull
    public final MediaFormat getInputFormat() {
        throw new RuntimeException("Not Support!");
//        return new MediaFormat(getFormatNative(true /* input */));
    }

    /**
     * Returns the output format for a specific output buffer.
     *
     * @param index The index of a client-owned input buffer previously
     *              returned from a call to {@link #dequeueInputBuffer}.
     *
     * @return the format for the output buffer, or null if the index
     * is not a dequeued output buffer.
     */
    @NonNull
    public final MediaFormat getOutputFormat(int index) {
        throw new RuntimeException("Not Support!");
//        return new MediaFormat(getOutputFormatNative(index));
    }

//    @NonNull
//    private native final Map<String, Object> getFormatNative(boolean input);
//
//    @NonNull
//    private native final Map<String, Object> getOutputFormatNative(int index);

    // used to track dequeued buffers
    private static class BufferMap {
        // various returned representations of the codec buffer
        private static class CodecBuffer {
            private Image mImage;
            private ByteBuffer mByteBuffer;

            public void free() {
                throw new RuntimeException("Not Support!");
//                if (mByteBuffer != null) {
//                    // all of our ByteBuffers are direct
//                    java.nio.NioUtils.freeDirectBuffer(mByteBuffer);
//                    mByteBuffer = null;
//                }
//                if (mImage != null) {
//                    mImage.close();
//                    mImage = null;
//                }
            }

            public void setImage(@Nullable Image image) {
                free();
                mImage = image;
            }

            public void setByteBuffer(@Nullable ByteBuffer buffer) {
                free();
                mByteBuffer = buffer;
            }
        }

        private final Map<Integer, CodecBuffer> mMap = new HashMap<Integer, CodecBuffer>();

        public void remove(int index) {
            CodecBuffer buffer = mMap.get(index);
            if (buffer != null) {
                buffer.free();
                mMap.remove(index);
            }
        }

        public void put(int index, @Nullable ByteBuffer newBuffer) {
            CodecBuffer buffer = mMap.get(index);
            if (buffer == null) { // likely
                buffer = new CodecBuffer();
                mMap.put(index, buffer);
            }
            buffer.setByteBuffer(newBuffer);
        }

        public void put(int index, @Nullable Image newImage) {
            CodecBuffer buffer = mMap.get(index);
            if (buffer == null) { // likely
                buffer = new CodecBuffer();
                mMap.put(index, buffer);
            }
            buffer.setImage(newImage);
        }

        public void clear() {
            for (CodecBuffer buffer: mMap.values()) {
                buffer.free();
            }
            mMap.clear();
        }
    }

    private ByteBuffer[] mCachedInputBuffers;
    private ByteBuffer[] mCachedOutputBuffers;
    private final BufferMap mDequeuedInputBuffers = new BufferMap();
    private final BufferMap mDequeuedOutputBuffers = new BufferMap();
    private final Map<Integer, BufferInfo> mDequeuedOutputInfos = new HashMap<Integer, BufferInfo>();
    final private Object mBufferLock;

    private final void invalidateByteBuffer(@Nullable ByteBuffer[] buffers, int index) {
        throw new RuntimeException("Not Support!");
//        if (buffers != null && index >= 0 && index < buffers.length) {
//            ByteBuffer buffer = buffers[index];
//            if (buffer != null) {
//                buffer.setAccessible(false);
//            }
//        }
    }

    private final void validateInputByteBuffer(@Nullable ByteBuffer[] buffers, int index) {
        throw new RuntimeException("Not Support!");
//        if (buffers != null && index >= 0 && index < buffers.length) {
//            ByteBuffer buffer = buffers[index];
//            if (buffer != null) {
//                buffer.setAccessible(true);
//                buffer.clear();
//            }
//        }
    }

    private final void revalidateByteBuffer(@Nullable ByteBuffer[] buffers, int index) {
        throw new RuntimeException("Not Support!");
//        synchronized(mBufferLock) {
//            if (buffers != null && index >= 0 && index < buffers.length) {
//                ByteBuffer buffer = buffers[index];
//                if (buffer != null) {
//                    buffer.setAccessible(true);
//                }
//            }
//        }
    }

    private final void validateOutputByteBuffer(@Nullable ByteBuffer[] buffers, int index, @NonNull BufferInfo info) {
        throw new RuntimeException("Not Support!");
//        if (buffers != null && index >= 0 && index < buffers.length) {
//            ByteBuffer buffer = buffers[index];
//            if (buffer != null) {
//                buffer.setAccessible(true);
//                buffer.limit(info.offset + info.size).position(info.offset);
//            }
//        }
    }

    private final void invalidateByteBuffers(@Nullable ByteBuffer[] buffers) {
        throw new RuntimeException("Not Support!");
//        if (buffers != null) {
//            for (ByteBuffer buffer: buffers) {
//                if (buffer != null) {
//                    buffer.setAccessible(false);
//                }
//            }
//        }
    }

    private final void freeByteBuffer(@Nullable ByteBuffer buffer) {
        throw new RuntimeException("Not Support!");
//        if (buffer != null /* && buffer.isDirect() */) {
//            // all of our ByteBuffers are direct
//            java.nio.NioUtils.freeDirectBuffer(buffer);
//        }
    }

    private final void freeByteBuffers(@Nullable ByteBuffer[] buffers) {
        throw new RuntimeException("Not Support!");
//        if (buffers != null) {
//            for (ByteBuffer buffer: buffers) {
//                freeByteBuffer(buffer);
//            }
//        }
    }

    private final void freeAllTrackedBuffers() {
//        throw new RuntimeException("Not Support!");
        synchronized(mBufferLock) {
            freeByteBuffers(mCachedInputBuffers);
            freeByteBuffers(mCachedOutputBuffers);
            mCachedInputBuffers = null;
            mCachedOutputBuffers = null;
            mDequeuedInputBuffers.clear();
            mDequeuedOutputBuffers.clear();
        }
    }

    private final void cacheBuffers(boolean input) {
        throw new RuntimeException("Not Support!");
//        ByteBuffer[] buffers = null;
//        try {
//            buffers = getBuffers(input);
//            invalidateByteBuffers(buffers);
//        } catch (IllegalStateException e) {
//            // we don't get buffers in async mode
//        }
//        if (input) {
//            mCachedInputBuffers = buffers;
//        } else {
//            mCachedOutputBuffers = buffers;
//        }
    }

    /**
     * Retrieve the set of input buffers.  Call this after start()
     * returns. After calling this method, any ByteBuffers
     * previously returned by an earlier call to this method MUST no
     * longer be used.
     *
     * @deprecated Use the new {@link #getInputBuffer} method instead
     * each time an input buffer is dequeued.
     *
     * <b>Note:</b> As of API 21, dequeued input buffers are
     * automatically {@link java.nio.Buffer#clear cleared}.
     *
     * <em>Do not use this method if using an input surface.</em>
     *
     * @throws IllegalStateException if not in the Executing state,
     *         or codec is configured in asynchronous mode.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @NonNull
    public ByteBuffer[] getInputBuffers() {
        if (mCachedInputBuffers == null) {
            throw new IllegalStateException();
        }
        // FIXME: check codec status
        return mCachedInputBuffers;
    }

    /**
     * Retrieve the set of output buffers.  Call this after start()
     * returns and whenever dequeueOutputBuffer signals an output
     * buffer change by returning {@link
     * #INFO_OUTPUT_BUFFERS_CHANGED}. After calling this method, any
     * ByteBuffers previously returned by an earlier call to this
     * method MUST no longer be used.
     *
     * @deprecated Use the new {@link #getOutputBuffer} method instead
     * each time an output buffer is dequeued.  This method is not
     * supported if codec is configured in asynchronous mode.
     *
     * <b>Note:</b> As of API 21, the position and limit of output
     * buffers that are dequeued will be set to the valid data
     * range.
     *
     * <em>Do not use this method if using an output surface.</em>
     *
     * @throws IllegalStateException if not in the Executing state,
     *         or codec is configured in asynchronous mode.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @NonNull
    public ByteBuffer[] getOutputBuffers() {
        if (mCachedOutputBuffers == null) {
            throw new IllegalStateException();
        }
        // FIXME: check codec status
        return mCachedOutputBuffers;
    }

    /**
     * Returns a {@link java.nio.Buffer#clear cleared}, writable ByteBuffer
     * object for a dequeued input buffer index to contain the input data.
     *
     * After calling this method any ByteBuffer or Image object
     * previously returned for the same input index MUST no longer
     * be used.
     *
     * @param index The index of a client-owned input buffer previously
     *              returned from a call to {@link #dequeueInputBuffer},
     *              or received via an onInputBufferAvailable callback.
     *
     * @return the input buffer, or null if the index is not a dequeued
     * input buffer, or if the codec is configured for surface input.
     *
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @Nullable
    public ByteBuffer getInputBuffer(int index) {
        throw new RuntimeException("Not Support!");
//        ByteBuffer newBuffer = getBuffer(true /* input */, index);
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedInputBuffers, index);
//            mDequeuedInputBuffers.put(index, newBuffer);
//        }
//        return newBuffer;
    }

    /**
     * Returns a writable Image object for a dequeued input buffer
     * index to contain the raw input video frame.
     *
     * After calling this method any ByteBuffer or Image object
     * previously returned for the same input index MUST no longer
     * be used.
     *
     * @param index The index of a client-owned input buffer previously
     *              returned from a call to {@link #dequeueInputBuffer},
     *              or received via an onInputBufferAvailable callback.
     *
     * @return the input image, or null if the index is not a
     * dequeued input buffer, or not a ByteBuffer that contains a
     * raw image.
     *
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @Nullable
    public Image getInputImage(int index) {
        throw new RuntimeException("Not Support!");
//        Image newImage = getImage(true /* input */, index);
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedInputBuffers, index);
//            mDequeuedInputBuffers.put(index, newImage);
//        }
//        return newImage;
    }

    /**
     * Returns a read-only ByteBuffer for a dequeued output buffer
     * index. The position and limit of the returned buffer are set
     * to the valid output data.
     *
     * After calling this method, any ByteBuffer or Image object
     * previously returned for the same output index MUST no longer
     * be used.
     *
     * @param index The index of a client-owned output buffer previously
     *              returned from a call to {@link #dequeueOutputBuffer},
     *              or received via an onOutputBufferAvailable callback.
     *
     * @return the output buffer, or null if the index is not a dequeued
     * output buffer, or the codec is configured with an output surface.
     *
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @Nullable
    public ByteBuffer getOutputBuffer(int index) {
        throw new RuntimeException("Not Support!");
//        ByteBuffer newBuffer = getBuffer(false /* input */, index);
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedOutputBuffers, index);
//            mDequeuedOutputBuffers.put(index, newBuffer);
//        }
//        return newBuffer;
    }

    /**
     * Returns a read-only Image object for a dequeued output buffer
     * index that contains the raw video frame.
     *
     * After calling this method, any ByteBuffer or Image object previously
     * returned for the same output index MUST no longer be used.
     *
     * @param index The index of a client-owned output buffer previously
     *              returned from a call to {@link #dequeueOutputBuffer},
     *              or received via an onOutputBufferAvailable callback.
     *
     * @return the output image, or null if the index is not a
     * dequeued output buffer, not a raw video frame, or if the codec
     * was configured with an output surface.
     *
     * @throws IllegalStateException if not in the Executing state.
     * @throws RKMediaCodec.CodecException upon codec error.
     */
    @Nullable
    public Image getOutputImage(int index) {
        throw new RuntimeException("Not Support!");
//        Image newImage = getImage(false /* input */, index);
//        synchronized(mBufferLock) {
//            invalidateByteBuffer(mCachedOutputBuffers, index);
//            mDequeuedOutputBuffers.put(index, newImage);
//        }
//        return newImage;
    }

    /**
     * The content is scaled to the surface dimensions
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT               = 1;

    /**
     * The content is scaled, maintaining its aspect ratio, the whole
     * surface area is used, content may be cropped.
     * <p class=note>
     * This mode is only suitable for content with 1:1 pixel aspect ratio as you cannot
     * configure the pixel aspect ratio for a {@link Surface}.
     * <p class=note>
     * As of {@link android.os.Build.VERSION_CODES#N} release, this mode may not work if
     * the video is {@linkplain MediaFormat#KEY_ROTATION rotated} by 90 or 270 degrees.
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 2;

    /** @hide */
    @IntDef({
            VIDEO_SCALING_MODE_SCALE_TO_FIT,
            VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VideoScalingMode {}

    /**
     * If a surface has been specified in a previous call to {@link #configure}
     * specifies the scaling mode to use. The default is "scale to fit".
     * <p class=note>
     * The scaling mode may be reset to the <strong>default</strong> each time an
     * {@link #INFO_OUTPUT_BUFFERS_CHANGED} event is received from the codec; therefore, the client
     * must call this method after every buffer change event (and before the first output buffer is
     * released for rendering) to ensure consistent scaling mode.
     * <p class=note>
     * Since the {@link #INFO_OUTPUT_BUFFERS_CHANGED} event is deprecated, this can also be done
     * after each {@link #INFO_OUTPUT_FORMAT_CHANGED} event.
     *
     * @throws IllegalArgumentException if mode is not recognized.
     * @throws IllegalStateException if in the Released state.
     */
//    public native final void setVideoScalingMode(@VideoScalingMode int mode);

    /**
     * Get the component name. If the codec was created by createDecoderByType
     * or createEncoderByType, what component is chosen is not known beforehand.
     * @throws IllegalStateException if in the Released state.
     */
    @NonNull
//    public native final String getName();

    /**
     * Change a video encoder's target bitrate on the fly. The value is an
     * Integer object containing the new bitrate in bps.
     */
    public static final String PARAMETER_KEY_VIDEO_BITRATE = "video-bitrate";

    /**
     * Temporarily suspend/resume encoding of input data. While suspended
     * input data is effectively discarded instead of being fed into the
     * encoder. This parameter really only makes sense to use with an encoder
     * in "surface-input" mode, as the client code has no control over the
     * input-side of the encoder in that case.
     * The value is an Integer object containing the value 1 to suspend
     * or the value 0 to resume.
     */
    public static final String PARAMETER_KEY_SUSPEND = "drop-input-frames";

    /**
     * Request that the encoder produce a sync frame "soon".
     * Provide an Integer with the value 0.
     */
    public static final String PARAMETER_KEY_REQUEST_SYNC_FRAME = "request-sync";

    /**
     * Communicate additional parameter changes to the component instance.
     * <b>Note:</b> Some of these parameter changes may silently fail to apply.
     *
     * @param params The bundle of parameters to set.
     * @throws IllegalStateException if in the Released state.
     */
    public final void setParameters(@Nullable Bundle params) {
        throw new RuntimeException("Not Support!");
//        if (params == null) {
//            return;
//        }
//
//        String[] keys = new String[params.size()];
//        Object[] values = new Object[params.size()];
//
//        int i = 0;
//        for (final String key: params.keySet()) {
//            keys[i] = key;
//            values[i] = params.get(key);
//            ++i;
//        }
//
//        setParameters(keys, values);
    }

    /**
     * Sets an asynchronous callback for actionable RKMediaCodec events.
     *
     * If the client intends to use the component in asynchronous mode,
     * a valid callback should be provided before {@link #configure} is called.
     *
     * When asynchronous callback is enabled, the client should not call
     * {@link #getInputBuffers}, {@link #getOutputBuffers},
     * {@link #dequeueInputBuffer(long)} or {@link #dequeueOutputBuffer(BufferInfo, long)}.
     *
     * Also, {@link #flush} behaves differently in asynchronous mode.  After calling
     * {@code flush}, you must call {@link #start} to "resume" receiving input buffers,
     * even if an input surface was created.
     *
     * @param cb The callback that will run.  Use {@code null} to clear a previously
     *           set callback (before {@link #configure configure} is called and run
     *           in synchronous mode).
     * @param handler Callbacks will happen on the handler's thread. If {@code null},
     *           callbacks are done on the default thread (the caller's thread or the
     *           main thread.)
     */
    public void setCallback(@Nullable /* RKMediaCodec. */ Callback cb, @Nullable Handler handler) {
        throw new RuntimeException("Not Support!");
//        if (cb != null) {
//            synchronized (mListenerLock) {
//                EventHandler newHandler = getEventHandlerOn(handler, mCallbackHandler);
//                // NOTE: there are no callbacks on the handler at this time, but check anyways
//                // even if we were to extend this to be callable dynamically, it must
//                // be called when codec is flushed, so no messages are pending.
//                if (newHandler != mCallbackHandler) {
//                    mCallbackHandler.removeMessages(EVENT_SET_CALLBACK);
//                    mCallbackHandler.removeMessages(EVENT_CALLBACK);
//                    mCallbackHandler = newHandler;
//                }
//            }
//        } else if (mCallbackHandler != null) {
//            mCallbackHandler.removeMessages(EVENT_SET_CALLBACK);
//            mCallbackHandler.removeMessages(EVENT_CALLBACK);
//        }
//
//        if (mCallbackHandler != null) {
//            // set java callback on main handler
//            Message msg = mCallbackHandler.obtainMessage(EVENT_SET_CALLBACK, 0, 0, cb);
//            mCallbackHandler.sendMessage(msg);
//
//            // set native handler here, don't post to handler because
//            // it may cause the callback to be delayed and set in a wrong state.
//            // Note that native codec may start sending events to the callback
//            // handler after this returns.
//            native_setCallback(cb);
//        }
    }

    /**
     * Sets an asynchronous callback for actionable RKMediaCodec events on the default
     * looper.
     *
     * Same as {@link #setCallback(Callback, Handler)} with handler set to null.
     * @param cb The callback that will run.  Use {@code null} to clear a previously
     *           set callback (before {@link #configure configure} is called and run
     *           in synchronous mode).
     * @see #setCallback(Callback, Handler)
     */
    public void setCallback(@Nullable /* RKMediaCodec. */ Callback cb) {
        setCallback(cb, null /* handler */);
    }

    /**
     * Listener to be called when an output frame has rendered on the output surface
     *
     * @see RKMediaCodec#setOnFrameRenderedListener
     */
    public interface OnFrameRenderedListener {

        /**
         * Called when an output frame has rendered on the output surface.
         *
         * <strong>Note:</strong> This callback is for informational purposes only: to get precise
         * render timing samples, and can be significantly delayed and batched. Some frames may have
         * been rendered even if there was no callback generated.
         *
         * @param codec the RKMediaCodec instance
         * @param presentationTimeUs the presentation time (media time) of the frame rendered.
         *          This is usually the same as specified in {@link #queueInputBuffer}; however,
         *          some codecs may alter the media time by applying some time-based transformation,
         *          such as frame rate conversion. In that case, presentation time corresponds
         *          to the actual output frame rendered.
         * @param nanoTime The system time when the frame was rendered.
         *
         * @see System#nanoTime
         */
        public void onFrameRendered(
                @NonNull RKMediaCodec codec, long presentationTimeUs, long nanoTime);
    }

    /**
     * Registers a callback to be invoked when an output frame is rendered on the output surface.
     *
     * This method can be called in any codec state, but will only have an effect in the
     * Executing state for codecs that render buffers to the output surface.
     *
     * <strong>Note:</strong> This callback is for informational purposes only: to get precise
     * render timing samples, and can be significantly delayed and batched. Some frames may have
     * been rendered even if there was no callback generated.
     *
     * @param listener the callback that will be run
     * @param handler the callback will be run on the handler's thread. If {@code null},
     *           the callback will be run on the default thread, which is the looper
     *           from which the codec was created, or a new thread if there was none.
     */
    public void setOnFrameRenderedListener(
            @Nullable OnFrameRenderedListener listener, @Nullable Handler handler) {
        throw new RuntimeException("Not Support!");
//        synchronized (mListenerLock) {
//            mOnFrameRenderedListener = listener;
//            if (listener != null) {
//                EventHandler newHandler = getEventHandlerOn(handler, mOnFrameRenderedHandler);
//                if (newHandler != mOnFrameRenderedHandler) {
//                    mOnFrameRenderedHandler.removeMessages(EVENT_FRAME_RENDERED);
//                }
//                mOnFrameRenderedHandler = newHandler;
//            } else if (mOnFrameRenderedHandler != null) {
//                mOnFrameRenderedHandler.removeMessages(EVENT_FRAME_RENDERED);
//            }
//            native_enableOnFrameRenderedListener(listener != null);
//        }
    }

//    private native void native_enableOnFrameRenderedListener(boolean enable);

    private EventHandler getEventHandlerOn(
            @Nullable Handler handler, @NonNull EventHandler lastHandler) {
        throw new RuntimeException("Not Support!");
//        if (handler == null) {
//            return mEventHandler;
//        } else {
//            Looper looper = handler.getLooper();
//            if (lastHandler.getLooper() == looper) {
//                return lastHandler;
//            } else {
//                return new EventHandler(this, looper);
//            }
//        }
    }

    /**
     * RKMediaCodec callback interface. Used to notify the user asynchronously
     * of various RKMediaCodec events.
     */
    public static abstract class Callback {
        /**
         * Called when an input buffer becomes available.
         *
         * @param codec The RKMediaCodec object.
         * @param index The index of the available input buffer.
         */
        public abstract void onInputBufferAvailable(@NonNull RKMediaCodec codec, int index);

        /**
         * Called when an output buffer becomes available.
         *
         * @param codec The RKMediaCodec object.
         * @param index The index of the available output buffer.
         * @param info Info regarding the available output buffer {@link RKMediaCodec.BufferInfo}.
         */
        public abstract void onOutputBufferAvailable(
                @NonNull RKMediaCodec codec, int index, @NonNull BufferInfo info);

        /**
         * Called when the RKMediaCodec encountered an error
         *
         * @param codec The RKMediaCodec object.
         * @param e The {@link RKMediaCodec.CodecException} object describing the error.
         */
        public abstract void onError(@NonNull RKMediaCodec codec, @NonNull CodecException e);

        /**
         * Called when the output format has changed
         *
         * @param codec The RKMediaCodec object.
         * @param format The new output format.
         */
        public abstract void onOutputFormatChanged(
                @NonNull RKMediaCodec codec, @NonNull MediaFormat format);
    }

    private void postEventFromNative(
            int what, int arg1, int arg2, @Nullable Object obj) {
        throw new RuntimeException("Not Support!");
//        synchronized (mListenerLock) {
//            EventHandler handler = mEventHandler;
//            if (what == EVENT_CALLBACK) {
//                handler = mCallbackHandler;
//            } else if (what == EVENT_FRAME_RENDERED) {
//                handler = mOnFrameRenderedHandler;
//            }
//            if (handler != null) {
//                Message msg = handler.obtainMessage(what, arg1, arg2, obj);
//                handler.sendMessage(msg);
//            }
//        }
    }

//    private native final void setParameters(@NonNull String[] keys, @NonNull Object[] values);

    /**
     * Get the codec info. If the codec was created by createDecoderByType
     * or createEncoderByType, what component is chosen is not known beforehand,
     * and thus the caller does not have the RKMediaCodecInfo.
     * @throws IllegalStateException if in the Released state.
     */
    @NonNull
    public MediaCodecInfo getCodecInfo() {
        throw new RuntimeException("Not Support!");
//        return MediaCodecList.getInfoFor(getName());
    }

//    @NonNull
//    private native final ByteBuffer[] getBuffers(boolean input);
//
//    @Nullable
//    private native final ByteBuffer getBuffer(boolean input, int index);
//
//    @Nullable
//    private native final Image getImage(boolean input, int index);
//
//    private static native final void native_init();
//
//    private native final void native_setup(
//            @NonNull String name, boolean nameIsType, boolean encoder);
//
//    private native final void native_finalize();

    static {
        System.loadLibrary("media_jni");
//        native_init();
    }

    private long mNativeContext;

//    /** @hide */
//    public static class MediaImage extends Image {
//        private final boolean mIsReadOnly;
//        private final int mWidth;
//        private final int mHeight;
//        private final int mFormat;
//        private long mTimestamp;
//        private final Plane[] mPlanes;
//        private final ByteBuffer mBuffer;
//        private final ByteBuffer mInfo;
//        private final int mXOffset;
//        private final int mYOffset;
//
//        private final static int TYPE_YUV = 1;
//
//        @Override
//        public int getFormat() {
//            throw new RuntimeException("Not Support!");
////            throwISEIfImageIsInvalid();
////            return mFormat;
//        }
//
//        @Override
//        public int getHeight() {
//            throw new RuntimeException("Not Support!");
////            throwISEIfImageIsInvalid();
////            return mHeight;
//        }
//
//        @Override
//        public int getWidth() {
//            throw new RuntimeException("Not Support!");
////            throwISEIfImageIsInvalid();
////            return mWidth;
//        }
//
//        @Override
//        public long getTimestamp() {
//            throw new RuntimeException("Not Support!");
////            throwISEIfImageIsInvalid();
////            return mTimestamp;
//        }
//
//        @Override
//        @NonNull
//        public Plane[] getPlanes() {
//            throw new RuntimeException("Not Support!");
////            throwISEIfImageIsInvalid();
////            return Arrays.copyOf(mPlanes, mPlanes.length);
//        }
//
//        @Override
//        public void close() {
//            throw new RuntimeException("Not Support!");
////            if (mIsImageValid) {
////                java.nio.NioUtils.freeDirectBuffer(mBuffer);
////                mIsImageValid = false;
////            }
//        }
//
//        /**
//         * Set the crop rectangle associated with this frame.
//         *
//         * The crop rectangle specifies the region of valid pixels in the image,
//         * using coordinates in the largest-resolution plane.
//         */
//        @Override
//        public void setCropRect(@Nullable Rect cropRect) {
//            throw new RuntimeException("Not Support!");
////            if (mIsReadOnly) {
////                throw new ReadOnlyBufferException();
////            }
////            super.setCropRect(cropRect);
//        }
//
//
//        public MediaImage(
//                @NonNull ByteBuffer buffer, @NonNull ByteBuffer info, boolean readOnly,
//                long timestamp, int xOffset, int yOffset, @Nullable Rect cropRect) {
//            mFormat = ImageFormat.YUV_420_888;
//            mTimestamp = timestamp;
//            mIsImageValid = true;
//            mIsReadOnly = buffer.isReadOnly();
//            mBuffer = buffer.duplicate();
//
//            // save offsets and info
//            mXOffset = xOffset;
//            mYOffset = yOffset;
//            mInfo = info;
//
//            // read media-info.  See MediaImage2
//            if (info.remaining() == 104) {
//                int type = info.getInt();
//                if (type != TYPE_YUV) {
//                    throw new UnsupportedOperationException("unsupported type: " + type);
//                }
//                int numPlanes = info.getInt();
//                if (numPlanes != 3) {
//                    throw new RuntimeException("unexpected number of planes: " + numPlanes);
//                }
//                mWidth = info.getInt();
//                mHeight = info.getInt();
//                if (mWidth < 1 || mHeight < 1) {
//                    throw new UnsupportedOperationException(
//                            "unsupported size: " + mWidth + "x" + mHeight);
//                }
//                int bitDepth = info.getInt();
//                if (bitDepth != 8) {
//                    throw new UnsupportedOperationException("unsupported bit depth: " + bitDepth);
//                }
//                int bitDepthAllocated = info.getInt();
//                if (bitDepthAllocated != 8) {
//                    throw new UnsupportedOperationException(
//                            "unsupported allocated bit depth: " + bitDepthAllocated);
//                }
//                mPlanes = new MediaPlane[numPlanes];
//                for (int ix = 0; ix < numPlanes; ix++) {
//                    int planeOffset = info.getInt();
//                    int colInc = info.getInt();
//                    int rowInc = info.getInt();
//                    int horiz = info.getInt();
//                    int vert = info.getInt();
//                    if (horiz != vert || horiz != (ix == 0 ? 1 : 2)) {
//                        throw new UnsupportedOperationException("unexpected subsampling: "
//                                + horiz + "x" + vert + " on plane " + ix);
//                    }
//                    if (colInc < 1 || rowInc < 1) {
//                        throw new UnsupportedOperationException("unexpected strides: "
//                                + colInc + " pixel, " + rowInc + " row on plane " + ix);
//                    }
//
//                    buffer.clear();
//                    buffer.position(mBuffer.position() + planeOffset
//                            + (xOffset / horiz) * colInc + (yOffset / vert) * rowInc);
//                    buffer.limit(buffer.position() + Utils.divUp(bitDepth, 8)
//                            + (mHeight / vert - 1) * rowInc + (mWidth / horiz - 1) * colInc);
//                    mPlanes[ix] = new MediaPlane(buffer.slice(), rowInc, colInc);
//                }
//            } else {
//                throw new UnsupportedOperationException(
//                        "unsupported info length: " + info.remaining());
//            }
//
//            if (cropRect == null) {
//                cropRect = new Rect(0, 0, mWidth, mHeight);
//            }
//            cropRect.offset(-xOffset, -yOffset);
//            super.setCropRect(cropRect);
//        }
//
//        private class MediaPlane extends Plane {
//            public MediaPlane(@NonNull ByteBuffer buffer, int rowInc, int colInc) {
//                mData = buffer;
//                mRowInc = rowInc;
//                mColInc = colInc;
//            }
//
//            @Override
//            public int getRowStride() {
//                throw new RuntimeException("Not Support!");
////                throwISEIfImageIsInvalid();
////                return mRowInc;
//            }
//
//            @Override
//            public int getPixelStride() {
//                throw new RuntimeException("Not Support!");
////                throwISEIfImageIsInvalid();
////                return mColInc;
//            }
//
//            @Override
//            @NonNull
//            public ByteBuffer getBuffer() {
//                throw new RuntimeException("Not Support!");
////                throwISEIfImageIsInvalid();
////                return mData;
//            }
//
//            private final int mRowInc;
//            private final int mColInc;
//            private final ByteBuffer mData;
//        }
//    }
}
