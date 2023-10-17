#include "am.h"
#include "debug.h"
#include <proc.h>

#define MAX_NR_PROC 4

static PCB pcb[MAX_NR_PROC] __attribute__((used)) = {};
static PCB pcb_boot = {};
PCB *current = NULL;

void switch_boot_pcb() {
  current = &pcb_boot;
}

void hello_fun(void *arg) {
  int j = 1;
  while (1) {
    Log("Hello World from Nanos-lite with arg '%s' for the %dth time!",
        (const char *)arg, j);
    j ++;
    yield();
  }
}

void context_uload(PCB *pcb, const char *fileName, char *const argv[],
                   char *const envp[]);
void init_proc() {
  Log("Initializing processes...");
  void context_kload(PCB * pcb, void (*entry)(void *), void *arg);
  context_kload(&pcb[0], hello_fun, "foo");
  char *argv[] = {"/bin/exec-test", "FOO", NULL};
  char *envp[] = {"HELLO=WORLD", "BAR=hello", NULL};
  context_uload(&pcb[1], "/bin/args-test", argv, envp);
  // context_uload(&pcb[0], "/bin/dummy", NULL, NULL);
  switch_boot_pcb();
}

void context_kload(PCB *pcb, void (*entry)(void *), void *arg) {
  Area sArea = {.end = pcb->stack + STACK_SIZE, .start = pcb->stack};
  Context *ctx = kcontext(sArea, entry, arg);
  pcb->cp = ctx;
}

Context *schedule(Context *prev) {
  // save the context pointer
  current->cp = prev;

  // always select pcb[0] as the new process
  if (current == &pcb[0]) {
    current = &pcb[1];
    Log("switch to usr prog, cp %lx", current->cp);
  } else {
    current = &pcb[0];
    Log("switch to kernel prog, cp %lx", current->cp);
  }
  // current = &pcb[0];

  return current->cp;
}

PCB *allocPCB() { return pcb + 1; }