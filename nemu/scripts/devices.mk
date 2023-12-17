
CC := clang
CXXSRC := $(shell find -L src/device/spike -name "*.cc")

SRCS   := src/device/device.c src/device/alarm.c src/device/intr.c
SRCS   += src/device/serial.c
SRCS   += src/device/timer.c
SRCS   += src/device/io/mmio.c
SRCS   += src/device/io/map.c

CXXFLAGS = -std=c++17 -Wno-format-security
INC_PATH := $(SPIKE_REPO_PATH) $(SPIKE_REPO_PATH)/riscv $(SPIKE_REPO_PATH)/build 

include $(NEMU_HOME)/scripts/build.mk

ARCHLIB = $(BUILD_DIR)/lib$(NAME).a

$(ARCHLIB): $(OBJS) $(ARCHIVES)
	ar rcs $@ $^

lib: $(ARCHLIB)
	mv $(ARCHLIB) $(PARENT_DIR)
