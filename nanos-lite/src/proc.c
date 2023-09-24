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

void init_proc() {
  void context_kload(PCB * pcb, void (*entry)(void *), void *arg);
  context_kload(&pcb[0], hello_fun, "foo");
  context_kload(&pcb[1], hello_fun, "bar");
  switch_boot_pcb();

  Log("Initializing processes...");

  //   // load program here
  //   void naive_uload(PCB * pcb, const char *filename);
  //   naive_uload(NULL,
  // #ifdef CONFIG_BATCH
  // #ifdef CONFIG_MENU
  //               "/bin/menu"
  // #else
  //               "/bin/nterm"
  // #endif
  // #else
  //               "/bin/dummy"
  // #endif
  //   );
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
  current = (current == &pcb[0] ? &pcb[1] : &pcb[0]);

  // then return the new context
  return current->cp;
}
