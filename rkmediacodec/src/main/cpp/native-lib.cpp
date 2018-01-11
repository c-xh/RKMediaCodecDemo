#include <jni.h>
#include "log.h"
#include "rk_mpp.h"

extern "C" {
JNIEXPORT void JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1flush(JNIEnv *env, jobject instance) {

    // TODO

}

JNIEXPORT jlong JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1create(JNIEnv *env, jobject instance,
                                                           jstring name_, jboolean nameIsType,
                                                           jboolean encoder) {
    const char *name = env->GetStringUTFChars(name_, 0);
    LOGD("type name : %s", name);
    env->ReleaseStringUTFChars(name_, name);

    RKMpp *rkmpp = new RKMpp();
    rkmpp->debug();
    return (jlong)rkmpp;
}

JNIEXPORT jlong JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1configure(JNIEnv *env, jobject instance,
                                                              jlong pMpp) {
    RKMpp *rkmpp = (RKMpp*) pMpp;
    LOGD("configure ........");
    rkmpp->debug();
}

JNIEXPORT jobject JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1dequeueInputBuffer(JNIEnv *env, jobject instance,
                                                                       jlong pMpp, jint timeoutUS) {
    RKMpp *rkmpp = (RKMpp*) pMpp;
    LOGD("dequeue input buffer ");
    rkmpp->debug();
}

}