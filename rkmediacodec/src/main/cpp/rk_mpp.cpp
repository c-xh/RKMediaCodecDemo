#include <malloc.h>
#include <unistd.h>
#include "rk_mpp.h"
#include "log.h"

RKMpp::RKMpp() : mTaskIndexCursor(0) {
    MPP_RET ret = mpp_create(&mMppCtx, &mMppApi);
    if (ret) {
        LOGE("mpp create failure! ret = %d", ret);
    }

    this->createInputBuffers();
}

RKMpp::~RKMpp() {
    mpp_destroy(mMppCtx);

    for (auto &p : mInputBuffers)
        free(p);
}

void RKMpp::createInputBuffers() {
    for (auto &p : mInputBuffers) {
        p = (char*)malloc(MPP_MAX_INPUT_BUF_SIZE);
        LOGD("createInputBuffers : %p", (void*)p);
    }
}

char *RKMpp::getInputBuffer(int index) {
    return mInputBuffers[index];
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

int RKMpp::dequeueInputBuffer(long timeoutUs) {
    int index = mTaskIndexCursor;
    mTaskIndexCursor = (mTaskIndexCursor + 1) % MPP_MAX_INPUT_TASK;
    return index;
}

void RKMpp::queueInputBuffer(int index, int offset, int size, long timeoutUs) {
    this->putPacket(mInputBuffers[index] + offset, (size_t)size);
}

int RKMpp::dequeueOutputBuffer(long timeoutUs) {
    return this->getFrame();
}

void RKMpp::putPacket(char *buf, size_t size) {
    MPP_RET ret = MPP_OK;
    MppPacket packet = NULL;

    ret = mpp_packet_init(&packet, buf, size);
    if (ret) {
        LOGE("failed to init packet!");
        return;
    }

    mpp_packet_write(packet, 0, buf, size);
    mpp_packet_set_pos(packet, buf);
    mpp_packet_set_length(packet, size);

    //LOGD("put packet %lu", size);
    int retry_put = 0;
    while(mMppApi->decode_put_packet(mMppCtx, packet)) {
        //LOGD("put packet error !");
        if(++retry_put > 30) {
            LOGW("skip this packet !");
            break;
        }
        usleep(3*1000);
    }

    //LOGD("put packet left len: %lu", mpp_packet_get_length(packet));

    if (packet) {
        mpp_packet_deinit(&packet);
    }
}

int RKMpp::getFrame() {
    MPP_RET ret = MPP_OK;
    MppFrame frame = NULL;

    ret = mMppApi->decode_get_frame(mMppCtx, &frame);
    if (MPP_OK != ret) {
        LOGW("decode_get_frame failed ret %d\n", ret);
        return -1;
    }

    if (frame) {
        if (mpp_frame_get_info_change(frame)) {
            mWidth = mpp_frame_get_width(frame);
            mHeight = mpp_frame_get_height(frame);
            RK_U32 hor_stride = mpp_frame_get_hor_stride(frame);
            RK_U32 ver_stride = mpp_frame_get_ver_stride(frame);

            LOGD("decode_get_frame get info changed found\n");
            LOGD("decoder require buffer w:h [%u:%u] stride [%d:%d]", mWidth, mHeight, hor_stride, ver_stride);

            //mMppApi->control(mMppCtx, MPP_DEC_SET_EXT_BUF_GROUP, output_grp);
            mMppApi->control(mMppCtx, MPP_DEC_SET_INFO_CHANGE_READY, NULL);
        } else {
            //RK_U32 width = mpp_frame_get_width(frame);
            //RK_U32 height = mpp_frame_get_height(frame);
            RK_U32 h_stride = mpp_frame_get_hor_stride(frame);
            RK_U32 v_stride = mpp_frame_get_ver_stride(frame);
            MppFrameFormat fmt = mpp_frame_get_fmt(frame);
            MppBuffer buf = mpp_frame_get_buffer(frame);
            int fd = mpp_buffer_get_fd(buf);
            char *ptr = (char *)mpp_buffer_get_ptr(buf);
            size_t siz = mpp_buffer_get_size(buf);

            //LOGD("got frame [fd:%d] . %dx%d [%dx%d]. size: %u", fd , width, height, h_stride, v_stride, siz);
            if (siz > 0) {
//                PRbsBuffer rbsBuff = RbsBuffer::createWithFd(siz, nullptr, fd, ptr);
//                BufferInfo info = {0};
//                info.type = UNIT_DATATYPE_YUV420;
//                info.width = h_stride;
//                info.height = v_stride;
//                info.pitch = h_stride;
//                info.drm_format = DRM_FORMAT_NV12;
//                info.timestamp = nanoTime();
//                rbsBuff->setBufferInfo(info);
//                rbsBuff->setValidSize(h_stride * v_stride * 3 / 2);

                /// check frame error
                int err = mpp_frame_get_errinfo(frame) | mpp_frame_get_discard(frame);
                if (err) {
                    //LOGW("mpp: get err info %d discard %d,go back.",
                    //        mpp_frame_get_errinfo(frame),
                    //        mpp_frame_get_discard(frame));
                } else {
                    //if(DEBUG_DECODER_DELAY) LOGD("Decoder delay: %u ms", (nanoTime() - mDelayTime)/NANOTIME_PER_MSECOND);
                }
            }

            //LOGD("- mpp_frame_deinit :%d", fd);
            mpp_frame_deinit(&frame);

            frame = NULL;
        }
    } else {
        //usleep(10 * 1000); /* 1ms */
        LOGD("no frame !");
    }

    return 0;
}



