from enum import auto, Enum
from pdb import set_trace

WORD_LEN = 64
RONL = auto()
WPRI = auto()
WLRL = auto()  # not legal value will not write
WARL = auto()  # not legal value will not write

class CSR(Enum):
    NAME = 0
    NUM = 1
    FIELDS = 2
    PRI = 3

class FIELD(Enum):
    NAME = 0
    MSB = 1
    LSB = 2
    INIT = 3
    WATTR = 4
    LEG_FUNC = 5

AllCRS = [
    (
        "mtvec",
        0x305,
        [
            ("base", WORD_LEN - 1, 2, WARL),
            ("mode", 1, 0, WARL),
        ],
    ),
    (
        "mepc",
        0x341,
        [
            ("all", WORD_LEN - 1, 0, WARL),
        ],
    ),
    (
        "mcause",
        0x342,
        [
            ("interrupted", WORD_LEN - 1, WORD_LEN - 1, RONL),
            ("exceptionCode", WORD_LEN - 2, 0, WLRL),
        ],
    ),
    (
        "mstatus",
        0x300,
        [
            ("mie", 3, 3, WARL),
            ("mpie", 7, 7, WARL),
            ("mpp", 12, 11, WARL),
        ],
    ),
]

headCode = []
srcCode = []

srcVarDef = "{name:s}_t {name:s};"
headTypeDef = (
    """
typedef union {{
    struct {{
        {fields:s}
    }};
    word_t val;
}} {name:s}_t;
extern 
"""
    + srcVarDef
)

csrWriteFmt = """
static bool {csrName:s}Write(word_t val) {{
    {csrName:s}_t newVar = {{.val = val}};
    {code:s}
}}
"""


csrOpFunc = "bool {name:s}RW(word_t* rd, word_t src1, csrOp op)"
csrOpDefFmt = csrOpFunc + ";"
csrOpImpFmt = (
    csrOpFunc
    + """{{
    if (rd) *rd = {name:s}.val;
    switch (op) {{
        case csrSET: res = {name:s}Write({name:s}.val | src1); break;
        case csrCLR: res = {name:s}Write({name:s}.val & ~src1); break;
        case csrWAR: res = {name:s}Write(src1); break;
    }}
    return res
}}"""
)

csrrwFunc = "bool csrRW(int csrDst, word_t* rd, word_t src1, csrOp op)"
csrrwDefFmt = csrrwFunc + ";"
csrrwImpFmt = (
    csrrwFunc
    + """{{
    bool res;
    switch (csrDst) {{
        {code:s}
        default: res = false; break;
    }}
    return res
}}"""
)


def genDefTypeLine(name, width):
    return "word_t {:s}: {:d};".format(name, width)


def genWriteFuncLine(field: tuple):
    return (
        (
            (
                "if ({func:s}(newVar.{fieldName:s})) {{ {csrName:s}.{fieldName:s} = newVar.{fieldName:s}; }}".format(
                    func=field[FIELD.LEG_FUNC], fieldName=field[FIELD.NAME], csrName=csr[CSR.NAME]
                )
                if (len(field) == len(FIELD))
                else "{csrName:s}.{fieldName:s} = newVar.{fieldName:s};".format(
                    fieldName=field[FIELD.NAME], csrName=csr[CSR.NAME]
                )
            )
        )
        if (field[FIELD.WATTR] == WLRL) or (field[FIELD.WATTR] == WARL)
        else ""
    )


csrrwInner = []

for csr in AllCRS:
    defTypeInner = []
    writeInner = []
    fillList = [-1 for i in range(WORD_LEN + 1)]
    fillList[WORD_LEN] = -2

    for idx in range(len(csr[CSR.FIELDS])):
        field = csr[CSR.FIELDS][idx]
        for bits in range(field[FIELD.LSB], field[FIELD.MSB] + 1):
            fillList[bits] = idx

    startBits = 0
    for i in range(1, WORD_LEN + 1):
        if fillList[i - 1] != fillList[i]:
            endBits = i - 1
            field = (
                ("r{:d}to{:d}".format(endBits, startBits), endBits, startBits, RONL)
                if (fillList[endBits] < 0)
                else csr[CSR.FIELDS][fillList[endBits]]
            )
            defTypeInner.append(genDefTypeLine(field[FIELD.NAME], endBits - startBits + 1))
            writeInner.append(genWriteFuncLine(field))
            print("gen field", field)
            startBits = i

    # for field in csr[2]:
    #     defTypeInner.append(
    #         "word_t {:s}: {:d};".format(field[0], field[1] - field[2] + 1)
    #     )
    #     writeLine = (
    #         (
    #             (
    #                 "if ({func:s}(newVar.{fieldName:s})) {{ {csrName:s}.{fieldName:s} = newVar.{fieldName:s}; }}".format(
    #                     func=field[4], fieldName=field[0], csrName=csr[0]
    #                 )
    #                 if (len(field) == 5)
    #                 else "{csrName:s}.{fieldName:s} = newVar.{fieldName:s};".format(
    #                     fieldName=field[0], csrName=csr[0]
    #                 )
    #             )
    #         )
    #         if (field[3] == WLRL) or (field[3] == WARL)
    #         else ""
    #     )
    #     writeInner.append(writeLine)

    # define code
    headCode.append(headTypeDef.format(name=csr[CSR.NAME], fields="\n".join(defTypeInner)))
    srcCode.append(srcVarDef.format(name=csr[CSR.NAME]))

    srcCode.append(csrWriteFmt.format(csrName=csr[CSR.NAME], code="\n".join(writeInner)))

    headCode.append(csrOpDefFmt.format(name=csr[CSR.NAME]))
    srcCode.append(csrOpImpFmt.format(name=csr[CSR.NAME]))

    csrrwInner.append(
        "case 0x{:x}: res = {:s}rw(rd, src1, op); break;".format(csr[CSR.NUM], csr[CSR.NAME])
    )

headCode.append(csrrwDefFmt)
srcCode.append(csrrwImpFmt.format(code="\n".join(csrrwInner)))

print("\n".join(headCode))
print("\n".join(srcCode))

# with open("./src/isa/riscv32/local-include/csrDefine.h", "w") as headFile:
#     headFile.write("\n".join(headCode))

# with open("./src/isa/riscv32/local-include/csrImplement.h", "w") as srcFile:
#     srcFile.write("\n".join(srcCode))
