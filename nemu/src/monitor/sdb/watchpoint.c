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

#include "sdb.h"

#define NR_WP 32

static int wpSize;
static rpn_t rpns[NR_WP];
static word_t oldValues[NR_WP];
static char exprStr[128][NR_WP];

static int8_t numUse[NR_WP];

void init_wp_pool() {
  wpSize = 0;
  assert(NR_WP < 127);
  for (int i = 0; i < NR_WP; i++) {
    numUse[i] = -1;
    oldValues[i] = 0;
  }
}

static int findName(int pos) {
  int name = -1;
  for (int i = 0; i < NR_WP; i++) {
    if (numUse[i] == pos) {
      name = i;
      break;
    }
  }
  return name;
}

bool insertWP(const char *expr) {
  bool success = true;
  int name = -1;
  for (int i = 0; i < NR_WP; i++) {
    if (numUse[i] == -1) {
      numUse[i] = wpSize;
      name = i;
    }
  }
  if (name < 0) {
    error("Fail to insert watchpoint for no space");
    success = false;
  } else {
    DAGnode *dag = NULL;
    if (expr2dag(expr, &dag)) {
      DAGnode *sim = simplifyDAG(dag);
      rpns[wpSize] = dag2rpn(sim);
      deleteDAG(sim);
      deleteDAG(dag);

      strcpy(exprStr[wpSize], expr);
      if (evalRPN(rpns + wpSize, oldValues + wpSize)) {
        ++wpSize;
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

void deleteWP(int deleted) {
  if (numUse[deleted] != -1) {
    for (int i = numUse[deleted]; i < wpSize; i++) {
      rpns[i] = rpns[i + 1];
      oldValues[i] = oldValues[i + 1];
      strcpy(exprStr[i], exprStr[i + 1]);
    }
    numUse[deleted] = -1;
    wpSize--;
  } else {
    error("Not Found watchpoint %d", deleted);
  }
}
void printInfoWP() {
  printf("Num Expr Hex Dec\n");
  for (int i = 0; i < wpSize; i++) {
    int name = findName(i);
    Assert(name >= 0, "Not find watchpoints %d name", i);
    printf("%d, %s, " FMT_WORD ", " DEC_WORD "\n", name, exprStr[i],
           oldValues[i], oldValues[i]);
  }
}

static void printErrorWP(int pos, word_t now, word_t old) {
  int name = findName(pos);
  Assert(name >= 0, "Not find watchpoints %d name", pos);
  printf("watchpoint %d: %s\n", name, exprStr[pos]);
  printf("Old value = " FMT_WORD "," DEC_WORD "\n", old, old);
  printf("New value = " FMT_WORD "," DEC_WORD "\n", now, now);
}

bool checkWP() {
  word_t nowValue;
  bool success = true;
  for (int i = 0; i < wpSize; i++) {
    if (likely(evalRPN(rpns + i, &nowValue))) {
      if (unlikely(nowValue != oldValues[i])) {
        success = false;
        printErrorWP(i, nowValue, oldValues[i]);
      }
      i++;
    } else {
      error("Fail to eval %s", exprStr[i]);
      success = false;
      break;
    }
  }
  return success;
}

#undef NR_WP
