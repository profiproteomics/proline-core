package fr.proline.core.algo.msq.config

trait IQuantConfig {
  val configVersion : String = "1.0"
}

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

trait IMzTimeTolerant extends IMzTolerant {
  val timeTol: Float
}

case class ExtractionParams( mozTol: Double, mozTolUnit: String ) extends IMzTolerant

case class MzToleranceParams( mozTol: Double, mozTolUnit: String ) extends IMzTolerant
