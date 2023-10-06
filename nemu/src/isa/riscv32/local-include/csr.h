#ifndef __RISCV_CSR_H__
#define __RISCV_CSR_H__
#include "common.h"

typedef enum {
  csrSET,
  csrCLR,
  csrWAR,
} csrOp;

typedef enum {
  SatpModeBare = 0,
  SatpModeSv32 = 1,
  SatpModeSv39 = 8,
  SatpModeSv48 = 9,
} SatpMode;

#define InterruptList(_)                                                       \
  _(EC_SSoftInt, 1, "SSoftInt")                                                \
  _(EC_MSoftInt, 3, "MSoftInt")                                                \
  _(EC_STimeInt, 5, "STimeInt")                                                \
  _(EC_MTimeInt, 7, "MTimeInt")                                                \
  _(EC_SExternInt, 9, "SExternInt")                                            \
  _(EC_MExternInt, 11, "MExternInt")

#define ExceptionList(_)                                                       \
  _(EC_InstrAddrMisAlign, 0, "InstrAddrMisAlign")                              \
  _(EC_InstrAccessFault, 1, "InstrAccessFault")                                \
  _(EC_IllegalInstr, 2, "IllegalInstr")                                        \
  _(EC_BreakPoint, 3, "BreakPoint")                                            \
  _(EC_LoadAddrMisAlign, 4, "LoadAddrMisAlign")                                \
  _(EC_LoadAccessFault, 5, "LoadAccessFault")                                  \
  _(EC_StoreAddrMisAlign, 6, "StoreAddrMisAlign")                              \
  _(EC_StoreAccessFault, 7, "StoreAccessFault")                                \
  _(EC_EnvCallFromU, 8, "EnvCallFromU")                                        \
  _(EC_EnvCallFromS, 9, "EnvCallFromS")                                        \
  _(EC_EnvCallFromM, 11, "EnvCallFromM")                                       \
  _(EC_InstrPageFault, 12, "InstrPageFault")                                   \
  _(EC_LoadPageFault, 13, "LoadPageFault")                                     \
  _(EC_StorePageFault, 15, "StorePageFault")

#define IntEnumDef(name, code, str) name = ((1 << (XLEN - 1)) | code),
#define ExpEnumDef(name, code, str) name = code,
typedef enum { InterruptList(IntEnumDef) ExceptionList(ExpEnumDef) } ExcCode;
#undef IntEnumDef
#undef ExpEnumDef

typedef enum { PRI_U = 0x0, PRI_S = 0x1, PRI_RSV = 0x2, PRI_M = 0x3 } Plevel;
extern Plevel machineMode;

#include "csrDefine.h"
#endif