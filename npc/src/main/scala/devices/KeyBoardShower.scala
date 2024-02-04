import chisel3._
import chisel3.util._
object KeyBoardShower {
  /* ascii, scan, seg */
  val ass = Seq(
    ('A'.toInt, 0x1c, 0x77),
    ('B'.toInt, 0x32, 0x7c),
    ('C'.toInt, 0x21, 0x39),
    ('D'.toInt, 0x23, 0x5e),
    ('E'.toInt, 0x24, 0x79),
    ('F'.toInt, 0x2b, 0x71),
    ('G'.toInt, 0x34, 0x3d),
    ('H'.toInt, 0x33, 0x76),
    ('I'.toInt, 0x43, 0x06),
    ('J'.toInt, 0x3b, 0x1e),
    ('K'.toInt, 0x42, 0xff),
    ('L'.toInt, 0x4b, 0x38),
    ('M'.toInt, 0x3a, 0xff),
    ('N'.toInt, 0x31, 0x54),
    ('O'.toInt, 0x44, 0x5c),
    ('P'.toInt, 0x4d, 0x73),
    ('Q'.toInt, 0x15, 0xff),
    ('R'.toInt, 0x2d, 0x50),
    ('S'.toInt, 0x1b, 0x6d),
    ('T'.toInt, 0x2c, 0x78),
    ('U'.toInt, 0x3c, 0x3e),
    ('V'.toInt, 0x2a, 0xff),
    ('W'.toInt, 0x1d, 0xff),
    ('X'.toInt, 0x22, 0xff),
    ('Y'.toInt, 0x35, 0x6e),
    ('Z'.toInt, 0x1a, 0xff),
    ('0'.toInt, 0x45, 0x3f),
    ('1'.toInt, 0x16, 0x06),
    ('2'.toInt, 0x1e, 0x5b),
    ('3'.toInt, 0x26, 0x4f),
    ('4'.toInt, 0x25, 0x66),
    ('5'.toInt, 0x2e, 0x6d),
    ('6'.toInt, 0x36, 0x7d),
    ('7'.toInt, 0x3d, 0x07),
    ('8'.toInt, 0x3e, 0x7f),
    ('9'.toInt, 0x46, 0x6f)
  )
  def getSegCode(scan: Int) = {
    ass.find(_._2 == scan).fold((0xff))(i => (i._3))
  }
  def getAscCode(scan: Int) = {
    ass.find(_._2 == scan).fold((0xff))(i => (i._1))
  }
}
class KeyBoardShower extends Module {
  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(UInt(8.W)))
    val segments = Output(Vec(6, UInt(8.W)))
  })
  val scanToAsc = WireInit(VecInit((0 until 256).map(i => {
    KeyBoardShower.getAscCode(i).U
  })))
  val scanCnt  = RegInit(VecInit.fill(256)(0.U(8.W)))
  val hasPress = RegInit(VecInit.fill(256)(false.B))
  val idx      = RegInit(0.U(8.W))
  val asc      = RegInit(0.U(8.W))
  val cnt      = RegInit(0.U(8.W))
  val unPress  = RegInit(false.B)

  when(io.in.fire && (io.in.bits =/= "hf0".U)) {
    idx := io.in.bits
    asc := scanToAsc(io.in.bits)

    val nextCnt = scanCnt(io.in.bits) + unPress.asUInt
    scanCnt(io.in.bits)  := nextCnt
    cnt                  := nextCnt
    hasPress(io.in.bits) := !unPress
  }

  when(io.in.fire) {
    when(io.in.bits === "hf0".U) {
      unPress := true.B
    }.otherwise { unPress := false.B }
    printf("%b: %x\n", unPress, io.in.bits)
  }
  io.in.ready := true.B

  val hexToSeg = WireInit(
    VecInit(
      Seq(
        0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f, 0x77, 0x7c, 0x39, 0x5e, 0x79, 0x71
      ).map(i => ~Reverse(i.U(8.W)))
    )
  )
  io.segments(0) := hexToSeg(idx(3, 0))
  io.segments(1) := hexToSeg(idx(7, 4))
  io.segments(2) := hexToSeg(asc(3, 0))
  io.segments(3) := hexToSeg(asc(7, 4))
  io.segments(4) := hexToSeg(cnt(3, 0))
  io.segments(5) := hexToSeg(cnt(7, 4))
  when(!hasPress(idx)) {
    (0 until 4).foreach(i => { io.segments(i) := ~0.U(8.W) })
  }
}

