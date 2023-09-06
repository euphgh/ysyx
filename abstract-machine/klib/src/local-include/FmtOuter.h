#ifndef _KLIB_LOCAL_INCLUDE_FMT_OUTER_H__
#define _KLIB_LOCAL_INCLUDE_FMT_OUTER_H__
#include "klib.h"

typedef struct {
  enum { str, ter } type;
  char *start;
  int ptr;
  int limit;
} out_t;
int fmtprint(out_t *outer, const char *fmt, va_list ap);

#endif