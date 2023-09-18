#include <fs.h>

typedef size_t (*ReadFn) (void *buf, size_t offset, size_t len);
typedef size_t (*WriteFn) (const void *buf, size_t offset, size_t len);

typedef struct {
  char *name;
  size_t size;
  size_t disk_offset;
  ReadFn read;
  WriteFn write;
  size_t openOffset;
} Finfo;

enum { FD_STDIN, FD_STDOUT, FD_STDERR, FD_FB, FD_KB };

size_t events_read(void *buf, size_t offset, size_t len);
size_t invalid_read(void *buf, size_t offset, size_t len) {
  panic("should not reach here");
  return 0;
}

size_t serial_write(const void *buf, size_t offset, size_t len);
size_t invalid_write(const void *buf, size_t offset, size_t len) {
  panic("should not reach here");
  return 0;
}

/* This is the information about all files in disk. */
static Finfo file_table[] __attribute__((used)) = {
    [FD_STDIN] = {"stdin", SIZE_MAX, 0, invalid_read, invalid_write, 0},
    [FD_STDOUT] = {"stdout", SIZE_MAX, 0, invalid_read, serial_write, 0},
    [FD_STDERR] = {"stderr", SIZE_MAX, 0, invalid_read, serial_write, 0},
    [FD_FB] = {"/dev/vga", SIZE_MAX, 0, invalid_read, invalid_write, 0},
    [FD_KB] = {"/dev/events", SIZE_MAX, 0, events_read, invalid_write, 0},
#include "files.h"
};

size_t ramdisk_read(void *buf, size_t offset, size_t len);
size_t ramdisk_write(const void *buf, size_t offset, size_t len);
size_t get_ramdisk_size();

void init_fs() {
  for (size_t i = FD_KB + 1; i < sizeof(file_table) / sizeof(file_table[0]);
       i++) {
    file_table[i].write = ramdisk_write;
    file_table[i].read = ramdisk_read;
  }
  // TODO: initialize the size of /dev/fb
}

int fs_open(const char *pathname, int flags, int mode) {
  for (int i = 0; i < sizeof(file_table) / sizeof(file_table[0]); i++) {
    if (strcmp(pathname, file_table[i].name) == 0) {
      file_table[i].openOffset = 0;
      return i;
    }
  }
  panic("Not found file %s", pathname);
}
size_t fs_read(int fd, void *buf, size_t len) {
  size_t leftBytes = file_table[fd].size - file_table[fd].openOffset;
  size_t rbNum = leftBytes >= len ? len : leftBytes;
  rbNum = file_table[fd].read(
      buf, file_table[fd].disk_offset + file_table[fd].openOffset, rbNum);
  file_table[fd].openOffset += rbNum;
  return rbNum;
}
size_t fs_write(int fd, const void *buf, size_t len) {
  size_t leftBytes = file_table[fd].size - file_table[fd].openOffset;
  size_t wbNum = leftBytes >= len ? len : leftBytes;
  wbNum = file_table[fd].write(
      buf, file_table[fd].disk_offset + file_table[fd].openOffset, wbNum);
  file_table[fd].openOffset += wbNum;
  return wbNum;
}
size_t fs_lseek(int fd, size_t offset, int whence) {
  size_t res = -1;
  switch (whence) {
  case SEEK_SET:
    res = offset;
    file_table[fd].openOffset = res;
    break;
  case SEEK_CUR:
    res = file_table[fd].openOffset + offset;
    file_table[fd].openOffset = res;
    break;
  case SEEK_END:
    res = file_table[fd].size + offset;
    file_table[fd].openOffset = res;
    break;
  }
  return res;
}
int fs_close(int fd) { return 0; }
const char *fs_pathname(int fd) {
  if ((fd >= 0) && (fd <= sizeof(file_table) / sizeof(file_table[0])))
    return file_table[fd].name;
  else
    return "No File";
}