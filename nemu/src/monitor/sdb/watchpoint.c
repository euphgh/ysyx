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

#include "adt/ArrayLink.h"
#include "sdb.h"

#define AL_ARR_NR 16
#define AL_ATTR(_)                                                             \
  _(rpn_t, rpns)                                                               \
  _(word_t, oldValues)                                                         \
  _(char, exprStr[128])

AL_DECLARE(Attr)

void init_wp_pool() { AL_INIT; }

bool insertWP(const char *e) {
  bool success = true;
  int num = AL_ALLOC_NUM;
  if (num < 0) {
    error("Fail to insert breakpoint for no space");
    success = false;
  } else {
    DAGnode *dag = NULL;
    if (expr2dag(e, &dag)) {
      DAGnode *sim = simplifyDAG(dag);
      rpns[alPtr] = dag2rpn(sim);
      deleteDAG(sim);
      deleteDAG(dag);

      strcpy(exprStr[alPtr], e);
      if (evalRPN(rpns + alPtr, oldValues + alPtr)) {
        ++alPtr;
      } else {
        error("Fail to insert watchpoint for eval error");
        success = false;
      }
    } else {
      error("Fail to insert watchpoint for grammer error");
      success = false;
    }
  }
  return success;
}

void deleteWP(int num) {
  if (AL_NUM2POS(num) != -1) {
    AL_FREE_NUM(num, i, {
      rpns[i] = rpns[i + 1];
      oldValues[i] = oldValues[i + 1];
      strcpy(exprStr[i], exprStr[i + 1]);
    });
  } else
    error("Not Found breakpoint %d", num);
}

void showInfoWP() {
  printf("Num Expr Vaddr\n");
  AL_FOREACH(i, {
    int num = AL_POS2NUM(i);
    Assert(num >= 0, "Not find breakpoint %d name", i);
    printf("%d, %s, " FMT_WORD ", " DEC_WORD "\n", num, exprStr[i],
           oldValues[i], oldValues[i]);
  })
}

static void showDiffWP(int pos, word_t now, word_t old) {
  int name = AL_POS2NUM(pos);
  Assert(name >= 0, "Not find watchpoints %d name", pos);
  printf("watchpoint %d: %s\n", name, exprStr[pos]);
  printf("Old value = " FMT_WORD "," DEC_WORD "\n", old, old);
  printf("New value = " FMT_WORD "," DEC_WORD "\n", now, now);
}

bool checkWP() {
  word_t nowValue;
  bool success = true;
  AL_FOREACH(i, {
    if (likely(evalRPN(rpns + i, &nowValue))) {
      if (unlikely(nowValue != oldValues[i])) {
        success = false;
        showDiffWP(i, nowValue, oldValues[i]);
      }
    } else {
      error("Fail to eval watchpoint %s", exprStr[i]);
      success = false;
    }
  })
  return success;
}

#undef AL_ARR_NR
#undef AL_ATTR