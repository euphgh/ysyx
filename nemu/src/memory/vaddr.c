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

extern const char *mtraceVname;
extern vaddr_t mtraceVaddr;
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

word_t vaddr_ifetch(vaddr_t addr, int len) {
#ifdef CONFIG_MTRACE
  mtraceVaddr = addr;
  mtraceVname = "Ifetch";
#endif
  paddr_t paddr = (isa_mmu_check(addr, len, MEM_TYPE_IFETCH) == MMU_TRANSLATE)
                      ? isa_mmu_translate(addr, len, MEM_TYPE_IFETCH)
                      : addr;
  word_t ret = paddr_read(paddr, len);
  if (addr & 0b11)
    goto exception;
  return ret;
exception:
  isa_raise_intr(EC_InstrAddrMisAlign, paddr);
  return 0;
}

word_t vaddr_read(vaddr_t addr, int len) {
#ifdef CONFIG_MTRACE
  mtraceVaddr = addr;
  mtraceVname = "Dread ";
#endif
  paddr_t paddr = (isa_mmu_check(addr, len, MEM_TYPE_READ) == MMU_TRANSLATE)
                      ? isa_mmu_translate(addr, len, MEM_TYPE_READ)
                      : addr;
  word_t ret = paddr_read(paddr, len);
  PADDR_ALIGN_CHECK(len, paddr);
  return ret;
exception:
  isa_raise_intr(EC_LoadAddrMisAlign, paddr);
  return 0;
}

void vaddr_write(vaddr_t addr, int len, word_t data) {
#ifdef CONFIG_MTRACE
  mtraceVaddr = addr;
  mtraceVname = "Dwrite";
#endif
  paddr_t paddr = (isa_mmu_check(addr, len, MEM_TYPE_WRITE) == MMU_TRANSLATE)
                      ? isa_mmu_translate(addr, len, MEM_TYPE_WRITE)
                      : addr;
  paddr_write(paddr, len, data);
  PADDR_ALIGN_CHECK(len, paddr);
  return;
exception:
  isa_raise_intr(EC_LoadAddrMisAlign, paddr);
  return;
}
