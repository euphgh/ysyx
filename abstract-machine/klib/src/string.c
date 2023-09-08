#include <klib-macros.h>
#include <klib.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

size_t strlen(const char *s) {
  register const char *p = s;
  for (; *p; p++)
    ;
  return p - s;
}

char *strcpy(char *dst, const char *src) {
  char *res = dst;
  while (*src != 0) {
    *dst = *src;
    dst++;
    src++;
  };
  *dst = 0;
  return res;
}

char *strncpy(char *dst, const char *src, size_t n) {
  size_t i;
  for (i = 0; i < n && src[i] != '\0'; i++)
    dst[i] = src[i];
  for (; i < n; i++)
    dst[i] = '\0';
  return dst;
}

char *strcat(char *dst, const char *src) {
  char *s = dst;
  strcpy(s + strlen(s), src);
  return dst;
}

int strcmp(const char *s1, const char *s2) {
  while ((*s1 == *s2) && *s2 && *s1) {
    s1++;
    s2++;
  }
  return *(unsigned char *)s1 - *(unsigned char *)s2;
}

int strncmp(const char *s1, const char *s2, size_t n) {
  int res = 0;
  do {
    if (n-- == 0)
      break;
    res = *(unsigned char *)s1 - *(unsigned char *)s2;
  } while (res == 0 && *s1++ && *s2++);
  return res;
}

void *memset(void *s, int c, size_t n) {
  unsigned char *p = (unsigned char *)s;
  while (n--) {
    *p = (unsigned char)c;
    p++;
  }
  return s;
}

void *memmove(void *dst, const void *src, size_t n) {
  unsigned char *dchr = dst;
  const unsigned char *schr = src;
  if (dchr == schr)
    return dchr;
  if (schr >= dchr) {
    while (n--)
      *dchr++ = *schr++;
  } else {
    while (n--)
      dchr[n] = schr[n];
  }
  return dst;
}

void *memcpy(void *out, const void *in, size_t n) {
  unsigned char *dst = out;
  const unsigned char *src = in;
  while (n--) {
    *dst = *src;
    src++;
    dst++;
  }
  return out;
}

int memcmp(const void *s1, const void *s2, size_t n) {
  int res = 0;
  do {
    if (n-- == 0)
      break;
    res = *(unsigned char *)s1 - *(unsigned char *)s2;
    s1++;
    s2++;
  } while (res == 0);
  return res;
}

#endif
