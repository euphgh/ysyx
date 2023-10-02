#include <proc.h>
#include <elf.h>

#include "am.h"
#include "fs.h"
#include "memory.h"
#ifdef __LP64__
# define Elf_Ehdr Elf64_Ehdr
# define Elf_Phdr Elf64_Phdr
#else
# define Elf_Ehdr Elf32_Ehdr
# define Elf_Phdr Elf32_Phdr
#endif

extern size_t get_ramdisk_size();
extern size_t ramdisk_write(const void *buf, size_t offset, size_t len);
extern size_t ramdisk_read(void *buf, size_t offset, size_t len);

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

    fs_lseek(fd, phdr.p_offset, SEEK_SET);
    fs_read(fd, (void *)phdr.p_vaddr, phdr.p_filesz);
    memset((void *)(phdr.p_vaddr + phdr.p_filesz), 0,
           phdr.p_memsz - phdr.p_filesz);
  }
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

static void *align_down(void *ptr, size_t alignment) {
  uintptr_t address = (uintptr_t)ptr;
  uintptr_t aligned_address = address & ~(alignment - 1);
  return (void *)aligned_address;
}

#define PushArr(sp, arr)                                                       \
  int arr##c = 0;                                                              \
  do {                                                                         \
    while (arr[arr##c])                                                        \
      arr##c++;                                                                \
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

void context_uload(PCB *pcb, const char *fileName, char *const argv[],
                   char *const envp[]) {
  uintptr_t entry = loader(pcb, fileName);
  Area sArea = {.end = pcb->stack + STACK_SIZE, .start = pcb->stack};
  Context *ctx = ucontext(NULL, sArea, (void *)entry);
  void *sp = heap.end;
  PushArr(sp, argv);
  PushArr(sp, envp);
  void *ptr = align_down(sp, _Alignof(char *));
  ptr = memcpy(ptr - sizeof(envpd), envpd, sizeof(envpd));
  ptr = memcpy(ptr - sizeof(argvd), argvd, sizeof(argvd));
  int *argcPtr = ptr - sizeof(int);
  *argcPtr = argvc;
  ctx->GPRx = (uintptr_t)argcPtr;
  pcb->cp = ctx;
}

int execCall(const char *pathname, char *const argv[], char *const envp[]) {
  PCB *pcb = current;
  uintptr_t entry = loader(pcb, pathname);
  Area sArea = {.end = pcb->stack + STACK_SIZE, .start = pcb->stack};
  Context *ctx = ucontext(NULL, sArea, (void *)entry);
  void *sp = heap.end;
  PushArr(sp, argv);
  PushArr(sp, envp);
  void *ptr = align_down(sp, _Alignof(char *));
  ptr = memcpy(ptr - sizeof(envpd), envpd, sizeof(envpd));
  ptr = memcpy(ptr - sizeof(argvd), argvd, sizeof(argvd));
  int *argcPtr = ptr - sizeof(int);
  *argcPtr = argvc;
  ctx->GPRx = (uintptr_t)argcPtr;
  pcb->cp = ctx;

  yield();
  return 0;
}
