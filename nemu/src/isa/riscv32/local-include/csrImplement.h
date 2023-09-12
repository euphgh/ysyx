
static bool mtvecWrite(word_t val) {
  mtvec_t newVar = {.val = val};
  mtvec.mode = newVar.mode;
  mtvec.base = newVar.base;
}

static bool mepcWrite(word_t val) {
  mepc_t newVar = {.val = val};
  mepc.all = newVar.all;
}

static bool mcauseWrite(word_t val) {
  mcause_t newVar = {.val = val};
  mcause.exceptionCode = newVar.exceptionCode;
}

static bool mstatusWrite(word_t val) {
  mstatus_t newVar = {.val = val};

  mstatus.mie = newVar.mie;

  mstatus.mpie = newVar.mpie;

  mstatus.mpp = newVar.mpp;
  mstatus.fs = newVar.fs;
}
bool mtvecRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mtvec.val;
  switch (op) {
  case csrSET:
    res = mtvecWrite(mtvec.val | src1);
    break;
  case csrCLR:
    res = mtvecWrite(mtvec.val & ~src1);
    break;
  case csrWAR:
    res = mtvecWrite(src1);
    break;
  }
  return res
}
bool mepcRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mepc.val;
  switch (op) {
  case csrSET:
    res = mepcWrite(mepc.val | src1);
    break;
  case csrCLR:
    res = mepcWrite(mepc.val & ~src1);
    break;
  case csrWAR:
    res = mepcWrite(src1);
    break;
  }
  return res
}
bool mcauseRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mcause.val;
  switch (op) {
  case csrSET:
    res = mcauseWrite(mcause.val | src1);
    break;
  case csrCLR:
    res = mcauseWrite(mcause.val & ~src1);
    break;
  case csrWAR:
    res = mcauseWrite(src1);
    break;
  }
  return res
}
bool mstatusRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mstatus.val;
  switch (op) {
  case csrSET:
    res = mstatusWrite(mstatus.val | src1);
    break;
  case csrCLR:
    res = mstatusWrite(mstatus.val & ~src1);
    break;
  case csrWAR:
    res = mstatusWrite(src1);
    break;
  }
  return res
}
bool csrRW(int csrDst, word_t *rd, word_t src1, csrOp op) {
  bool res;
  switch (csrDst) {
  case 0x305:
    res = mtvecrw(rd, src1, op);
    break;
  case 0x341:
    res = mepcrw(rd, src1, op);
    break;
  case 0x342:
    res = mcauserw(rd, src1, op);
    break;
  case 0x300:
    res = mstatusrw(rd, src1, op);
    break;
  default:
    res = false;
    break;
  }
  return res
}
void csrInit() {
  mtvec.val = 0x0;
  mepc.val = 0x0;
  mcause.val = 0x0;
  mstatus.val = 0xa00001800;
}
