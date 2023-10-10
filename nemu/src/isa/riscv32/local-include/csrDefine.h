
typedef union {
  struct {
    word_t mode : 2;
    word_t base : 62;
  };
  word_t val;
} mtvec_t;
extern mtvec_t *mtvec;

typedef union {
  struct {
    word_t mode : 2;
    word_t base : 62;
  };
  word_t val;
} stvec_t;
extern stvec_t *stvec;

typedef union {
  word_t val;
} mepc_t;
extern mepc_t *mepc;

typedef union {
  word_t val;
} sepc_t;
extern sepc_t *sepc;

typedef union {
  struct {
    word_t exceptionCode : 63;
    word_t interrupted : 1;
  };
  word_t val;
} mcause_t;
extern mcause_t *mcause;

typedef union {
  struct {
    word_t exceptionCode : 63;
    word_t interrupted : 1;
  };
  word_t val;
} scause_t;
extern scause_t *scause;

typedef union {
  word_t val;
} medeleg_t;
extern medeleg_t *medeleg;

typedef union {
  word_t val;
} mtval_t;
extern mtval_t *mtval;

typedef union {
  word_t val;
} stval_t;
extern stval_t *stval;

typedef union {
  struct {
    word_t uie : 1;
    word_t sie : 1;
    word_t z2to2 : 1;
    word_t mie : 1;
    word_t upie : 1;
    word_t spie : 1;
    word_t ube : 1;
    word_t mpie : 1;
    word_t spp : 1;
    word_t vs : 2;
    word_t mpp : 2;
    word_t fs : 2;
    word_t xs : 2;
    word_t mprv : 1;
    word_t sum : 1;
    word_t mxr : 1;
    word_t z31to20 : 12;
    word_t uxl : 2;
    word_t sxl : 2;
    word_t sbe : 1;
    word_t mbe : 1;
    word_t z62to38 : 25;
    word_t sd : 1;
  };
  word_t val;
} mstatus_t;
extern mstatus_t *mstatus;

typedef union {
  struct {
    word_t uie : 1;
    word_t sie : 1;
    word_t z3to2 : 2;
    word_t upie : 1;
    word_t spie : 1;
    word_t ube : 1;
    word_t z7to7 : 1;
    word_t spp : 1;
    word_t vs : 2;
    word_t z12to11 : 2;
    word_t fs : 2;
    word_t xs : 2;
    word_t z17to17 : 1;
    word_t sum : 1;
    word_t mxr : 1;
    word_t z31to20 : 12;
    word_t uxl : 2;
    word_t z62to34 : 29;
    word_t sd : 1;
  };
  word_t val;
} sstatus_t;
extern sstatus_t *sstatus;

typedef union {
  word_t val;
} mscratch_t;
extern mscratch_t *mscratch;

typedef union {
  word_t val;
} sscratch_t;
extern sscratch_t *sscratch;

typedef union {
  struct {
    word_t ppn : 44;
    word_t asid : 16;
    word_t mode : 4;
  };
  word_t val;
} satp_t;
extern satp_t *satp;
word_t mtvecRead();
word_t stvecRead();
word_t mepcRead();
word_t sepcRead();
word_t mcauseRead();
word_t scauseRead();
word_t medelegRead();
word_t mtvalRead();
word_t stvalRead();
word_t mstatusRead();
word_t sstatusRead();
word_t mscratchRead();
word_t sscratchRead();
word_t satpRead();
void csrRW(int csrDst, word_t *rd, word_t *src1, csrOp op);
void csrInit();
