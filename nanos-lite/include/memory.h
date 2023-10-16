#ifndef __MEMORY_H__
#define __MEMORY_H__

#include <common.h>

#ifndef PGSIZE
#define PGSIZE 4096
#define PG_OFFSET_MASK (PGSIZE - 1)
#endif

#define PG_ALIGN __attribute((aligned(PGSIZE)))

void* new_page(size_t);
void mapPages(AddrSpace *as, void *alignVa, void *alignPa, size_t nr, int prot);

#endif
