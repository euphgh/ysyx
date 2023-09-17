#include "../local-include/csr.h"
#include "../local-include/csrImplement.h"
Plevel machineMode;
void isa_init_csr() {
  machineMode = PRI_M;
  csrInit();
}