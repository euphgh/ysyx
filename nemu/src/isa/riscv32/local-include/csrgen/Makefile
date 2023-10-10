DEF_FILE = $(NEMU_HOME)/src/isa/riscv64/local-include/csrDefine.h
IMP_FILE = $(NEMU_HOME)/src/isa/riscv64/local-include/csrImplement.h
ENU_FILE = $(NEMU_HOME)/src/isa/riscv64/local-include/csrEnum.h
PY_DIR = $(NEMU_HOME)/src/isa/riscv32/local-include/csrgen
PY_SRCS = $(shell find $(PY_DIR) -name "*.py")

all: $(DEF_FILE) $(IMP_FILE) $(ENU_FILE)

$(DEF_FILE): $(PY_SRCS)
	python $(PY_DIR)/main.py h | clang-format > $@

$(IMP_FILE): $(PY_SRCS)
	python $(PY_DIR)/main.py s | clang-format > $@

$(ENU_FILE): $(PY_SRCS)
	python $(PY_DIR)/main.py e | clang-format > $@