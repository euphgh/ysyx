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
#include "debug.h"
#include "memory/paddr.h"

#include <isa.h>
#include <memory/vaddr.h>

typedef union {
  struct {
    vaddr_t offset : 12;
    vaddr_t vpn : 27;
    vaddr_t ext : 25;
  };
  vaddr_t val;
} Sv39Vaddr;

typedef union {
  struct {
    word_t v : 1;
    word_t r : 1;
    word_t w : 1;
    word_t x : 1;
    word_t u : 1;
    word_t a : 1;
    word_t d : 1;
    word_t rsw : 2;
    word_t ppn : 27;
    word_t reserved : 10;
  };
  word_t val;
} Sv39Pte;

static Plevel memPLv;
int isa_mmu_check(vaddr_t foo, int len, int type) {
  /* set privilege of mem access */
  memPLv = machineMode;
  if (type != MEM_TYPE_IFETCH && mstatus->mprv)
    memPLv = mstatus->mpp;

  /* check need address translate */
  if (memPLv == PRI_M || satp->mode == SatpModeBare)
    return MMU_DIRECT;
  Assert(satp->mode == SatpModeSv39, "memPLv = %d, satp->mode = %d", memPLv,
         satp->mode);
  return MMU_TRANSLATE;
}

static char errstr[128];
#define MMU_ASSERT(cond, fmt, ...)                                             \
  if (unlikely(!(cond)))                                                       \
    do {                                                                       \
      snprintf(errstr, 128, #cond ":" fmt, ##__VA_ARGS__);                     \
      goto mmuFail;                                                            \
  } while (0)

inline static bool sum(Sv39Pte pte) {
  bool ret = pte.u;
  if (memPLv == PRI_S)
    ret = (pte.u == false) || mstatus->sum;
  return ret;
}
inline static bool mxr(Sv39Pte pte) { return pte.r || (mstatus->mxr && pte.x); }
inline static word_t ppn(Sv39Pte pte, int lv) {
  int msb[] = {18, 27, 53};
  return BITS(pte.val, msb[lv], 10 + lv * 9);
}

#define HARDWAVE_AD

paddr_t isa_mmu_translate(vaddr_t vaddr, int len, int type) {
  errstr[0] = '\0';
  Assert(type == MEM_TYPE_IFETCH || type == MEM_TYPE_WRITE ||
             type == MEM_TYPE_READ,
         "type = %d not expected", type);

  /* Step1: Effective privilege mode must be S-mode or U-mode */
  MMU_ASSERT(memPLv == PRI_S || memPLv == PRI_U, "MemPLv = %d", memPLv);

  /* Step2: Calculate pte address and load pte */
  word_t base = satp->ppn * PAGE_SIZE;
  Sv39Vaddr sv39va = {.val = vaddr};
  MMU_ASSERT(sv39va.vpn >> 26 ? ~sv39va.ext : sv39va.ext, "vaddr = %lx", vaddr);
  for (int pageLv = 2; pageLv >= 0; pageLv--) {
    base += BITS(sv39va.vpn, pageLv * 9 + 8, pageLv * 9);
    Sv39Pte pte = {.val = paddr_read(base, 8)};
    MMU_ASSERT(pte.reserved == 0, "pte = %lx", pte.val);

    /* Step3: Check pte.v = 0, or if pte.r = 0 and pte.w = 1 */
    MMU_ASSERT((!pte.v || (!pte.r && pte.w)) == false, "pte = %lx", pte.val);

    /* Step4: Check pte.r = 1 or pte.x = 1 for leaf or node */
    if ((pte.r || pte.x) == false)
      continue;

    /* Step5: Check leaf PTE r, w, x, u bits */
    switch (type) {
    case MEM_TYPE_IFETCH:
      MMU_ASSERT(pte.x && pte.u ^ (memPLv != PRI_U), "pte = %lx", pte.val);
      break;
    case MEM_TYPE_READ:
      MMU_ASSERT(mxr(pte) && sum(pte), "pte = %lx", pte.val);
      break;
    case MEM_TYPE_WRITE:
      MMU_ASSERT(pte.w && sum(pte), "pte = %lx", pte.val);
      break;
    default:
      MMU_ASSERT(false, "type not expected %d", type);
    }

    /* Step6: Check misaligned superpage */
    for (int i = pageLv - 1; i >= 0; i--)
      MMU_ASSERT(ppn(pte, i) == 0, "pte = %lx", pte.val);

    /* Step7: Check for d and a bits */
    if (pte.a == false || (type == MEM_TYPE_WRITE && pte.d == false)) {
#ifdef HARDWAVE_AD
      Sv39Pte newPte = {.val = pte.val};
      newPte.a = true;
      if (type == MEM_TYPE_WRITE)
        newPte.d = true;
      paddr_write(base, 8, newPte.val);
#undef HARDWAVE_AD
#else
      MMU_ASSERT(false, "a == 0 || (d == 0 && type == %d) %lx", type, pte.val);
#endif
    }

    /* Step8: return paddr */
    return (pte.ppn << 12) | BITS(vaddr, 11 + 9 * pageLv, 0);
  }

mmuFail : {
  word_t num = type == MEM_TYPE_IFETCH ? EC_InstrPageFault
               : type == MEM_TYPE_READ ? EC_LoadPageFault
                                       : EC_StorePageFault;
  isa_raise_intr(num, vaddr);
  return 0;
}
}
#undef MMU_ASSERT

bool isa_mmu_success() { return errstr[0] == '\0'; }
const char *isa_mmu_errorInfo() { return errstr; }
