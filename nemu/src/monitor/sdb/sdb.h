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

#ifndef __SDB_H__
#define __SDB_H__

#include <common.h>

enum {
  TK_NOTYPE = 256,

  TK_EQ,
  TK_NE,
  TK_GT,
  TK_LT,
  TK_GE,
  TK_LE,

  TK_MINUS,
  TK_DEREF,

  TK_DEC,
  TK_HEX,
  TK_REGS,

  TK_OR,
  TK_AND,
};

typedef struct token {
  uint16_t type;
  char str[32];
} Token;
extern Token tokens[32];
extern int nr_token;

typedef struct Node DAGnode;
struct Node {
  word_t var;
  DAGnode *left;
  DAGnode *right;
  uint16_t synType;
  char *str;
  bool isImm;
};

DAGnode *expr2dag(const char *e, bool *success);
bool evalDAG(DAGnode *root);
word_t expr(const char *e, bool *success);

#endif
