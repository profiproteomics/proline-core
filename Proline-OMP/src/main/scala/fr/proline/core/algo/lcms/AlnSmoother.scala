package fr.proline.core.algo.lcms

object AlnSmoother {
  
  import alignment._
    
  def apply( methodName: String ): IAlnSmoother = { methodName match {
    case "time_window" => new TimeWindowSmoother()
    case "landmark_range" => new LandmarkRangeSmoother()
    case _ => throw new Exception("can't find an appropriate alignment smoother")
    }
  }
}