#include "common.h"
#include <assert.h>
#include <stdint.h>
#include <sys/queue.h>

#define FITEM_NR 1024

typedef struct {
  uint64_t len;
  const char *name;
} FuncInfo;
static size_t fitemSize;

static uint64_t funcBegin[FITEM_NR];
static FuncInfo funcInfos[FITEM_NR];
void load_elf_info(const char *filename,
                   void (*addEle)(uint64_t, uint64_t, const char *));
void addItem(uint64_t start, uint64_t len, const char *name) {
  int i = fitemSize++ - 1;
  assert(fitemSize < FITEM_NR);
  while (i >= 0 && funcBegin[i] > start) {
    funcBegin[i + 1] = funcBegin[i];
    funcInfos[i + 1] = funcInfos[i];
    i--;
  }
  funcBegin[i + 1] = start;
  funcInfos[i + 1].len = len;
  funcInfos[i + 1].name = name;
}

int compUInt64(const void *l, const void *r) {
  const uint64_t *da = (const uint64_t *)l;
  const uint64_t *db = (const uint64_t *)r;
  return (*da > *db) - (*da < *db);
}

void initFtrace(const char *elfName) { load_elf_info(elfName, addItem); }

int searchByVaddr(uint64_t vaddr) {
  int low = 0;
  int high = fitemSize - 1;
  int mid;

  while (low <= high) {
    mid = (low + high) / 2;
    if (funcBegin[mid] == vaddr) {
      return mid;
    } else if (funcBegin[mid] < vaddr) {
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  return high;
}

struct StackNode {
  int value;
  SLIST_ENTRY(StackNode) entries;
};
int stackDeep = 0;

typedef SLIST_HEAD(stack_head, StackNode) StackHead;

void pushStack(StackHead *head, int value) {
  stackDeep++;
  struct StackNode *node = malloc(sizeof(struct StackNode));
  node->value = value;
  SLIST_INSERT_HEAD(head, node, entries);
}

bool popStack(StackHead *head, int *value) {
  if (SLIST_EMPTY(head))
    return false;

  stackDeep--;
  struct StackNode *node = SLIST_FIRST(head);
  *value = node->value;
  SLIST_REMOVE_HEAD(head, entries);
  free(node);
  return true;
}

static StackHead funcStack;

void pushFunc(uint64_t vaddr) {
  int idx = searchByVaddr(vaddr);
  if (vaddr <= funcBegin[idx] + funcInfos[idx].len) {
    pushStack(&funcStack, idx);
    printf("Push func %s\n", funcInfos[idx].name);
  } else {
    printf("Not Found match range\n");
  }
}

void popFunc(uint64_t dst, uint64_t src) {
  int idx = searchByVaddr(dst);
  if (dst <= funcBegin[idx] + funcInfos[idx].len) {
    int lastIdx;
    if (popStack(&funcStack, &lastIdx)) {
      if (SLIST_FIRST(&funcStack)) {
        int topIdx = SLIST_FIRST(&funcStack)->value;
        if (topIdx == idx)
          printf("Pop func %s to %s\n", funcInfos[lastIdx].name,
                 funcInfos[idx].name);
        else
          printf("Pop to func %s not same with push %s\n", funcInfos[idx].name,
                 funcInfos[topIdx].name);
      } else {
        printf("Pop last func %s\n", funcInfos[idx].name);
      }
    } else {
      printf("Pop empty\n");
    }
  } else {
    printf("Not Found match range\n");
  }
}
