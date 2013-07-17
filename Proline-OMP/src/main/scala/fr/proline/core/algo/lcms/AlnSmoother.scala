package fr.proline.core.algo.lcms

object AlnSmoothing extends Enumeration {
  val LANDMARK_RANGE = Value("LANDMARK_RANGE")
  val LOESS = Value("LOESS")
  val TIME_WINDOW = Value("TIME_WINDOW")
}

object AlnSmoother {
  
  import alignment._
  
  def apply( methodName: String ): IAlnSmoother = {
    
    val smoothingMethod = try {
      AlnSmoothing.withName( methodName.toUpperCase() )
    } catch {
      case _ => throw new Exception("can't find an appropriate alignment smoother")
    }
    
    smoothingMethod match {
      case AlnSmoothing.LANDMARK_RANGE => new LandmarkRangeSmoother()
      case AlnSmoothing.LOESS => new LoessSmoother()
      case AlnSmoothing.TIME_WINDOW => new TimeWindowSmoother()
    }
  }
}