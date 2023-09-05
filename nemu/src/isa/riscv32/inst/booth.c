#include "../local-include/InstrImpl.h"

#define MUL_DATA_WIDTH 128
typedef struct BigInt {
  bool bits[MUL_DATA_WIDTH];
} bint;

#define HMUL_DATA_WIDTH (MUL_DATA_WIDTH >> 1)
static bint initBint() {
  bint bi;
  memset(bi.bits, 0, MUL_DATA_WIDTH);
  return bi;
}

static bint sword2bint(sword_t in) {
  bint bi;
  for (int i = 0; i < HMUL_DATA_WIDTH; i++) {
    bi.bits[i] = in & 0x1;
    in = in >> 1;
  }
  for (int i = HMUL_DATA_WIDTH; i < MUL_DATA_WIDTH; i++) {
    bi.bits[i] = bi.bits[HMUL_DATA_WIDTH - 1];
  }
  return bi;
}

static bint word2bint(word_t in) {
  bint bi;
  for (int i = 0; i < HMUL_DATA_WIDTH; i++) {
    bi.bits[i] = in & 0x1;
    in = in >> 1;
  }
  for (int i = HMUL_DATA_WIDTH; i < MUL_DATA_WIDTH; i++) {
    bi.bits[i] = false;
  }
  return bi;
}

static word_t bint2word(bint bi, int msb, int lsb) {
  word_t in = 0;
  for (int i = msb; i >= lsb; i--) {
    in |= bi.bits[i];
    in = in << 1;
  }
  return in >> 1;
}

static bint addBint(bint src0, bint src1, bool *overflow) {
  bint res;
  bool car = false;
  for (int i = 0; i < MUL_DATA_WIDTH; i++) {
    res.bits[i] = src0.bits[i] ^ src1.bits[i] ^ car;
    car = (src0.bits[i] && src1.bits[i]) ||
          (car && (src0.bits[i] ^ src1.bits[i]));
  }
  if (overflow) {
    *overflow =
        (src0.bits[MUL_DATA_WIDTH - 1] && src1.bits[MUL_DATA_WIDTH - 1] &&
         !res.bits[MUL_DATA_WIDTH - 1]) ||
        (!src0.bits[MUL_DATA_WIDTH - 1] && !src1.bits[MUL_DATA_WIDTH - 1] &&
         res.bits[MUL_DATA_WIDTH - 1]);
  }
  return res;
}

static bint minusBint(bint src) {
  bint res;
  bool findOne = false;
  for (int i = 0; i < MUL_DATA_WIDTH; i++) {
    res.bits[i] = findOne ^ src.bits[i];
    if (!findOne)
      findOne = src.bits[i];
  }
  return res;
}

static bint sllBint(bint src, int n) {
  bint res;
  for (int i = 0; i < n; i++) {
    res.bits[i] = 0;
  }
  for (int i = n; i < MUL_DATA_WIDTH; i++) {
    res.bits[i] = src.bits[i - n];
  }
  return res;
}

void showBint(bint bi) {
  for (int i = MUL_DATA_WIDTH - 1; i >= 0; i--) {
    printf("%d", bi.bits[i]);
  }
  printf("\n");
}

static bint mulBint(bint bsrc0, bint bsrc1) {
  enum {
    rightShift,
    addSrcs0,
    addMinus,
  };
  uint8_t op[4] = {rightShift, addSrcs0, addMinus, rightShift};
  bint res = initBint();
  bint bmsrc0 = minusBint(bsrc0);
  uint8_t idx = bsrc1.bits[0] << 1;
  for (int i = 0; i < HMUL_DATA_WIDTH; i++) {
    switch (op[idx]) {
    case addSrcs0:
      res = addBint(res, sllBint(bsrc0, i), NULL);
      break;
    case addMinus:
      res = addBint(res, sllBint(bmsrc0, i), NULL);
      break;
    }
    if (i < HMUL_DATA_WIDTH - 1)
      idx = bsrc1.bits[i + 1] << 1 | bsrc1.bits[i];
  }
  return res;
}

word_t rv64instr_mulh(sword_t src1, sword_t src2) {
  return bint2word(mulBint(sword2bint(src1), sword2bint(src2)), 63, 32);
}
word_t rv64instr_mulhsu(sword_t src1, word_t src2) {
  return bint2word(mulBint(sword2bint(src1), word2bint(src2)), 63, 32);
}
word_t rv64instr_mulhu(word_t src1, word_t src2) {
  return bint2word(mulBint(word2bint(src1), word2bint(src2)), 63, 32);
}

#undef HMUL_DATA_WIDTH
#undef MUL_DATA_WIDTH

#define INT_TYPE(isSign, is64)                                                 \
  MUXONE(is64, MUXONE(isSign, int64_t, uint64_t),                              \
         MUXONE(isSign, int32_t, uint32_t))

#define WORD_TYPE(isSign) MUXONE(isSign, sword_t, word_t)

#define DIV_MAC(name, isSign, is64, op, zeroValue, overValue)                  \
  WORD_TYPE(isSign)                                                            \
  rv64instr_##name(WORD_TYPE(isSign) end, WORD_TYPE(isSign) sor) {             \
    INT_TYPE(isSign, is64) res = zeroValue;                                    \
    IFONE(isSign,                                                              \
          if ((sor == -1) && (end == MUXDEF(is64, INT64_MAX, INT32_MAX)))      \
              res = overValue);                                                \
    IFONE(isSign, else) if (sor != 0) res = end op sor;                        \
    return MUXONE(is64, res, SEXT(res, 32));                                   \
  }

#define SIGN 1
#define UNSIGN 0

#define IS64 1
#define IS32 0

DIV_MAC(div, SIGN, IS64, /, -1, INT64_MAX);
DIV_MAC(divu, UNSIGN, IS64, /, -1, INT64_MAX);
DIV_MAC(rem, SIGN, IS64, %, end, 0);
DIV_MAC(remu, UNSIGN, IS64, %, end, 0);
DIV_MAC(divw, SIGN, IS32, /, -1, INT32_MAX);
DIV_MAC(divuw, UNSIGN, IS32, /, -1, INT32_MAX);
DIV_MAC(remw, SIGN, IS32, %, end, 0);
DIV_MAC(remuw, UNSIGN, IS32, %, end, 0);

#undef SIGN
#undef UNSIGN
#undef IS64
#undef IS32
#undef INT_TYPE
#undef WORD_TYPE
#undef DIV_MAC