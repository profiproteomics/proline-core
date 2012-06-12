package fr.proline.core.algo.msi.validation

import scala.collection.mutable.HashMap

trait ValidationParams {
  val minPepSeqLength: Int
  val properties: Option[Map[String,Any]]
}
case class ComputerValidationParams( expectedFdr: Float, minPepSeqLength: Int = 0, properties: Option[Map[String,Any]] = None ) extends ValidationParams
case class UserValidationParams( pValue: Float, minPepSeqLength: Int = 0, properties: Option[Map[String,Any]] = None ) extends ValidationParams

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val separated = Value("separated")
  val concatenated = Value("concatenated")
}

case class ValidationResult( nbTargetMatches: Int,
                             nbDecoyMatches: Option[Int] = None,
                             fdr: Option[Float] = None,
                             properties: Option[HashMap[String,Any]] = None
                            )
case class ValidationResults( expectedResult: ValidationResult, computedResults: Option[Seq[ValidationResult]] )
  
