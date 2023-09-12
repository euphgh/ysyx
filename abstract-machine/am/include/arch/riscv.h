#ifndef ARCH_H__
#define ARCH_H__

#ifdef __riscv_e
#define NR_REGS 16
#else
#define NR_REGS 32
#endif

struct Context {
  uintptr_t gpr[NR_REGS], mcause, mstatus, mepc;
};

#define DEFEXC(int, exc) ((int << (64 - 1)) | exc)

typedef enum {
  EC_SSoftInt = DEFEXC(1, 1),
  EC_MSoftInt = DEFEXC(1, 3),
  EC_STimeInt = DEFEXC(1, 5),
  EC_MTimeInt = DEFEXC(1, 7),
  EC_SExternInt = DEFEXC(1, 9),
  EC_MExternInt = DEFEXC(1, 11),
  EC_InstrAddrMisAlign = DEFEXC(0, 0),
  EC_InstrAccessFault = DEFEXC(0, 1),
  EC_IllegalInstr = DEFEXC(0, 2),
  EC_BreakPoint = DEFEXC(0, 3),
  EC_LoadAddrMisAlign = DEFEXC(0, 4),
  EC_LoadAccessFault = DEFEXC(0, 5),
  EC_StoreAddrMisAlign = DEFEXC(0, 6),
  EC_StoreAccessFault = DEFEXC(0, 7),
  EC_EnvCallFromU = DEFEXC(0, 8),
  EC_EnvCallFromS = DEFEXC(0, 9),
  EC_EnvCallFromM = DEFEXC(0, 11),
  EC_InstrPageFault = DEFEXC(0, 12),
  EC_LoadPageFault = DEFEXC(0, 13),
  EC_StorePageFault = DEFEXC(0, 15),
} ExcCode;

#undef DEFEXC

#ifdef __riscv_e
#define GPR1 gpr[15] // a5
#else
#define GPR1 gpr[17] // a7
#endif

#define GPR2 gpr[0]
#define GPR3 gpr[0]
#define GPR4 gpr[0]
#define GPRx gpr[0]

#endif
