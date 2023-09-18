#include <common.h>
#include "syscall.h"
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
    Log("%s(%s, %d, %d)", sysCallInfo[a[0]], fs_pathname(a[1]), a[2], a[3]);
  } else {
    Log("%s(%d, %d, %d)", sysCallInfo[a[0]], a[1], a[2], a[3]);
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
    if (a[1] == 1 || a[1] == 2) {
      for (int i = 0; i < a[3]; i++) {
        putch(((char *)a[2])[i]);
        c->GPRx = a[3];
      }
    } else {
      panic("Unimplement write fd = %d", a[1]);
      c->GPRx = -1;
    }
    break;
  default:
    panic("Unhandled syscall ID = %d", a[0]);
  }
}
