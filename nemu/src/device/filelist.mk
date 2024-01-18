#***************************************************************************************
# Copyright (c) 2014-2022 Zihao Yu, Nanjing University
#
# NEMU is licensed under Mulan PSL v2.
# You can use this software according to the terms and conditions of the Mulan PSL v2.
# You may obtain a copy of Mulan PSL v2 at:
#          http://license.coscl.org.cn/MulanPSL2
#
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
# EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
# MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
#
# See the Mulan PSL v2 for more details.
#**************************************************************************************/

ifdef CONFIG_HAS_SERIAL
CXXSRC += src/device/spike/serial.cc
endif

ifdef CONFIG_HAS_TIMER
CXXSRC += src/device/spike/rtc.cc
endif

CXXSRC += src/device/spike/dts.cc
CXXSRC += src/device/spike/invoke.cc

CXXFLAGS += -Wno-format-security
CXXFLAGS += -I$(SPIKE_HOME)
CXXFLAGS += -I$(SPIKE_HOME)/build
CXXFLAGS += -I$(SPIKE_HOME)/riscv
LIBS += -lSDL2

ifndef CONFIG_TARGET_SPIKE_DEVICES
DIRS-y += src/device/io
SRCS-$(CONFIG_DEVICE) += src/device/device.c src/device/alarm.c src/device/intr.c
SRCS-$(CONFIG_HAS_KEYBOARD) += src/device/keyboard.c
SRCS-$(CONFIG_HAS_VGA) += src/device/vga.c
SRCS-$(CONFIG_HAS_AUDIO) += src/device/audio.c
SRCS-$(CONFIG_HAS_DISK) += src/device/disk.c
SRCS-$(CONFIG_HAS_SDCARD) += src/device/sdcard.c
SRCS-BLACKLIST-$(CONFIG_TARGET_AM) += src/device/alarm.c

ifdef CONFIG_DEVICE
ifndef CONFIG_TARGET_AM
LIBS += -lSDL2
endif
endif

endif