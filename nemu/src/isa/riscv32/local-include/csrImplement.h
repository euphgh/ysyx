
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

inline static void mtvecWrite(word_t val) {
  mtvec_t newVar = {.val = val};
  mtvec->mode = newVar.mode;
  mtvec->base = newVar.base;
}

inline static void stvecWrite(word_t val) {
  stvec_t newVar = {.val = val};
  stvec->mode = newVar.mode;
  stvec->base = newVar.base;
}

inline static void mepcWrite(word_t val) {
  mepc_t newVar = {.val = val};
  mepc->val = newVar.val;
}

inline static void sepcWrite(word_t val) {
  sepc_t newVar = {.val = val};
  sepc->val = newVar.val;
}

inline static void mcauseWrite(word_t val) {
  mcause_t newVar = {.val = val};
  mcause->exceptionCode = newVar.exceptionCode;
}

inline static void scauseWrite(word_t val) {
  scause_t newVar = {.val = val};
  scause->exceptionCode = newVar.exceptionCode;
}

inline static void medelegWrite(word_t val) {
  medeleg_t newVar = {.val = val};
  medeleg->val = newVar.val;
}

inline static void mtvalWrite(word_t val) {
  mtval_t newVar = {.val = val};
  mtval->val = newVar.val;
}

inline static void stvalWrite(word_t val) {
  stval_t newVar = {.val = val};
  stval->val = newVar.val;
}

inline static void mstatusWrite(word_t val) {
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
}

inline static void sstatusWrite(word_t val) {
  sstatus_t newVar = {.val = val};
  sstatus->uie = newVar.uie;
  sstatus->sie = newVar.sie;

  sstatus->upie = newVar.upie;
  sstatus->spie = newVar.spie;

  sstatus->spp = newVar.spp;

  sstatus->sum = newVar.sum;
  sstatus->mxr = newVar.mxr;
}

inline static void mscratchWrite(word_t val) {
  mscratch_t newVar = {.val = val};
  mscratch->val = newVar.val;
}

inline static void sscratchWrite(word_t val) {
  sscratch_t newVar = {.val = val};
  sscratch->val = newVar.val;
}

inline static void satpWrite(word_t val) {
  satp_t newVar = {.val = val};
  satp->ppn = newVar.ppn;
  satp->asid = newVar.asid;
  if (isLegalPageMode(newVar.mode)) {
    satp->mode = newVar.mode;
  }
}
word_t mtvecRead() { return (mtvec->val); }
word_t stvecRead() { return (stvec->val); }
word_t mepcRead() { return (mepc->val); }
word_t sepcRead() { return (sepc->val); }
word_t mcauseRead() { return (mcause->val); }
word_t scauseRead() { return (scause->val); }
word_t medelegRead() { return (medeleg->val); }
word_t mtvalRead() { return (mtval->val); }
word_t stvalRead() { return (stval->val); }
word_t mstatusRead() { return (mstatus->val); }
word_t sstatusRead() { return (sstatusClrmstatus(sstatus->val)); }
word_t mscratchRead() { return (mscratch->val); }
word_t sscratchRead() { return (sscratch->val); }
word_t satpRead() { return (satp->val); }
void csrRW(int csrDst, word_t *rd, word_t *src1, csrOp op) {
  switch (csrDst) {
  case 0x305:
    if (rd)
      *rd = mtvecRead();
    if (src1)
      mtvecWrite(whichOp(mtvec->val, *src1, op));
    break;
  case 0x105:
    if (rd)
      *rd = stvecRead();
    if (src1)
      stvecWrite(whichOp(stvec->val, *src1, op));
    break;
  case 0x341:
    if (rd)
      *rd = mepcRead();
    if (src1)
      mepcWrite(whichOp(mepc->val, *src1, op));
    break;
  case 0x141:
    if (rd)
      *rd = sepcRead();
    if (src1)
      sepcWrite(whichOp(sepc->val, *src1, op));
    break;
  case 0x342:
    if (rd)
      *rd = mcauseRead();
    if (src1)
      mcauseWrite(whichOp(mcause->val, *src1, op));
    break;
  case 0x142:
    if (rd)
      *rd = scauseRead();
    if (src1)
      scauseWrite(whichOp(scause->val, *src1, op));
    break;
  case 0x302:
    if (rd)
      *rd = medelegRead();
    if (src1)
      medelegWrite(whichOp(medeleg->val, *src1, op));
    break;
  case 0x343:
    if (rd)
      *rd = mtvalRead();
    if (src1)
      mtvalWrite(whichOp(mtval->val, *src1, op));
    break;
  case 0x143:
    if (rd)
      *rd = stvalRead();
    if (src1)
      stvalWrite(whichOp(stval->val, *src1, op));
    break;
  case 0x300:
    if (rd)
      *rd = mstatusRead();
    if (src1)
      mstatusWrite(whichOp(mstatus->val, *src1, op));
    break;
  case 0x100:
    if (rd)
      *rd = sstatusRead();
    if (src1)
      sstatusWrite(whichOp(sstatus->val, *src1, op));
    break;
  case 0x340:
    if (rd)
      *rd = mscratchRead();
    if (src1)
      mscratchWrite(whichOp(mscratch->val, *src1, op));
    break;
  case 0x140:
    if (rd)
      *rd = sscratchRead();
    if (src1)
      sscratchWrite(whichOp(sscratch->val, *src1, op));
    break;
  case 0x180:
    if (rd)
      *rd = satpRead();
    if (src1)
      satpWrite(whichOp(satp->val, *src1, op));
    break;
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

  mscratch->val = 0x0;
  sscratch->val = 0x0;
  satp->val = 0x0;
}
