#include <NDL.h>
#include <SDL.h>
#include <string.h>

#define keyname(k) #k,

static const char *keyname[] = {
  "NONE",
  _KEYS(keyname)
};

int SDL_PushEvent(SDL_Event *ev) {
  return 0;
}

int SDL_PollEvent(SDL_Event *ev) {
  return 0;
}

int SDL_WaitEvent(SDL_Event *event) {
  char buf[16];
  int nread = 0;

  while (1) {
    nread = NDL_PollEvent(buf, 16);
    if (nread < 0)
      return 0;
    else if (nread == 0)
      continue;

    buf[nread - 1] = '\0';
    event->key.type = buf[1] == 'u' ? SDL_KEYUP : SDL_KEYDOWN;
#define PaserKey(k)                                                            \
  if (strcmp(buf + 3, #k) == 0) {                                              \
    event->key.keysym.sym = SDLK_##k;                                          \
    break;                                                                     \
  }
    _KEYS(PaserKey)
#undef PaserKey
  }
  return 1;
}

int SDL_PeepEvents(SDL_Event *ev, int numevents, int action, uint32_t mask) {
  return 0;
}

uint8_t* SDL_GetKeyState(int *numkeys) {
  return NULL;
}
