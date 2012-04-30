package fr.proline.core.algo.msi.validation

import scala.collection.mutable.HashMap

case class ComputerValidationParams( wantedFdr: Double, minPepSeqLength: Int = 0, properties: Option[Map[String,Any]] = None )
case class UserValidationParams( pValue: Float, minPepSeqLength: Int = 0, properties: Option[Map[String,Any]] = None )

case class ValidationResult( nbTargetMatches: Int,
                             nbDecoyMatches: Option[Int] = None,
                             fdr: Option[Float] = None,
                             properties: Option[HashMap[String,Any]] = None
                            )
case class ValidationResults( wantedResult: ValidationResult, computedResults: Option[Seq[ValidationResult]] )
  
