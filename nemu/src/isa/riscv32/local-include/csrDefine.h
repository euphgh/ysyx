
typedef union {
  struct {
    word_t mode : 2;
    word_t base : 62;
  };
  word_t val;
} mtvec_t;
extern mtvec_t mtvec;

typedef union {
  struct {
    word_t all : 64;
  };
  word_t val;
} mepc_t;
extern mepc_t mepc;

typedef union {
  struct {
    word_t exceptionCode : 63;
    word_t interrupted : 1;
  };
  word_t val;
} mcause_t;
extern mcause_t mcause;

typedef union {
  struct {
    word_t r2to0 : 3;
    word_t mie : 1;
    word_t r5to4 : 2;
    word_t ube : 1;
    word_t mpie : 1;
    word_t r10to8 : 3;
    word_t mpp : 2;
    word_t fs : 2;
    word_t r31to15 : 17;
    word_t uxl : 2;
    word_t sxl : 2;
    word_t sbe : 1;
    word_t mbe : 1;
    word_t r63to38 : 26;
  };
  word_t val;
} mstatus_t;
extern mstatus_t mstatus;
bool mtvecRW(word_t *rd, word_t src1, csrOp op);
bool mepcRW(word_t *rd, word_t src1, csrOp op);
bool mcauseRW(word_t *rd, word_t src1, csrOp op);
bool mstatusRW(word_t *rd, word_t src1, csrOp op);
bool csrRW(int csrDst, word_t *rd, word_t src1, csrOp op);
void csrInit();
