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
    for (int alTempIter = 0; alTempIter < AL_ARR_NR; alTempIter++)             \
      alNumUse[alTempIter] = -1;                                               \
  } while (0)

#define AL_POS2NUM(pos)                                                        \
  ({                                                                           \
    int alTmpName = -1;                                                        \
    for (int alTempIter = 0; alTempIter < AL_ARR_NR; alTempIter++) {           \
      if (alNumUse[alTempIter] == pos) {                                       \
        alTmpName = alTempIter;                                                \
        break;                                                                 \
      }                                                                        \
    }                                                                          \
    alTmpName;                                                                 \
  })
#define AL_NUM2POS(num) alNumUse[num]

#define AL_ALLOC_NUM                                                           \
  ({                                                                           \
    int alTmpName = -1;                                                        \
    for (int alTempIter = 0; alTempIter < AL_ARR_NR; alTempIter++) {           \
      if (alNumUse[alTempIter] == -1) {                                        \
        alNumUse[alTempIter] = alPtr;                                          \
        alTmpName = alTempIter;                                                \
        break;                                                                 \
      }                                                                        \
    }                                                                          \
    alTmpName;                                                                 \
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