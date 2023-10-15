#include "klib-macros.h"

#include <am.h>
#include <riscv/riscv.h>
#include <klib.h>

extern void __am_get_cur_as(Context *c);
extern void __am_switch(Context *c);
static Context* (*user_handler)(Event, Context*) = NULL;

Context* __am_irq_handle(Context *c) {
  /* save address space */
  __am_get_cur_as(c);

  if (user_handler) {
    Event ev = {0};
    switch (c->cause) {
    case EC_EnvCallFromM:
    case EC_EnvCallFromS:
    case EC_EnvCallFromU:
      ev.event = EVENT_SYSCALL;
      c->epc = c->epc + 4;
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
  /* switch address space */
  __am_switch(c);
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
extern void __am_asm_strap(void);

bool cte_init(Context*(*handler)(Event, Context*)) {
  // initialize exception entry
  asm volatile("csrw mtvec, %0;\
                csrw stvec, %1;\
                "
               :
               : "r"(__am_asm_trap), "r"(__am_asm_strap));

  // register event handler
  user_handler = handler;

  /* set medeleg for ecall in s-mode */
  uintptr_t secall = 1 << 9;
  asm volatile("csrrs zero, medeleg, %0" ::"r"(secall) :);

  /* set medeleg for ecall in u-mode */
  uintptr_t uecall = 1 << 8;
  asm volatile("csrrs zero, medeleg, %0" ::"r"(uecall) :);

  /* use ret to S mode */
  uintptr_t mpp = 1 << 11; // set ret to mode as S
  uintptr_t sum = 1 << 18; // s-mode use u-mode page
  uintptr_t tmp;
  asm volatile("csrrs zero, mstatus, %2; \
                csrrs zero, mstatus, %1; \
                auipc %0, 0x0;\
                addi  %0, %0, 0x10;\
                csrrw zero, mepc, %0; \
                mret;"
               : "=r"(tmp)
               : "r"(mpp), "r"(sum)
               :);

  return true;
}

Context *kcontext(Area kstack, void (*entry)(void *), void *arg) {
  void *ctxStart = kstack.end - sizeof(Context);
  Context *ctx = (Context *)ctxStart;
  ctx->GPRx = (uintptr_t)arg;
  ctx->epc = (uintptr_t)entry;
  ctx->cause = (uintptr_t)0x0;
  ctx->status = (uintptr_t)0x200000100; // init value as sstatus not mstatus
  __am_get_cur_as(ctx);
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
