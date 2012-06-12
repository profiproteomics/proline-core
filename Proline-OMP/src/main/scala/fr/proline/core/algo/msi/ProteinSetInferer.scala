package fr.proline.core.algo.msi

import inference._

object InferenceMethods extends Enumeration {
  type InferenceMethod = Value
  val parsimonious = Value("parsimonious")
}

object ProteinSetInferer {
  
  def apply( methodName: InferenceMethods.InferenceMethod ): IProteinSetInferer = { methodName match {
    case InferenceMethods.parsimonious => new ParsimoniousProteinSetInferer()
    }
  }

}