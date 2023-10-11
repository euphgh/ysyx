#include "klib-macros.h"

#include <am.h>
#include <riscv/riscv.h>
#include <klib.h>

static Context* (*user_handler)(Event, Context*) = NULL;

Context* __am_irq_handle(Context *c) {
  if (user_handler) {
    Event ev = {0};
    switch (c->mcause) {
    case EC_EnvCallFromM:
      ev.event = EVENT_SYSCALL;
      c->mepc = c->mepc + 4;
      if (c->gpr[17] == -1)
        ev.event = EVENT_YIELD;
      break;
    case EC_InstrAddrMisAlign:
      panic("EC_InstrAddrMisAlign");
      break;
    case EC_LoadAddrMisAlign:
      panic("EC_LoadAddrMisAlign");
      break;
    case EC_StoreAddrMisAlign:
      panic("EC_StoreAddrMisAlign");
      break;
    default:
      ev.event = EVENT_ERROR;
      break;
    }

    c = user_handler(ev, c);
    assert(c != NULL);
  }

  return c; // must is a stack point, pointing a Context
  /*
   x31 high addr
  ...
   x2
   x1
c->x0  low addr
  */
}

extern void __am_asm_trap(void);

bool cte_init(Context*(*handler)(Event, Context*)) {
  // initialize exception entry
  asm volatile("csrw mtvec, %0" : : "r"(__am_asm_trap));

  // register event handler
  user_handler = handler;

  return true;
}

Context *kcontext(Area kstack, void (*entry)(void *), void *arg) {
  void *ctxStart = kstack.end - sizeof(Context);
  Context *ctx = (Context *)ctxStart;
  ctx->GPRx = (uintptr_t)arg;
  ctx->mepc = (uintptr_t)entry;
  ctx->mcause = (uintptr_t)0x0;
  ctx->mstatus = (uintptr_t)0xa00001800;
  return ctx;
}

void yield() {
#ifdef __riscv_e
  asm volatile("li a5, -1; ecall");
#else
  asm volatile("li a7, -1; ecall");
#endif
}

bool ienabled() {
  return false;
}

void iset(bool enable) {
}
