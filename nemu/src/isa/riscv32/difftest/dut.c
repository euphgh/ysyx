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

#include "../local-include/csr.h"
#include "../local-include/csrEnum.h"
#include "../local-include/reg.h"
#include "cpu/decode.h"
#include "cpu/difftest.h"
#include "isa.h"

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

static word_t csrs[16];
bool isa_difftest_checkcsrs() {
  ref_difftest_get_csr(csrs);
  bool res = true;
  int idx = 0;
#define CMP_CSR(name, num, ...) res &= csrs[idx++] == name->val;
  CSR_NUM_LIST(CMP_CSR)
#undef CMP_CSR
  Log("check csr = %d at PC " FMT_WORD, res, isa_decode.pc);
  return res;
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

void isa_difftest_showCSRerr() {
#define CMP_CSR(name, num, ...)                                                \
  if (csrs[idx++] != name->val) {                                              \
    showErrorReg(#name, csrs[idx - 1], name->val);                             \
  }
  int idx = 0;
  CSR_NUM_LIST(CMP_CSR)
#undef CMP_CSR
}

void isa_difftest_showError(CPU_state *ref_r, vaddr_t pc) {
  error("Difftest catch Nemu error at PC = " FMT_WORD, pc);
#define LAMBDA(name, ref, dut) showErrorReg(name, ref, dut)
  FOREACH_REGS
#undef LAMBDA
}

void isa_difftest_attach() {
}
