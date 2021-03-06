/******************************************************************************
  *  This file describes the Axi4 interface
  *
  *   _________________________________________________________________________
  *  | Global | Write Data | Write Addr | Write Resp | Read Data  | Read Addr  |
  *  |   -    |    w       |    aw      |      b     |     r      |     ar     |
  *  |-------------------------------------------------------------------------|
  *  |  aclk  |  wid       |  *awid     |  *bid      |  *arid     |  rid       |
  *  |  arstn |  wdata     |  awaddr    |  *bresp    |  araddr    |  rdata     |
  *  |        |  *wstrb    |  *awlen    |  buser     |  *arlen    |  rresp     |
  *  |        |  wlast     |  *awsize   |  bvalid    |  *arsize   |  rlast     |
  *  |        |  *wuser    |  *awburst  |  bready    |  *arburst  |  *ruser    |
  *  |        |  wvalid    |  *awlock   |            |  *arlock   |  rvalid    |
  *  |        |  wready    |  *awcache  |            |  *arcache  |  rready    |
  *  |        |            |  awprot    |            |  arprot    |            |
  *  |        |            |  *awqos    |            |  *arqos    |            |
  *  |        |            |  *awregion |            |  *arregion |            |
  *  |        |            |  *awuser   |            |  *aruser   |            |
  *  |        |            |  awvalid   |            |  arvalid   |            |
  *  |        |            |  awready   |            |  arready   |            |
  *  |________|____________|____________|____________|____________|____________|
  *   * Optional signal
  * @TODO add signals for the low power ???
  */


package spinal.lib.bus.amba4.axi

import spinal.core._
import spinal.lib._



/**
 * Configuration class for the Axi4 bus
 */
case class Axi4Config(addressWidth : Int,
                      dataWidth    : Int,
                      idWidth      : Int = -1,
                      useId        : Boolean = true,
                      useRegion    : Boolean = true,
                      useBurst     : Boolean = true,
                      useLock      : Boolean = true,
                      useCache     : Boolean = true,
                      useSize      : Boolean = true,
                      useQos       : Boolean = true,
                      useLen       : Boolean = true,
                      useLast      : Boolean = true,
                      useResp      : Boolean = true,
                      useProt      : Boolean = true,
                      useStrb      : Boolean = true,
                      arUserWidth   : Int = -1,
                      awUserWidth   : Int = -1,
                      rUserWidth   : Int = -1,
                      wUserWidth   : Int = -1,
                      bUserWidth   : Int = -1) {


  def useArUser = arUserWidth >= 0
  def useAwUser = awUserWidth >= 0
  def useRUser = rUserWidth >= 0
  def useWUser = wUserWidth >= 0
  def useBUser = bUserWidth >= 0
  def useArwUser = arwUserWidth >= 0 //Shared AR/AW channel
  def arwUserWidth = Math.max(arUserWidth, awUserWidth)

  if(useId)
    require(idWidth >= 0,"You need to set idWidth")

  def addressType = UInt(addressWidth bits)
  def dataType = Bits(dataWidth bits)
  def idType = UInt(idWidth bits)
  def lenType = UInt(8 bits)
  def bytePerWord = dataWidth/8
  def symbolRange = log2Up(bytePerWord)-1 downto 0
  def wordRange    = addressWidth-1 downto log2Up(bytePerWord)
}


trait Axi4Bus

/**
 * Axi4 interface definition
 * @param config Axi4 configuration class
 */
