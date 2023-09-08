#include "common.h"
#include <sys/queue.h>

#define FITEM_NR 1024
#define FHOLE_CHECK
#define FSTACK_CHECK

typedef struct {
  vaddr_t len;
  const char *name;
} FuncInfo;
static size_t fitemSize;

static vaddr_t funcBegin[FITEM_NR];
static FuncInfo funcInfos[FITEM_NR];
void load_elf_info(const char *filename,
                   void (*addEle)(vaddr_t, vaddr_t, const char *));
void addItem(vaddr_t start, vaddr_t len, const char *name) {
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
  const vaddr_t *da = (const vaddr_t *)l;
  const vaddr_t *db = (const vaddr_t *)r;
  return (*da > *db) - (*da < *db);
}

void initFtrace(const char *elfName) { load_elf_info(elfName, addItem); }

inline static int binarySearch(vaddr_t key, vaddr_t arr[]) {
  int low = 0;
  int high = fitemSize - 1;
  int mid;

  while (low <= high) {
    mid = (low + high) / 2;
    if (arr[mid] == key) {
      return mid;
    } else if (arr[mid] < key) {
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  return high;
}

int searchByVaddr(vaddr_t vaddr) {
  int res = binarySearch(vaddr, funcBegin);
#ifdef FHOLE_CHECK
  if (res > 0) {
    if (vaddr > funcInfos[res].len + funcBegin[res]) {
      res = -1;
    }
  }
#endif
  return res;
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
#ifdef FSTACK_CHECK
static const char *getFSname(int idx) {
  return idx < 0 ? "Top" : funcInfos[idx].name;
}
#endif

static const char *getPSname(int idx) {
  return idx < 0 ? NULL : funcInfos[idx].name;
}

void pushFunc(vaddr_t vaddr) {
  int idx = searchByVaddr(vaddr);
  pushStack(&funcStack, idx);
  traceWrite("[F] call %3d {%s@" FMT_WORD "}", stackDeep, getPSname(idx),
             vaddr);
}

void popFunc(vaddr_t dst, vaddr_t src) {
  int srcIdx = searchByVaddr(src);
  int lastIdx;
  if (popStack(&funcStack, &lastIdx)) {
#ifdef FSTACK_CHECK
    int dstIdx = searchByVaddr(dst);
    int nextIdx = -1;
    if (SLIST_FIRST(&funcStack) != NULL) {
      nextIdx = SLIST_FIRST(&funcStack)->value;
    }
#endif
    if (srcIdx != lastIdx) {
#ifdef FSTACK_CHECK
      if (nextIdx == dstIdx) {
        traceWrite("[F] ret  %3d TailCall {%s} to {%s@" FMT_WORD "}", stackDeep,
                   getPSname(srcIdx), getPSname(dstIdx), dst);
      } else {
        error("src func %s diff with %s, dst func %s diff with %s",
              getPSname(srcIdx), funcInfos[lastIdx].name, getPSname(dstIdx),
              getFSname(nextIdx));
      }
#else
      traceWrite("[F] ret  %3d TailCall {%s}", stackDeep, getPSname(srcIdx));
#endif
    } else {
#ifdef FSTACK_CHECK
      if (nextIdx == dstIdx) {
        traceWrite("[F] ret  %3d {%s} to {%s@" FMT_WORD "}", stackDeep,
                   getPSname(srcIdx), getPSname(dstIdx), dst);
      } else {
        traceWrite("[F] ret  %3d {%s} to TailCall {%s@" FMT_WORD "}", stackDeep,
                   getPSname(srcIdx), getPSname(dstIdx), dst);
      }
#else
      traceWrite("[F] ret  %3d {%s}", stackDeep, getPSname(srcIdx));
#endif
    }
  } else {
    error("FuncStack is empty, can not pop\n");
  }
}

void popushFunc(vaddr_t dst, vaddr_t src) {
  int srcIdx = searchByVaddr(src);
  int dstIdx = searchByVaddr(dst);
  int lastIdx;

  if (popStack(&funcStack, &lastIdx)) {
    if (srcIdx != lastIdx) {
      warn("switch from tail call func %s diff with %s to %s",
           getPSname(srcIdx), funcInfos[lastIdx].name, getPSname(dstIdx));
    } else {
      warn("switch from func %s to %s\n", getPSname(srcIdx), getPSname(dstIdx));
    }
  } else {
    error("FuncStack is empty, can not pop\n");
  }
}