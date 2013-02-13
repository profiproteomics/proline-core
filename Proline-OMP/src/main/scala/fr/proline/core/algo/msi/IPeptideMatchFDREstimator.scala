package fr.proline.core.algo.msi

//import fr.proline.core.algo.msi.TargetDecoyComputer.TDCompetitionCounts
/*
 case class TDCompetitionCounts( var better: Int = 0, var only: Int = 0, var under: Int = 0 )

trait IPeptideMatchFDREstimator {

  def computeFdr(targetCount :TDCompetitionCounts, decoyCount : TDCompetitionCounts  ) : Double
  
}

/**
 * Computes FDR using the refined method described by Navarro et al. (JPR, 2009)  
 */
class NavarroFDREstimator extends IPeptideMatchFDREstimator{
  
    def computeFdr(targetCount :TDCompetitionCounts, decoyCount : TDCompetitionCounts ): Double = {
      val (tB,tO) = (targetCount.better, targetCount.only)
      val (dB,dO) = (decoyCount.better, decoyCount.only)
      
      require( tB + tO + dB > 0 )    
      100 * (2 * dB + dO) / (tB + tO + dB)
  }
}*/