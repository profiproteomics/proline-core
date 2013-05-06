package fr.proline.core.algo.msi.validation

import scala.collection.mutable.{ HashMap, ArrayBuffer }
import scala.math.{ pow, log10 }
import fr.proline.core.om.model.msi.{ MsQuery, PeptideMatch }
import fr.proline.core.om.model.msi.MsQueryDbSearchProperties
import fr.proline.core.om.model.msi.MsQueryProperties
import com.weiglewilczek.slf4s.Logging

object MascotThresholdTypes extends Enumeration {
  val IDENTITY_THRESHOLD = Value("IDENTITY_THRESHOLD")
  val HOMOLOGY_THRESHOLD = Value("HOMOLOGY_THRESHOLD")
  val LOWEST_THRESHOLD = Value("LOWEST_THRESHOLD")
}

case class MascotIonsScoreThresholds(identityThreshold: Float, homologyThreshold: Float)

object MascotValidationHelper extends Logging {

  implicit def doubleToFloat(d: Double): Float = d.toFloat

  def calcEvalue(ionsScore: Float, identityThreshold: Float, probability: Double = 0.05): Double = {
    probability * pow(10, (identityThreshold - ionsScore) / 10)
  }

  def calcCandidatePeptidesCount(identityThreshold: Float, probability: Double = 0.05): Float = {
    (probability / 0.05) * pow(10, identityThreshold / 10)
  }

  def inferCandidatePeptidesCount(ionsScore: Float, eValue: Double): Unit = {
    this.calcCandidatePeptidesCount(ionsScore, eValue)
  }

  def calcIdentityThreshold(candidatePeptidesCount: Double, probability: Double): Float = {
    -10.0 * log10((probability / 0.05) / candidatePeptidesCount)
  }

  def inferIdentityThreshold(ionsScore: Float, eValue: Double, probability: Double = 0.05): Float = {
    ionsScore - 10.0 * log10(probability / eValue)
  }

  def calcScoreThresholdOffset(prob: Double, probRef: Double): Float = {
    -10.0 * log10(prob / probRef)
  }

  def sumPeptideMatchesScoreOffsets(peptideMatches: Seq[PeptideMatch], mascotThresholdsByPepMatchId: Map[Int, MascotIonsScoreThresholds]): Float = {

    var (sumOfScoreOffsets, substractedThresholds) = (0.0f, 0.0f)
    for (peptideMatch <- peptideMatches) {

      val peptideScore = peptideMatch.score
      val pmThresholds = mascotThresholdsByPepMatchId(peptideMatch.id)
      val (identityThreshold, homologyThreshold) = (pmThresholds.identityThreshold, pmThresholds.homologyThreshold)

      // If there is a homology threshold and ions score > homology threshold
      if (!homologyThreshold.isNaN && homologyThreshold > 0 && peptideScore > homologyThreshold) {
        sumOfScoreOffsets += peptideScore - homologyThreshold
        substractedThresholds += homologyThreshold
      } else if (peptideScore > identityThreshold) {
        sumOfScoreOffsets += peptideScore - identityThreshold
        substractedThresholds += identityThreshold
      }
    }

    sumOfScoreOffsets
  }

  def sumPeptideMatchesScoreOffsets(peptideMatches: Seq[PeptideMatch]): Float = {
    this.sumPeptideMatchesScoreOffsets(peptideMatches, this.getMascotThresholdsByPepMatchId(peptideMatches))
  }

  def sumPeptideMatchesScoreOffsets(peptideMatches: Seq[PeptideMatch], scoreThresholdOffset: Float): Float = {

    val pepMatchThresholdsMap = this.getMascotThresholdsByPepMatchId(peptideMatches)
    val pmThresholdsMapBuilder = collection.immutable.Map.newBuilder[Int, MascotIonsScoreThresholds]

    // Add the score threshold offset to the peptide matches thresholds
    for ((pepMatchId, pmThresholds) <- pepMatchThresholdsMap) {
      var (identityThreshold, homologyThreshold) = (pmThresholds.identityThreshold, pmThresholds.homologyThreshold)

      identityThreshold += scoreThresholdOffset
      if (!homologyThreshold.isNaN && homologyThreshold > 0) homologyThreshold += scoreThresholdOffset

      pmThresholdsMapBuilder += pepMatchId -> MascotIonsScoreThresholds(identityThreshold, homologyThreshold)
    }

    this.sumPeptideMatchesScoreOffsets(peptideMatches, pmThresholdsMapBuilder.result())
  }

