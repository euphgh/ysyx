#include "local-include/FmtOuter.h"
#include "local-include/FmtType.h"
#include <klib-macros.h>
#include <stdint.h>

static void outOneChar(const char chr, out_t *outer) {
  if (outer->ptr + 1 < outer->limit) {
    if (outer->type == str)
      outer->start[outer->ptr] = chr;
    else
      putch(chr);
    outer->ptr++;
  }
}

static void closeOuter(out_t *outer) {
  if (outer->type == str) {
    outer->start[outer->ptr++] = '\0';
  }
}

static int outHexX(uint64_t var, out_t *outer, fmt_t *fmter) {
  panic("Not Implement");
}
static int outDecD(int64_t var, out_t *outer, fmt_t *fmter) {
  int len = 0;
  char buf[24];
  while (var != 0) {
    *buf = var % 10;
    var /= 10;
    len++;
  }
  for (int i = 1; i <= len; i++) {
    outOneChar(buf[len - i], outer);
  }
  return len;
}
static int outDecU(uint64_t var, out_t *outer, fmt_t *fmter) {
  panic("Not Implement");
}
static int outStrS(const char *str, out_t *outer, fmt_t *fmter) {
  int len = strlen(str);
  for (int i = 0; i < len; i++)
    outOneChar(str[i], outer);
  return len;
}
static int outChrC(const char chr, out_t *outer, fmt_t *fmter) {
  panic("Not Implement");
}

int fmtnprint(out_t *outer, const char *fmt, va_list ap) {
  fmt_t fmter;
  int cnt = 0;
  while (*fmt) {
    if (*fmt == '%') {
      fmt = parseFmt(++fmt, &fmter);
      switch (fmter.conSpec) {
      case X_HEX:
        cnt += outHexX(va_arg(ap, uint64_t), outer, &fmter);
        break;
      case D_DEC:
        cnt += outDecD(va_arg(ap, int64_t), outer, &fmter);
        break;
      case U_DEC:
        cnt += outDecU(va_arg(ap, uint64_t), outer, &fmter);
        break;
      case S_STR:
        cnt += outStrS(va_arg(ap, const char *), outer, &fmter);
        break;
      case C_CHR: {
        int chr = va_arg(ap, int);
        cnt += outChrC((char)chr, outer, &fmter);
        break;
      }
      default:
        panic("Unexpected fmt char in fmt print");
      }
    } else {
      outOneChar(*fmt++, outer);
      cnt++;
    }
  }
  closeOuter(outer);
  return cnt;
}
