/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 *PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 *KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/
#include "../local-include/csr.h"
#include "cpu/decode.h"
#include "isa.h"

#include <setjmp.h>

#ifdef CONFIG_ETRACE
#define StrArrDef(name, code, str) [code] = str,
static const char *ExpStr[] = {ExceptionList(StrArrDef)};
static const char *IntStr[] = {InterruptList(StrArrDef)};
#undef StrArrDef
#endif

#define GetStr(name)                                                           \
  ((name >> (XLEN - 1)) ? IntStr[name & 0xffff] : ExpStr[name & 0xffff])

#define isInter(num) (((sword_t)num) < 0)
#define isDeleg(num)                                                           \
  ((isInter(num)) ? BITS(medeleg->val, num, num) : BITS(mideleg->val, num, num))

void isa_raise_intr(word_t NO, vaddr_t tval) {
  if (BITS(medeleg->val, NO, NO) == 0) {
    mepc->val = isa_decode.pc;
    mcause->val = NO;
    mstatus->mpp = machineMode;
    mstatus->mpie = mstatus->mie;
    mstatus->mie = 0;
    // mtval->val = tval;
    machineMode = PRI_M;
  } else {
    sepc->val = isa_decode.pc;
    scause->val = NO;
    sstatus->spp = machineMode;
    sstatus->spie = sstatus->sie;
    sstatus->sie = 0;
    // stval->val = tval;
    machineMode = PRI_S;
  }

  IFDEF(CONFIG_ETRACE, traceWrite("[E] trigger %s to " FMT_WORD, GetStr(NO),
                                  mtvec->base << 2));
  cpu.pc = mtvec->base << 2;
  isa_decode.isa.csrChange = true;
  longjmp(isa_except_buf, 1);
}

word_t isa_query_intr() { return INTR_EMPTY; }
