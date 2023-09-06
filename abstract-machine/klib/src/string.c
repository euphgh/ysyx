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
  while (*src != 0) {
    *dst = *src;
    dst++;
    src++;
  };
  return dst;
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
  /* may not !!!*/
  return strcpy(dst + strlen(dst), src);
}

int strcmp(const char *s1, const char *s2) {
  while ((*s1 != *s2) && *s2 && *s1) {
    s1++;
    s2++;
  }
  return *(unsigned char *)s1 - *(unsigned char *)s2;
}

int strncmp(const char *s1, const char *s2, size_t n) {
  for (size_t i = 0; i < n; i++) {
    if (s1[i] != s2[i] || s1[i] == 0 || s2[i] == 0)
      break;
  }
  return *(unsigned char *)s1 - *(unsigned char *)s2;
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
  const unsigned char *l = s1, *r = s2;
  while (*l != *r && n--) {
    l++;
    r++;
  }
  return *(l--) - *(r--);
}

#endif
