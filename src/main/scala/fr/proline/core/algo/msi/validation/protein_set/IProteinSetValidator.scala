package fr.proline.core.algo.msi.validation.protein_set

import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi._

trait IProteinSetValidator {

  def validateWithComputerParams( validationParams: ComputerValidationParams,
                                  targetRsm: ResultSummary,
                                  decoyRsm: ResultSummary
                                 ): ValidationResults

}