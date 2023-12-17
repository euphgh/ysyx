#ifndef __DEVICE_SPIKE_INVOKE_HH__
#define __DEVICE_SPIKE_INVOKE_HH__

#ifdef __cplusplus
extern "C" {
#endif

#include "common.h"

bool invoke_load(void *instance, word_t addr, size_t len, uint8_t *bytes);
bool invoke_store(void *instance, word_t addr, size_t len,
                  const uint8_t *bytes);

#ifdef __cplusplus
}
#endif

#endif