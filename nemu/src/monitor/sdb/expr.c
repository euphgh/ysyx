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

#include "common.h"
#include "sdb.h"
// #include <isa.h>

/* clang-format off */
/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>

bool isData(int tokenType);
bool isOp(int tokenType);
int getPrec(int tokenType);

static struct rule {
  const char *regex;
  int token_type;
} rules[] = {
    {" +", TK_NOTYPE}, // spaces

    /* compare op */
    {"==", TK_EQ},
    {"!=", TK_NE},
    {">=", TK_GE},
    {"<=", TK_LE},
    {"<", TK_LT},
    {">", TK_GT},

    {"\\(", TK_LP},        
    {"\\)", TK_RP},        

    {"\\!", TK_NOT},        

    {"[1-9][0-9]*", TK_DEC},
    {"0[xX][0-9A-Fa-f]+", TK_HEX},
    {"\\$[a-zA-Z0-9]+", TK_REGS},

    /* operator */
    {"\\|\\|", TK_OR},
    {"\\&\\&", TK_AND},
    {"\\+", TK_ADD},
    {"-", TK_SUB},
    {"\\*", TK_MUL},
    {"\\/", TK_DIV},        
};

#define NR_REGEX ARRLEN(rules)

static regex_t re[NR_REGEX] = {};

/* clang-format on */
/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex() {
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i ++) {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0) {
      regerror(ret, &re[i], error_msg, 128);
      panic("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
  }
}

Token tokens[32] __attribute__((used)) = {};
int nr_token __attribute__((used)) = 0;

static bool make_token(const char *e) {
  int position = 0;
  int i;
  regmatch_t pmatch;

  nr_token = 0;

  while (e[position] != '\0') {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i ++) {
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 && pmatch.rm_so == 0) {
        const char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s",
            i, rules[i].regex, position, substr_len, substr_len, substr_start);

        position += substr_len;

        /* skip empty char */
        int thisType = rules[i].token_type;
        if (thisType == TK_NOTYPE)
          break;
        /* assign tokens array */
        tokens[nr_token].type = thisType;
        strncpy(tokens[nr_token].str, substr_start, substr_len);
        tokens[nr_token].str[substr_len] = '\0';
        nr_token++;
        break;
      }
    }

    if (i == NR_REGEX) {
      printf("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  return true;
}

bool expr2dag(const char *e, DAGnode **root) {
  bool success = false;
  if (make_token(e)) {
    DAGnode *dagSyntax();
    *root = dagSyntax();
    success = *root != NULL;
  }
  return success;
}

bool expr(const char *e, word_t *res) {
  bool success = false;
  DAGnode *root;
  if (expr2dag(e, &root)) {
    evalDAG(root);
    *res = root->var;
    success = true;
  }
  return success;
}

#undef NR_REGEX