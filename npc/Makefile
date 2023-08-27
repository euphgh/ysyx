BUILD_DIR = ./build
VSRC_DIR ?= ./vsrc
CSRC_DIR = ./csrc
VSRC = $(shell find $(VSRC_DIR) -type f -name "*.sv")
CSRC = $(shell find $(CSRC_DIR) -type f -name "*.cpp")
VXXFLAGS = --trace-fst -CFLAGS "-g" -LDFLAGS "-lfmt" -Mdir $(BUILD_DIR)

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
	verilator --trace-fst -CFLAGS "-g" -LDFLAGS "-lfmt" --cc $(VSRC)

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	verilator $(VXXFLAGS) --cc $(VSRC) --exe $(CSRC) --build
	$(BUILD_DIR)/Vtop

-include ../Makefile
