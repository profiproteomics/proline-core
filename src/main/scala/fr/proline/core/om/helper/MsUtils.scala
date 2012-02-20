package fr.proline.core.om.helper

object MsUtils {
  
  val protonMass = 1.00727646688
  //val electronMass = 0.00054857990943

  def mozToMass( moz: Double, charge: Int ): Double = ( moz * math.abs(charge) ) - charge * protonMass
  def massToMoz( mass: Double, charge: Int ): Double = (mass + charge * protonMass) / math.abs(charge)
  
  def calcMozTolInDalton( moz: Double, mozTol: Double, currentTolUnit: String ): Double = {
    
    import scala.util.matching.Regex
    
    val DaType = """(?i)Da""".r
    val PPMType = """(?i)PPM""".r
    
    currentTolUnit match {
      case DaType() => mozTol
      case PPMType() => mozTol * moz / 1000000
      case _ => throw new IllegalArgumentException("currentTolUnit must be Da or PPM and not '" + currentTolUnit + "'")
    }

  }  
  
}