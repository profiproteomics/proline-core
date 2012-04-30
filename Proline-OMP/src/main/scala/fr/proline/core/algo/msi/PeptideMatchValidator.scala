package fr.proline.core.algo.msi

import validation.peptide_match._

object PeptideMatchValidator {
  
  def apply( searchEngine: String ): IPeptideMatchValidator = { searchEngine match {
    case "mascot" => new MascotPeptideMatchValidator()
    case _ => throw new Exception("unknown search engine : "+ searchEngine )
    }
  }

}