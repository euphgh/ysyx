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

#include <../local-include/csr.h>
#include <isa.h>

#ifdef CONFIG_ETRACE
#define StrArrDef(name, code, str) [code] = str,
static const char *ExpStr[] = {ExceptionList(StrArrDef)};
static const char *IntStr[] = {InterruptList(StrArrDef)};
#undef StrArrDef
#endif

#define GetStr(name)                                                           \
  ((name >> (XLEN - 1)) ? IntStr[name & 0xffff] : ExpStr[name & 0xffff])

vaddr_t isa_raise_intr(word_t NO, vaddr_t epc) {
  mepc.all = epc;
  mcause.val = NO;
  mstatus.mpp = machineMode;
  machineMode = PRI_M;
  IFDEF(CONFIG_ETRACE,
        traceWrite("[E] trigger %s to " FMT_WORD, GetStr(NO), mtvec.base << 2));
  return mtvec.base << 2;
}

word_t isa_query_intr() {
  return INTR_EMPTY;
}
