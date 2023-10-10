#define CSR_NUM_LIST(_)                                                        \
  _(mtvec, 0x305, MTVEC)                                                       \
  _(stvec, 0x105, STVEC)                                                       \
  _(mepc, 0x341, MEPC)                                                         \
  _(sepc, 0x141, SEPC)                                                         \
  _(mcause, 0x342, MCAUSE)                                                     \
  _(scause, 0x142, SCAUSE)                                                     \
  _(medeleg, 0x302, MEDELEG)                                                   \
  _(mtval, 0x343, MTVAL)                                                       \
  _(stval, 0x143, STVAL)                                                       \
  _(mstatus, 0x300, MSTATUS)                                                   \
  _(sstatus, 0x100, SSTATUS)                                                   \
  _(mscratch, 0x340, MSCRATCH)                                                 \
  _(sscratch, 0x140, SSCRATCH)                                                 \
  _(satp, 0x180, SATP)\
