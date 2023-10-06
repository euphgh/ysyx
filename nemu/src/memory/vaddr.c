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
#include "isa.h"

#include <memory/vaddr.h>
#include <memory/paddr.h>

extern const char *mtraceVname;
extern vaddr_t mtraceVaddr;
bool vaddr_success() { return true; }

word_t vaddr_ifetch(vaddr_t addr, int len) {
#ifdef CONFIG_MTRACE
  mtraceVaddr = addr;
  mtraceVname = "Ifetch";
#endif
  paddr_t paddr = (isa_mmu_check(addr, len, MEM_TYPE_IFETCH) == MMU_TRANSLATE)
                      ? isa_mmu_translate(addr, len, MEM_TYPE_IFETCH)
                      : addr;
  return paddr_read(paddr, len);
}

word_t vaddr_read(vaddr_t addr, int len) {
#ifdef CONFIG_MTRACE
  mtraceVaddr = addr;
  mtraceVname = "Dread ";
#endif
  paddr_t paddr = (isa_mmu_check(addr, len, MEM_TYPE_READ) == MMU_TRANSLATE)
                      ? isa_mmu_translate(addr, len, MEM_TYPE_READ)
                      : addr;
  return paddr_read(paddr, len);
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
}
