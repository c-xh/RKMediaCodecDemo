#include <malloc.h>
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
    MPP_RET ret = MPP_OK;
    int index = mTaskIndexCursor;
    MppTask *task = &mInputTaskList[index];
    mTaskIndexCursor = (mTaskIndexCursor + 1) % MPP_MAX_INPUT_TASK;

    ret = mMppApi->poll(mMppCtx, MPP_PORT_INPUT, MPP_POLL_BLOCK);
    if (ret) {
        LOGE("mpp input poll failed");
        return ret;
    }

    mMppApi->dequeue(mMppCtx, MPP_PORT_INPUT, task);
    LOGD("RKMpp[%p]::dequeueInputBuffer %d", this, index);
    return index;
}

void RKMpp::queueInputBuffer(int index, int offset, int size, long timeoutUs) {
    MPP_RET ret = MPP_OK;
    MppPacket packet = {0};
    MppTask *task = &mInputTaskList[index];

    LOGD("queue input buffer : %d is %p", index, (void*)mInputBuffers[index]);
    mpp_packet_set_pos(packet, mInputBuffers[index] + offset);
    mpp_packet_set_length(packet, size);
    mpp_packet_set_eos(packet);

    mpp_task_meta_set_packet(task, KEY_INPUT_PACKET, packet);
    //mpp_task_meta_set_frame (task, KEY_OUTPUT_FRAME,  frame);

    ret = mMppApi->enqueue(mMppCtx, MPP_PORT_INPUT, task);  /* input queue */
    if (ret) {
        LOGE("mpp task input enqueue failed");
    }
}

int RKMpp::dequeueOutputBuffer(long timeoutUs) {
    MPP_RET ret = MPP_OK;
    MppTask task = NULL;

    ret = mMppApi->poll(mMppCtx, MPP_PORT_OUTPUT, MPP_POLL_BLOCK);
    if (ret) {
        LOGE("mpp output poll failed");
        return -1; /* INFO_TRY_AGAIN_LATER */
    }

    ret = mMppApi->dequeue(mMppCtx, MPP_PORT_OUTPUT, &task); /* output queue */
    if (ret) {
        LOGE("mpp task output dequeue failed\n");
        return -10;
    }

    MppFrame  frame; // todo:: return to display

    if (task) {
        MppFrame frame_out = NULL;
        mpp_task_meta_get_frame(task, KEY_OUTPUT_FRAME, &frame_out);
        //mpp_assert(packet_out == packet);

        if (frame) {
            /* write frame to file here */
            MppBuffer buf_out = mpp_frame_get_buffer(frame_out);

            if (buf_out) {
                void *ptr = mpp_buffer_get_ptr(buf_out);
                size_t len = mpp_buffer_get_size(buf_out);

                LOGD("decoded frame size %d", len);
            }

            if (mpp_frame_get_eos(frame_out))
                LOGD("found eos frame\n");
        }

        /* output queue */
        ret = mMppApi->enqueue(mMppCtx, MPP_PORT_OUTPUT, task);
        if (ret) {
            LOGD("mpp task output enqueue failed");
            return -11;
        }
    } else {
        LOGE("task error!");
        return -12;
    }
    return 0;
}



