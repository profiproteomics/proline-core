package fr.proline.core.algo.msq.config

trait IQuantConfig

trait IMsQuantConfig extends IQuantConfig {
  val extractionParams: ExtractionParams
}

trait IMzTolerant {
  val mozTol: Double
  val mozTolUnit: String
  
  def calcMozTolInDalton( moz: Double ): Double = {
    fr.profi.util.ms.calcMozTolInDalton( moz, mozTol, mozTolUnit )
  }
}

case class ExtractionParams( mozTol: Double, mozTolUnit: String ) extends IMzTolerant

