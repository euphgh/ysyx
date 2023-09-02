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

#include "openssl/types.h"
#include <assert.h>
#include <openssl/rand.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

// this should be enough
static char buf[65536] = {};
static char code_buf[65536 + 128] = {}; // a little larger than `buf`
static char *code_format = "#include <stdio.h>\n"
                           "#include <stdint.h>\n"
                           "int main() { "
                           "  uint64_t result = 0lu + %s; "
                           "  printf(\"%%lu\", result); "
                           "  return 0; "
                           "}";

static size_t bufPtr = 0;
static size_t tokenCnt = 0;

static uint64_t rand64() {
  uint64_t random_value;

  /* 生成 8 字节的随机数 */
  unsigned char buffer[8];
  RAND_bytes(buffer, sizeof(buffer));

  /* 将随机数转换为 uint64_t 类型 */
  random_value = 0;
  for (int i = 0; i < 8; i++) {
    random_value = (random_value << 8) | buffer[i];
  }

  return random_value;
  // return random();
}

static int choose(int n) { return rand() % n; }

static void randSpace() {
  if (choose(2)) {
    buf[bufPtr++] = ' ';
  }
}
static void gen(char chr) {
  tokenCnt++;
  randSpace();
  buf[bufPtr++] = chr;
  buf[bufPtr] = '\0';
}
static void gen_num() {
  tokenCnt++;
  randSpace();
  if (choose(2)) { // hex
    const char *fmt = choose(2) ? "0X%lx" : "0x%lx";
    bufPtr += sprintf(buf + bufPtr, fmt, rand64());
  } else { // dec
    bufPtr += sprintf(buf + bufPtr, "%lu", rand64());
  }
  buf[bufPtr++] = 'l';
  buf[bufPtr++] = 'u';
  buf[bufPtr] = '\0';
}
static void gen_rand_op() {
#define AddRule(num, chr)                                                      \
  case num:                                                                    \
    gen(chr);                                                                  \
    break;

  switch (choose(4)) {
    AddRule(0, '+');
    AddRule(1, '-');
    AddRule(2, '*');
    AddRule(3, '/');
  }
}

static void gen_rand_expr() {
  if (tokenCnt > 30) {
    gen_num();
    return;
  }
  switch (choose(3)) {
  case 0:
    gen_num();
    break;
  case 1:
    gen('(');
    gen_rand_expr();
    gen(')');
    break;
  default:
    gen_rand_expr();
    gen_rand_op();
    gen_rand_expr();
    break;
  }
}

void removelu(char *buf) {
  int ptr = 0;
  int len = strlen(buf);
  while (buf[ptr]) {
    if ((buf[ptr] == 'l') && (buf[ptr + 1] == 'u')) {
      for (int i = ptr; i < len - 1; i++) {
        buf[i] = buf[i + 2];
      }
    }
    ptr++;
  }
}

int main(int argc, char *argv[]) {
  int seed = time(0);
  srand(seed);
  int loop = 1;
  if (argc > 1) {
    sscanf(argv[1], "%d", &loop);
  }
  int i = 0;
  do {
    bufPtr = 0;
    tokenCnt = 0;
    gen_rand_expr();
    if (tokenCnt > 32)
      continue;

    sprintf(code_buf, code_format, buf);

    FILE *fp = fopen("/tmp/.code.c", "w");
    assert(fp != NULL);
    fputs(code_buf, fp);
    fclose(fp);

    int ret = system("gcc -Werror /tmp/.code.c -o /tmp/.expr");
    if (ret != 0)
      continue;

    fp = popen("/tmp/.expr", "r");
    assert(fp != NULL);

    uint64_t result;
    ret = fscanf(fp, "%lu", &result);
    pclose(fp);

    removelu(buf);
    printf("%lu %s\n", result, buf);
    i++;
  } while (i < loop);
  return 0;
}
