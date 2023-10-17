/***************************************************************************************
* Copyright (c) 2014-2022 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "isa.h"
#include <common.h>
#include <cpu/decode.h>
#include <time.h>

extern uint64_t g_nr_guest_inst;
FILE *log_fp = NULL;

static size_t startPtr;
static size_t endPtr;
static time_t lastWrite;

void init_log(const char *log_file) {
  log_fp = stdout;
  if (log_file != NULL) {
    FILE *fp = fopen(log_file, "w");
    Assert(fp, "Can not open '%s'", log_file);
    log_fp = fp;
  }
  startPtr = 0;
  endPtr = 0;
  lastWrite = time(NULL);
  Log("Log is written to %s", log_file ? log_file : "stdout");
}

bool log_enable() {
  return MUXDEF(CONFIG_TRACE, (g_nr_guest_inst >= CONFIG_TRACE_START) &&
         (g_nr_guest_inst <= CONFIG_TRACE_END), false);
}

#define logPC()                                                                \
  ({                                                                           \
    extern CPU_state cpu;                                                      \
    isa_decode.pc;                                                             \
  })

#define logTick()                                                              \
  ({                                                                           \
    extern uint64_t g_nr_guest_inst;                                           \
    g_nr_guest_inst;                                                           \
  })

#ifdef CONFIG_RINGBUF_ENABLE
#define RINGBUF_NR (CONFIG_RINGBUF_SIZE * 1024 / TRACE_MSG_LEN)
#define INC_PTR(it)                                                            \
  ({                                                                           \
    it = (it + 1) % RINGBUF_NR;                                                \
    it;                                                                        \
  })

struct {
  uint64_t tick;
  vaddr_t pc;
} marks[RINGBUF_NR];
char msg[RINGBUF_NR][TRACE_MSG_LEN];

void traceFlush() {
  log_fp = freopen(NULL, "w", log_fp);
  if (!log_fp) {
    error("Fail to reopen log File");
    exit(1);
  }
  for (size_t i = startPtr; i != endPtr; INC_PTR(i))
    fprintf(log_fp, "[%lu][" FMT_WORD "]%s\n", marks[i].tick, marks[i].pc,
            msg[i]);
  fflush(log_fp);
}

void traceWrite(const char *fmt, ...) {
  if (!log_enable())
    return;

  va_list ap;
  va_start(ap, fmt);
  vsnprintf(msg[endPtr], TRACE_MSG_LEN, fmt, ap);
  va_end(ap);
  marks[endPtr].pc = logPC();
  marks[endPtr].tick = logTick();

  if (likely(INC_PTR(endPtr) == startPtr))
    INC_PTR(startPtr);

  time_t this = time(NULL);
  if (unlikely(this - lastWrite > 1)) {
    lastWrite = this;
    traceFlush();
  }
}
#else

void traceFlush() { fflush(log_fp); }

void traceWrite(const char *fmt, ...) {
  char msgBuf[TRACE_MSG_LEN];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(msgBuf, TRACE_MSG_LEN, fmt, ap);
  va_end(ap);
  fprintf(log_fp, "[%lu][" FMT_WORD "]%s\n", logTick(), logPC(), msgBuf);
}

#endif