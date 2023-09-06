#include "local-include/FmtType.h"

static void parseFlagChar(const char *fmt, fmt_t *fmter) {
#define CaseAttr(char, field)                                                  \
  case char:                                                                   \
    fmter->field = true;                                                       \
    break
  while (*fmt) {
    switch (*fmt) {
      CaseAttr('#', alternate);
      CaseAttr('0', zeroPadded);
      CaseAttr('-', leftAdjust);
      CaseAttr(' ', leftAdjust);
      CaseAttr('+', forceSign);
    default:
      return;
    }
    fmt++;
  }
#undef CaseAttr
}

static void parseFieldWidth(const char *fmt, fmt_t *fmter) {
  fmter->fieldWidth = 0;
  while (*fmt >= '0' && *fmt <= '9') {
    fmter->fieldWidth *= 10;
    fmter->fieldWidth += *fmt - 48;
    fmt++;
  }
}

static void parseLengthModifier(const char *fmt, fmt_t *fmter) {
  fmter->length = LM_NON;
  while (*fmt) {
    if (*fmt == 'h') {
      if (fmter->length == LM_CHAR)
        fmter->length = LW_SHORT;
      else if (fmter->length == LM_NON)
        fmter->length = LM_CHAR;
      else
        return;
    } else if (*fmt == 'l') {
      if (fmter->length == LM_LONG)
        fmter->length = LM_LONGLONG;
      else if (fmter->length == LM_NON)
        fmter->length = LM_LONG;
      else
        return;
    } else
      return;
    fmt++;
  }
}

static void parseConversionSpecifiers(const char *fmt, fmt_t *fmter) {
#define CaseAttr(chr, type)                                                    \
  case chr:                                                                    \
    fmter->conSpec = type;                                                     \
    break
  fmter->conSpec = N_ERR;
  switch (*fmt) {
    CaseAttr('d', D_DEC);
    CaseAttr('u', U_DEC);
    CaseAttr('x', X_HEX);
    CaseAttr('c', C_CHR);
    CaseAttr('p', P_PTR);
  default:
    break;
  }
}

void parseFmt(const char *fmt, fmt_t *fmter) {
  parseFlagChar(fmt, fmter);
  parseFieldWidth(fmt, fmter);
  parseLengthModifier(fmt, fmter);
  parseConversionSpecifiers(fmt, fmter);
}