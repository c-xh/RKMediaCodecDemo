#include "rk_mpp.h"
#include "log.h"

RKMpp::RKMpp() {
    MPP_RET ret = mpp_create(&mMppCtx, &mMppApi);
    if (ret) {
        LOGE("mpp create failure! ret = %d", ret);
    }
}

RKMpp::~RKMpp() {
    mpp_destroy(mMppCtx);
}


int RKMpp::initCodec(std::string type, bool isEncoder) {
    MppCodingType codingType;
    if (type == "video/x-vnd.on2.vp8") {
        codingType = MPP_VIDEO_CodingVP8;
    } else if (type == "video/x-vnd.on2.vp9") {
        codingType = MPP_VIDEO_CodingVP9;
    } else if (type == "video/avc") {
        codingType = MPP_VIDEO_CodingAVC;
    } else if (type == "video/hevc") {
        codingType = MPP_VIDEO_CodingHEVC;
    } else if (type == "video/mp4v-es") {
        codingType = MPP_VIDEO_CodingMPEG4;
    } else if (type == "video/3gpp") {
        codingType = MPP_VIDEO_CodingFLV1;
    } else {
        LOGE("Can't support %s", type.c_str());
        return -1;
    }

    MppCtxType ctxType;
    if (isEncoder)
        ctxType = MPP_CTX_ENC;
    else
        ctxType = MPP_CTX_DEC;

    MPP_RET ret = mpp_init(mMppCtx, ctxType, codingType);
    if (ret) {
        LOGE("mpp_init failure !");
        return -2;
    }
    return 0;
}

