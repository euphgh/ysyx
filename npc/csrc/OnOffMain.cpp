#include "Vtop.h"
#include "verilated_fst_c.h"
#include <memory>
int main() {
  Verilated::mkdir("waves");
  const auto contextp = std::make_unique<VerilatedContext>();
  contextp->traceEverOn(true);
  contextp->randReset(2);

  /* Wave file init */
  VerilatedFstC tfp;
  const auto top = std::make_unique<Vtop>();
  top->trace(&tfp, 0);
  tfp.open("waves/OnOffSwitch.fst");

  /* mainloop for simulate */
  while (contextp->time() < 1024) {

    /* Init Input */
    contextp->timeInc(1);
    int a = rand() & 1;
    int b = rand() & 1;
    top->a = a;
    top->b = b;

    /* Eval dump wave*/
    top->eval();
    tfp.dump(contextp->time());

    /* Test and Print */
    printf("a = %d, b = %d, f = %d\n", a, b, top->f);
    assert(top->f == (a ^ b));
  }
  top->final();
  tfp.close();
}