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

#include "../local-include/reg.h"
#include "common.h"
#include <cpu/difftest.h>
#include <isa.h>

#define FOREACH_REGS                                                           \
  for (int i = 0; i < 32; i++) {                                               \
    LAMBDA(reg_name(i), ref_r->gpr[i], cpu.gpr[i]);                            \
  }                                                                            \
  LAMBDA("pc", ref_r->pc, cpu.pc);

bool isa_difftest_checkregs(CPU_state *ref_r, vaddr_t pc) {
  bool same = true;
#define LAMBDA(name, ref, dut) same &= ref == dut
  FOREACH_REGS
#undef LAMBDA
  return same;
}

static void showErrorReg(const char *name, word_t ref, word_t dut) {
#define FMT_REG "[%s] = " FMT_WORD ", " DEC_WORD "\n"
  bool error = ref != dut;
  const char *dutFmt = error ? ANSI_FMT(FMT_REG, ANSI_FG_RED) : FMT_REG;
  if (error) {
    printf(dutFmt, name, dut, dut);
    printf(ANSI_FMT(FMT_REG, ANSI_FG_GREEN), name, ref, ref);
  }
#undef FMT_REG
}

void isa_difftest_showError(CPU_state *ref_r, vaddr_t pc) {
  error("Difftest catch Nemu error at PC = " FMT_WORD, pc);
#define LAMBDA(name, ref, dut) showErrorReg(name, ref, dut)
  FOREACH_REGS
#undef LAMBDA
}

void isa_difftest_attach() {
}
