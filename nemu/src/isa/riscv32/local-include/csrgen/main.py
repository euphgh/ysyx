from csrgen import *

mstatusFields = [
    # xIE
    FLD("uie", 0, 0, "WARL"),
    FLD("sie", 1, 1, "WARL"),
    # for h extension
    FLD("mie", 3, 3, "WARL"),
    # xPIE
    FLD("upie", 4, 4, "WARL"),
    FLD("spie", 5, 5, "WARL"),
    FLD("ube", 6, 6, "READ", 0x0),
    # for h extension
    FLD("mpie", 7, 7, "WARL"),
    # xPP
    FLD("spp", 8, 8, "WARL", 0b00),
    FLD("vs", 10, 9, "READ", 0x0),
    FLD("mpp", 12, 11, "WARL", 0b11),
    FLD("fs", 14, 13, "READ", 0x0),
    FLD("xs", 16, 15, "READ", 0x0),
    FLD("mprv", 17, 17, "WARL", 0x0),
    FLD("sum", 18, 18, "WARL", 0x0),
    FLD("mxr", 19, 19, "WARL", 0x0),
    FLD("uxl", 33, 32, "READ", 0x2),
    FLD("sxl", 35, 34, "READ", 0x2),
    # Endianess
    FLD("sbe", 36, 36, "READ", 0x0),
    FLD("mbe", 37, 37, "READ", 0x0),
    FLD("sd", WORD_LEN - 1, WORD_LEN - 1, "READ", 0x0),
]

mtvecFields = [
    FLD("base", WORD_LEN - 1, 2, "WARL"),
    FLD("mode", 1, 0, "WARL"),
]
mcauseFields = [
    FLD("interrupted", WORD_LEN - 1, WORD_LEN - 1, "READ"),
    FLD("exceptionCode", WORD_LEN - 2, 0, "WLRL"),
]

AllCRS: list[CtrlStatReg] = [
    CSR("mtvec", 0x305, mtvecFields),
    CSR("stvec", 0x105, mtvecFields),

    CSR("mepc", 0x341),
    CSR("sepc", 0x141),

    CSR("mcause", 0x342, mcauseFields),
    CSR("scause", 0x142, mcauseFields),

    CSR("medeleg", 0x302),

    CSR("mtval", 0x343),
    CSR("stval", 0x143),

    CSR("mstatus", 0x300, mstatusFields),
    CSR(
        "sstatus",
        0x100,
        removeFields(
            mstatusFields,
            [
                "mie",
                "mpie",
                "mpp",
                "mprv",
                "sxl",
                "sbe",
                "mbe",
            ],
        ),
        autoPtr=False,
        beforeRead="sstatusRead",
    ),

    CSR("mscratch", 0x340),
    CSR("sscratch", 0x140),

    CSR("satp", 0x180,[
        FLD("mode", 63, 60, "WARL", 0x0, legalFunc="isLegalPageMode"),
        FLD("asid", 59, 44, "WARL", 0x0),
        FLD("ppn", 43, 0, "WARL", 0x0),
    ]),
]

from sys import argv

if __name__ == "__main__":
    paser = PaserCSR(AllCRS)
    if argv[1] == "h":
        print(paser.headCode())
    elif argv[1] == "s":
        print(paser.srcCode())