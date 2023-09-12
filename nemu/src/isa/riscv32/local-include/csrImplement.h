mtvec_t mtvec;

static bool mtvecWrite(word_t val) {
    mtvec_t newVar = {.val = val};
    mtvec.base = newVar.base;
mtvec.mode = newVar.mode;
}

bool mtvecRW(word_t* rd, word_t src1, csrOp op){
    if (rd) *rd = mtvec.val;
    switch (op) {
        case csrSET: res = mtvecWrite(mtvec.val | src1); break;
        case csrCLR: res = mtvecWrite(mtvec.val & ~src1); break;
        case csrWAR: res = mtvecWrite(src1); break;
    }
    return res
}
mepc_t mepc;

static bool mepcWrite(word_t val) {
    mepc_t newVar = {.val = val};
    mepc.all = newVar.all;
}

bool mepcRW(word_t* rd, word_t src1, csrOp op){
    if (rd) *rd = mepc.val;
    switch (op) {
        case csrSET: res = mepcWrite(mepc.val | src1); break;
        case csrCLR: res = mepcWrite(mepc.val & ~src1); break;
        case csrWAR: res = mepcWrite(src1); break;
    }
    return res
}
mcause_t mcause;

static bool mcauseWrite(word_t val) {
    mcause_t newVar = {.val = val};
    
mcause.exceptionCode = newVar.exceptionCode;
}

bool mcauseRW(word_t* rd, word_t src1, csrOp op){
    if (rd) *rd = mcause.val;
    switch (op) {
        case csrSET: res = mcauseWrite(mcause.val | src1); break;
        case csrCLR: res = mcauseWrite(mcause.val & ~src1); break;
        case csrWAR: res = mcauseWrite(src1); break;
    }
    return res
}
bool csrRW(int csrDst, word_t* rd, word_t src1, csrOp op){
    bool res;
    switch (csrDst) {
        case 0x305: res = mtvecrw(rd, src1, op); break;
case 0x341: res = mepcrw(rd, src1, op); break;
case 0x342: res = mcauserw(rd, src1, op); break;
        default: res = false; break;
    }
    return res
}