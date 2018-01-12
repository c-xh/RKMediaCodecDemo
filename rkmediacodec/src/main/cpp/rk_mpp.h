#include "rk_mpi.h"
#include <string>

#define MPP_MAX_INPUT_TASK 3
#define MPP_MAX_INPUT_BUF_SIZE 1920*1080

class RKMpp {
public:
    RKMpp();
    ~RKMpp();
    int initCodec(std::string type, bool isEncoder);
    void createInputBuffers();
    char *getInputBuffer(int index);
    int dequeueInputBuffer(long timeoutUs);
    void queueInputBuffer(int index, int offset, int size, long timeoutUs);
    int dequeueOutputBuffer(long timeoutUs);

    void putPacket(char* buf, size_t size);
    int getFrame();

private:
    MppCtx mMppCtx;
    MppApi *mMppApi;
    MppTask mInputTaskList[MPP_MAX_INPUT_TASK];
    int mTaskIndexCursor;
    char *mInputBuffers[MPP_MAX_INPUT_TASK];

    long mWidth;
    long mHeight;
};