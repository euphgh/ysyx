#define SDL_malloc  malloc
#define SDL_free    free
#define SDL_realloc realloc

#define SDL_STBIMAGE_IMPLEMENTATION
#include "SDL_stbimage.h"
#include <fcntl.h>
#include <unistd.h>

SDL_Surface* IMG_Load_RW(SDL_RWops *src, int freesrc) {
  assert(src->type == RW_TYPE_MEM);
  assert(freesrc == 0);
  return NULL;
}

SDL_Surface* IMG_Load(const char *filename) {
  int fd = open(filename, O_RDONLY);
  assert(fd > 0);

  int len = lseek(fd, 0, SEEK_END);
  unsigned char *buf = malloc(len);

  int nread = 0;
  int begin = lseek(fd, 0, SEEK_SET);
  int nr = 0;
  while (nread < len) {
    int this = read(fd, buf + nread, len);
    nread += this;
  }
  assert(nread == len);

  SDL_Surface *suf = STBIMG_LoadFromMemory(buf, len);

  close(fd);
  free(buf);
  return suf;
}

int IMG_isPNG(SDL_RWops *src) {
  return 0;
}

SDL_Surface* IMG_LoadJPG_RW(SDL_RWops *src) {
  return IMG_Load_RW(src, 0);
}

char *IMG_GetError() {
  return "Navy does not support IMG_GetError()";
}
