#include <jni.h>
#include "log.h"
#include "rk_mpp.h"

extern "C" {
JNIEXPORT void JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1flush(JNIEnv *env, jobject instance) {


}

JNIEXPORT jlong JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1create(JNIEnv *env, jobject instance,
                                                           jstring name_, jboolean encoder) {
    const char *name = env->GetStringUTFChars(name_, 0);
    LOGD("type name : %s", name);
    env->ReleaseStringUTFChars(name_, name);

    RKMpp *rkmpp = new RKMpp();
    if (rkmpp->initCodec(name, encoder)) {
        jclass Exception = env->FindClass("java/lang/Exception");
        env->ThrowNew(Exception, "RKMediaCodec can't support this codec type");
    }

    return (jlong) rkmpp;
}

JNIEXPORT jlong JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1configure(JNIEnv *env, jobject instance,
                                                              jlong pMpp) {
    RKMpp *rkmpp = (RKMpp *) pMpp;
    LOGD("configure ........");

}

JNIEXPORT jobjectArray JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1getBuffers(jlong mpp_instance, jboolean input) {

}

JNIEXPORT jint JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1dequeueInputBuffer(JNIEnv *env,
                                                                       jobject instance,
                                                                       jlong pMpp, jint timeoutUS) {
    RKMpp *rkmpp = (RKMpp *) pMpp;
    LOGD("dequeue Input Buffer ");
    return rkmpp->dequeueInputBuffer(timeoutUS);
}

JNIEXPORT jobject JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1getInputBuffer(JNIEnv *env, jobject instance,
                                                                   jlong pMpp, jint index) {
    LOGD("get Input Buffer : %d", index);
    RKMpp *rkmpp = (RKMpp *) pMpp;
    return env->NewDirectByteBuffer(rkmpp->getInputBuffer(index), MPP_MAX_INPUT_BUF_SIZE);
}

JNIEXPORT void JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1queueInputBuffer(JNIEnv *env, jobject instance,
                                                                     jlong pMpp,
                                                                     jint index,
                                                                     jint offset,
                                                                     jint size,
                                                                     jlong presentationTimeUs,
                                                                     jint flags) {

    LOGD("queue Input Buffer: %d, siz: %d", index, size);
    RKMpp *rkmpp = (RKMpp *) pMpp;
    rkmpp->queueInputBuffer(index, offset, size, presentationTimeUs);
}

JNIEXPORT jint JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1dequeueOutputBuffer(JNIEnv *env, jobject instance,
                                                                        jlong pMpp, jlong timeoutUs) {
    LOGD("dequeue Output Buffer ");
    RKMpp *rkmpp = (RKMpp *) pMpp;
    return rkmpp->dequeueOutputBuffer(timeoutUs);
}

} /* Extern C */