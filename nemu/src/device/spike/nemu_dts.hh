#ifndef __DEVICE_SPIKE_DTS_HPP__
#define __DEVICE_SPIKE_DTS_HPP__

#include "decode.h"

int fdt_parse_ntimer(const void *fdt, reg_t *rtc_addr, const char *compatible);

#endif