#include "syscall.h"
#include "fs.h"
#include <common.h>
#define SYSCALL_DEF_STR(name, str) [name] = str,
static const char *sysCallInfo[32] = {SYSCALL_LIST(SYSCALL_DEF_STR)};
#undef SYSCALL_DEF_STR
extern const char *fs_pathname(int fd);
void do_syscall(Context *c) {
  uintptr_t a[4];
  a[0] = c->GPR1;
  a[1] = c->GPR2;
  a[2] = c->GPR3;
  a[3] = c->GPR4;
  if (a[0] == SYS_close || a[0] == SYS_read || a[0] == SYS_write ||
      a[0] == SYS_lseek || a[0] == SYS_open) {
    Log("%s[%s, %x(%u), %x(%u)]", sysCallInfo[a[0]], fs_pathname(a[1]), a[2],
        a[2], a[3], a[3]);
  } else {
    Log("%s[%x(%u), %x(%u), %x(%u)]", sysCallInfo[a[0]], a[1], a[1], a[2], a[2],
        a[3], a[3]);
  }
  switch (a[0]) {
  case SYS_yield:
    c->GPRx = 0;
    break;
  case SYS_brk:
    c->GPRx = 0;
    break;
  case SYS_exit:
    c->GPRx = a[1];
    halt(a[1]);
    break;
  case SYS_write:
    c->GPRx = fs_write(a[1], (void *)a[2], a[3]);
    break;
  case SYS_read:
    c->GPRx = fs_read(a[1], (void *)a[2], a[3]);
    break;
  case SYS_open:
    c->GPRx = fs_open((const char *)a[1], a[2], a[3]);
    break;
  case SYS_close:
    c->GPRx = fs_close(a[1]);
    break;
  case SYS_lseek:
    c->GPRx = fs_lseek(a[1], a[2], a[3]);
    break;
  default:
    panic("Unhandled syscall ID = %d", a[0]);
  }
}
