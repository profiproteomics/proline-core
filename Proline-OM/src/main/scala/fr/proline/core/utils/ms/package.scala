package fr.proline.core.utils

package ms  {
  case class Isotope( mass: Double, abundance: Double )
}

package object ms {  
  
  // Source : http://pdg.lbl.gov/2012/reviews/rpp2012-rev-phys-constants.pdf
  val protonMass = 1.007276466812
  val electronMass = 0.00054857990946
  
  // Source : http://packages.python.org/pyteomics/_modules/pyteomics/mass.html
  val isotopeTable = Map(
    "H" -> Array( Isotope(1.0078250320710, 0.99988570),
                  Isotope(2.01410177784, 0.00011570),
                  Isotope(3.016049277725, 0.0)
                ),
    
    //"H+" -> Array( Isotope(1.00727646677, 1.0) ),
    //"e-" -> Array( Isotope(0.00054857990943, 1.0) ), 

    "C" -> Array( Isotope(12.0000000, 0.98938),
                  Isotope(13.0033548378, 0.01078),
                  Isotope(14.0032419894, 0.0)
                 ),

    "N" -> Array( Isotope(14.00307400486, 0.9963620),
                  Isotope(15.00010889827, 0.0036420)
                ),

    "O" -> Array( Isotope(15.9949146195616, 0.9975716),
                  Isotope(16.9991317012, 0.000381),
                  Isotope(17.99916107, 0.0020514)
                 ),
    
    "P" -> Array( Isotope(30.9737616320, 1.0000) ),

    "S" -> Array( Isotope(31.9720710015, 0.949926),
                  Isotope(32.9714587615, 0.00752),
                  Isotope(33.9678669012, 0.042524),
                  Isotope(35.9670807620, 0.00011)
                )
  )

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