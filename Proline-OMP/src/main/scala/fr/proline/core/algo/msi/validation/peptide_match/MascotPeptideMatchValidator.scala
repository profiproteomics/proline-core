package fr.proline.core.algo.msi.validation.peptide_match

import math.abs
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PeptideMatchValidationProperties

class MascotPeptideMatchValidator extends IPeptideMatchValidator {

  def validateWithComputerParams( validationParams: ComputerValidationParams,
                                  targetPeptideMatches: Seq[PeptideMatch],
                                  decoyPeptideMatches: Seq[PeptideMatch]
                                  ): ValidationResults = {
    
    val expectedFdr = validationParams.expectedFdr
    val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer
    
    // Compute the joint table
    val allPepMatches = targetPeptideMatches ++ decoyPeptideMatches
    val pepMatchJointTable = tdComputer.buildPeptideMatchJointTable( allPepMatches )
    //println( "joint table computed !" )
    
    val rocPoints = MascotValidationHelper.rocAnalysis( pepMatchJointTable )
    //println( "roc analysis done !" )
    
    // Retrieve the nearest ROC point of the expected FDR
    val expectedRocPoint = rocPoints.reduce { (a,b) => if( abs(a.fdr.get - expectedFdr) < abs(b.fdr.get - expectedFdr) ) a else b } 
    val pValue = expectedRocPoint.properties.get("p_value").asInstanceOf[Double]
    
    // TODO retrieve min seq length from params
    val userValParams = UserValidationParams( pValue.toFloat, validationParams.minPepSeqLength )

    this.validateWithUserParams( userValParams, allPepMatches, None, None )
    
    new ValidationResults( expectedRocPoint, Some(rocPoints) )
    
  }
  
  /** tagetDecoyMode = concatenated || separated */
  def validateWithUserParams( validationParams: UserValidationParams,
                              targetPeptideMatches: Seq[PeptideMatch],
                              decoyPeptideMatches: Option[Seq[PeptideMatch]],
                              targetDecoyMode: Option[TargetDecoyModes.Mode] ): ValidationResult = {
    
    val nbTargetMatches = this.validateWithUserParams( validationParams, targetPeptideMatches )
    
    var nbDecoyMatches: Option[Int] = None
    var fdr: Option[Float] = None
    if( decoyPeptideMatches != None ) {
      nbDecoyMatches = Some( this.validateWithUserParams( validationParams, decoyPeptideMatches.get ) )
       
      val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer
       
      fdr = targetDecoyMode.get match {
        case TargetDecoyModes.concatenated => Some( tdComputer.computeCdFdr( nbTargetMatches, nbDecoyMatches.get ) )
        case TargetDecoyModes.separated => Some( tdComputer.computeSdFdr( nbTargetMatches, nbDecoyMatches.get ) )
        case _ => throw new Exception("unknown target decoy mode: " + targetDecoyMode )
      }
    }
    
    new ValidationResult( nbTargetMatches, nbDecoyMatches, fdr = fdr )
    
  }
  
  private def validateWithUserParams( validationParams: UserValidationParams,
                                      peptideMatches: Seq[PeptideMatch] ): Int = {
    
    val pValue = validationParams.pValue
    val minPepSeqLength = validationParams.minPepSeqLength
      
    var nbValidMatches = 0
    for( peptideMatch <- peptideMatches ) {
      
      val isValidated = MascotValidationHelper.isPepMatchValid( peptideMatch, pValue, minPepSeqLength )
      peptideMatch.isValidated = isValidated
      
      if( isValidated ) nbValidMatches += 1 
      
      // Compute RSM properties
      var valProps = peptideMatch.validationProperties.orElse( Some( new PeptideMatchValidationProperties() ) ).get 
      //valProps.setMascotAdjustedExpectationValue(Some(0))
      //valProps.setMascotScoreOffset(Some(0))
      //valProps.getOrElseUpdate("mascot:adjusted expectation value",0)
      //valProps.getOrElseUpdate("mascot:score offset",0)
      
      // TODO: check if this is really the expected value (adjusted ?)
      val adjustedEvalue = MascotValidationHelper.calcPepMatchEvalue( peptideMatch )
      valProps.setMascotAdjustedExpectationValue( Some(adjustedEvalue) )
      valProps.setMascotScoreOffset( Some( MascotValidationHelper.calcScoreThresholdOffset( adjustedEvalue, pValue ) ) )
      //valProps("mascot:adjusted expectation value") = adjustedEvalue
      //valProps("mascot:score offset") = MascotValidationHelper.calcScoreThresholdOffset( adjustedEvalue, pValue )
      
      peptideMatch.validationProperties = Some( valProps )
    }
    
    nbValidMatches
    
  }
                              
}