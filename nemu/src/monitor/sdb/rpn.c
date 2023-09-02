#include "sdb.h"

void showNode(DAGnode *node);
void showDAG(DAGnode *node);
DAGnode *simplifyDAG(DAGnode *old);
void deleteDAG(DAGnode *node);

static void findTopOfRPN(rpn_t rpn, int *synTop, int *srcTop) {
  *synTop = 0;
  *srcTop = 0;
  while (rpn.syn[*synTop]) {
    *srcTop += (rpn.syn[*synTop] == TK_HEX || rpn.syn[*synTop] == TK_REGS);
    (*synTop)++;
  }
}

static rpn_t mergeRPN(rpn_t left, rpn_t right) {
  int leftSynPtr = 0, leftSrcPtr = 0, rightSynPtr = 0, rightSrcPtr = 0;
  findTopOfRPN(left, &leftSynPtr, &leftSrcPtr);
  findTopOfRPN(right, &rightSynPtr, &rightSrcPtr);
  Assert(leftSynPtr + rightSynPtr < 32, "rpn syn number > 31");
  Assert(leftSrcPtr + rightSrcPtr <= 12, "rpn src number > 12");
  memcpy(left.syn + leftSynPtr, right.syn, rightSynPtr * sizeof(right.syn[0]));
  memcpy(left.src + leftSrcPtr, right.src, rightSrcPtr * sizeof(right.src[0]));
  left.syn[leftSynPtr + rightSynPtr] = 0;
  return left;
}

void showRPN(rpn_t *rpn) {
  int synPtr = 0;
  int srcPtr = 0;
  while (rpn->syn[synPtr] != 0) {
    if (rpn->syn[synPtr] == TK_HEX) {
      printf("[Imm(%lu)]", rpn->src[srcPtr++]);
    } else if (rpn->syn[synPtr] == TK_REGS) {
      printf("[Reg(%lu)]", rpn->src[srcPtr++]);
    } else {
      printf("[%d]", rpn->syn[synPtr]);
    }
    synPtr++;
  }
  printf("\n");
}

static rpn_t newOpRPN(uint8_t op) {
  rpn_t rpn;
  rpn.syn[0] = op;
  rpn.syn[1] = 0;
  return rpn;
}

static rpn_t newImmRPN(word_t imm) {
  rpn_t rpn;
  rpn.syn[0] = TK_HEX;
  rpn.syn[1] = 0;
  rpn.src[0] = imm;
  return rpn;
}

rpn_t dag2rpn(DAGnode *node) {
  rpn_t rpn;
  if (node->left == NULL) {
    rpn = newImmRPN(node->var);
  } else {
    rpn_t opRPN = newOpRPN(node->synType);
    rpn_t leftRPN = dag2rpn(node->left);
    if (node->right)
      leftRPN = mergeRPN(dag2rpn(node->right), leftRPN);
    rpn = mergeRPN(leftRPN, opRPN);
  }
  return rpn;
}

S_DECLARE(WordStack, word_t, 12);
#define BinaryMode(syn, op)                                                    \
  case syn: {                                                                  \
    word_t src0 = WordStackPop(&srcsStack);                                    \
    word_t src1 = WordStackPop(&srcsStack);                                    \
    S_PUSH(srcsStack, src0 op src1);                                           \
    break;                                                                     \
  }

bool evalRPN(rpn_t *rpn, word_t *res) {
  WordStack srcsStack;
  S_CLEAR(srcsStack);
  int srcPtr = 0;
  int synPtr = 0;
  bool success = true;
  while (rpn->syn[synPtr] != 0) {
    uint8_t thisSyn = rpn->syn[synPtr];
    switch (thisSyn) {
    case TK_HEX:
      S_PUSH(srcsStack, rpn->src[srcPtr]);
      srcPtr++;
      break;
    case TK_MINUS: {
      word_t minus = -WordStackPop(&srcsStack);
      S_PUSH(srcsStack, minus);
      break;
    }
    case TK_NOT: {
      word_t notVar = ~WordStackPop(&srcsStack);
      S_PUSH(srcsStack, notVar);
      break;
    }
    case TK_DEREF: {
      printf("Mem[" FMT_WORD "] = 0\n", WordStackPop(&srcsStack));
      word_t loadVar = 0;
      S_PUSH(srcsStack, loadVar);
      break;
    }
      BinaryMode(TK_ADD, +);
      BinaryMode(TK_SUB, -);
      BinaryMode(TK_MUL, *);
      BinaryMode(TK_DIV, /);
      BinaryMode(TK_GE, >=);
      BinaryMode(TK_GT, >);
      BinaryMode(TK_LE, <=);
      BinaryMode(TK_LT, <);
      BinaryMode(TK_NE, ==);
      BinaryMode(TK_EQ, !=);
    default:
      Assert(false, );
    }
    synPtr++;
  }
  if (success) {
    *res = WordStackPop(&srcsStack);
    Assert((srcsStack.ptr == 0), "RPN stack not empty when finish");
  }
  return success;
}

#ifdef MANUAL_MODE
int main(int argc, char **argv) {
  void init_regex();
  init_regex();
  FILE *fp = fopen(argv[1], "r");
  Assert(fp, "Can not open file %s", argv[1]);

  word_t ref, rpnAns;
  char expr[65535];

  while (fscanf(fp, "%lu %[^\n]", &ref, expr) == 2) {
    bool success = true;
    printf("%s = %lu\n", expr, ref);
    DAGnode *dag;
    success &= expr2dag(expr, &dag);
    Assert(success, "dag grammer error");
    success &= evalDAG(dag);
    Assert(success && (dag->var == ref), "dag eval %d: %lu", success, dag->var);
    printf("[dag] = %lu\n", dag->var);

    rpn_t rpn = dag2rpn(dag);
    success &= evalRPN(&rpn, &rpnAns);
    Assert(success && rpnAns == ref, "rpn eval %d: %lu", success, rpnAns);
    printf("[rpn] = %lu\n", rpnAns);

    deleteDAG(dag);
  }
  return 0;
}
#endif