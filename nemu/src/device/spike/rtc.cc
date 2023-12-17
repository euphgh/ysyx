#include "nemu_device.hh"
#include "nemu_dts.hh"
#include "sim.h"

#ifndef CONFIG_TARGET_AM
static void timer_intr() {
  if (nemu_state.state == NEMU_RUNNING) {
    extern void dev_raise_intr();
    dev_raise_intr();
  }
}
#endif

ntimer_t::ntimer_t(bool is_nemu, const char *_name, paddr_t _paddr_start)
    : nemu_device_t(_name, _paddr_start) {
  if (is_nemu) {
    init_timer();
  } else {
    rtc_port_base = (uint32_t *)malloc(8);
  }
  io_regs = rtc_port_base;
}

ntimer_t ntimer_t::nemu_instance() { return {true}; }

void ntimer_t::callback(uint32_t offset, int len, bool is_write) {
  assert(offset == 0 || offset == 4);
  if (!is_write && offset == 4) {
    uint64_t us = get_time();
    rtc_port_base[0] = (uint32_t)us;
    rtc_port_base[1] = us >> 32;
  }
}

void ntimer_t::init_timer() {
  rtc_port_base = (uint32_t *)new_space(8);
#ifdef CONFIG_HAS_PORT_IO
  add_pio_map("rtc", CONFIG_RTC_PORT, rtc_port_base, 8, rtc_io_handler);
#else
  add_mmio_map("rtc", CONFIG_RTC_MMIO, rtc_port_base, 8, this);
#endif
  IFNDEF(CONFIG_TARGET_AM, add_alarm_handle(timer_intr));
}

ntimer_t *ntimer_parse_from_fdt(const void *fdt, const sim_t *sim, reg_t *base,
                                const std::vector<std::string> &sargs) {
  if (fdt_parse_ntimer(fdt, base, "nemu,rtc") == 0)
    return new ntimer_t();
  return nullptr;
}

std::string ntimer_generate_dts(const sim_t *sim) {
  const char *fmt = R"(
  rtc: rtc@%x {
		compatible = "nemu,rtc";
		reg = <0x0 %x 0x0 %x>;
	};)";
  std::array<char, 64> buf;
  std::sprintf(buf.data(), fmt, CONFIG_RTC_MMIO, CONFIG_RTC_MMIO, 8);
  return buf.data();
}

REGISTER_DEVICE(ntimer, ntimer_parse_from_fdt, ntimer_generate_dts)