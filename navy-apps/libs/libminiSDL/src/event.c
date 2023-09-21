#include <NDL.h>
#include <SDL.h>
#include <assert.h>
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
  assert(ev);
#define BUF_SIZE 16
  char buf[BUF_SIZE];
  int nread = NDL_PollEvent(buf, BUF_SIZE - 1);
  buf[nread - 1] = '\0';
  if (nread > 0) {
    ev->key.type = buf[1] == 'u' ? SDL_KEYUP : SDL_KEYDOWN;
#define PaserKey(k)                                                            \
  if (strcmp(buf + 3, #k) == 0) {                                              \
    ev->key.keysym.sym = SDLK_##k;                                             \
    return 1;                                                                  \
  }
    _KEYS(PaserKey)
    ev->key.keysym.sym = SDLK_NONE;
    return 1;
#undef PaserKey
  } else
    return 0;
#undef BUF_SIZE
}

int SDL_WaitEvent(SDL_Event *event) {
#define BUF_SIZE 16
  char buf[BUF_SIZE];
  int nread = 0;

  while (1) {
    nread = NDL_PollEvent(buf, BUF_SIZE - 1);
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
#undef BUF_SIZE
}

int SDL_PeepEvents(SDL_Event *ev, int numevents, int action, uint32_t mask) {
  return 0;
}

uint8_t* SDL_GetKeyState(int *numkeys) {
  return NULL;
}