case class Axi4(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus{

  val aw = Stream(Axi4Aw(config))
  val w  = Stream(Axi4W(config))
  val b  = Stream(Axi4B(config))
  val ar = Stream(Axi4Ar(config))
  val r  = Stream(Axi4R(config))

  def writeCmd  = aw
  def writeData = w
  def writeRsp  = b
  def readCmd   = ar
  def readRsp   = r

  def <<(that : Axi4) : Unit = that >> this
  def >> (that : Axi4) : Unit = {
    this.readCmd drive that.readCmd
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.readRsp drive this.readRsp
    that.writeRsp drive this.writeRsp
  }

  def <<(that : Axi4WriteOnly) : Unit = that >> this
  def >> (that : Axi4WriteOnly) : Unit = {
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
  }


  def <<(that : Axi4ReadOnly) : Unit = that >> this
  def >> (that : Axi4ReadOnly) : Unit = {
    this.readCmd drive that.readCmd
    that.readRsp drive this.readRsp
  }

  def axValidPipe() : Axi4 = {
    val sink = Axi4(config)
    sink.ar << this.ar.validPipe()
    sink.aw << this.aw.validPipe()
    sink.w  << this.w
    sink.r  >> this.r
    sink.b  >> this.b
    sink
  }

  override def asMaster(): Unit = {
    master(ar,aw,w)
    slave(r,b)
  }

  def toReadOnly(): Axi4ReadOnly ={
    val ret = Axi4ReadOnly(config)
    ret << this
    ret
  }

  def toWriteOnly(): Axi4WriteOnly ={
    val ret = Axi4WriteOnly(config)
    ret << this
    ret
  }
}




/**
  * Definition of the constants used by the Axi4 bus
  */
object Axi4{
  object size{
    def apply() = Bits(3 bits)
    def BYTE_1   = B"000"
    def BYTE_2   = B"001"
    def BYTE_4   = B"010"
    def BYTE_8   = B"011"
    def BYTE_16  = B"100"
    def BYTE_32  = B"101"
    def BYTE_64  = B"110"
    def BYTE_128 = B"111"
  }

  object awcache{
    def apply() = Bits(4 bits)
    def OTHER      = B"1000"
    def ALLOCATE   = B"0100"
    def MODIFIABLE = B"0010"
    def BUFFERABLE = B"0001"
  }

  object arcache{
    def apply() = Bits(4 bits)
    def ALLOCATE   = B"1000"
    def OTHER      = B"0100"
    def MODIFIABLE = B"0010"
    def BUFFERABLE = B"0001"
  }

  object burst{
    def apply() = Bits(2 bits)
    def FIXED    = B"00"
    def INCR     = B"01"
    def WRAP     = B"10"
    def RESERVED = B"11"
  }

  object lock{
    def apply() = Bits(2 bits)
    def NORMAL    = B"0"
    def EXCLUSIVE = B"1"
  }

  object resp{
    def apply() = Bits(2 bits)
    def OKAY   = B"00" // Normal access success
    def EXOKAY = B"01" // Exclusive access okay
    def SLVERR = B"10" // Slave error
    def DECERR = B"11" // Decode error
  }

  //Return the increment of a address depending the burst configuration (INCR,WRAP,FIXED)
  def incr(address : UInt,burst : Bits,len : UInt,size : UInt,bytePerWord : Int) : UInt = {
    val area = new Area {
      val result = UInt(address.getWidth bits)
      val highCat = if (address.getWidth > 12) address(address.high downto 12) else U""
      val sizeValue = (0 to bytePerWord).map(idx => idx === size).asBits.asUInt
      val base = address(Math.min(12, address.getWidth) - 1 downto 0).resize(12)
      val baseIncr = base + sizeValue
      val wrapCaseMax = 3 + log2Up(bytePerWord)
      val wrapCaseWidth = log2Up(wrapCaseMax + 1)
      val wrapCase = size.resize(wrapCaseWidth) + len.mux(
        M"----1---" -> U"11",
        M"-----1--" -> U"10",
        M"------1-" -> U"01",
        default -> U"00"
      )
      val baseWrap = sizeValue
      switch(burst) {
        is(Axi4.burst.FIXED) {
          result := address
        }
        is(Axi4.burst.WRAP) {
          val cases = Vec((0 to wrapCaseMax).map(i => base(11 downto i + 1) @@ baseIncr(i downto 0)))
          result := (highCat @@ cases(wrapCase)).resized
        }
        default {
          result := (highCat @@ baseIncr).resized
        }
      }
    }.setWeakName("Axi4Incr")
    area.result
  }
}

