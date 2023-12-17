export SPIKE_REPO_PATH = $(abspath  riscv-isa-sim)
export PARENT_DIR = $(shell  pwd)
OPCODES_REPO_PATH = riscv-opcodes
ifeq ($(wildcard $(SPIKE_REPO_PATH)/spike_main),)
  $(shell git clone https://github.com/riscv-software-src/riscv-isa-sim)
  $(shell echo compile_commands.json >> $(SPIKE_REPO_PATH)/.git/info/exclude)
  $(info finish $(SPIKE_REPO_PATH) repo initial configuration)
else
  $(info $(SPIKE_REPO_PATH) repo already initiated)
endif

ifeq ($(wildcard $(OPCODES_REPO_PATH)/unratified),)
  $(shell git clone https://github.com/riscv/riscv-opcodes.git)
  $(info finish $(OPCODES_REPO_PATH) repo initial configuration)
else
  $(info $(OPCODES_REPO_PATH) repo already initiated)
endif

SPIKE_JSON = $(SPIKE_REPO_PATH)/compile_commands.json
init: $(SPIKE_JSON)
$(SPIKE_JSON):
	cd $(SPIKE_REPO_PATH) && bear -- make -C build -j $(shell nproc)

REPO_BUILD_PATH = $(SPIKE_REPO_PATH)/build
REPO_MAKEFILE = $(REPO_BUILD_PATH)/Makefile
$(REPO_MAKEFILE):
	@mkdir -p $(@D)
	cd $(@D) && $(abspath $(SPIKE_REPO_PATH))/configure

export NAME := ndevices

lib$(NAME).a:
	$(MAKE) -C $(NEMU_HOME) -f scripts/devices.mk lib

lib: lib$(NAME).a
	mv lib$(NAME).a $(REPO_BUILD_PATH)
	