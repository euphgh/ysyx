#include "local-include/FmtType.h"

static const char *parseFlagChar(const char *fmt, fmt_t *fmter) {
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
      goto back;
    }
    fmt++;
  }
#undef CaseAttr
back:
  return fmt;
}

static const char *parseFieldWidth(const char *fmt, fmt_t *fmter) {
  fmter->fieldWidth = 0;
  while (*fmt >= '0' && *fmt <= '9') {
    fmter->fieldWidth *= 10;
    fmter->fieldWidth += *fmt - 48;
    fmt++;
  }
  return fmt;
}

static const char *parseLengthModifier(const char *fmt, fmt_t *fmter) {
  fmter->length = LM_NON;
  while (*fmt) {
    if (*fmt == 'h') {
      if (fmter->length == LM_CHAR)
        fmter->length = LW_SHORT;
      else if (fmter->length == LM_NON)
        fmter->length = LM_CHAR;
      else
        goto back;
    } else if (*fmt == 'l') {
      if (fmter->length == LM_LONG)
        fmter->length = LM_LONGLONG;
      else if (fmter->length == LM_NON)
        fmter->length = LM_LONG;
      else
        goto back;
    } else
      goto back;
    fmt++;
  }
back:
  return fmt;
}

static const char *parseConversionSpecifiers(const char *fmt, fmt_t *fmter) {
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
    CaseAttr('s', S_STR);
  default:
    return fmt;
  }
  return fmt + 1;
}

const char *parseFmt(const char *fmt, fmt_t *fmter) {
  const char *ptr = fmt;
  ptr = parseFlagChar(ptr, fmter);
  ptr = parseFieldWidth(ptr, fmter);
  ptr = parseLengthModifier(ptr, fmter);
  ptr = parseConversionSpecifiers(ptr, fmter);
  return ptr;
}