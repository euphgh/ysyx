#ifndef __NEMU_DEVICE_INCLUDE_NEMU_DEVICE_H__
#define __NEMU_DEVICE_INCLUDE_NEMU_DEVICE_H__

#include "abstract_device.h"
extern "C" {
#include "device/alarm.h"
#include "device/map.h"
#include "utils.h"
#undef str
#undef log_write
}

class ntimer_t : public abstract_device_t {
public:
  ntimer_t(bool is_nemu = false, const char *_name = "rtc",
           paddr_t _paddr_start = CONFIG_RTC_MMIO);

  bool load(reg_t addr, size_t len, uint8_t *bytes) override;

  bool store(reg_t addr, size_t len, const uint8_t *bytes) override;

  [[nodiscard]] reg_t get_paddr() const;

  static ntimer_t nemu_instance();

private:
  uint32_t *rtc_port_base = nullptr;
  uintptr_t paddr_start;
  std::string name;

  void rtc_io_handler(uint32_t offset, int len, bool is_write) const;

  bool load_store(reg_t addr, size_t len, uint8_t *bytes, bool is_store);

  static void check_size(size_t len);
  void init_timer();
};

class nemu_device_exception : public std::exception {
public:
  nemu_device_exception() = default;
};

#endif