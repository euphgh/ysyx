#include <fcntl.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>

static int evtdev = -1;
static int fbdev = -1;
static int screen_w = 0, screen_h = 0;
static int canvas_x = 0, canvas_y = 0;
static int krdFd;

uint32_t NDL_GetTicks() {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

int NDL_PollEvent(char *buf, int len) { return read(krdFd, buf, len); }

void NDL_OpenCanvas(int *w, int *h) {
  if (getenv("NWM_APP")) {
    int fbctl = 4;
    fbdev = 5;
    screen_w = *w; screen_h = *h;
    char buf[64];
    int len = sprintf(buf, "%d %d", screen_w, screen_h);
    // let NWM resize the window and create the frame buffer
    write(fbctl, buf, len);
    while (1) {
      // 3 = evtdev
      int nread = read(3, buf, sizeof(buf) - 1);
      if (nread <= 0) continue;
      buf[nread] = '\0';
      if (strcmp(buf, "mmap ok") == 0) break;
    }
    close(fbctl);
  } else {
    /* open and read dispinfo */
    int dispInfo = open("/proc/dispinfo", 0, 0);
    char buf[64];
    while (1) {
      int nread = read(dispInfo, buf, sizeof(buf) - 1);
      if (nread <= 0)
        continue;
      buf[nread] = '\0';
      if (sscanf(buf, "WIDTH : %d\nHEIGHT : %d", &screen_w, &screen_h) == 2) {
        if (*w <= screen_h || *h < screen_w) {
          /* save canvas param */
          *w = *w ? *w : screen_w;
          *h = *h ? *h : screen_h;
          canvas_x = (screen_w - *w) / 2;
          canvas_y = (screen_h - *h) / 2;
          printf("Open canvas %dw * %dh in %dw * %dh screen\n", *w, *h,
                 screen_w, screen_h);
        } else {
          printf("set width %d is large than screen width %d\n", *w, screen_w);
          printf("set height %d is large than screen height %d\n", *h,
                 screen_h);
        }
        break;
      }
    }
    fbdev = open("/dev/fb", 0, 0);
  }
}

void NDL_DrawRect(uint32_t *pixels, int x, int y, int w, int h) {
  size_t baseX = canvas_x + x;
  size_t baseY = canvas_y + y;
  size_t offset = (baseY * screen_w + baseX) * 4;
  lseek(fbdev, offset, SEEK_SET);
  for (size_t i = 0; i < h; i++) {
    write(fbdev, pixels + (i * w), w * 4);
    lseek(fbdev, (screen_w - w) * 4, SEEK_CUR);
  }
}

void NDL_OpenAudio(int freq, int channels, int samples) {
}

void NDL_CloseAudio() {
}

int NDL_PlayAudio(void *buf, int len) {
  return 0;
}

int NDL_QueryAudio() {
  return 0;
}

int NDL_Init(uint32_t flags) {
  if (getenv("NWM_APP")) {
    evtdev = 3;
  }
  krdFd = open("/dev/events", 0, 0);
  return 0;
}

void NDL_Quit() {
  close(krdFd);
  if (fbdev != -1)
    close(fbdev);
}
