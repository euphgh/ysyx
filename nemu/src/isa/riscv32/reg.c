/***************************************************************************************
* Copyright (c) 2014-2022 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "local-include/reg.h"
#include <isa.h>

const char *regs[] = {"$0", "ra", "sp",  "gp",  "tp", "t0", "t1", "t2",
                      "s0", "s1", "a0",  "a1",  "a2", "a3", "a4", "a5",
                      "a6", "a7", "s2",  "s3",  "s4", "s5", "s6", "s7",
                      "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

void isa_reg_display() {
#define PrintRegs(name, value)                                                 \
  printf("%2s: " FMT_WORD "\t" DEC_WORD "\n", name, value, value);

  for (int i = 0; i < 32; i++)
    PrintRegs(regs[i], gpr(i));
  PrintRegs("pc", cpu.pc);

#undef PrintRegs
}

int isa_reg_str2code(const char *name) {
#define CompAssign(str, code)                                                  \
  if (strcmp(str, name) == 0)                                                  \
    return code;
  for (int i = 0; i < 32; i++)
    CompAssign(regs[i], i);

  CompAssign("pc", RC_PC);
  return -1;
#undef CompAssign
}
bool isa_reg_code2val(int rcode, word_t *value) {
  if ((rcode > 0) && (rcode < 32)) {
    *value = gpr(rcode);
    return true;
  }

#define CaseAssign(code, var)                                                  \
  case code:                                                                   \
    *value = var;                                                              \
    return true
  switch (rcode) {
    CaseAssign(RC_PC, cpu.pc);
  default:
    return false;
  }
#undef CaseAssign
}

const char *isa_reg_code2str(int rcode) {
  if ((rcode > 0) && (rcode < 32)) {
    return regs[rcode];
  }

#define CaseAssign(code, str)                                                  \
  case code:                                                                   \
    return str
  switch (rcode) {
    CaseAssign(RC_PC, "pc");
  default:
    return NULL;
  }
#undef CaseAssign
}

bool isa_reg_str2val(const char *s, word_t *value) {
  return isa_reg_code2val(isa_reg_str2code(s), value);
}
