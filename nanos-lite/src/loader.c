#include <proc.h>
#include <elf.h>

#include "am.h"
#include "fs.h"
#include "memory.h"
#ifdef __LP64__
#define Elf_Ehdr Elf64_Ehdr
#define Elf_Phdr Elf64_Phdr
#define Elf_Off Elf64_Off
#define Elf_Shdr Elf64_Shdr
#define Elf_Sym Elf64_Sym
#define ELF_ST_TYPE ELF64_ST_TYPE
#else
#define Elf_Ehdr Elf32_Ehdr
#define Elf_Phdr Elf32_Phdr
#define Elf_Off Elf32_Off
#define Elf_Shdr Elf32_Shdr
#define Elf_Sym Elf32_Sym
#define ELF_ST_TYPE ELF32_ST_TYPE
#endif

extern size_t get_ramdisk_size();
extern size_t ramdisk_write(const void *buf, size_t offset, size_t len);
extern size_t ramdisk_read(void *buf, size_t offset, size_t len);
static void *align_down(void *ptr, size_t alignment) {
  uintptr_t addr = (uintptr_t)ptr;
  uintptr_t aligned_address = addr & ~(alignment - 1);
  return (void *)aligned_address;
}
void *align_up(void *ptr, size_t alignment) {
  uintptr_t addr = (uintptr_t)ptr;
  uintptr_t alignedAddr = (addr + alignment - 1) & ~(alignment - 1);
  return (void *)alignedAddr;
}
static uintptr_t findEnd(Elf_Ehdr ehdr, int fd) {
  Elf_Shdr symtabShdr;
  for (size_t i = 0; i < ehdr.e_shnum; i++) {
    fs_lseek(fd, ehdr.e_shoff + ehdr.e_shentsize * i, SEEK_SET);
    if (fs_read(fd, &symtabShdr, sizeof(Elf_Shdr)) != sizeof(Elf_Shdr)) {
      return 0;
    }
    if (symtabShdr.sh_type == SHT_SYMTAB) {
      fs_lseek(fd, ehdr.e_shoff + (ehdr.e_shentsize * symtabShdr.sh_link),
               SEEK_SET);
      Elf_Shdr strtabShdr;
      if (fs_read(fd, &strtabShdr, sizeof(Elf_Shdr)) != sizeof(Elf_Shdr)) {
        return 0;
      }
      // 在符号表中查找 _end 符号
      size_t symNR = symtabShdr.sh_size / symtabShdr.sh_entsize;
      Elf_Sym sym;
      fs_lseek(fd, symtabShdr.sh_offset, SEEK_SET);
      for (size_t i = 0; i < symNR; ++i) {
        fs_lseek(fd, symtabShdr.sh_offset + i * sizeof(sym), SEEK_SET);
        if (fs_read(fd, &sym, sizeof(Elf_Sym)) != sizeof(sym)) {
          return 0;
        }
        if (ELF32_ST_TYPE(sym.st_info) != STT_NOTYPE)
          continue;
        char symStr[8];
        fs_lseek(fd, strtabShdr.sh_offset + sym.st_name, SEEK_SET);
        fs_read(fd, &symStr, 5);
        if (strncmp(symStr, "_end", 5) == 0) {
          return sym.st_value;
        }
      }
    }
  }
  return 0;
}
static uintptr_t loader(PCB *pcb, const char *filename) {
  int fd = fs_open(filename, 0, 0);

  /* read elf header */
  Elf_Ehdr ehdr;
  fs_read(fd, &ehdr, sizeof(Elf_Ehdr));
  uint8_t magicNum[4] = {0x7f, 'E', 'L', 'F'};
  assert(*(uint32_t *)&ehdr.e_ident == *(uint32_t *)&magicNum);

  /* read program header */
  Elf_Phdr phdr;
  assert(ehdr.e_phoff != 0);
  for (size_t i = 0; i < ehdr.e_phnum; i++) {
    fs_lseek(fd, ehdr.e_phoff + i * ehdr.e_phentsize, SEEK_SET);
    fs_read(fd, &phdr, sizeof(Elf_Phdr));
    if (phdr.p_type != PT_LOAD)
      continue;

    void *pgBot = align_down((void *)phdr.p_vaddr, PGSIZE);
    void *pgTop = align_up((void *)(phdr.p_vaddr + phdr.p_memsz), PGSIZE);
    assert((pgTop - pgBot) % PGSIZE == 0);
    int pageNum = (pgTop - pgBot) / PGSIZE;
    void *basePAddr = new_page(pageNum);
    for (size_t j = 0; j < pageNum; j++) {
      map(&pcb->as, pgBot + j * PGSIZE, basePAddr + j * PGSIZE,
          MMAP_READ | MMAP_WRITE);
    }
    fs_lseek(fd, phdr.p_offset, SEEK_SET);

    /* phdr.p_vaddr may not align */
    uintptr_t paddrOff = (uintptr_t)basePAddr | (phdr.p_vaddr & PG_OFFSET_MASK);
    fs_read(fd, (void *)paddrOff, phdr.p_filesz);
    memset((void *)(paddrOff + phdr.p_filesz), 0, phdr.p_memsz - phdr.p_filesz);
  }
  pcb->max_brk = (uintptr_t)align_up((void *)findEnd(ehdr, fd), PGSIZE);
  fs_close(fd);
  return ehdr.e_entry;
}

