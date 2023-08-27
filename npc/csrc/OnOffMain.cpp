#include "Vtop.h"
#include "nvboard.h"
#include "verilated_fst_c.h"
#include <memory>
void nvboard_bind_all_pins(Vtop *top);

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

  nvboard_bind_all_pins(top.get());
  nvboard_init();

  /* mainloop for simulate */
  while (contextp->gotFinish() == false) {

    /* Init Input */
    contextp->timeInc(1);
    nvboard_update();
    // int a = rand() & 1;
    // int b = rand() & 1;
    // top->a = a;
    // top->b = b;

    int a = top->a;
    int b = top->b;

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