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


class CtrlStatReg:
    def __init__(
        self,
        name: str,
        num: int,
        fields: list[Field],
        beforeRead: str,
        autoPtr: bool,
        preCheck: str,
    ) -> None:
        self.name = name
        self.num = num
        self.fields: list[Field] = []
        self.beforeRead: str = beforeRead
        self.autoPtr: bool = autoPtr
        self.preCheck: str = preCheck

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

    srcVarDef = "{name:s}_t* {name:s}"

    def genTypeDef(self) -> str:
        headTypeDef = (
            """
        typedef union {{ {fields:s}
            word_t val;
        }} {name:s}_t;
        extern """
            + CtrlStatReg.srcVarDef
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
                CtrlStatReg.srcVarDef
            )
        else:
            fmt = CtrlStatReg.srcVarDef + " = NULL;"
        return fmt.format(name=self.name)

    readFuncDefFmt: str = "word_t {name:s}Read()"

    def genReadFuncDef(self) -> str:
        fmt = CtrlStatReg.readFuncDefFmt + ";"
        return fmt.format(name=self.name)

    def genReadFuncImpl(self) -> str:
        fmt = (
            CtrlStatReg.readFuncDefFmt
            + """{{
            return ({code:s});
        }}"""
        )
        readCode = "{name:s}->val".format(name=self.name)
        if self.beforeRead != "":
            readCode = "{:s}({:s})".format(self.beforeRead, readCode)
        return fmt.format(name=self.name, code=readCode)

    def genRWcase(self) -> str:
        checkCall: str = (
            f"{self.preCheck}(rd, src1, op);" if self.preCheck != "" else ""
        )
        return """case {num:#x}: {check:s}
                    if (rd) *rd = {name:s}Read();
                    if (src1) {name:s}Write(whichOp({name:s}->val, *src1, op));
                    break;""".format(
            num=self.num, check=checkCall, name=self.name
        )

    def genPrivateWriteFunc(self) -> str:
        csrWriteFmt = """
        inline static void {csrName:s}Write(word_t val) {{
            {csrName:s}_t newVar = {{.val = val}};
            {code:s}
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
    csrrwFunc = "void csrRW(int csrDst, word_t* rd, word_t *src1, csrOp op)"
    csrrwDefFmt = csrrwFunc + ";"
    csrrwImpFmt = (
        csrrwFunc
        + """{{
        switch (csrDst) {{
            {code:s}
        }}
    }}"""
    )

    csrInitFunc = "void csrInit()"
    csrInitDefFmt: str = csrInitFunc + ";"
    csrInitImpFmt: str = (
        csrInitFunc
        + """{{
        {code:s}
    }}"""
    )

    def __init__(self, csrs: list[CtrlStatReg]) -> None:
        self.allCSR = csrs

    def headCode(self) -> str:
        code: list[str] = []
        code = code + [csr.genTypeDef() for csr in self.allCSR]
        code = code + [csr.genReadFuncDef() for csr in self.allCSR]
        code.append(PaserCSR.csrrwDefFmt)
        code.append(PaserCSR.csrInitDefFmt)
        return "\n".join(code)

    def srcCode(self) -> str:
        code: list[str] = []
        code = code + [csr.genVarDef() for csr in self.allCSR]
        code = code + [csr.genPrivateWriteFunc() for csr in self.allCSR]
        code = code + [csr.genReadFuncImpl() for csr in self.allCSR]
        caseCode = [csr.genRWcase() for csr in self.allCSR]
        code.append(PaserCSR.csrrwImpFmt.format(code="\n".join(caseCode)))
        initCode = [csr.genInitCode() for csr in self.allCSR]
        code.append(PaserCSR.csrInitImpFmt.format(code="\n".join(initCode)))
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
    preCheck: str = "",
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
    return CtrlStatReg(name, num, fields, beforeRead, autoPtr, preCheck)
