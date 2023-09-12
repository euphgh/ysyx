from csrgen import *

AllCRS = [
    CSR(
        "mtvec",
        0x305,
        [
            FLD("base", WORD_LEN - 1, 2, "WARL"),
            FLD("mode", 1, 0, "WARL"),
        ],
    ),
    CSR(
        "mepc",
        0x341,
        [
            FLD("all", WORD_LEN - 1, 0, "WARL"),
        ],
    ),
    CSR(
        "mcause",
        0x342,
        [
            FLD("interrupted", WORD_LEN - 1, WORD_LEN - 1, "READ"),
            FLD("exceptionCode", WORD_LEN - 2, 0, "WLRL"),
        ],
    ),
    CSR(
        "mstatus",
        0x300,
        [
            FLD("mie", 3, 3, "WARL"),
            FLD("ube", 6, 6, "READ"),
            FLD("mpie", 7, 7, "WARL",),
            FLD("mpp", 12, 11, "WARL", 0b11),
            FLD("fs", 14, 13, "WARL"),
            FLD("uxl", 33, 32, "READ", 0x2),
            FLD("sxl", 35, 34, "READ", 0x2),
            FLD("sbe", 36, 36, "READ", 0x0),
            FLD("mbe", 37, 37, "READ", 0x0),
        ],
    ),
]

from sys import argv

if __name__ == "__main__":
    paser = PaserCSR(AllCRS)
    if argv[1] == "h":
        print(paser.headCode())
    elif argv[1] == "s":
        print(paser.srcCode())