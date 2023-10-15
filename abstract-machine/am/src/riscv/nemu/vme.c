#include <am.h>
#include <nemu.h>
#include <klib.h>

static AddrSpace kas = {};
static void* (*pgalloc_usr)(int) = NULL;
static void (*pgfree_usr)(void*) = NULL;
static int vme_enable = 0;

static Area segments[] = {      // Kernel memory mappings
  NEMU_PADDR_SPACE
};

#define USER_SPACE RANGE(0x40000000, 0x80000000)

static inline void set_satp(void *pdir) {
  uintptr_t mode = 1ul << (__riscv_xlen - 1);
  asm volatile("csrw satp, %0" : : "r"(mode | ((uintptr_t)pdir >> 12)));
}
static inline void valid_satp() { asm volatile("sfence.vma" : : :); }
static inline void privMtoS() {
  uintptr_t setMstatusMPP = 1 << 11;
  asm volatile("csrrs zero, mstatus;  \
                mret; "
               :
               : "r"(setMstatusMPP)
               :);
  asm volatile("sfence.vme" : : :);
}

static inline uintptr_t get_satp() {
  uintptr_t satp;
  asm volatile("csrr %0, satp" : "=r"(satp));
  return satp << 12;
}

typedef union {
  struct {
    uintptr_t v : 1;
    uintptr_t r : 1;
    uintptr_t w : 1;
    uintptr_t x : 1;
    uintptr_t u : 1;
    uintptr_t g : 1;
    uintptr_t a : 1;
    uintptr_t d : 1;
    uintptr_t rsw : 2;
    uintptr_t ppn : 44;
    uintptr_t reserved : 10;
  };
  uintptr_t val;
} Sv39Pte;
void writePTE(AddrSpace *as, void *va, void *pa, Sv39Pte tempePte);
bool vme_init(void* (*pgalloc_f)(int), void (*pgfree_f)(void*)) {
  pgalloc_usr = pgalloc_f;
  pgfree_usr = pgfree_f;

  kas.ptr = pgalloc_f(PGSIZE);
  Sv39Pte temp = {
      .v = true,
      .r = true,
      .w = true,
      .x = true,
      .g = true,
      .u = false,
      .a = true,
      .d = true,
  };
  int i;
  for (i = 0; i < LENGTH(segments); i ++) {
    void *va = segments[i].start;
    for (; va < segments[i].end; va += PGSIZE) {
      writePTE(&kas, va, va, temp);
    }
  }

  set_satp(kas.ptr);
  valid_satp();
  vme_enable = 1;

  return true;
}

void protect(AddrSpace *as) {
  PTE *updir = (PTE*)(pgalloc_usr(PGSIZE));
  as->ptr = updir;
  as->area = USER_SPACE;
  as->pgsize = PGSIZE;
  // map kernel space
  memcpy(updir, kas.ptr, PGSIZE);
}

void unprotect(AddrSpace *as) {
  /* write priority to zero */
  pgfree_usr(as->ptr);
}

void __am_get_cur_as(Context *c) {
  c->pdir = (vme_enable ? (void *)get_satp() : NULL);
}

void __am_switch(Context *c) {
  if (vme_enable && c->pdir != NULL) {
    set_satp(c->pdir);
    valid_satp();
  }
}

typedef union {
  struct {
    uintptr_t offset : 12;
    uintptr_t vpn : 27;
    uintptr_t ext : 25;
  };
  uintptr_t val;
} Sv39Vaddr;

#define BITMASK(bits) ((1ull << (bits)) - 1)
#define BITS(x, hi, lo)                                                        \
  (((x) >> (lo)) & BITMASK((hi) - (lo) + 1)) // similar to x[hi:lo] in verilog
inline static uintptr_t ppn(Sv39Pte pte, int lv) {
  int msb[] = {18, 27, 53};
  return BITS(pte.val, msb[lv], 10 + lv * 9);
}
inline static uintptr_t vpn(Sv39Vaddr va, int lv) {
  return BITS(va.vpn, lv * 9 + 8, lv * 9);
}

void writePTE(AddrSpace *as, void *va, void *pa, Sv39Pte template) {
  Sv39Vaddr sv39va = {
      .val = (uintptr_t)va,
  };
  uintptr_t base = (uintptr_t)as->ptr;
  for (int lv = 2; lv > 0; lv--) {
    Sv39Pte *pte = (Sv39Pte *)(base + vpn(sv39va, lv) * 8);
    if (pte->val == 0) {
      PTE *nextLv = (PTE *)(pgalloc_usr(PGSIZE));
      pte->val = 0;
      pte->v = true;
      pte->ppn = ((uintptr_t)nextLv >> 12);
    }
    base = (uintptr_t)pte->ppn << 12;
  }
  Sv39Pte *pteAddr = (Sv39Pte *)(base + vpn(sv39va, 0) * 8);
  *pteAddr = template;
  pteAddr->ppn = (uintptr_t)pa >> 12;
}

void map(AddrSpace *as, void *va, void *pa, int prot) {
  Sv39Pte temp = {
      .v = true,
      .r = true,
      .w = true,
      .x = true,
      .g = true,
      .u = true,
      .a = true,
      .d = true,
  };
  writePTE(as, va, pa, temp);
}

Context *ucontext(AddrSpace *as, Area kstack, void *entry) {
  void *ctxStart = kstack.end - sizeof(Context);
  Context *ctx = (Context *)ctxStart;
  ctx->epc = (uintptr_t)entry;
  ctx->cause = (uintptr_t)0x0;
  ctx->status = (uintptr_t)0x200040000; // ret to u mode, with status.sum set
  ctx->pdir = as->ptr;
  return ctx;
}
