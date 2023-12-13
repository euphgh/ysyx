SPIKE_REPO_PATH = riscv-isa-sim
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

SPIKE = $(REPO_BUILD_PATH)/spike
$(SPIKE): $(REPO_MAKEFILE)
	CFLAGS="-fvisibility=hidden" CXXFLAGS="-fvisibility=hidden" $(MAKE) -C $(^D)

BUILD_DIR = ./build
$(shell mkdir -p $(BUILD_DIR))

inc_dependencies = fesvr riscv disasm customext fdt softfloat spike_main spike_dasm build
INC_PATH  = -I$(SPIKE_REPO_PATH) $(addprefix -I$(REPO_PATH)/, $(inc_dependencies))
INC_PATH += -I$(NEMU_HOME)/include
lib_dependencies = libspike_main.a libriscv.a libdisasm.a libsoftfloat.a libfesvr.a libfdt.a
INC_LIBS  = $(addprefix $(SPIKE_REPO_PATH)/build/, $(lib_dependencies))

NAME = $(GUEST_ISA)-spike-so
BINARY = $(BUILD_DIR)/$(NAME)
SRCS = difftest.cc

$(BINARY): $(SPIKE) $(SRCS)
	g++ -std=c++17 -O2 -shared -fPIC -fvisibility=hidden $(INC_PATH) $(SRCS) $(INC_LIBS) -o $@

clean:
	rm -rf $(BUILD_DIR)

all: $(BINARY)
.DEFAULT_GOAL = all

.PHONY: all clean $(SPIKE) init
