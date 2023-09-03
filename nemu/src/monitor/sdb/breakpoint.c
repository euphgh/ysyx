#include "adt/ArrayLink.h"
#include "isa.h"
#include "sdb.h"

#define AL_ARR_NR 16
#define AL_ATTR(_)                                                             \
  _(vaddr_t, vaddrs)                                                           \
  _(char, exprStr[128])

AL_DECLARE(Attr)

void initBP() { AL_INIT; }

void deleteBP(int num) {
  if (AL_NUM2POS(num) != -1) {
    AL_FREE_NUM(num, i, {
      vaddrs[i] = vaddrs[i + 1];
      strcpy(exprStr[i], exprStr[i + 1]);
    });
  } else
    error("Not Found breakpoint %d", num);
}

bool insertBP(const char *e) {
  bool success = true;
  int num = AL_ALLOC_NUM;
  if (num < 0) {
    error("Fail to insert breakpoint for no space");
    success = false;
  } else {
    if (expr(e, vaddrs + alPtr)) {
      strcpy(exprStr[alPtr], e);
      alPtr++;
    } else {
      error("Fail to insert breakpoint for grammer error");
      success = false;
    }
  }
  return success;
}

void showInfoBP() {
  printf("Num Expr Vaddr\n");
  AL_FOREACH(i, {
    int num = AL_POS2NUM(i);
    Assert(num >= 0, "Not find breakpoint %d name", i);
    printf("%d, %s, " FMT_WORD "\n", num, exprStr[i], vaddrs[i]);
  })
}

bool checkBP() {
  bool success = true;
  AL_FOREACH(i, {
    if (unlikely(cpu.pc == vaddrs[i])) {
      success = false;
      int name = AL_POS2NUM(i);
      Assert(name >= 0, "Not find breakpoint %d name", i);
      printf("breakpoint %d: %s = " FMT_WORD "\n", name, exprStr[i], vaddrs[i]);
    }
  })
  return success;
}

#undef AL_ARR_NR
#undef AL_ATTR