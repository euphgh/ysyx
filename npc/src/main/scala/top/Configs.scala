package top

import org.chipsalliance.cde.config._
import core._
import freechips.rocketchip.tile.CoreParams

class BaseConfig(n: Int)
    extends Config((site, here, up) => {
      case DebugOptionsKey => DebugOptions()
      case CoreParamsKey   => CoreParams()
    })

class DefaultConfig(n: Int = 1)
    extends Config(new BaseConfig(n).alter((site, here, up) => {
      case DebugOptionsKey => up(DebugOptionsKey)
      case CoreParamsKey   => up(CoreParamsKey)
    }))

// trait HasTopParameter {

//   implicit val p: Parameters

//   val coreParams = p(CoreParamsKey)
//   val env        = p(DebugOptionsKey)

//   val XLEN    = coreParams.XLEN
//   val minFLen = 32
//   val fLen    = 64
//   def xLen    = XLEN

//   val HasMExtension = coreParams.HasMExtension
//   val HasCExtension = coreParams.HasCExtension
//   val HasHExtension = coreParams.HasHExtension
//   val AddrBits      = coreParams.AddrBits // AddrBits is used in some cases
//   val VAddrBits = {
//     if (HasHExtension) {
//       coreParams.GPAddrBits
//     } else {
//       coreParams.VAddrBits
//     }
//   } // VAddrBits is Virtual Memory addr bits

//   type FoldedHistoryInfo = Tuple2[Int, Int]
//   val foldedGHistInfos =
//     (TageTableInfos.map {
//       case (nRows, h, t) =>
//         if (h > 0)
//           Set((h, min(log2Ceil(nRows / numBr), h)), (h, min(h, t)), (h, min(h, t - 1)))
//         else
//           Set[FoldedHistoryInfo]()
//     }.reduce(_ ++ _).toSet ++
//       SCTableInfos.map {
//         case (nRows, _, h) =>
//           if (h > 0)
//             Set((h, min(log2Ceil(nRows / TageBanks), h)))
//           else
//             Set[FoldedHistoryInfo]()
//       }.reduce(_ ++ _).toSet ++
//       ITTageTableInfos.map {
//         case (nRows, h, t) =>
//           if (h > 0)
//             Set((h, min(log2Ceil(nRows), h)), (h, min(h, t)), (h, min(h, t - 1)))
//           else
//             Set[FoldedHistoryInfo]()
//       }.reduce(_ ++ _) ++
//       Set[FoldedHistoryInfo]((UbtbGHRLength, log2Ceil(UbtbSize)))).toList

//   val CacheLineSize                = coreParams.CacheLineSize
//   val CacheLineHalfWord            = CacheLineSize / 16
//   val ExtHistoryLength             = HistoryLength + 64
//   val IBufSize                     = coreParams.IBufSize
//   val DecodeWidth                  = coreParams.DecodeWidth
//   val RenameWidth                  = coreParams.RenameWidth
//   val CommitWidth                  = coreParams.CommitWidth
//   val FtqSize                      = coreParams.FtqSize
//   val IssQueSize                   = coreParams.IssQueSize
//   val EnableLoadFastWakeUp         = coreParams.EnableLoadFastWakeUp
//   val NRPhyRegs                    = coreParams.NRPhyRegs
//   val PhyRegIdxWidth               = log2Up(NRPhyRegs)
//   val RobSize                      = coreParams.RobSize
//   val IntRefCounterWidth           = log2Ceil(RobSize)
//   val LoadQueueSize                = coreParams.LoadQueueSize
//   val LoadQueueNWriteBanks         = coreParams.LoadQueueNWriteBanks
//   val StoreQueueSize               = coreParams.StoreQueueSize
//   val StoreQueueNWriteBanks        = coreParams.StoreQueueNWriteBanks
//   val dpParams                     = coreParams.dpParams
//   val exuParameters                = coreParams.exuParameters
//   val NRMemReadPorts               = exuParameters.LduCnt + 2 * exuParameters.StuCnt
//   val NRIntReadPorts               = 2 * exuParameters.AluCnt + NRMemReadPorts
//   val NRIntWritePorts              = exuParameters.AluCnt + exuParameters.MduCnt + exuParameters.LduCnt
//   val NRFpReadPorts                = 3 * exuParameters.FmacCnt + exuParameters.StuCnt
//   val NRFpWritePorts               = exuParameters.FpExuCnt + exuParameters.LduCnt
//   val LoadPipelineWidth            = coreParams.LoadPipelineWidth
//   val StorePipelineWidth           = coreParams.StorePipelineWidth
//   val StoreBufferSize              = coreParams.StoreBufferSize
//   val StoreBufferThreshold         = coreParams.StoreBufferThreshold
//   val EnableLoadToLoadForward      = coreParams.EnableLoadToLoadForward
//   val EnableFastForward            = coreParams.EnableFastForward
//   val EnableLdVioCheckAfterReset   = coreParams.EnableLdVioCheckAfterReset
//   val EnableSoftPrefetchAfterReset = coreParams.EnableSoftPrefetchAfterReset
//   val EnableCacheErrorAfterReset   = coreParams.EnableCacheErrorAfterReset
//   val EnablePTWPreferCache         = coreParams.EnablePTWPreferCache
//   val EnableAccurateLoadError      = coreParams.EnableAccurateLoadError
//   val asidLen                      = coreParams.MMUAsidLen
//   val vmidLen                      = coreParams.MMUVmidLen
//   val BTLBWidth                    = coreParams.LoadPipelineWidth + coreParams.StorePipelineWidth
//   val refillBothTlb                = coreParams.refillBothTlb
//   val itlbParams                   = coreParams.itlbParameters
//   val ldtlbParams                  = coreParams.ldtlbParameters
//   val ld_tlb_ports                 = if (coreParams.prefetcher.nonEmpty) 3 else 2
//   val sttlbParams                  = coreParams.sttlbParameters
//   val btlbParams                   = coreParams.btlbParameters
//   val l2tlbParams                  = coreParams.l2tlbParameters
//   val NumPerfCounters              = coreParams.NumPerfCounters

