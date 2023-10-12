#include "common.h"

static char databuf[32];
const char *mtraceVname;
bool mtraceTrans;
vaddr_t mtraceVaddr;
void mtrace(const char *pname, paddr_t paddr, word_t data, int len) {
  uint8_t *bytes = (uint8_t *)&data;
  uint8_t lane = paddr & MUXDEF(CONFIG_ISA64, 0x7, 0x3);
  char digs[] = "0123456789abcdef";
  for (int i = 0; i < MUXDEF(CONFIG_ISA64, 8, 4); i++) {
    if (i < lane || i > lane + len - 1) {
      databuf[i * 3] = '?';
      databuf[i * 3 + 1] = '?';
      databuf[i * 3 + 2] = ' ';
    } else {
      databuf[i * 3] = digs[bytes[i - lane] >> 4];
      databuf[i * 3 + 1] = digs[bytes[i - lane] & 0x0f];
      databuf[i * 3 + 2] = ' ';
    }
  }
  databuf[24] = 0;
  traceWrite("[M] %s(" FMT_WORD ") %s %s(" FMT_PADDR ")=%s", mtraceVname,
             mtraceVaddr, (mtraceTrans ? "trl" : "dir"), pname, paddr, databuf);
}