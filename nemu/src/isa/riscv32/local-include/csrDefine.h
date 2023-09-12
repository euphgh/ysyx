typedef union {
  struct {
    word_t base : 30;
    word_t mode : 2;
  };
  word_t val;
} mtvec_t;
extern mtvec_t mtvec;
bool mtvecRW(word_t *rd, word_t src1, csrOp op);

typedef union {
  struct {
    word_t all : 30;
  };
  word_t val;
} mepc_t;
extern mepc_t mepc;
bool mepcRW(word_t *rd, word_t src1, csrOp op);

typedef union {
  struct {
    word_t interrupted : 1;
    word_t exceptionCode : 31;
  };
  word_t val;
} mcause_t;
extern mcause_t mcause;
bool mcauseRW(word_t *rd, word_t src1, csrOp op);
bool csrRW(int csrDst, word_t *rd, word_t src1, csrOp op);