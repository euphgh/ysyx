#include <stdio.h>
#include <unistd.h>

int main(int argc, char *argv[], char *envp[]) {
  while (1) {
    printf("argc = %d\n", argc);
    for (size_t i = 0; i < argc; i++) {
      printf("argv[%lu] = %s\n", i, argv[i]);
    }
    int i = 0;
    while (envp[i]) {
      printf("envp[%d] = %s\n", i, envp[i]);
      i++;
    }
  }
  return 0;
}
