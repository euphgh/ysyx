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

    def writeLine(self, csrName: str) -> str:
        if self.spec == FieldSpec.READ:
            return ""
        return (
            (
                (
                    "if ({func:s}(newVar.{fieldName:s})) {{ {csrName:s}->{fieldName:s} = newVar.{fieldName:s}; }}".format(
                        func=self.legalFunc, fieldName=self.name, csrName=csrName
                    )
                    if (self.legalFunc != "")
                    else "{csrName:s}->{fieldName:s} = newVar.{fieldName:s};".format(
                        fieldName=self.name, csrName=csrName
                    )
                )
            )
            if (self.spec == FieldSpec.WARL) or (self.spec == FieldSpec.WLRL)
            else ""
        )

    def initValue(self) -> int:
        return self.init << self.lsb


srcVarDef = "{name:s}_t* {name:s}"

csrOpFunc = "bool {name:s}RW(word_t* rd, word_t src1, csrOp op)"
csrOpDefFmt = csrOpFunc + ";"

csrrwFunc = "bool csrRW(int csrDst, word_t* rd, word_t src1, csrOp op)"
csrrwDefFmt = csrrwFunc + ";"
csrrwImpFmt = (
    csrrwFunc
    + """{{
    switch (csrDst) {{
        {code:s}
        default: return false;
    }}
}}"""
)

csrInitFunc = "void csrInit()"
csrInitDefFmt = csrInitFunc + ";"
csrInitImpFmt = (
    csrInitFunc
    + """{{
    {code:s}
}}"""
)


class CtrlStatReg:
    def __init__(
        self, name: str, num: int, fields: list[Field], beforeRead: str, autoPtr: bool
    ) -> None:
        self.name = name
        self.num = num
        self.fields: list[Field] = []
        self.beforeRead: str = beforeRead
        self.autoPtr: bool = autoPtr

        bitsList = [-1] * (WORD_LEN + 1)
        bitsList[WORD_LEN] = -2
        for idx in range(len(fields)):
            field = fields[idx]
            assert field.lsb <= field.msb, "csr {} field {} lsb > msb".format(
                name, field.name
            )
            for bits in range(field.lsb, field.msb + 1):
                assert bitsList[bits] == -1, "csr {} overlap".format(name)
                bitsList[bits] = idx

        startBits = 0
        for i in range(1, WORD_LEN + 1):
            if bitsList[i - 1] != bitsList[i]:
                endBits = i - 1
                self.fields.append(
                    Field(
                        "z{:d}to{:d}".format(endBits, startBits),
                        endBits,
                        startBits,
                        FieldSpec["READ"],
                        0,
                        "",
                    )
                    if (bitsList[endBits] < 0)
                    else fields[bitsList[endBits]]
                )
                startBits = i

    def isValField(self) -> bool:
        return (self.fields.__len__() == 1) and (self.fields[0].name == "val")

    def __str__(self) -> str:
        return "name = {:s}\nnum = 0x{:x}\nfields = {{\n{:s}\n}}".format(
            self.name,
            self.num,
            "\n".join([field.__str__() for field in self.fields]),
        )

    def genTypeDef(self) -> str:
        headTypeDef = (
            """
        typedef union {{ {fields:s}
            word_t val;
        }} {name:s}_t;
        extern """
            + srcVarDef
            + ";"
        )
        if self.isValField():
            return headTypeDef.format(name=self.name, fields="")
        structFields = "\n".join([field.structDef() for field in self.fields])
        structDef = f"""
        struct {{
            {structFields}
        }}; """
        return headTypeDef.format(name=self.name, fields=structDef)

    def genVarDef(self) -> str:
        fmt = ""
        if self.autoPtr:
            fmt = """
            static {{name:s}}_t {{name:s}}Reg;
            {} = &{{name:s}}Reg;
            """.format(
                srcVarDef
            )
        else:
            fmt = srcVarDef + " = NULL;"
        return fmt.format(name=self.name)

    def genOpFuncDef(self) -> str:
        return csrOpDefFmt.format(name=self.name)

    def genOpFuncImp(self) -> str:
        readStr = "{name:s}->val".format(name=self.name)
        if self.beforeRead != "":
            readStr = "{:s}({:s})".format(self.beforeRead, readStr)
        return """{head:s} {{
            if (rd) *rd = {read:s};
            return {name:s}Write(whichOp({name:s}->val, src1, op));
        }}""".format(
            name=self.name, head=csrOpFunc.format(name=self.name), read=readStr
        )

    def genRWcase(self) -> str:
        return "case 0x{:x}:  return {:s}RW(rd, src1, op);".format(self.num, self.name)

    def genPrivateWriteFunc(self) -> str:
        csrWriteFmt = """
        inline static bool {csrName:s}Write(word_t val) {{
            {csrName:s}_t newVar = {{.val = val}};
            {code:s}
            return true;
        }}"""
        writeLines = [field.writeLine(self.name) for field in self.fields]
        writeCode = "\n".join(writeLines)
        if str.strip(writeCode) == "":
            return "inline static bool {csrName:s}Write(word_t val) {{ return true; }}".format(
                csrName=self.name
            )
        else:
            return csrWriteFmt.format(csrName=self.name, code=writeCode)

    def genInitCode(self) -> str:
        if self.autoPtr == False:
            return ""
        initValue = 0
        for field in self.fields:
            initValue = initValue | field.initValue()
        return "{:s}->val = 0x{:x};".format(self.name, initValue)

    def genNumMacro(self) -> str:
        return "_({:s}, {:#x}, {:s})\\".format(
            self.name, self.num, str.upper(self.name)
        )

    def genEnumMacro(self) -> str:
        return '_("{name:s}", RC_{name:s})\\'.format(name=self.name)


