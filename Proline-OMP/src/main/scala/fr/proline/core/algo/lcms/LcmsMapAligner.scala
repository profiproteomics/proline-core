package fr.proline.core.algo.lcms

object LcmsMapAligner {

  import alignment._
  
  def apply( methodName: String ): AbstractLcmsMapAligner = {
    
    val alnMethod = try {
      AlnMethod.withName( methodName.toUpperCase() )
    } catch {
      case t: Throwable => throw new Exception("can't find an appropriate lcms map aligner",t)
    }
    
    alnMethod match {
      case AlnMethod.EXHAUSTIVE => new ComprehensiveMapAligner()
      case AlnMethod.ITERATIVE => new IterativeMapAligner()
    }
  }
  
}