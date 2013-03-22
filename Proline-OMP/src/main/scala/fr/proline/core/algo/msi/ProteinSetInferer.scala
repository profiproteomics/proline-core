package fr.proline.core.algo.msi

import inference._

object InferenceMethods extends Enumeration {
  type InferenceMethods = Value
  val parsimonious = Value("parsimonious")
  val communist = Value("communist")
  
}

object ProteinSetInferer {
  
  def apply( methodName: InferenceMethods.InferenceMethods ): IProteinSetInferer = { methodName match {
    case InferenceMethods.parsimonious => new ParsimoniousProteinSetInferer()
    case InferenceMethods.communist => new CommunistProteinSetInferer()
    }
  }

}