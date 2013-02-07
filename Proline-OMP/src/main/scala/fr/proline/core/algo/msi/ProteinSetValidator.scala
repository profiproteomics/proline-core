package fr.proline.core.algo.msi

import fr.proline.core.algo.msi.validation.proteinset.MascotPeptideMatchRulesValidator
import fr.proline.core.algo.msi.validation.proteinset.MascotProteinSetScoreValidator
import fr.proline.core.algo.msi.validation.proteinset.IProteinSetValidator

object ValidationMethods extends Enumeration {
  type MethodName = Value
  val proteinSetScore = Value("protein_set_score")
  val peptideMatchRules = Value("peptide_match_rules")
}

object ProteinSetValidator {
  
  def apply( searchEngine: String, method: ValidationMethods.MethodName ): IProteinSetValidator = { searchEngine.toLowerCase match {
    case "mascot" => MascotProteinSetValidator( method )
    case _ => throw new Exception("unknown validator for search engine : "+ searchEngine )
    }
  }

}

object MascotProteinSetValidator {
  
  def apply( method: ValidationMethods.MethodName ): IProteinSetValidator = { method match {
    case ValidationMethods.proteinSetScore => new MascotProteinSetScoreValidator()
    case ValidationMethods.peptideMatchRules => new MascotPeptideMatchRulesValidator()
    case _ => throw new Exception("unknown mascot validator for method named : "+ method )
    }
  }

}