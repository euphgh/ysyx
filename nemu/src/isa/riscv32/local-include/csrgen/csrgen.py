from enum import auto, Enum

WORD_LEN = 64


class FieldSpec(Enum):
    READ = auto()
    WPRI = auto()
    WLRL = auto()
    WARL = auto()


class Field:
    def __init__(
        self,
        name: str,
        msb: int,
        lsb: int,
        spec: FieldSpec,
        init: int,
        legalFunc: str,
    ) -> None:
        self.name = name
        self.msb = msb
        self.lsb = lsb
        self.spec = spec
        self.init = init
        self.legalFunc = legalFunc

    def __str__(self) -> str:
        return "({:s} [{:d}, {:d}] = {:d}, {:s})".format(
            self.name,
            self.msb,
            self.lsb,
            self.init,
            self.spec,
        )

    def structDef(self) -> str:
        return "word_t {:s}: {:d};".format(self.name, self.msb - self.lsb + 1)

    def writeLine(self, csrName) -> str:
        return (
            (
                (
                    "if ({func:s}(newVar.{fieldName:s})) {{ {csrName:s}.{fieldName:s} = newVar.{fieldName:s}; }}".format(
                        func=self.legalFunc, fieldName=self.name, csrName=csrName
                    )
                    if (self.legalFunc != None)
                    else "{csrName:s}.{fieldName:s} = newVar.{fieldName:s};".format(
                        fieldName=self.name, csrName=csrName
                    )
                )
            )
            if (self.spec == FieldSpec.WARL) or (self.spec == FieldSpec.WLRL)
            else ""
        )

    def initValue(self) -> int:
        return self.init << self.lsb


srcVarDef = "{name:s}_t {name:s};"
headTypeDef = (
    """
typedef union {{
    struct {{
        {fields:s}
    }};
    word_t val;
}} {name:s}_t;
extern """
    + srcVarDef
)
csrOpFunc = "bool {name:s}RW(word_t* rd, word_t src1, csrOp op)"
csrOpDefFmt = csrOpFunc + ";"
csrOpImpFmt = (
    csrOpFunc
    + """{{
    bool res = true;
    if (rd) *rd = {name:s}.val;
    switch (op) {{
        case csrSET: res = {name:s}Write({name:s}.val | src1); break;
        case csrCLR: res = {name:s}Write({name:s}.val & ~src1); break;
        case csrWAR: res = {name:s}Write(src1); break;
    }}
    return res;
}}"""
)

csrrwFunc = "bool csrRW(int csrDst, word_t* rd, word_t src1, csrOp op)"
csrrwDefFmt = csrrwFunc + ";"
csrrwImpFmt = (
    csrrwFunc
    + """{{
    bool res = false;
    switch (csrDst) {{
        {code:s}
    }}
    return res;
}}"""
)

csrInitFunc = "void csrInit()"
csrInitDefFmt = csrInitFunc + ";"
csrInitImpFmt = csrInitFunc + """{{\n{code:s}\n}}"""


class CtrlStatReg:
    def __init__(self, name: str, num: int, fields: list[Field]) -> None:
        self.name = name
        self.num = num
        self.fields: list[Field] = []

        bitsList = [-1 for i in range(WORD_LEN + 1)]
        bitsList[WORD_LEN] = -2
        for idx in range(len(fields)):
            field = fields[idx]
            for bits in range(field.lsb, field.msb + 1):
                bitsList[bits] = idx

        startBits = 0
        for i in range(1, WORD_LEN + 1):
            if bitsList[i - 1] != bitsList[i]:
                endBits = i - 1
                self.fields.append(
                    Field(
                        "r{:d}to{:d}".format(endBits, startBits),
                        endBits,
                        startBits,
                        FieldSpec["READ"],
                        0,
                        None,
                    )
                    if (bitsList[endBits] < 0)
                    else fields[bitsList[endBits]]
                )
                startBits = i

    def __str__(self) -> str:
        return "name = {:s}\nnum = 0x{:x}\nfields = {{\n{:s}\n}}".format(
            self.name,
            self.num,
            "\n".join([field.__str__() for field in self.fields]),
        )

    def genTypeDef(self) -> str:
        structList = [field.structDef() for field in self.fields]
        return headTypeDef.format(name=self.name, fields="\n".join(structList))

    def genVarDef(self) -> str:
        return srcVarDef.format(name=self.name)

    def genOpFuncDef(self) -> str:
        return csrOpDefFmt.format(name=self.name)

    def genOpFuncImp(self) -> str:
        return csrOpImpFmt.format(name=self.name)

    def genRWcase(self) -> str:
        return "case 0x{:x}: res = {:s}RW(rd, src1, op); break;".format(
            self.num, self.name
        )

    def genPrivateWriteFunc(self) -> str:
        csrWriteFmt = """
        static bool {csrName:s}Write(word_t val) {{
            {csrName:s}_t newVar = {{.val = val}};
            {code:s}
            return true;
        }}"""
        writeLines = [field.writeLine(self.name) for field in self.fields]
        return csrWriteFmt.format(csrName=self.name, code="\n".join(writeLines))

    def genInitCode(self) -> str:
        initValue = 0
        for field in self.fields:
            initValue = initValue | field.initValue()
        return "{:s}.val = 0x{:x};".format(self.name, initValue)


class PaserCSR:
    def __init__(self, csrs: list[CtrlStatReg]) -> None:
        self.allCSR = csrs

    def headCode(self) -> str:
        code = []
        code = code + [csr.genTypeDef() for csr in self.allCSR]
        code = code + [csr.genOpFuncDef() for csr in self.allCSR]
        code.append(csrrwDefFmt)
        code.append(csrInitDefFmt)
        return "\n".join(code)

    def srcCode(self) -> str:
        code = []
        code = code + [csr.genVarDef() for csr in self.allCSR]
        code = code + [csr.genPrivateWriteFunc() for csr in self.allCSR]
        code = code + [csr.genOpFuncImp() for csr in self.allCSR]
        caseCode = [csr.genRWcase() for csr in self.allCSR]
        code.append(csrrwImpFmt.format(code="\n".join(caseCode)))
        initCode = [csr.genInitCode() for csr in self.allCSR]
        code.append(csrInitImpFmt.format(code="\n".join(initCode)))
        return "\n".join(code)


def FLD(*args, **kwargs) -> Field:
    init = 0x0
    legalFunc = None
    if len(args) > 4:
        if isinstance(args[4], int):
            init = args[4]
        elif isinstance(args[4], str):
            legalFunc = args[4]
        if len(args) > 5:
            if isinstance(args[4], int):
                init = args[5]
            elif isinstance(args[5], str):
                legalFunc = args[5]
    return Field(args[0], args[1], args[2], FieldSpec[args[3]], init, legalFunc)


def CSR(*args, **kwargs) -> CtrlStatReg:
    return CtrlStatReg(args[0], args[1], args[2])