//   val NumRs = (exuParameters.JmpCnt + 1) / 2 + (exuParameters.AluCnt + 1) / 2 + (exuParameters.MulCnt + 1) / 2 +
//     (exuParameters.MduCnt + 1) / 2 + (exuParameters.FmacCnt + 1) / 2 + +(exuParameters.FmiscCnt + 1) / 2 +
//     (exuParameters.FmiscDivSqrtCnt + 1) / 2 + (exuParameters.LduCnt + 1) / 2 +
//     ((exuParameters.StuCnt + 1) / 2) + ((exuParameters.StuCnt + 1) / 2)

//   val instBytes      = if (HasCExtension) 2 else 4
//   val instOffsetBits = log2Ceil(instBytes)

//   val icacheParameters = coreParams.icacheParameters
//   val dcacheParameters = coreParams.dcacheParametersOpt.getOrElse(DCacheParameters())

//   // dcache block cacheline when lr for LRSCCycles - LRSCBackOff cycles
//   // for constrained LR/SC loop
//   val LRSCCycles = 64
//   // for lr storm
//   val LRSCBackOff = 8

//   // cache hierarchy configurations
//   val l1BusDataWidth = 256

//   // load violation predict
//   val ResetTimeMax2Pow = 20 //1078576
//   val ResetTimeMin2Pow = 10 //1024
//   // wait table parameters
//   val WaitTableSize     = 1024
//   val MemPredPCWidth    = log2Up(WaitTableSize)
//   val LWTUse2BitCounter = true
//   // store set parameters
//   val SSITSize       = WaitTableSize
//   val LFSTSize       = 32
//   val SSIDWidth      = log2Up(LFSTSize)
//   val LFSTWidth      = 4
//   val StoreSetEnable = true // LWT will be disabled if SS is enabled

//   val loadExuConfigs  = coreParams.loadExuConfigs
//   val storeExuConfigs = coreParams.storeExuConfigs

//   val intExuConfigs = coreParams.intExuConfigs

//   val fpExuConfigs = coreParams.fpExuConfigs

//   val exuConfigs = coreParams.exuConfigs

//   val PCntIncrStep: Int = 6
//   val numPCntHc:    Int = 25
//   val numPCntPtw:   Int = 19

//   val numCSRPCntFrontend = 8
//   val numCSRPCntCtrl     = 8
//   val numCSRPCntLsu      = 8
//   val numCSRPCntHc       = 5
//   val printEventCoding   = true
//   // Parameters for Sdtrig extension
//   protected val TriggerNum            = 10
//   protected val TriggerChainMaxLength = 2
// }