  def calcMascotMudpitScore(peptideMatches: Seq[PeptideMatch], pepMatchThresholdsMap: Map[Int, MascotIonsScoreThresholds]): Float = {

    var (mudpitScore, substractedThresholds, nbValidPepMatches) = (0.0f, 0.0f, 0)

    for (peptideMatch <- peptideMatches) {

      val peptideScore = peptideMatch.score
      val pmThresholds = pepMatchThresholdsMap(peptideMatch.id)
      val (identityThreshold, homologyThreshold) = (pmThresholds.identityThreshold, pmThresholds.homologyThreshold)

      // If there is a homology threshold and ions score > homology threshold
      if (!homologyThreshold.isNaN && homologyThreshold > 0 && peptideScore > homologyThreshold) {
        mudpitScore += peptideScore - homologyThreshold
        substractedThresholds += homologyThreshold
        nbValidPepMatches += 1
      } else if (peptideScore > identityThreshold) {
        mudpitScore += peptideScore - identityThreshold
        substractedThresholds += identityThreshold
        nbValidPepMatches += 1
      }
    }

    if (mudpitScore > 0) {
      val averageSubstractedThreshold = substractedThresholds / nbValidPepMatches
      mudpitScore += averageSubstractedThreshold
    }

    mudpitScore
  }

  def calcMascotMudpitScore(peptideMatches: Seq[PeptideMatch]): Float = {
    this.calcMascotMudpitScore(peptideMatches, this.getMascotThresholdsByPepMatchId(peptideMatches))
  }

  def getMascotThresholdsByPepMatchId(peptideMatches: Seq[PeptideMatch]): Map[Int, MascotIonsScoreThresholds] = {

    val pmThresholdsMapBuilder = collection.immutable.Map.newBuilder[Int, MascotIonsScoreThresholds]
    for (peptideMatch <- peptideMatches) {

      val pmThresholds = this.getPeptideMatchThresholds(peptideMatch)
      var (identityThreshold, homologyThreshold) = (pmThresholds.identityThreshold, pmThresholds.homologyThreshold)

      if (identityThreshold.isNaN || identityThreshold < 13) identityThreshold = 13
      if (homologyThreshold.isNaN || homologyThreshold < 13) homologyThreshold = 13

      pmThresholdsMapBuilder += peptideMatch.id -> MascotIonsScoreThresholds(identityThreshold, homologyThreshold)
    }

    pmThresholdsMapBuilder.result()
  }