void naive_uload(PCB *pcb, const char *filename) {
  uintptr_t entry = loader(pcb, filename);
  Log("Jump to entry = %p", (void *)entry);
  ((void(*)())entry) ();
}

static char *strcpyd(char *dst, const char *src) {
  int len = strlen(src);
  for (size_t i = 0; i < len; i++) {
    dst[i - len] = src[i];
  }
  dst[0] = '\0';
  return dst - len;
}

#define PushArr(sp, arr)                                                       \
  int arr##c = 0;                                                              \
  do {                                                                         \
    if (arr)                                                                   \
      while (arr[arr##c])                                                      \
        arr##c++;                                                              \
  } while (0);                                                                 \
  char *arr##d[arr##c + 1];                                                    \
  do {                                                                         \
    char *ptr = sp - 1;                                                        \
    for (int i = 0; i < arr##c; i++) {                                         \
      arr##d[i] = strcpyd(ptr, arr[i]);                                        \
      ptr = arr##d[i] - 1;                                                     \
    }                                                                          \
    sp = ptr;                                                                  \
    arr##d[arr##c] = NULL;                                                     \
  } while (0)

static void *putArgsEnvp(void *sp, char *const argv[], char *const envp[]) {
  PushArr(sp, argv);
  PushArr(sp, envp);
  void *ptr = align_down(sp, 2 * _Alignof(void *));
  if ((sizeof(envpd) + sizeof(argvd) + sizeof(void *)) % 16)
    ptr -= sizeof(void *);
  ptr = memcpy(ptr - sizeof(envpd), envpd, sizeof(envpd));
  ptr = memcpy(ptr - sizeof(argvd), argvd, sizeof(argvd));
  uintptr_t *argcPtr = ptr - sizeof(uintptr_t);
  *argcPtr = argvc;
  return argcPtr;
}

void context_uload_stack(Area stack, PCB *pcb, const char *fileName,
                         char *const argv[], char *const envp[]) {
  uintptr_t entry = loader(pcb, fileName);
  Context *ctx = ucontext(NULL, stack, (void *)entry);
  ctx->GPRx = (uintptr_t)putArgsEnvp(heap.end, argv, envp);
  pcb->cp = ctx;
}

void context_uload(PCB *pcb, const char *fileName, char *const argv[],
                   char *const envp[]) {
  /* copy kernel map */
  protect(&pcb->as);

  /* load elf to mem, write pcb->as */
  uintptr_t entry = loader(pcb, fileName);

  /* set start context into pcb's stack point */
  Area pcbStack = {.end = pcb->stack + STACK_SIZE, .start = pcb->stack};
  Context *ctx = ucontext(&pcb->as, pcbStack, (void *)entry);

  /* alloc user stack address, va and pa is page align, put argv and envp */
  Area vStack = RANGE(pcb->as.area.end - STACK_SIZE, pcb->as.area.end);
  size_t stackPageNum = STACK_SIZE / PGSIZE;
  void *pStackBot = new_page(stackPageNum);
  Area pStack = RANGE(pStackBot, pStackBot + STACK_SIZE);
  mapPages(&pcb->as, vStack.start, pStack.start, stackPageNum,
           MMAP_READ | MMAP_WRITE);
  }
  ctx->GPRx = (uintptr_t)uspVa |
              ((uintptr_t)putArgsEnvp(uspPa + STACK_SIZE, argv, envp) & 0xfff);
  pcb->cp = ctx;
}

int execCall(const char *pathname, char *const argv[], char *const envp[]) {
  /* new user stack */
  void *stackTop = new_page(8);
  Area sArea = {.end = stackTop + STACK_SIZE, .start = stackTop};

  /* construct user context with specificed stack in `current` pcb */
  context_uload_stack(sArea, current, pathname, argv, envp);

  /* proc switch */
  void switch_boot_pcb();
  switch_boot_pcb();
  yield();

  panic("never arrive here");
}
