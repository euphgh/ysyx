#ifndef __ARRAY_LINK_H__
#define __ARRAY_LINK_H__

#define AS_DECLARE(sname, type, num)                                           \
  typedef struct {                                                             \
    type data[num];                                                            \
    uint32_t ptr;                                                              \
  } sname;

#define AS_CLEAR(stack) stack.ptr = 0
#define AS_EMPTY(stack) (stack.ptr == 0)

#define AS_PUSH(stack, element)                                                \
  do {                                                                         \
    stack.data[stack.ptr++] = element;                                         \
  } while (0)

#define AS_POP(stack)                                                          \
  do {                                                                         \
    stack.ptr--;                                                               \
  } while (0)

#define AS_REVERSE(type, stack)                                                \
  do {                                                                         \
    type old = stack;                                                          \
    AS_CLEAR(stack);                                                           \
    while (!AS_EMPTY(old)) {                                                   \
      AS_PUSH(stack, AS_TOP(old));                                             \
      AS_POP(old);                                                             \
    }                                                                          \
  } while (0)

#define AS_TOP(stack) stack.data[stack.ptr - 1]
#define AS_TOPOP(stack) ({ stack.data[--stack.ptr]; })
#endif