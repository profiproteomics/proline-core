package fr.proline.core.algo.msi.validation

import scala.collection.mutable.HashMap

case class ValidationResult( nbTargetMatches: Int,
                             nbDecoyMatches: Option[Int] = None,
                             fdr: Option[Float] = None,
                             properties: Option[HashMap[String,Any]] = None
                            )
                            
case class ValidationResults( expectedResult: ValidationResult, computedResults: Option[Seq[ValidationResult]] )

object TargetDecoyModes extends Enumeration {
  type Mode = Value
  val separated = Value("separated")
  val concatenated = Value("concatenated")
}