class PS2Ctrl extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk        = Input(Bool())
    val clrn       = Input(Bool())
    val ps2_clk    = Input(Bool())
    val ps2_data   = Input(Bool())
    val nextdata_n = Input(Bool())
    val data       = Output(UInt(8.W))
    val ready      = Output(Bool())
    val overflow   = Output(Bool())
  })
  setInline(
    "PS2Ctrl",
    """
      |module PS2Ctrl(clk,clrn,ps2_clk,ps2_data,data,
      |                    ready,nextdata_n,overflow);
      |    input clk,clrn,ps2_clk,ps2_data;
      |    input nextdata_n;
      |    output [7:0] data;
      |    output reg ready;
      |    output reg overflow;     // fifo overflow
      |    // internal signal, for test
      |    reg [9:0] buffer;        // ps2_data bits
      |    reg [7:0] fifo[7:0];     // data fifo
      |    reg [2:0] w_ptr,r_ptr;   // fifo write and read pointers
      |    reg [3:0] count;  // count ps2_data bits
      |    // detect falling edge of ps2_clk
      |    reg [2:0] ps2_clk_sync;
      |
      |    always @(posedge clk) begin
      |        ps2_clk_sync <=  {ps2_clk_sync[1:0],ps2_clk};
      |    end
      |
      |    wire sampling = ps2_clk_sync[2] & ~ps2_clk_sync[1];
      |
      |    always @(posedge clk) begin
      |        if (clrn == 0) begin // reset
      |            count <= 0; w_ptr <= 0; r_ptr <= 0; overflow <= 0; ready<= 0;
      |        end
      |        else begin
      |            if ( ready ) begin // read to output next data
      |                if(nextdata_n == 1'b0) //read next data
      |                begin
      |                    r_ptr <= r_ptr + 3'b1;
      |                    if(w_ptr==(r_ptr+1'b1)) //empty
      |                        ready <= 1'b0;
      |                end
      |            end
      |            if (sampling) begin
      |              if (count == 4'd10) begin
      |                if ((buffer[0] == 0) &&  // start bit
      |                    (ps2_data)       &&  // stop bit
      |                    (^buffer[9:1])) begin      // odd  parity
      |                    fifo[w_ptr] <= buffer[8:1];  // kbd scan code
      |                    w_ptr <= w_ptr+3'b1;
      |                    ready <= 1'b1;
      |                    overflow <= overflow | (r_ptr == (w_ptr + 3'b1));
      |                end
      |                count <= 0;     // for next
      |              end else begin
      |                buffer[count] <= ps2_data;  // store ps2_data
      |                count <= count + 3'b1;
      |              end
      |            end
      |        end
      |    end
      |    assign data = fifo[r_ptr]; //always set output data
      |
      |endmodule
  """.stripMargin
  )

}

class top extends Module {
  val io = IO(new Bundle {
    val ps2 = Input(new Bundle {
      val clk = Bool()
      val dat = Bool()
    })
    val seg = Output(Vec(6, UInt(8.W)))
  })
  val ctrl   = Module(new PS2Ctrl)
  val shower = Module(new KeyBoardShower)
  io.seg           := shower.io.segments
  ctrl.io.ps2_clk  := io.ps2.clk
  ctrl.io.ps2_data := io.ps2.dat

  shower.io.in.valid := ctrl.io.ready
  shower.io.in.bits  := ctrl.io.data
  ctrl.io.nextdata_n := !shower.io.in.ready
  ctrl.io.clk        := clock.asBool
  ctrl.io.clrn       := !reset.asBool
}
