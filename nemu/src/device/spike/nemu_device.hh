#ifndef __NEMU_DEVICE_INCLUDE_NEMU_DEVICE_H__
#define __NEMU_DEVICE_INCLUDE_NEMU_DEVICE_H__

#include <cstddef>
extern "C" {
#include "device/alarm.h"
#include "device/map.h"
#include "utils.h"
#undef str
#undef log_write
#undef likely
#undef unlikely
}

#include "abstract_device.h"
#include <memory>
#include <stdexcept>

class nemu_device_t : public abstract_device_t {
public:
  bool load(reg_t addr, size_t len, uint8_t *bytes) override {
    return load_store(addr, len, bytes, false);
  }
  bool store(reg_t addr, size_t len, const uint8_t *bytes) override {
    return load_store(addr, len, const_cast<uint8_t *>(bytes), true);
  }
  std::string name;
  uintptr_t paddr_start;
  int len;

protected:
  nemu_device_t(const char *_name, uintptr_t _paddr_start)
      : name(_name), paddr_start(_paddr_start) {}
  nemu_device_t(std::string &_name, uintptr_t _paddr_start)
      : name(_name), paddr_start(_paddr_start) {}
  void *io_regs = nullptr;
  virtual void callback(uint32_t, int, bool) = 0;
  bool load_store(reg_t addr, size_t len, uint8_t *bytes, bool is_store) {
    try {
      callback(addr, (int)len, is_store);
    } catch (...) {
      return false;
    }
    if (is_store)
      memcpy((uint8_t *)io_regs + addr, bytes, len);
    else
      memcpy(bytes, (uint8_t *)io_regs + addr, len);
    return true;
  }
};

class ntimer_t : public nemu_device_t {
public:
  ntimer_t(bool is_nemu = false, const char *_name = "rtc",
           paddr_t _paddr_start = MUXDEF(CONFIG_HAS_TIMER, CONFIG_RTC_MMIO, 0));

  void init_timer();

private:
  uint32_t *rtc_port_base = nullptr;

  void callback(uint32_t offset, int len, bool is_write) override;
};

class nserial_t : public nemu_device_t {
public:
  nserial_t(bool is_nemu = false, const char *_name = "serial",
            paddr_t _paddr_start = MUXDEF(CONFIG_HAS_SERIAL, CONFIG_SERIAL_MMIO,
                                          0));
  void init_serial();

private:
  uint32_t *serial_base = nullptr;

  void callback(uint32_t offset, int len, bool is_write) override;
};

extern device_factory_t *nserial_factory;
class nemu_device_exception : public std::exception {
public:
  template <typename... Args>
  nemu_device_exception(const std::string &format, Args... args) {
    int size_s = std::snprintf(nullptr, 0, format.c_str(), args...) +
                 1; // Extra space for '\0'
    if (size_s <= 0) {
      throw std::runtime_error("Error during formatting.");
    }
    auto size = static_cast<size_t>(size_s);
    msg = new char[size];
    std::snprintf(msg, size, format.c_str(), args...);
  };
  [[nodiscard]] const char *what() const noexcept override { return msg; }

private:
  char *msg;
};

#define NDEVICE_PANIC(fmt, ...) throw nemu_device_exception(fmt, ##__VA_ARGS__)

#define NEMU_REGISTER_DEVICE(name, nemu_name, parse, generate)                 \
  class name##_factory_t : public device_factory_t {                           \
  public:                                                                      \
    name##_factory_t() {                                                       \
      std::string str(#name);                                                  \
      IFDEF(CONFIG_TARGET_SPIKE_DEVICES,                                       \
            if (!mmio_device_map().emplace(str, this).second) throw std::      \
                runtime_error("Plugin \"" + str + "\" already registered"));   \
    };                                                                         \
    name##_t *parse_from_fdt(const void *fdt, const sim_t *sim,                \
                             reg_t *base) const override {                     \
      return parse(fdt, sim, base, sargs);                                     \
    }                                                                          \
    std::string generate_dts(const sim_t *sim) const override {                \
      return generate(sim);                                                    \
    }                                                                          \
  };                                                                           \
  device_factory_t *name##_factory = new name##_factory_t();                   \
  extern "C" void init_##nemu_name() {                                         \
    name##_t *instance = new name##_t{true};                                   \
    instance->init_##nemu_name();                                              \
    IFNDEF(CONFIG_TARGET_SPIKE_DEVICES,                                        \
           MUXDEF(CONFIG_HAS_PORT_IO, add_pio_map, add_mmio_map))              \
    IFNDEF(CONFIG_TARGET_SPIKE_DEVICES,                                        \
           (#nemu_name, instance->paddr_start, instance->len, instace));       \
  }

#endif