#include "rk_mpi.h"
#include <string>

class RKMpp {
public:
    RKMpp();
    ~RKMpp();
    int initCodec(std::string type, bool isEncoder);


private:
    MppCtx mMppCtx;
    MppApi *mMppApi;
};