package fr.proline.core.algo.msi.validation.peptide_match

import math.abs
import fr.proline.core.algo.msi.validation._

import fr.proline.core.om.model.msi.PeptideMatch

class MascotPeptideMatchValidator extends IPeptideMatchValidator {

  def validateWithComputerParams( validationParams: ComputerValidationParams,
                                  targetPeptideMatches: Seq[PeptideMatch],
                                  decoyPeptideMatches: Seq[PeptideMatch]
                                  ): ValidationResults = {
    
    val wantedFdr = validationParams.wantedFdr
    val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer
    
    // Compute the joint table
    val allPepMatches = targetPeptideMatches ++ decoyPeptideMatches
    val pepMatchJointTable = tdComputer.buildPeptideMatchJointTable( allPepMatches )
    //println( "joint table computed !" )
    
    val rocPoints = MascotValidationHelper.rocAnalysis( pepMatchJointTable )
    //println( "roc analysis done !" )
    
    // Retrieve the nearest ROC point of the wanted FDR
    val wantedRocPoint = rocPoints.reduce { (a,b) => if( abs(a.fdr.get - wantedFdr) < abs(b.fdr.get - wantedFdr) ) a else b } 
    val pValue = wantedRocPoint.properties.get("p_value").asInstanceOf[Float]
    
    // TODO retrieve min seq length from params
    val userValParams = UserValidationParams( pValue, validationParams.minPepSeqLength )

    this.validateWithUserParams( userValParams, allPepMatches, None, None )
    
    new ValidationResults( wantedRocPoint, Some(rocPoints) )
    
  }
  
  /** tagetDecoyMode = concatenated || separated */
  def validateWithUserParams( validationParams: UserValidationParams,
                              targetPeptideMatches: Seq[PeptideMatch],
                              decoyPeptideMatches: Option[Seq[PeptideMatch]],
                              targetDecoyMode: Option[String] ): ValidationResult = {
    
    val nbTargetMatches = this.validateWithUserParams( validationParams, targetPeptideMatches )
    
    var nbDecoyMatches: Option[Int] = None
    var fdr: Option[Float] = None
    if( decoyPeptideMatches != None ) {
      nbDecoyMatches = Some( this.validateWithUserParams( validationParams, decoyPeptideMatches.get ) )
       
      val tdComputer = fr.proline.core.algo.msi.TargetDecoyComputer
       
      fdr = targetDecoyMode.get match {
        case "concatenated" => Some( tdComputer.computeCdFdr( nbTargetMatches, nbDecoyMatches.get ) )
        case "separated" => Some( tdComputer.computeSdFdr( nbTargetMatches, nbDecoyMatches.get ) )
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
      var valProps = peptideMatch.validationProperties.orElse( Some( new collection.mutable.HashMap[String,Any]() ) ).get 
      valProps.getOrElseUpdate("mascot:adjusted expectation value",0)
      valProps.getOrElseUpdate("mascot:score offset",0)
      
      // TODO: check if this is really the wanted value (adjusted ?)
      val adjustedEvalue = MascotValidationHelper.calcPepMatchEvalue( peptideMatch )
      valProps("mascot:adjusted expectation value") = adjustedEvalue
      valProps("mascot:score offset") = MascotValidationHelper.calcScoreThresholdOffset( adjustedEvalue, pValue )
      
      peptideMatch.validationProperties = Some( valProps )
    }
    
    nbValidMatches
    
  }
                              
}