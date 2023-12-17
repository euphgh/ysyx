#include "dts.h"
#include "libfdt.h"
#include "nemu_dts.hh"

int fdt_parse_ntimer(const void *fdt, reg_t *rtc_addr, const char *compatible) {
  int nodeoffset;
  int rc;

  nodeoffset = fdt_node_offset_by_compatible(fdt, -1, compatible);
  if (nodeoffset < 0)
    return nodeoffset;

  rc = fdt_get_node_addr_size(fdt, nodeoffset, rtc_addr, nullptr, "reg");
  if (rc < 0 || !rtc_addr)
    return -ENODEV;

  return 0;
}

int fdt_parse_nserial(const void *fdt, reg_t *rtc_addr,
                      const char *compatible) {
  int nodeoffset;
  int rc;

  nodeoffset = fdt_node_offset_by_compatible(fdt, -1, compatible);
  if (nodeoffset < 0)
    return nodeoffset;

  rc = fdt_get_node_addr_size(fdt, nodeoffset, rtc_addr, nullptr, "reg");
  if (rc < 0 || !rtc_addr)
    return -ENODEV;

  return 0;
}