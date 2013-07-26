package fr.proline.core.util.generator.lcms

import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.MapAlignment

object Distortions {
  
  sealed trait Distortion {
    def apply(): (Array[Float] => Array[Float])
  }
  
  case object NULL_DISTORTION extends Distortion {
    def apply(): (Array[Float] => Array[Float]) = {
      values: Array[Float] => values
    }
  }
  
  case object SINE_DISTORTION extends Distortion {
    def apply(): (Array[Float] => Array[Float]) = {
      values: Array[Float] => values.map( math.sin(_).toFloat )
    }
  }
  
  case object COSINE_DISTORTION extends Distortion {
    def apply(): (Array[Float] => Array[Float]) = {
      values: Array[Float] => values.map( math.cos(_).toFloat )
    }
  }
  
}


class MapAlignmentFakeGenerator(
  distortion: Array[Float] => Array[Float]
  ) {
  
  def generateMapAlignment( timeList: Array[Float], refMapId: Long, targetMapId: Long, massRange: Pair[Float,Float] ): MapAlignment = {
    
    val deltaTimeList = distortion( timeList )
    
    new MapAlignment(
      refMapId = refMapId,
      targetMapId = targetMapId,
      massRange = massRange,
      timeList = timeList,
      deltaTimeList = deltaTimeList
    )
  }
    
}