package fr.proline.core.algo.msi

import inference._

object InferenceMethod extends Enumeration {
  val PARSIMONIOUS = Value("PARSIMONIOUS")
}

object ProteinSetInferer {
  
  def apply( methodName: InferenceMethod.Value ): IProteinSetInferer = {
    methodName match {
      case InferenceMethod.PARSIMONIOUS => new ParsimoniousProteinSetInferer()
    }
  }

}