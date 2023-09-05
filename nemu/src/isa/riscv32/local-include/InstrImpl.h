#ifndef __INSTR_IMPL_H__
#define __INSTR_IMPL_H__
#include "common.h"
/* mul */
word_t rv64instr_mulh(sword_t src1, sword_t src2);
word_t rv64instr_mulhsu(sword_t src1, word_t src2);
word_t rv64instr_mulhu(word_t src1, word_t src2);

/* div */
sword_t rv64instr_div(sword_t dividend, sword_t divisor);
word_t rv64instr_divu(word_t dividend, word_t divisor);
sword_t rv64instr_rem(sword_t dividend, sword_t divisor);
word_t rv64instr_remu(word_t dividend, word_t divisor);
sword_t rv64instr_divw(sword_t dividend, sword_t divisor);
word_t rv64instr_divuw(word_t dividend, word_t divisor);
sword_t rv64instr_remw(sword_t dividend, sword_t divisor);
word_t rv64instr_remuw(word_t dividend, word_t divisor);
#endif