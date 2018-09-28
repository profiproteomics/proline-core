package fr.proline.core.algo.lcms

object AlnSmoother {
  
  import alignment._

  def apply( method: AlnSmoothing.Value): IAlnSmoother = {
    method match {
      case AlnSmoothing.LANDMARK_RANGE => new LandmarkRangeSmoother()
      case AlnSmoothing.LOESS => new LoessSmoother()
      case AlnSmoothing.TIME_WINDOW => new TimeWindowSmoother()
    }
  }

  def apply( methodName: String ): IAlnSmoother = {
    
    val smoothingMethod = try {
      AlnSmoothing.withName( methodName.toUpperCase() )
    } catch {
      case _ : Throwable => throw new Exception("can't find an appropriate alignment smoother")
    }
    AlnSmoother(smoothingMethod)
  }
}