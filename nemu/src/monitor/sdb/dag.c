#include "common.h"
#include "debug.h"
#include "sdb.h"
#include <stdio.h>
#include <stdlib.h>
/*
expr    : ande || ande

ande    : comp && comp

comp    : term >  term
        | term <  term
        | term >= term
        | term <= term
        | term == term
        | term != term

term    : fact + fact
        | fact - fact

fact    : unar * unar
        | unar / unar

unar    : ! unar
        | * unar
        | - unar
        | prim

prim    : ( expr )
        | ID
*/

static int curPtr = 0;
static char *foundStr = 0;
static uint16_t foundOp = 0;
#define UHEX_WORD "%" MUXDEF(CONFIG_ISA64, PRIx64, PRIx32)
#define UDEC_WORD "%" MUXDEF(CONFIG_ISA64, PRIu64, PRIu32)

static char *node2Str(DAGnode *node) {
  char *buf = malloc(64);
  sprintf(buf, "{ %s, %u, %lu }", node->str, node->synType, node->var);
  return buf;
}

void showNode(DAGnode *node) {
  printf("%s = ", node2Str(node));
  if (node->left || node->right) {
    if (node->left) {
      printf("left: %s, ", node2Str(node->left));
    }
    if (node->right)
      printf("right: %s", node2Str(node->right));
    printf("\n");
  } else {
    printf("leave");
    printf("\n");
  }
}

bool findFirst(uint16_t *syn) {
  int i = 0;
  while (syn[i]) {
    if (tokens[curPtr].type == syn[i]) {
      foundStr = tokens[curPtr].str;
      curPtr++;
      foundOp = syn[i];
      return true;
    }
    i++;
  }
  return false;
}
bool consume(uint16_t synType) {
  bool res = false;
  if (tokens[curPtr].type == synType) {
    curPtr++;
    res = true;
  }
  return res;
}
DAGnode *orep();
DAGnode *ande();
DAGnode *comp();
DAGnode *term();
DAGnode *fact();
DAGnode *unar();
DAGnode *prim();

#define BinaryMode(nextTerm, ...)                                              \
  DAGnode *res = NULL, *left = NULL;                                           \
  left = res = nextTerm();                                                     \
  if (left == NULL)                                                            \
    return NULL;                                                               \
  uint16_t list[] = {__VA_ARGS__, 0};                                          \
  while (findFirst(list)) {                                                    \
    res = (DAGnode *)malloc(sizeof(DAGnode));                                  \
    res->str = foundStr;                                                       \
    res->left = left;                                                          \
    res->synType = foundOp;                                                    \
    res->right = nextTerm();                                                   \
    if (res->right == NULL)                                                    \
      return NULL;                                                             \
    left = res;                                                                \
  }                                                                            \
  return res;

DAGnode *orep() { BinaryMode(ande, TK_OR); }
DAGnode *ande() { BinaryMode(comp, TK_AND); }
DAGnode *comp() { BinaryMode(term, TK_GT, TK_LT, TK_GE, TK_LE, TK_EQ, TK_NE); }
DAGnode *term() { BinaryMode(fact, '+', '-'); }
DAGnode *fact() { BinaryMode(unar, '*', '/'); }
DAGnode *unar() {
  DAGnode *res = NULL;
  uint16_t target[] = {'-', '*', '!', 0};
  if (findFirst(target)) {
    res = (DAGnode *)malloc(sizeof(DAGnode));
    res->str = foundStr;
    if (foundOp == '-') {
      res->synType = TK_MINUS;
    } else if (foundOp == '*') {
      res->synType = TK_DEREF;
    } else {
      Assert(foundOp == '!', "Unexpected unary op %d", foundOp);
    }
    res->left = unar();
    if (res->left == NULL)
      return NULL;

    res->right = NULL;
  } else {
    res = prim();
  }
  return res;
}

DAGnode *prim() {
  DAGnode *res = NULL;
  uint16_t list[] = {'(', 0};
  if (findFirst(list)) {
    res = orep();
    if (!consume(')')) {
      Assert(false, "not match \")\"");
      return NULL;
    }
  } else {
    res = (DAGnode *)malloc(sizeof(DAGnode));
    res->synType = tokens[curPtr].type;
    res->left = NULL;
    res->right = NULL;
    res->str = tokens[curPtr].str;

    const char *fmt = tokens[curPtr].type == TK_HEX   ? UHEX_WORD
                      : tokens[curPtr].type == TK_DEC ? UDEC_WORD
                                                      : NULL;
    if (fmt != NULL) {
      sscanf(res->str, fmt, &res->var);
    } else if (tokens[curPtr].type != TK_REGS) {
      Assert(false, "Unexpected %u type in prim", tokens[curPtr].type);
      return NULL;
    }
    curPtr++;
  }
  return res;
}

void showDAG(DAGnode *node) {
  showNode(node);
  if (node->left)
    showDAG(node->left);
  if (node->right)
    showDAG(node->right);
}

bool evalDAG(DAGnode *node) {
  bool success = true;
  if (node->left) {
    word_t left, right = 0;
    success &= evalDAG(node->left);
    left = node->left->var;
    if (node->right) {
      success &= evalDAG(node->right);
      right = node->right->var;
    }
    switch (node->synType) {
    case '+':
      node->var = left + right;
      break;
    case '-':
      node->var = left - right;
      break;
    case '*':
      node->var = left * right;
      break;
    case '/':
      node->var = left / right;
      break;
    case TK_AND:
      node->var = left & right;
      break;
    case TK_OR:
      node->var = left | right;
      break;
    case '!':
      node->var = ~left;
      break;
    case TK_DEREF:
      printf("Mem[" FMT_WORD "] = 0\n", node->var);
      node->var = 0;
      break;
    case TK_MINUS:
      node->var = -left;
      break;
    default:
      Assert(false, "Unexpected operator %u", node->synType);
    }
  } else {
    Assert(node->right == NULL, "Right not null but left null");
    if (node->synType == TK_REGS) {
      printf("Reg[%s] = 0\n", node->str);
      node->var = 0;
    }
  }
  showNode(node);
  return success;
}

int main() {
  char *q = "(1 * 2) + *3";
  bool success = true;
  void init_regex();
  init_regex();
  word_t ans = expr(q, &success);
  printf("%d: %s = %lu\n", success, q, ans);
  return 0;
}