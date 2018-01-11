#include <jni.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1flush(JNIEnv *env, jobject instance) {

    // TODO

}

extern "C"
JNIEXPORT void JNICALL
Java_com_rockchip_rkmediacodec_RKMediaCodec_native_1setup(JNIEnv *env, jobject instance,
                                                          jstring name_, jboolean nameIsType,
                                                          jboolean encoder) {
    const char *name = env->GetStringUTFChars(name_, 0);

    // TODO

    env->ReleaseStringUTFChars(name_, name);
}