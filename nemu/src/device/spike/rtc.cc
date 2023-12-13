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

void add_mmio_map(const char *name, paddr_t addr, void *space, uint32_t len,
                  abstract_device_t *callback);

ntimer_t::ntimer_t(bool is_nemu, const char *_name, paddr_t _paddr_start)
    : paddr_start(_paddr_start), name(_name) {
  if (is_nemu) {
    init_timer();
  } else {
    rtc_port_base = (uint32_t *)malloc(8);
  }
}

bool ntimer_t::load(reg_t addr, size_t len, uint8_t *bytes) {
  return load_store(addr, len, bytes, false);
}

bool ntimer_t::store(reg_t addr, size_t len, const uint8_t *bytes) {
  return load_store(addr, len, const_cast<uint8_t *>(bytes), true);
}

[[nodiscard]] reg_t ntimer_t::get_paddr() const { return paddr_start; }

ntimer_t ntimer_t::nemu_instance() { return {true}; }

void ntimer_t::rtc_io_handler(uint32_t offset, int len, bool is_write) const {
  assert(offset == 0 || offset == 4);
  if (!is_write && offset == 4) {
    uint64_t us = get_time();
    rtc_port_base[0] = (uint32_t)us;
    rtc_port_base[1] = us >> 32;
  }
}

bool ntimer_t::load_store(reg_t addr, size_t len, uint8_t *bytes,
                          bool is_store) {
  try {
    check_size(len);
    rtc_io_handler(addr, (int)len, is_store);
  } catch (...) {
    return false;
  }
  if (is_store)
    memcpy(rtc_port_base + addr, bytes, len);
  else
    memcpy(bytes, rtc_port_base + addr, len);
  return true;
}

void ntimer_t::check_size(size_t len) {
  if (len == 0 || len > 8)
    throw nemu_device_exception();
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

extern "C" void nemu_device_assert(bool cond) { throw nemu_device_exception(); }

ntimer_t *ntimer_parse_from_fdt(const void *fdt, const sim_t *sim, reg_t *base,
                                const std::vector<std::string> &sargs) {
  if (fdt_parse_ntimer(fdt, base, "nemu,rtc") == 0)
    return new ntimer_t();
  return nullptr;
}

std::string ntimer_generate_dts(const sim_t *sim) {
  const char *fmt =
      R"(
  rtc: rtc@%x {
		compatible = "nemu,rtc";
		reg = <0x0 %x 0x0 %x>;
	};
  )";
  std::array<char, 64> buf;
  std::sprintf(buf.data(), fmt, CONFIG_RTC_MMIO, CONFIG_RTC_MMIO, 8);
  return buf.data();
}

REGISTER_DEVICE(ntimer, ntimer_parse_from_fdt, ntimer_generate_dts)