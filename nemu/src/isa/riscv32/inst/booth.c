#include "../local-include/InstrImpl.h"
#define HMUL_DATA_WIDTH (MUL_DATA_WIDTH >> 1)
bint initBint() {
  bint bi;
  memset(bi.bits, 0, MUL_DATA_WIDTH);
  return bi;
}

bint sword2bint(sword_t in) {
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

bint word2bint(word_t in) {
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

word_t bint2word(bint bi, int msb, int lsb) {
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

bint mulBint(bint bsrc0, bint bsrc1) {
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
#undef HMUL_DATA_WIDTH

// int main() {
//   for (int i = 0; i < 10000; i++) {
//     word_t src0 = random();
//     word_t src1 = random();
//     bint res = mulBint(src0, src1);
//     word_t dut = bint2word(res, MUL_DATA_WIDTH - 1, 32);
//   }
// }