package fr.proline.core.util.generator.lcms

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms.{LcMsRun, MapAlignment, MapAlignmentProperties}

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
  distortion: Array[Float] => Array[Float],
  amplitude: Float = 1f
  ) {
  
  def generateMapAlignment( timeList: Array[Float], refMapId: Long, targetMapId: Long, massRange: Pair[Float,Float] ): MapAlignment = {
    
    val deltaTimeList = distortion( timeList ).map( _ * amplitude )
    
    val fixedTimeList = new ArrayBuffer[Float](timeList.length)
    val fixedDeltaTimeList = new ArrayBuffer[Float](deltaTimeList.length)
    var prevX = 0f
    var prevY = 0f
    timeList.zip(deltaTimeList).foreach { case(x,y) =>
      val xySum = x + y
      val prevXYSum = prevX + prevY
      if( x > prevX && xySum > prevXYSum ) {
        fixedTimeList += x
        fixedDeltaTimeList += y
        //println( x +"\t"+ y)
        prevX = x
        prevY = y
      }
    }
    
    new MapAlignment(
      refMapId = refMapId,
      targetMapId = targetMapId,
      massRange = massRange,
      timeList = fixedTimeList.toArray,
      deltaTimeList = fixedDeltaTimeList.toArray
    )
  }
    
}