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

enum {FD_STDIN, FD_STDOUT, FD_STDERR, FD_FB};

size_t invalid_read(void *buf, size_t offset, size_t len) {
  panic("should not reach here");
  return 0;
}

size_t invalid_write(const void *buf, size_t offset, size_t len) {
  panic("should not reach here");
  return 0;
}

/* This is the information about all files in disk. */
static Finfo file_table[] __attribute__((used)) = {
  [FD_STDIN]  = {"stdin", 0, 0, invalid_read, invalid_write},
  [FD_STDOUT] = {"stdout", 0, 0, invalid_read, invalid_write},
  [FD_STDERR] = {"stderr", 0, 0, invalid_read, invalid_write},
#include "files.h"
};

size_t ramdisk_read(void *buf, size_t offset, size_t len);
size_t ramdisk_write(const void *buf, size_t offset, size_t len);
size_t get_ramdisk_size();

void init_fs() {
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
  ramdisk_read(buf, file_table[fd].disk_offset + file_table[fd].openOffset,
               rbNum);
  file_table[fd].openOffset += rbNum;
  return rbNum;
}
size_t fs_write(int fd, const void *buf, size_t len) {
  size_t leftBytes = file_table[fd].size - file_table[fd].openOffset;
  size_t rbNum = leftBytes >= len ? len : leftBytes;
  ramdisk_write(buf, file_table[fd].disk_offset + file_table[fd].openOffset,
                rbNum);
  return rbNum;
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
const char *fs_pathname(int fd) { return file_table[fd].name; }