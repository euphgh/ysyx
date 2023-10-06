#include "../local-include/csr.h"
Plevel machineMode;
static word_t whichOp(word_t csr, word_t src, csrOp op) {
  switch (op) {
  case csrSET:
    return satp->val | src;
  case csrCLR:
    return satp->val & ~src;
  case csrWAR:
    return src;
  }
}
static word_t sstatusRead(word_t val) {
  sstatus_t newVar = {.val = val};
  newVar.z12to11 = 0;
  newVar.z17to17 = 0;
  newVar.z31to20 = 0;
  newVar.z62to34 = 0;
  return newVar.val;
}
static bool isLegalPageMode(word_t val) {
  if (val == 0 || val == 8)
    return true;
  return false;
}
/* Define all csr regs here */
#include "../local-include/csrImplement.h"
void isa_init_csr() {
  /* Initialize all csr ptr here */
  sstatus = (sstatus_t *)&mstatusReg;
  machineMode = PRI_M;
  csrInit();
}