#include "invoke.hh"
#include "abstract_device.h"
#include "nemu_device.hh"

extern "C" bool invoke_load(void *instance, word_t addr, size_t len,
                            uint8_t *bytes) {
  auto *obj = static_cast<abstract_device_t *>(instance);
  return obj->load(addr, len, bytes);
}

extern "C" bool invoke_store(void *instance, word_t addr, size_t len,
                             const uint8_t *bytes) {
  auto *obj = static_cast<abstract_device_t *>(instance);
  return obj->store(addr, len, bytes);
}