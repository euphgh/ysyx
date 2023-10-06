/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 *PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 *KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#ifndef __ISA_RISCV_H__
#define __ISA_RISCV_H__

#include <common.h>

typedef struct {
  word_t gpr[MUXDEF(CONFIG_RVE, 16, 32)];
  vaddr_t pc;
} MUXDEF(CONFIG_RV64, riscv64_CPU_state, riscv32_CPU_state);

// regs code
typedef enum {
  RC_PC = 32,
} riscv64_Rcode;

// decode
typedef struct {
  union {
    struct {
      word_t opcode : 7;
      word_t rd : 5;
      word_t funct3 : 3;
      word_t rs1 : 5;
      word_t rs2 : 5;
      word_t funct7 : 7;
    } r;
    struct {
      word_t opcode : 7;
      word_t rd : 5;
      word_t funct3 : 3;
      word_t rs1 : 5;
      word_t imm : 12;
    } i;
    struct {
      word_t opcode : 7;
      word_t rd : 5;
      word_t imm : 20;
    } u;
    uint32_t val;
  } inst;
} MUXDEF(CONFIG_RV64, riscv64_ISADecodeInfo, riscv32_ISADecodeInfo);

#endif
