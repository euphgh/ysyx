#include "fixedptc.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>

#define DATA_PAIR(_) _(1.23, 2.23) _(4.5, -3.5) _(-1.2, -6.7)
#define DATA_ONE(_) _(0.0) _(1.0) _(-1.0) _(1.2) _(-1.2) _(5.8) _(-9.7)
#define AQ(left, right, str)                                                   \
  do {                                                                         \
    if (left != right) {                                                       \
      printf("\"%s\": ref = %x, dut = %x\n", str, left, right);                \
      exit(1);                                                                 \
    }                                                                          \
  } while (0)

int main() {
#define TestOne(x)                                                             \
  printf("=================== start one %f(%x) =================\n", x,        \
         fixedpt_rconst(x));                                                   \
  AQ(fixedpt_rconst(ceil(x)), fixedpt_ceil(fixedpt_rconst(x)), "Ceil");        \
  AQ(fixedpt_rconst(floor(x)), fixedpt_floor(fixedpt_rconst(x)), "Floor");

#define TestTwo(a, b)                                                          \
  printf("=================== start pair %f(%x) %f(%x) =================\n",   \
         a, fixedpt_rconst(a), b, fixedpt_rconst(b));                          \
  AQ(fixedpt_rconst(a + b), fixedpt_add(fixedpt_rconst(a), fixedpt_rconst(b)), \
     "Add");                                                                   \
  AQ(fixedpt_rconst(a - b), fixedpt_sub(fixedpt_rconst(a), fixedpt_rconst(b)), \
     "Sub");                                                                   \
  AQ(fixedpt_rconst((a * b)),                                                  \
     fixedpt_mul(fixedpt_rconst(a), fixedpt_rconst(b)), "Mul");                \
  AQ(fixedpt_rconst(a / b), fixedpt_div(fixedpt_rconst(a), fixedpt_rconst(b)), \
     "Div");                                                                   \
  AQ(fixedpt_rconst((a / ((int)b))),                                           \
     fixedpt_divi(fixedpt_rconst(a), ((int)b)), "RightDivi");                  \
  AQ(fixedpt_rconst((b / ((int)a))),                                           \
     fixedpt_divi(fixedpt_rconst(b), ((int)a)), "LeftDivi");                   \
  AQ(fixedpt_rconst((a * ((int)b))),                                           \
     fixedpt_muli(fixedpt_rconst(a), ((int)b)), "RightMuli");                  \
  AQ(fixedpt_rconst((b * ((int)a))),                                           \
     fixedpt_muli(fixedpt_rconst(b), ((int)a)), "LeftMuli");

  DATA_ONE(TestOne)
  DATA_PAIR(TestTwo)

  return 0;
}