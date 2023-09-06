#include "local-include/FmtOuter.h"
#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <limits.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

int printf(const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  out_t outer = {
      .limit = INT_MAX,
      .ptr = 0,
      .start = NULL,
      .type = ter,
  };
  int res = fmtprint(&outer, fmt, ap);
  return res;
}

int vsprintf(char *out, const char *fmt, va_list ap) {
  return vsnprintf(out, SIZE_MAX, fmt, ap);
}

int sprintf(char *out, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  int res = vsprintf(out, fmt, ap);
  va_end(ap);
  return res;
}

int snprintf(char *out, size_t n, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  int res = vsnprintf(out, n, fmt, ap);
  va_end(ap);
  return res;
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap) {
  out_t outer = {
      .limit = n,
      .ptr = 0,
      .start = out,
      .type = str,
  };
  return fmtprint(&outer, fmt, ap);
}

#endif
