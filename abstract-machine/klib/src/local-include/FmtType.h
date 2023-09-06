#ifndef _KLIB_LOCAL_INCLUDE_FMT_TYPE_H__
#define _KLIB_LOCAL_INCLUDE_FMT_TYPE_H__
#include "klib.h"

typedef struct {
  enum { X_HEX, D_DEC, U_DEC, S_STR, C_CHR, P_PTR, N_ERR } conSpec;
  enum { LM_CHAR, LW_SHORT, LM_INT, LM_LONG, LM_LONGLONG, LM_NON } length;
  int fieldWidth;
  bool alternate;
  bool zeroPadded;
  bool leftAdjust;
  bool blankPos;
  bool forceSign;
} fmt_t;
void parseFmt(const char *fmt, fmt_t *fmter);

#endif