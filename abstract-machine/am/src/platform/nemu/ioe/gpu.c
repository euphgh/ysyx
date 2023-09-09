#include <am.h>
#include <nemu.h>
#include <stdio.h>
#include <string.h>

#define SYNC_ADDR (VGACTL_ADDR + 4)

static int screenWidth, screenHeigh;

void __am_gpu_init() {
  screenHeigh = inw(VGACTL_ADDR);
  screenWidth = inw(VGACTL_ADDR + 2);
  int i;
  int w = screenWidth;
  int h = screenHeigh;
  uint32_t *fb = (uint32_t *)(uintptr_t)FB_ADDR;
  for (i = 0; i < w * h; i++)
    fb[i] = 0;
  fb[300] = 0x0000ff;
  fb[400] = 0x00ff00;
  outl(SYNC_ADDR, 1);
}

void __am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  *cfg = (AM_GPU_CONFIG_T){.present = true,
                           .has_accel = false,
                           .width = screenWidth,
                           .height = screenHeigh,
                           .vmemsz = 0};
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {
  int x = ctl->x, y = ctl->y, w = ctl->w, h = ctl->h;
  uint32_t *dst = (uint32_t *)(uintptr_t)FB_ADDR;
  uint32_t *src = (uint32_t *)ctl->pixels;
  for (int i = 0; i < h; i++) {
    for (int j = 0; j < w; j++) {
      dst[(y + i) * screenWidth + (x + j)] = src[i * w + j];
    }
  }
  if (ctl->sync) {
    outl(SYNC_ADDR, 1);
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
