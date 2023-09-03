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
#include "cpu/cpu.h"
#include "isa.h"
#include "memory/vaddr.h"
#include <readline/history.h>
#include <readline/readline.h>

static int is_batch_mode = false;

void init_regex();
void init_wp_pool();
void initBP();

/* We use the `readline' library to provide more flexibility to read from stdin. */
static char* rl_gets() {
  static char *line_read = NULL;

  if (line_read) {
    free(line_read);
    line_read = NULL;
  }

  line_read = readline("(nemu) ");

  if (line_read && *line_read) {
    add_history(line_read);
  }

  return line_read;
}

static int cmd_c(char *args) {
  cpu_exec(-1);
  return 0;
}

static int cmd_si(char *args);
static int cmd_info(char *args);
static int cmd_x(char *args);
static int cmd_p(char *args);
static int cmd_w(char *args);
static int cmd_b(char *args);
static int cmd_d(char *args);
static int cmd_help(char *args);

static int cmd_si(char *args) {
  char *stepStr = strtok(NULL, " ");
  int steps = 1;
  if (stepStr)
    sscanf(stepStr, "%d", &steps);
  cpu_exec(steps);
  return 0;
}

static int cmd_info(char *args) {
  char *name = strtok(NULL, " ");
  if (name) {
    switch (name[0]) {
    case 'w':
      showInfoWP();
      break;
    case 'b':
      showInfoBP();
      break;
    case 'r':
      isa_reg_display();
      break;
    default:
      error("info not support %c", name[0]);
    }
  }
  return 0;
}

#define UHEX_WORD "%" MUXDEF(CONFIG_ISA64, PRIx64, PRIx32)
static int cmd_x(char *args) {
  int size;
  char buf[128];
  vaddr_t vaddr;
  if (args) {
    if (sscanf(args, "%d %[^\n]", &size, buf) == 2) {
      if (expr(buf, &vaddr)) {
        for (int i = 0; i < ((size - 1) / 8) + 1; i++) {
          for (int j = 0; j < 8; j++) {
            word_t offset = i * 8 + j;
            if (offset == size)
              break;
            uint8_t back = vaddr_read(vaddr + offset, 1);
            printf("%02x ", back);
          }
          printf("\n");
        }
      } else {
        error("please check expr grammer");
      }
    } else {
      error("please check command grammer: x [size] [expr]");
    }
  }
  return 0;
};

static int cmd_p(char *args) {
  word_t res;
  if (expr(args, &res))
    printf("Hex: " FMT_WORD "\tDec: " DEC_WORD "\n", res, res);
  return 0;
};

static int cmd_w(char *args) {
  if (insertWP(args))
    printf("successfully add watchpoint\n");
  return 0;
};

static int cmd_b(char *args) {
  if (insertBP(args))
    printf("successfully add breakpoint\n");
  return 0;
}

static int cmd_d(char *args) {
  char *name = strtok(NULL, " ");
  if (name) {
    char *numStr = strtok(NULL, " ");
    int num;
    if (numStr && sscanf(numStr, "%d", &num)) {
      switch (name[0]) {
      case 'w':
        deleteWP(num);
        break;
      case 'b':
        deleteBP(num);
        break;
      default:
        error("delete not support %c", name[0]);
      }
    }
  }
  return 0;
};

static int cmd_q(char *args) {
  nemu_state.state = NEMU_QUIT;
  return -1;
}

static int cmd_help(char *args);

static struct {
  const char *name;
  const char *description;
  int (*handler) (char *);
} cmd_table[] = {
    {"help", "Display information about all supported commands", cmd_help},
    {"c", "Continue the execution of the program", cmd_c},
    {"q", "Exit NEMU", cmd_q},
    {"si", "Step one instruction exactly", cmd_si},
    {"info",
     "Generic command for showing things about the program being debugged",
     cmd_info},
    {"x", "Examine memory: x/FMT ADDRESS", cmd_x},
    {"p", "Print value of expression EXP", cmd_p},
    {"w", "Set a watchpoint for EXPRESSION", cmd_w},
    {"b", "Set breakpoint at specified location", cmd_b},
    {"d", "Delete all or some breakpoints or watchpoints", cmd_d},
};

#define NR_CMD ARRLEN(cmd_table)

static int cmd_help(char *args) {
  /* extract the first argument */
  char *arg = strtok(NULL, " ");
  int i;

  if (arg == NULL) {
    /* no argument given */
    for (i = 0; i < NR_CMD; i ++) {
      printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
    }
  }
  else {
    for (i = 0; i < NR_CMD; i ++) {
      if (strcmp(arg, cmd_table[i].name) == 0) {
        printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
        return 0;
      }
    }
    printf("Unknown command '%s'\n", arg);
  }
  return 0;
}

void sdb_set_batch_mode() {
  is_batch_mode = true;
}

void sdb_mainloop() {
  if (is_batch_mode) {
    cmd_c(NULL);
    return;
  }

  for (char *str; (str = rl_gets()) != NULL; ) {
    char *str_end = str + strlen(str);

    /* extract the first token as the command */
    char *cmd = strtok(str, " ");
    if (cmd == NULL) { continue; }

    /* treat the remaining string as the arguments,
     * which may need further parsing
     */
    char *args = cmd + strlen(cmd) + 1;
    if (args >= str_end) {
      args = NULL;
    }

#ifdef CONFIG_DEVICE
    extern void sdl_clear_event_queue();
    sdl_clear_event_queue();
#endif

    int i;
    for (i = 0; i < NR_CMD; i ++) {
      if (strcmp(cmd, cmd_table[i].name) == 0) {
        if (cmd_table[i].handler(args) < 0) { return; }
        break;
      }
    }

    if (i == NR_CMD) { printf("Unknown command '%s'\n", cmd); }
  }
}

void init_sdb() {
  /* Compile the regular expressions. */
  init_regex();

  /* Initialize the watchpoint pool. */
  init_wp_pool();

  /* Initialize the brekpoint pool. */
  initBP();
}