  def calcPeptideMatchTDThresholds(peptideMatch: PeptideMatch, pValue: Float): Pair[MascotIonsScoreThresholds,MascotIonsScoreThresholds] = {

    val msQProp = peptideMatch.msQuery.properties.get

    val tRSCandPSM = msQProp.getTargetDbSearch.get.getCandidatePeptidesCount
    val dRSCandPSM = if (msQProp.getDecoyDbSearch.isDefined) msQProp.getDecoyDbSearch.get.getCandidatePeptidesCount else tRSCandPSM

    var targetITh = MascotValidationHelper.calcIdentityThreshold(tRSCandPSM, pValue)
    if (targetITh == 0.0) targetITh = 13.0
    var decoyITh = MascotValidationHelper.calcIdentityThreshold(dRSCandPSM, pValue)
    if (decoyITh == 0.0) decoyITh = 13.0
    
    //Infer HT 
    if (!msQProp.getTargetDbSearch.get.getMascotHomologyThreshold.isDefined) {
      logger.warn(" ------ UNABLE TO CALCULATE P VALUE  getMascotHomologyThreshold !!" + peptideMatch.msQueryId)
      Pair(MascotIonsScoreThresholds(0.0, 0.0), MascotIonsScoreThresholds(0.0, 0.0))
    } else {

      val tRs_ht0_05: Float = msQProp.getTargetDbSearch.get.getMascotHomologyThreshold.get
      val dRs_ht0_05: Float = if (msQProp.getDecoyDbSearch.isDefined && msQProp.getDecoyDbSearch.get.getMascotHomologyThreshold.isDefined) msQProp.getDecoyDbSearch.get.getMascotHomologyThreshold.get else tRs_ht0_05

      val targetHtProbCstValue = MascotValidationHelper.calcCandidatePeptidesCount(tRs_ht0_05, 0.05)
      var targetHTh = MascotValidationHelper.calcIdentityThreshold(targetHtProbCstValue, pValue)
      val decoyHtProbCstValue = MascotValidationHelper.calcCandidatePeptidesCount(dRs_ht0_05, 0.05)
      var decoyHTh = MascotValidationHelper.calcIdentityThreshold(decoyHtProbCstValue, pValue)

      // if Homology is undefined then use Identity threshold
      if ((targetHTh > targetITh) || (targetHTh < 13) || (tRSCandPSM <= 100)) { targetHTh = 0.0 }
      if ((decoyHTh > decoyITh) || (decoyHTh < 13) || (dRSCandPSM <= 100)) { decoyHTh = 0.0 }

      Pair(MascotIonsScoreThresholds(targetITh, targetHTh), MascotIonsScoreThresholds(decoyITh, decoyHTh))
    }

  }
  
  def calcPeptideMatchThresholds(peptideMatch: PeptideMatch, pValue: Float): MascotIonsScoreThresholds = {
    
    val thresholds = MascotValidationHelper.calcPeptideMatchTDThresholds(peptideMatch, pValue)
    
    val( targetThresholds, decoyThresholds ) = thresholds
    
    val HT = if (decoyThresholds.homologyThreshold > 0) decoyThresholds.homologyThreshold else targetThresholds.homologyThreshold
    val( targetIT, decoyIT ) = (targetThresholds.identityThreshold,decoyThresholds.identityThreshold)
      
    val IT = if (decoyIT > 0 && targetIT > 0) { (decoyIT + targetIT) / 2f }
    else if (decoyIT > 0) { decoyIT }
    else if (targetIT > 0) { targetIT }
    else 0.0f
    
    MascotIonsScoreThresholds(IT,HT)
  }
  
  def getPeptideMatchThresholds(peptideMatch: PeptideMatch): MascotIonsScoreThresholds = {

    val targetMsqProps = this.getTargetMsQueryProperties(peptideMatch.msQuery)
    val decoyMsqProps = this.getDecoyMsQueryProperties(peptideMatch.msQuery)

    // Determine homology threshold
    val targetHt = targetMsqProps.map(_.getMascotHomologyThreshold.getOrElse(0f)).getOrElse(0f)
    val decoyHt = decoyMsqProps.map(_.getMascotHomologyThreshold.getOrElse(0f)).getOrElse(0f)
    val homologyThreshold = if (decoyHt > 0) decoyHt else targetHt

    // Determine identity threshold
    val targetIt = targetMsqProps.map(_.getMascotIdentityThreshold.getOrElse(0f)).getOrElse(0f)
    val decoyIt = decoyMsqProps.map(_.getMascotIdentityThreshold.getOrElse(0f)).getOrElse(0f)

    var identityThreshold = 0.0f
    if (decoyIt > 0 && targetIt > 0) { identityThreshold = (decoyIt + targetIt) / 2 }
    else if (decoyIt > 0) { identityThreshold = decoyIt }
    else if (targetIt > 0) { identityThreshold = targetIt }

    MascotIonsScoreThresholds(identityThreshold, homologyThreshold)
  }

  def getLowestPeptideMatchThreshold(peptideMatch: PeptideMatch): Float = {
    this.getLowestPeptideMatchThreshold(this.getPeptideMatchThresholds(peptideMatch))
  }

  def getLowestPeptideMatchThreshold(pmThresholds: MascotIonsScoreThresholds): Float = {

    var (identityThreshold, homologyThreshold) = (pmThresholds.identityThreshold, pmThresholds.homologyThreshold)

    var lowestThreshold = 0.0f
    if (homologyThreshold > 0 && homologyThreshold < identityThreshold) { lowestThreshold = homologyThreshold }
    else { lowestThreshold = identityThreshold }

    // Apply the cutoff derived from mascot minimal value (nb candidates = 20)
    if (lowestThreshold < 13) lowestThreshold = 13

    lowestThreshold
  }