class PaserCSR:
    def __init__(self, csrs: list[CtrlStatReg]) -> None:
        self.allCSR = csrs

    def headCode(self) -> str:
        code: list[str] = []
        code = code + [csr.genTypeDef() for csr in self.allCSR]
        code = code + [csr.genOpFuncDef() for csr in self.allCSR]
        code.append(csrrwDefFmt)
        code.append(csrInitDefFmt)
        return "\n".join(code)

    def srcCode(self) -> str:
        code: list[str] = []
        code = code + [csr.genVarDef() for csr in self.allCSR]
        code = code + [csr.genPrivateWriteFunc() for csr in self.allCSR]
        code = code + [csr.genOpFuncImp() for csr in self.allCSR]
        caseCode = [csr.genRWcase() for csr in self.allCSR]
        code.append(csrrwImpFmt.format(code="\n".join(caseCode)))
        initCode = [csr.genInitCode() for csr in self.allCSR]
        code.append(csrInitImpFmt.format(code="\n".join(initCode)))
        return "\n".join(code)

    def macroCode(self) -> str:
        return "#define CSR_NUM_LIST(_) " + "\n".join(
            [csr.genNumMacro() for csr in self.allCSR]
        )

    def incCode(self) -> str:
        return "\n".join([csr.genEnumMacro() for csr in self.allCSR])


def FLD(
    name: str = "",
    msb: int = -1,
    lsb: int = -1,
    fspec: str = "",
    init: int = 0,
    legalFunc: str = "",
) -> Field:
    assert name != ""
    assert lsb >= 0
    assert msb >= 0
    assert lsb <= msb, "parse field {} lsb({}) > msb({})".format(name, lsb, msb)
    return Field(name, msb, lsb, FieldSpec[fspec], init, legalFunc)


def removeFields(all: list[Field], minus: list[str]) -> list[Field]:
    res: list[Field] = []
    for e in all:
        if e.name in minus:
            continue
        res.append(e)
    return res


def CSR(
    name: str = "",
    num: int = -1,
    fields: list[Field] = [],
    beforeRead: str = "",
    autoPtr: bool = True,
    fspec: str = "",
    init: int = 0,
    legalFunc: str = "",
) -> CtrlStatReg:
    assert name != ""
    assert num != -1
    if fields.__len__() == 0:
        assert fspec != "", "field spec should not be empty when no field"
        fields = [Field("val", WORD_LEN - 1, 0, FieldSpec[fspec], init, legalFunc)]
    else:
        assert fspec == "", "field spec should be empty when fields not empty"
        assert init == 0, "init should be empty when fields not empty"
        assert legalFunc == "", "legalFunc should be empty when fields not empty"
    return CtrlStatReg(name, num, fields, beforeRead, autoPtr)
