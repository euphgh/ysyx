#include "nemu_device.hh"
#include "nemu_dts.hh"
#include <array>

#define CH_OFFSET 0
#define SERIAL_REG_SIZE 8

nserial_t::nserial_t(bool is_nemu, const char *_name, paddr_t _paddr_start)
    : nemu_device_t(_name, _paddr_start) {
  this->len = SERIAL_REG_SIZE;
  if (!is_nemu) {
    serial_base = (uint32_t *)malloc(SERIAL_REG_SIZE);
    io_regs = serial_base;
  }
}

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
#ifdef CONFIG_TARGET_NATIVE_ELF

  serial_base = (uint32_t *)new_space(SERIAL_REG_SIZE);
  io_regs = serial_base;

#else
  printf("not in nemu, should not call %s\n", __func__);
#endif
}

nserial_t *nserial_parse_from_fdt(const void *fdt, const sim_t *sim,
                                  reg_t *base,
                                  const std::vector<std::string> &sargs) {
  if (fdt_parse_nserial(fdt, base, "nemu,rtc") == 0) {
    auto *ret = new nserial_t();
    *base = ret->paddr_start;
    return ret;
  }
  return nullptr;
}

std::string nserial_generate_dts(const sim_t *sim) {
  const char *fmt = R"(
  serial: serial@%x {
      compatible = "ns8250";
      reg = <0x0 %#x 0x0 %#x>;
      /* not support interrupt now */
      // interrupts = <10>;
      reg-shift = <2>;
      clock-frequency = <48000000>;
  };
  )";
  std::array<char, 256> buf;
  std::sprintf(buf.data(), fmt, CONFIG_SERIAL_MMIO, CONFIG_SERIAL_MMIO,
               SERIAL_REG_SIZE);
  return buf.data();
}

NEMU_REGISTER_DEVICE(nserial, serial, nserial_parse_from_fdt,
                     nserial_generate_dts)