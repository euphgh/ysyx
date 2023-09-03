#ifndef __ARRAY_LINK_H__
#define __ARRAY_LINK_H__

/*
 * before use
 * define `AL_ARR_NR` for size
 * define `Attr` for type
 */
#define AL_DECLARE(attr)                                                       \
  static int alPtr;                                                            \
  static int alNumUse[AL_ARR_NR];                                              \
  AL_ATTR(__AL_PRIVATE_DEFINE__);

#define AL_INIT                                                                \
  do {                                                                         \
    alPtr = 0;                                                                 \
    for (int i = 0; i < AL_ARR_NR; i++)                                        \
      alNumUse[i] = -1;                                                        \
  } while (0)

#define AL_POS2NUM(pos)                                                        \
  ({                                                                           \
    int name = -1;                                                             \
    for (int i = 0; i < AL_ARR_NR; i++) {                                      \
      if (alNumUse[i] == pos) {                                                \
        name = i;                                                              \
        break;                                                                 \
      }                                                                        \
    }                                                                          \
    name;                                                                      \
  })
#define AL_NUM2POS(num) alNumUse[num]

#define AL_ALLOC_NUM                                                           \
  ({                                                                           \
    int name = -1;                                                             \
    for (int i = 0; i < AL_ARR_NR; i++) {                                      \
      if (alNumUse[i] == -1) {                                                 \
        alNumUse[i] = alPtr;                                                   \
        name = i;                                                              \
      }                                                                        \
    }                                                                          \
    name;                                                                      \
  })

#define AL_FREE_NUM(num, it, move)                                             \
  do {                                                                         \
    for (int it = alNumUse[num]; it < alPtr; it++) {                           \
      move                                                                     \
    }                                                                          \
    alNumUse[num] = -1;                                                        \
    alPtr--;                                                                   \
  } while (0)

#define AL_FOREACH(it, ...)                                                    \
  do {                                                                         \
    for (int it = 0; it < alPtr; it++) {                                       \
      __VA_ARGS__                                                              \
    }                                                                          \
  } while (0);

#define __AL_PRIVATE_DEFINE__(type, name) static type name[AL_ARR_NR];
#endif