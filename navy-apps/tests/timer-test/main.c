#include "NDL.h"
#include <stdio.h>

int main() {
  unsigned long last = NDL_GetTicks();
  while (1) {
    unsigned long now = NDL_GetTicks();
    if ((now - last) > 500) {
      last = now;
      printf("pass 0.5 second\n");
    }
  }
  return 0;
}