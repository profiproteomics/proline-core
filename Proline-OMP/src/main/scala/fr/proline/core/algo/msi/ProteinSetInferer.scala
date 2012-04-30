package fr.proline.core.algo.msi

import inference._

object ProteinSetInferer {
  
  def apply( methodName: String ): IProteinSetInferer = { methodName match {
    case "parsimonious" => new ParsimoniousProteinSetInferer()
    case _ => throw new Exception("unknown protein set ifnerence mathod: "+ methodName )
    }
  }

}