package fr.proline.core.om.helper

object MsUtils {
  
  val protonMass = 1.00727646688

  def mozToMass( moz: Double, charge: Int ): Double = ( moz * charge ) - charge * protonMass
    
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
  
  /** Compute slope and intercept of a line using two data points coordinates */
  def calcLineParams( x1: Double, y1: Double, x2: Double, y2: Double ): Tuple2[Double,Double] =  {
    
    val deltaX = x2 - x1
    if( deltaX == 0 )  {
      throw new IllegalArgumentException("can't solve line parameters with two identical x values (" + x1 + ")" )
    }
    
    val slope = (y2 - y1) / deltaX
    val intercept = y1 - (slope * x1)
    
    ( slope, intercept )
  }
  
}