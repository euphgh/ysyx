#include "nemu_device.hh"
#include "nemu_dts.hh"
#include <array>

#define CH_OFFSET 0
#define SERIAL_REG_SIZE 8

static void serial_putc(uint8_t ch) {
  MUXDEF(CONFIG_TARGET_AM, putch(ch), putc(ch, stderr));
}

void nserial_t::callback(uint32_t offset, int len, bool is_write) {
  assert(len == 1);
  switch (offset) {
  /* We bind the serial port with the host stderr in NEMU. */
  case CH_OFFSET:
    if (is_write)
      serial_putc(serial_base[0]);
    else
      NDEVICE_PANIC("do not support read");
    break;
  default:
    NDEVICE_PANIC("do not support offset = %d", offset);
  }
}

void nserial_t::init_serial() {
  serial_base = (uint32_t *)new_space(SERIAL_REG_SIZE);
#ifdef CONFIG_HAS_PORT_IO
  add_pio_map("serial", CONFIG_SERIAL_PORT, serial_base, SERIAL_REG_SIZE,
              serial_io_handler);
#else
  add_mmio_map("serial", CONFIG_SERIAL_MMIO, serial_base, SERIAL_REG_SIZE,
               this);
#endif
}

nserial_t::nserial_t(bool is_nemu, const char *_name, paddr_t _paddr_start)
    : nemu_device_t(_name, _paddr_start) {
  if (is_nemu) {
    init_serial();
  } else {
    serial_base = (uint32_t *)malloc(SERIAL_REG_SIZE);
  }
  io_regs = serial_base;
}

nserial_t nserial_t::nemu_instance() { return {true}; }

nserial_t *nserial_parse_from_fdt(const void *fdt, const sim_t *sim,
                                  reg_t *base,
                                  const std::vector<std::string> &sargs) {
  if (fdt_parse_nserial(fdt, base, "nemu,rtc") == 0)
    return new nserial_t();
  return nullptr;
}

std::string nserial_generate_dts(const sim_t *sim) {
  const char *fmt = R"(
  serial@%x {
      compatible = "ns8250";
      reg = <%#x %#x>;
      /* not support interrupt now */
      // interrupts = <10>;
      reg-shift = <2>;
      clock-frequency = <48000000>;
  };)";
  std::array<char, 64> buf;
  std::sprintf(buf.data(), fmt, CONFIG_RTC_MMIO, CONFIG_RTC_MMIO,
               SERIAL_REG_SIZE);
  return buf.data();
}

REGISTER_DEVICE(nserial, nserial_parse_from_fdt, nserial_generate_dts)