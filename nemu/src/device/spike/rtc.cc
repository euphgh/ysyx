#include "nemu_device.hh"
#include "nemu_dts.hh"
#include <array>

#define RTC_REG_SIZE 8
ntimer_t::ntimer_t(bool is_nemu, const char *_name, paddr_t _paddr_start)
    : nemu_device_t(_name, _paddr_start) {
  this->len = 8;
  if (!is_nemu) {
    rtc_port_base = (uint32_t *)malloc(len);
    io_regs = rtc_port_base;
  }
}

void ntimer_t::callback(uint32_t offset, int len, bool is_write) {
  assert(offset == 0 || offset == 4);
  if (!is_write && offset == 4) {
    uint64_t us = get_time();
    rtc_port_base[0] = (uint32_t)us;
    rtc_port_base[1] = us >> 32;
  }
}

#ifdef CONFIG_TARGET_NATIVE_ELF

extern "C" void dev_raise_intr();
static void timer_intr() {
  if (nemu_state.state == NEMU_RUNNING) {
    dev_raise_intr();
  }
}
void ntimer_t::init_timer() {
  rtc_port_base = (uint32_t *)new_space(len);
  io_regs = rtc_port_base;
  IFNDEF(CONFIG_TARGET_AM, add_alarm_handle(timer_intr));
}

#else
void ntimer_t::init_timer() {
  printf("not in nemu, should not call %s\n", __func__);
}
#endif

ntimer_t *ntimer_parse_from_fdt(const void *fdt, const sim_t *sim, reg_t *base,
                                const std::vector<std::string> &sargs) {
  if (fdt_parse_ntimer(fdt, base, "nemu,rtc") == 0) {
    auto *ret = new ntimer_t();
    *base = ret->paddr_start;
    return ret;
  }
  return nullptr;
}

std::string ntimer_generate_dts(const sim_t *sim) {
  const char *fmt = R"(
  rtc: rtc@%x {
		compatible = "nemu,rtc";
		reg = <0x0 %#x 0x0 %#x>;
	};
  )";
  std::array<char, 256> buf;
  std::sprintf(buf.data(), fmt, CONFIG_RTC_MMIO, CONFIG_RTC_MMIO, 8);
  return buf.data();
}

NEMU_REGISTER_DEVICE(ntimer, timer, ntimer_parse_from_fdt, ntimer_generate_dts)