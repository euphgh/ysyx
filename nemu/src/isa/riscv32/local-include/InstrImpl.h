#ifndef __INSTR_IMPL_H__
#define __INSTR_IMPL_H__
#include "common.h"
#define MUL_DATA_WIDTH 128

typedef struct BigInt {
  bool bits[MUL_DATA_WIDTH];
} bint;

bint sword2bint(sword_t in);
bint word2bint(word_t in);
word_t bint2word(bint bi, int msb, int lsb);
bint mulBint(bint bsrc0, bint bsrc1);
#endif