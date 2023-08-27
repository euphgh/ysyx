# Configurations
BUILD_DIR = ./build
VSRC_DIR ?= ./vsrc
CSRC_DIR = ./csrc
CONFIG_NVBOARD_ENABLE=y


VSRC = $(shell find $(VSRC_DIR) -type f -name "*.sv")
CSRC = $(shell find $(CSRC_DIR) -type f -name "*.cpp")
CXX_INC += -I$(NVBOARD_HOME)/include
LD_LIBS += -lfmt $(if $(CONFIG_NVBOARD_ENABLE), -lSDL2 -lSDL2_image)
VXX_FLAGS = --trace-fst -CFLAGS "-g $(CXX_INC)" -LDFLAGS "$(LD_LIBS)" -Mdir $(BUILD_DIR)
SIM_ELF = $(BUILD_DIR)/Vtop
NVBOARD_BIND = $(BUILD_DIR)/auto_bind.cpp
NVBOARD_DEP = $(if $(CONFIG_NVBOARD_ENABLE), $(NVBOARD_ARCHIVE) $(NVBOARD_BIND),)

export PATH := $(PATH):$(abspath ./utils)

test:
	mill -i __.test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain Elaborate -td $(BUILD_DIR)

help:
	mill -i __.test.runMain Elaborate --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help compile bsp reformat checkformat clean

onOff:
	mkdir -p $(BUILD_DIR)
	cp $(VSRC_DIR)/OnOffSwitch/top.sv $(BUILD_DIR)/top.sv

vhead:
	verilator $(VXX_FLAGS) --cc $(VSRC)

$(SIM_ELF): $(VSRC) $(CSRC) $(NVBOARD_DEP)
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	verilator $(VXX_FLAGS) --cc $(VSRC) --exe $(CSRC) $(NVBOARD_DEP) --build

elf: $(SIM_ELF)

sim: $(SIM_ELF)
	$(SIM_ELF)

-include ../Makefile
-include $(NVBOARD_HOME)/scripts/nvboard.mk
$(NVBOARD_BIND): constr/top.nxdc
	mkdir -p $(BUILD_DIR)
	python $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@
