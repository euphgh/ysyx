#ifndef __RISCV_CSR_H__
#define __RISCV_CSR_H__
#include "common.h"

typedef enum {
  csrSET,
  csrCLR,
  csrWAR,
} csrOp;

#define DEFEXC(int, exc) ((int << (XLEN - 1)) | exc)

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

#include "csrDefine.h"
#endif