package fr.proline.core.algo.lcms

object LcmsMapAligner {

  import alignment._
    
  def apply( methodName: String ): IAlnSmoother = { methodName match {
    case "comprehensive" => new TimeWindowSmoother()
    case "iterative" => new LandmarkRangeSmoother()
    case _ => throw new Exception("can't find an appropriate lcms map aligner")
    }
  }
  
}