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
#include "isa.h"

#include <memory/vaddr.h>
#include <memory/paddr.h>

bool vaddr_success() { return true; }

#define PADDR_ALIGN_CHECK(len, addr)                                           \
  if (len == 2) {                                                              \
    if (unlikely(addr & 0b1))                                                  \
      goto exception;                                                          \
  } else if (len == 4) {                                                       \
    if (unlikely(addr & 0b11))                                                 \
      goto exception;                                                          \
  } else if (len == 8) {                                                       \
    if (unlikely(addr & 0b111))                                                \
      goto exception;                                                          \
  }

#define MMU_CHECK(typeVar, typeStr)                                            \
  int mmu_check_res = isa_mmu_check(addr, len, typeVar);                       \
  paddr_t paddr = addr;                                                        \
  IFDEF(CONFIG_MTRACE, const char *tranStr = "[M] " typeStr " va " FMT_WORD    \
                                             " dir pa " FMT_PADDR);            \
  if (mmu_check_res == MMU_TRANSLATE) {                                        \
    paddr = isa_mmu_translate(addr, len, typeVar);                             \
    IFDEF(tranStr = "[M] " typeStr " va " FMT_WORD " trl pa " FMT_PADDR);      \
  }                                                                            \
  IFDEF(CONFIG_MTRACE, traceWrite(tranStr, addr, paddr));

word_t vaddr_ifetch(vaddr_t addr, int len) {
  MMU_CHECK(MEM_TYPE_IFETCH, "Ifetch");
  word_t ret = paddr_read(paddr, len);
  if (addr & 0b11)
    goto exception;
  return ret;
exception:
  isa_raise_intr(EC_InstrAddrMisAlign, paddr);
  return 0;
}

word_t vaddr_read(vaddr_t addr, int len) {
  MMU_CHECK(MEM_TYPE_READ, "Dread ");
  word_t ret = paddr_read(paddr, len);
  PADDR_ALIGN_CHECK(len, paddr);
  return ret;
exception:
  isa_raise_intr(EC_LoadAddrMisAlign, paddr);
  return 0;
}

void vaddr_write(vaddr_t addr, int len, word_t data) {
  MMU_CHECK(MEM_TYPE_WRITE, "Dwrite");
  paddr_write(paddr, len, data);
  PADDR_ALIGN_CHECK(len, paddr);
  return;
exception:
  isa_raise_intr(EC_LoadAddrMisAlign, paddr);
  return;
}
