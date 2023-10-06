
static mtvec_t mtvecReg;
mtvec_t *mtvec = &mtvecReg;

static stvec_t stvecReg;
stvec_t *stvec = &stvecReg;

static mepc_t mepcReg;
mepc_t *mepc = &mepcReg;

static sepc_t sepcReg;
sepc_t *sepc = &sepcReg;

static mcause_t mcauseReg;
mcause_t *mcause = &mcauseReg;

static scause_t scauseReg;
scause_t *scause = &scauseReg;

static medeleg_t medelegReg;
medeleg_t *medeleg = &medelegReg;

static mtval_t mtvalReg;
mtval_t *mtval = &mtvalReg;

static stval_t stvalReg;
stval_t *stval = &stvalReg;

static mstatus_t mstatusReg;
mstatus_t *mstatus = &mstatusReg;

sstatus_t *sstatus = NULL;

static mscratch_t mscratchReg;
mscratch_t *mscratch = &mscratchReg;

static sscratch_t sscratchReg;
sscratch_t *sscratch = &sscratchReg;

static satp_t satpReg;
satp_t *satp = &satpReg;

inline static bool mtvecWrite(word_t val) {
  mtvec_t newVar = {.val = val};
  mtvec->mode = newVar.mode;
  mtvec->base = newVar.base;
  return true;
}

inline static bool stvecWrite(word_t val) {
  stvec_t newVar = {.val = val};
  stvec->mode = newVar.mode;
  stvec->base = newVar.base;
  return true;
}
inline static bool mepcWrite(word_t val) { return true; }
inline static bool sepcWrite(word_t val) { return true; }

inline static bool mcauseWrite(word_t val) {
  mcause_t newVar = {.val = val};
  mcause->exceptionCode = newVar.exceptionCode;

  return true;
}

inline static bool scauseWrite(word_t val) {
  scause_t newVar = {.val = val};
  scause->exceptionCode = newVar.exceptionCode;

  return true;
}
inline static bool medelegWrite(word_t val) { return true; }
inline static bool mtvalWrite(word_t val) { return true; }
inline static bool stvalWrite(word_t val) { return true; }

inline static bool mstatusWrite(word_t val) {
  mstatus_t newVar = {.val = val};
  mstatus->uie = newVar.uie;
  mstatus->sie = newVar.sie;

  mstatus->mie = newVar.mie;
  mstatus->upie = newVar.upie;
  mstatus->spie = newVar.spie;

  mstatus->mpie = newVar.mpie;
  mstatus->spp = newVar.spp;

  mstatus->mpp = newVar.mpp;

  mstatus->mprv = newVar.mprv;
  mstatus->sum = newVar.sum;
  mstatus->mxr = newVar.mxr;

  return true;
}

inline static bool sstatusWrite(word_t val) {
  sstatus_t newVar = {.val = val};
  sstatus->uie = newVar.uie;
  sstatus->sie = newVar.sie;

  sstatus->upie = newVar.upie;
  sstatus->spie = newVar.spie;

  sstatus->spp = newVar.spp;

  sstatus->sum = newVar.sum;
  sstatus->mxr = newVar.mxr;

  return true;
}
inline static bool mscratchWrite(word_t val) { return true; }
inline static bool sscratchWrite(word_t val) { return true; }

inline static bool satpWrite(word_t val) {
  satp_t newVar = {.val = val};
  satp->ppn = newVar.ppn;
  satp->asid = newVar.asid;
  if (isLegalPageMode(newVar.mode)) {
    satp->mode = newVar.mode;
  }
  return true;
}
bool mtvecRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mtvec->val;
  return mtvecWrite(whichOp(mtvec->val, src1, op));
}
bool stvecRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = stvec->val;
  return stvecWrite(whichOp(stvec->val, src1, op));
}
bool mepcRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mepc->val;
  return mepcWrite(whichOp(mepc->val, src1, op));
}
bool sepcRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = sepc->val;
  return sepcWrite(whichOp(sepc->val, src1, op));
}
bool mcauseRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mcause->val;
  return mcauseWrite(whichOp(mcause->val, src1, op));
}
bool scauseRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = scause->val;
  return scauseWrite(whichOp(scause->val, src1, op));
}
bool medelegRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = medeleg->val;
  return medelegWrite(whichOp(medeleg->val, src1, op));
}
bool mtvalRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mtval->val;
  return mtvalWrite(whichOp(mtval->val, src1, op));
}
bool stvalRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = stval->val;
  return stvalWrite(whichOp(stval->val, src1, op));
}
bool mstatusRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mstatus->val;
  return mstatusWrite(whichOp(mstatus->val, src1, op));
}
bool sstatusRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = sstatusRead(sstatus->val);
  return sstatusWrite(whichOp(sstatus->val, src1, op));
}
bool mscratchRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = mscratch->val;
  return mscratchWrite(whichOp(mscratch->val, src1, op));
}
bool sscratchRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = sscratch->val;
  return sscratchWrite(whichOp(sscratch->val, src1, op));
}
bool satpRW(word_t *rd, word_t src1, csrOp op) {
  if (rd)
    *rd = satp->val;
  return satpWrite(whichOp(satp->val, src1, op));
}
bool csrRW(int csrDst, word_t *rd, word_t src1, csrOp op) {
  switch (csrDst) {
  case 0x305:
    return mtvecRW(rd, src1, op);
  case 0x105:
    return stvecRW(rd, src1, op);
  case 0x341:
    return mepcRW(rd, src1, op);
  case 0x141:
    return sepcRW(rd, src1, op);
  case 0x342:
    return mcauseRW(rd, src1, op);
  case 0x142:
    return scauseRW(rd, src1, op);
  case 0x302:
    return medelegRW(rd, src1, op);
  case 0x343:
    return mtvalRW(rd, src1, op);
  case 0x143:
    return stvalRW(rd, src1, op);
  case 0x300:
    return mstatusRW(rd, src1, op);
  case 0x100:
    return sstatusRW(rd, src1, op);
  case 0x340:
    return mscratchRW(rd, src1, op);
  case 0x140:
    return sscratchRW(rd, src1, op);
  case 0x180:
    return satpRW(rd, src1, op);
  default:
    return false;
  }
}
void csrInit() {
  mtvec->val = 0x0;
  stvec->val = 0x0;
  mepc->val = 0x0;
  sepc->val = 0x0;
  mcause->val = 0x0;
  scause->val = 0x0;
  medeleg->val = 0x0;
  mtval->val = 0x0;
  stval->val = 0x0;
  mstatus->val = 0xa00001800;
  sstatus->val = 0x200000000;
  mscratch->val = 0x0;
  sscratch->val = 0x0;
  satp->val = 0x0;
}
