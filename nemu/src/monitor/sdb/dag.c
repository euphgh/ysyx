#include "common.h"
#include "debug.h"
#include "sdb.h"
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
static const char *foundStr = NULL;
static uint8_t foundOp = 0;
#define UHEX_WORD "%" MUXDEF(CONFIG_ISA64, PRIx64, PRIx32)
#define UDEC_WORD "%" MUXDEF(CONFIG_ISA64, PRIu64, PRIu32)

static char *node2Str(DAGnode *node) {
  static char buf[64];
  sprintf(buf, "{ \"%s\", %s, %u, %lu }", node->str,
          node->isImm ? "Imm" : "Var", node->synType, node->var);
  return buf;
}

static char *dstrcpy(const char *src) {
  char *dst = (char *)malloc(strlen(src) + 1);
  strcpy(dst, src);
  return dst;
}

static DAGnode *dnodecpy(const DAGnode *src) {
  DAGnode *dst = (DAGnode *)malloc(sizeof(DAGnode));
  *dst = *src;
  dst->str = dstrcpy(src->str);
  return dst;
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

bool findFirst(uint8_t *syn) {
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
bool consume(uint8_t synType) {
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
  uint8_t list[] = {__VA_ARGS__, 0};                                           \
  while (findFirst(list)) {                                                    \
    res = (DAGnode *)malloc(sizeof(DAGnode));                                  \
    res->str = dstrcpy(foundStr);                                              \
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
DAGnode *term() { BinaryMode(fact, TK_ADD, TK_SUB); }
DAGnode *fact() { BinaryMode(unar, TK_MUL, TK_DIV); }
DAGnode *unar() {
  DAGnode *res = NULL;
  uint8_t target[] = {TK_SUB, TK_MUL, TK_NOT, 0};
  if (findFirst(target)) {
    res = (DAGnode *)malloc(sizeof(DAGnode));
    res->str = dstrcpy(foundStr);
    if (foundOp == TK_SUB) {
      res->synType = TK_MINUS;
    } else if (foundOp == TK_MUL) {
      res->synType = TK_DEREF;
    } else {
      Assert(foundOp == TK_NOT, "Unexpected unary op %d", foundOp);
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
  uint8_t list[] = {TK_LP, 0};
  if (findFirst(list)) {
    res = orep();
    if (!consume(TK_RP)) {
      Assert(false, "not match \")\"");
      return NULL;
    }
  } else {
    res = (DAGnode *)malloc(sizeof(DAGnode));
    res->synType = tokens[curPtr].type;
    res->left = NULL;
    res->right = NULL;
    res->str = dstrcpy(tokens[curPtr].str);

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

DAGnode *simplifyDAG(DAGnode *old) {
  DAGnode *sim = dnodecpy(old);
  if (!old->isImm) {
    if (old->left) {
      sim->left = simplifyDAG(old->left);
      if (old->right) {
        sim->right = simplifyDAG(old->right);
      }
    }
  } else {
    sim->left = NULL;
    sim->right = NULL;
  }
  return sim;
}

void deleteDAG(DAGnode *node) {
  if (node->left) {
    deleteDAG(node->left);
    node->left = NULL;
    if (node->right)
      deleteDAG(node->right);
    node->right = NULL;
  }
  showNode(node);
  free(node->str);
  free(node);
}

bool evalDAG(DAGnode *node) {
  bool success = true;
  if (node->left) {
    word_t left, right = 0;
    success &= evalDAG(node->left);
    if (!success)
      return false;
    left = node->left->var;
    node->isImm = node->left;
    if (node->right) {
      success &= evalDAG(node->right);
      if (!success)
        return false;
      right = node->right->var;
      node->isImm &= node->right->isImm;
    }
    switch (node->synType) {
    case TK_ADD:
      node->var = left + right;
      break;
    case TK_SUB:
      node->var = left - right;
      break;
    case TK_MUL:
      node->var = left * right;
      break;
    case TK_DIV:
      node->var = left / right;
      break;
    case TK_AND:
      node->var = left & right;
      break;
    case TK_OR:
      node->var = left | right;
      break;
    case TK_NOT:
      node->var = ~left;
      break;
    case TK_DEREF:
      printf("Mem[" FMT_WORD "] = 0\n", left);
      node->isImm = false;
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
    node->isImm = true;
    if (node->synType == TK_REGS) {
      printf("Reg[%s] = 0\n", node->str);
      node->var = 0;
      node->isImm = false;
    }
  }
  return success;
}

int main() {
  const char *q = "(1 + 2) * *3";
  bool success = true;
  void init_regex();
  init_regex();
  DAGnode *old = expr2dag(q, &success);
  success &= evalDAG(old);
  printf("[old] %d: %s = %lu\n", success, q, old->var);
  showDAG(old);

  if (success) {
    DAGnode *sim = simplifyDAG(old);
    success &= evalDAG(old);
    printf("[sim] %d: %s = %lu\n", success, q, sim->var);
    showDAG(sim);
    printf("free sim\n");
    deleteDAG(sim);
  }
  printf("free old\n");
  deleteDAG(old);

  return 0;
}