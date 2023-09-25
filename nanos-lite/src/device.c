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
  yield();
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
  yield();
  AM_INPUT_KEYBRD_T krd;
  ioe_read(AM_INPUT_KEYBRD, &krd);
  if (krd.keycode != AM_KEY_NONE) {
    return snprintf(buf, len, krd.keydown ? "kd %s\n" : "ku %s\n",
                    keyname[krd.keycode]);
  } else {
    return 0;
  }
}

static char dispInfo[64];
static int dispWidth, dispHeight;
int dispinfo_init(int width, int height) {
  dispWidth = width;
  dispHeight = height;
  return sprintf(dispInfo, "WIDTH:%d\nHEIGHT:%d\n", width, height);
}

size_t dispinfo_read(void *buf, size_t offset, size_t len) {
  strncpy(buf, dispInfo + offset, len);
  return len;
}

size_t fb_write(const void *buf, size_t offset, size_t len) {
  yield();
  AM_GPU_FBDRAW_T fb;

  assert(len % 4 == 0);
  assert(offset % 4 == 0);

  size_t leftP = len / 4;
  size_t nextP = offset / 4;
  uint8_t *srcPtr = (uint8_t *)buf;
  int startX, startY, width;

  /* draw pixels */
  do {
    startX = (nextP % dispWidth);
    startY = (nextP / dispWidth);
    width = leftP < (dispWidth - startX) ? leftP : (dispWidth - startX);
    fb.x = startX;
    fb.y = startY;
    fb.w = width;
    fb.h = 1;
    fb.pixels = srcPtr;
    ioe_write(AM_GPU_FBDRAW, &fb);
    srcPtr += width * 4;
    leftP -= width;
    nextP += width;
  } while (leftP);

  /* flush */
  fb.h = 0;
  fb.w = 0;
  fb.sync = true;
  ioe_write(AM_GPU_FBDRAW, &fb);
  return len;
}

void init_device() {
  Log("Initializing devices...");
  ioe_init();
}
