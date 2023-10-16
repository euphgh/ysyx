#include "debug.h"
#include "proc.h"
#include <memory.h>

static void *pf = NULL;

void* new_page(size_t nr_page) {
  void *this = pf;
  pf += nr_page * PGSIZE;
  memset(this, 0, nr_page * PGSIZE);
  return this;
}

void mapPages(AddrSpace *as, void *alignVa, void *alignPa, size_t nr,
              int prot) {
  assert((uintptr_t)alignVa % PGSIZE == 0);
  assert((uintptr_t)alignPa % PGSIZE == 0);
  for (size_t i = 0; i < nr; i++) {
    map(as, alignVa + i * PGSIZE, alignPa + i * PGSIZE, prot);
  }
}

#ifdef HAS_VME
static void *pg_alloc(int n) { return new_page(n / PGSIZE); }
#endif

void free_page(void *p) { Log("free page %p", p); }

/* The brk() system call handler. */
int mm_brk(uintptr_t brk) {
  /* max_brk must be page align */
  if (current->max_brk < brk) {
    size_t pageNum = (brk - current->max_brk) / PGSIZE;
    void *paddr = new_page(pageNum);
    mapPages(&current->as, (void *)current->max_brk, paddr, pageNum,
             MMAP_READ | MMAP_WRITE);
  }
  return 0;
}

void init_mm() {
  pf = (void *)ROUNDUP(heap.start, PGSIZE);
  Log("free physical pages starting from %p", pf);

#ifdef HAS_VME
  vme_init(pg_alloc, free_page);
#endif
}
