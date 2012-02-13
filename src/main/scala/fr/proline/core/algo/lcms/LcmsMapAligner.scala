package fr.proline.core.algo.lcms

object LcmsMapAligner {

  import alignment._
    
  def apply( methodName: String ): ILcmsMapAligner = { methodName match {
    case "comprehensive" => new ComprehensiveMapAligner()
    case "iterative" => new IterativeMapAligner()
    case _ => throw new Exception("can't find an appropriate lcms map aligner")
    }
  }
  
}