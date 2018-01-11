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

    return (jlong)rkmpp;
}

JNIEXPORT jlong JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1configure(JNIEnv *env, jobject instance,
                                                              jlong pMpp) {
    RKMpp *rkmpp = (RKMpp*) pMpp;
    LOGD("configure ........");

}

JNIEXPORT jobject JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1dequeueInputBuffer(JNIEnv *env, jobject instance,
                                                                       jlong pMpp, jint timeoutUS) {
    RKMpp *rkmpp = (RKMpp*) pMpp;
    LOGD("dequeue input buffer ");

}

}