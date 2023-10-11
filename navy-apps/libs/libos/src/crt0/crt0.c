#include <stdint.h>
#include <stdlib.h>
#include <assert.h>

int main(int argc, char *argv[], char *envp[]);
extern char **environ;
void call_main(uintptr_t *args) {
  int argc = *(uintptr_t *)args;
  char **argv = (char **)((unsigned char *)args + sizeof(uintptr_t));
  char **envp = argv + (argc + 1);
  exit(main(argc, argv, envp));
  assert(0);
}