  def getTargetMsQueryProperties(msQuery: MsQuery): Option[MsQueryDbSearchProperties] = {
    msQuery.properties.get.getTargetDbSearch
  }

  def getDecoyMsQueryProperties(msQuery: MsQuery): Option[MsQueryDbSearchProperties] = {
    msQuery.properties.get.getDecoyDbSearch
  }

  def buildJointTable(pepMatchJointTable: Array[Pair[PeptideMatch, PeptideMatch]],
                      valuePicker: (PeptideMatch) => Double): Array[Pair[Double, Double]] = {

    val jointTable = pepMatchJointTable.map { pmPair =>
      Pair(valuePicker(pmPair._1),
        valuePicker(pmPair._2))
    }

    jointTable
  }
  
  // TODO: move to an other package
  def rocAnalysisOnPair(pmJointTable: Array[Pair[PeptideMatch, PeptideMatch]],
                        pmScoringParam: String = "log_evalue"): Array[ValidationResult] = { // pmScoringParam = log_evalue || score_offset

    // Create anonymous functions to extract the right peptide match values
    val logEvaluePicker = { pm: PeptideMatch => if (pm != null) -log10(calcPepMatchEvalue(pm)) else 0 } // 0 is for S=0 and IT=13
    val scoreOffsetPicker = { pm: PeptideMatch => if (pm != null) calcPepMatchScoreOffset(pm).toDouble else -13 } // -13 is for S=0 and IT=13
    val valuePickerMap = Map("log_evalue" -> logEvaluePicker, "score_offset" -> scoreOffsetPicker)

    // Create anonymous functions to compute the right threshold value
    val thresholdComputerMap = Map("log_evalue" -> { prob: Double => -log10(prob) },
      "score_offset" -> { prob: Double => -10 * log10(prob / 0.05) }
    )

    // Build log evalue joint table
    val jointTable = this.buildJointTable(pmJointTable, valuePickerMap(pmScoringParam))

    // Instantiate a target decoy computer
    val tdComputer = TargetDecoyComputer

    // Define some vars
    var (probThreshold, fdr) = (1.0, 100.0f)
    val rocPoints = new ArrayBuffer[ValidationResult]

    while (fdr > 0) {
      // Run target/decoy competition
      val competitionThreshold = thresholdComputerMap(pmScoringParam)(probThreshold)
      val competitionCount = tdComputer.computeTdCompetition(jointTable, competitionThreshold)
      //print Dumper competitionCount

      val (targetCount, decoyCount) = (competitionCount._1, competitionCount._2)
      val (tB, tO, dB, dO) = (targetCount.better, targetCount.only, decoyCount.better, decoyCount.only)

      // Compute FDR (note that FDR may be greater than 100%)
      fdr = tdComputer.calcCompetitionFDR(tB, tO, dB, dO)

      // Add ROC point to the list
      val rocPoint = ValidationResult(
        targetMatchesCount = tB + tO + dB,
        decoyMatchesCount = Some(dB + dO + tB),
        fdr = Some(fdr),
        properties = Some(HashMap("p_value" -> probThreshold))
      )

      rocPoints += rocPoint

      //print 'fdr:'.fdr."\n"

      // Update probablity threshold
      probThreshold *= 0.95 // has been arbitrary chosen
    }

    rocPoints.toArray
  }

  // TODO: memoize
  def calcPepMatchEvalue(peptideMatch: PeptideMatch): Double = {

    val lowestPepMatchThreshold = this.getLowestPeptideMatchThreshold(peptideMatch)
    this.calcEvalue(peptideMatch.score, lowestPepMatchThreshold)

  }

  def calcPepMatchScoreOffset(peptideMatch: PeptideMatch): Float = {

    val lowestScoreThreshold = this.getLowestPeptideMatchThreshold(peptideMatch)
    peptideMatch.score - lowestScoreThreshold

  }

}