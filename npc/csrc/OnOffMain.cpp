#include "Vtop.h"
#include "macro.h"
#include "npc/common.hpp"
#include "nvboard.h"
#include <memory>

/* Include verilator wave header */
#define wave_file_t MUXDEF(CONFIG_EXT_FST, VerilatedFstC, VerilatedVcdC)
#define __WAVE_INC__                                                           \
  MUXDEF(CONFIG_EXT_FST, "verilated_fst_c.h", "verilated_vcd_c.h")
#ifdef CONFIG_WAVE_ON
#include __WAVE_INC__
#endif

void nvboard_bind_all_pins(Vtop *top);

int main() {
  Verilated::mkdir("waves");
  const auto contextp = std::make_unique<VerilatedContext>();
  contextp->traceEverOn(true);
  contextp->randReset(2);
  const auto top = std::make_unique<Vtop>();

  /* Wave file init */
  Verilated::traceEverOn(MUXDEF(CONFIG_WAVE_ON, true, false));
#ifdef CONFIG_WAVE_ON
  IFDEF(CONFIG_WAVE_ON, wave_file_t tfp);
  IFDEF(CONFIG_WAVE_ON, top->trace(&tfp, 0));
  auto wavePath =
      CONFIG_WAVE_DIR "/" + std::string("OnOffSwitch") + "." CONFIG_WAVE_EXT;
  IFDEF(CONFIG_WAVE_ON, tfp.open(wavePath.c_str()));
#endif

  IFDEF(CONFIG_NVBOARD_ENABLE, nvboard_bind_all_pins(top.get()));
  IFDEF(CONFIG_NVBOARD_ENABLE, nvboard_init());

  /* mainloop for simulate */
  while (contextp->gotFinish() == false) {

    /* Init Input */
    contextp->timeInc(1);
    IFDEF(CONFIG_NVBOARD_ENABLE, nvboard_update());
    // int a = rand() & 1;
    // int b = rand() & 1;
    // top->a = a;
    // top->b = b;

    int a = top->a;
    int b = top->b;

    /* Eval dump wave*/
    top->eval();
    IFDEF(CONFIG_WAVE_ON, tfp.dump(contextp->time()));

    /* Test and Print */
    // printf("a = %d, b = %d, f = %d\n", a, b, top->f);
    assert(top->f == (a ^ b));
  }
  top->final();
  IFDEF(CONFIG_WAVE_ON, tfp.close());
}