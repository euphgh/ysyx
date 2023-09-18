#include "am.h"
#include "debug.h"
#include "klib-macros.h"
#include <common.h>
#include <stddef.h>

#if defined(MULTIPROGRAM) && !defined(TIME_SHARING)
# define MULTIPROGRAM_YIELD() yield()
#else
# define MULTIPROGRAM_YIELD()
#endif

#define NAME(key) \
  [AM_KEY_##key] = #key,

static const char *keyname[256] __attribute__((used)) = {
  [AM_KEY_NONE] = "NONE",
  AM_KEYS(NAME)
};

size_t serial_write(const void *buf, size_t offset, size_t len) {
  for (size_t i = 0; i < len; i++)
    putch(((const char *)buf)[i]);
  return len;
}

void uptimer_read(size_t *sec, size_t *us) {
  AM_TIMER_UPTIME_T timer;
  ioe_read(AM_TIMER_UPTIME, &timer);
  *sec = timer.us / 1000000;
  *us = timer.us % 1000000;
}

size_t events_read(void *buf, size_t offset, size_t len) {
  return 0;
}

size_t dispinfo_read(void *buf, size_t offset, size_t len) {
  return 0;
}

size_t fb_write(const void *buf, size_t offset, size_t len) {
  return 0;
}

void init_device() {
  Log("Initializing devices...");
  ioe_init();
}
