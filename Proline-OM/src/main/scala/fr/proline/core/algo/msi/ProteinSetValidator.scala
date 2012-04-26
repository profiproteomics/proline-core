package fr.proline.core.algo.msi

import validation.protein_set._

object ProteinSetValidator {
  
  def apply( searchEngine: String, methodName: String ): IProteinSetValidator = { searchEngine match {
    case "mascot" => MascotProteinSetValidator( methodName )
    case _ => throw new Exception("unknown validator for search engine : "+ searchEngine )
    }
  }

}

object MascotProteinSetValidator {
  
  def apply( methodName: String ): IProteinSetValidator = { methodName match {
    case "protein_set_score" => new MascotProteinSetScoreValidator()
    case "peptide_match_rules" => new MascotPeptideMatchRulesValidator()
    case _ => throw new Exception("unknown mascot validator for method named : "+ methodName )
    }
  }

}