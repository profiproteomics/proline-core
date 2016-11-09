package fr.proline.core.algo.msq.config.profilizer

import fr.profi.util.lang.EnhancedEnum

object MissingAbundancesInferenceMethod extends EnhancedEnum {
  val GAUSSIAN_MODEL = Value // SmartMissingAbundancesInferer
  val PERCENTILE = Value // FixedNoiseMissingAbundancesReplacer
}

case class MissingAbundancesInferenceConfig(
  noisePercentile: Option[Int] = None // should be only defined for PERCENTILE method
) {
  def getNoisePercentile(): Int = noisePercentile.getOrElse(1)
}

object MqPeptidesClusteringMethod extends EnhancedEnum {
  val PEPTIDE_SEQUENCE = Value // Cluster name = SEQUENCE
  val PEPTIDE_SET = Value // Cluster name = PROTEIN ACCESSION
  val PTM_PATTERN = Value // Cluster name = MODIFIED/UNMODIFIED LOCATED PTM IN PROTEIN SEQUENCE AND ACCESSION FOR OTHERS
  val QUANT_PROFILE = Value // Cluster name = RATIO STATES
}

case class MqPeptidesClustererConfig(
  ptmPatternPtmDefIds: Seq[Long] = Seq() // only for PTM_PATTERN method
)

object OxidizedPeptideFilteringMethod extends EnhancedEnum {
  val DISCARD_ALL_FORMS = Value
  val DISCARD_OXIDIZED_FORMS = Value
  val KEEP_MOST_ABUNDANT_FORM = Value
}

object MissCleavedPeptideFilteringMethod extends EnhancedEnum {
  val DISCARD_ALL_FORMS = Value
  val DISCARD_MISS_CLEAVED_FORMS = Value
  val KEEP_MOST_ABUNDANT_FORM = Value
}

// TODO: rename AbundanceSummarizerMethod
object AbundanceSummarizerMethod extends EnhancedEnum {
  val BEST_SCORE = Value // has no implementation here, should be called before
  val MAX_ABUNDANCE_SUM = Value // return one single row
  val MEAN = Value
  val MEAN_OF_TOP3 = Value
  val MEDIAN = Value
  val MEDIAN_BIOLOGICAL_PROFILE = Value // has no implementation here, should be called before
  val MEDIAN_PROFILE = Value
  val SUM = Value
  val LFQ = Value
}

object QuantComponentItem extends EnhancedEnum {
  val QUANT_PEPTIDES = Value 
  val QUANT_PEPTIDE_IONS = Value 
}

case class ProfilizerStatConfig(
  // Note: maxCv is experimental => DO NOT PUT IN GUI
  var maxCv: Option[Float] = None, // TODO: do not discard peptides => apply this filter during the summarization step ?
  statTestsAlpha: Float = 0.01f,
  minZScore: Float = 0.4f, // ZScore equals ln(ratio) followed by standardisation
  minPsmCountPerRatio: Int = 0, // TODO: remove me ???
  applyNormalization: Boolean = true,
  
  applyMissValInference: Boolean = true, // TODO: remove me when IHMs haven been updated
  // TODO: replace Some(MissingAbundancesInferenceConfig) by None when IHMs haven been updated
  var missValInferenceMethod: String = null,
  var missValInferenceConfig: Option[MissingAbundancesInferenceConfig] = None,
  
  applyVarianceCorrection: Boolean = true,
  applyTTest: Boolean = true,
  applyZTest: Boolean = true
) {
  // Workaround for jackson support of default values
  if(missValInferenceMethod == null) missValInferenceMethod = MissingAbundancesInferenceMethod.GAUSSIAN_MODEL
  if(missValInferenceConfig.isEmpty) missValInferenceConfig = Some(MissingAbundancesInferenceConfig())
}

case class ProfilizerConfig(
  discardMissedCleavedPeptides: Boolean = true, // TODO: rename me in discardMissCleavedPeptides
  var missCleavedPeptideFilteringMethod: Option[String] = None,
  
  discardOxidizedPeptides: Boolean = true,
  var oxidizedPeptideFilteringMethod: Option[String] = None,
  
  //discardLowIdentPeptides: Boolean = false,
  useOnlySpecificPeptides: Boolean = true,
  discardPeptidesSharingPeakels: Boolean = true,
  
  applyProfileClustering: Boolean = true,
  var profileClusteringMethod: Option[String] = None,
  profileClusteringConfig: Option[MqPeptidesClustererConfig] = None,
  
  // TODO: rename into abundanceSummarizingMethod ???
  var abundanceSummarizerMethod: String = null,
  
  peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
  proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
  
  var summarizingBasedOn: Option[String] = None
  
) {
  // Workaround for jackson support of default values
  if( oxidizedPeptideFilteringMethod.isEmpty ) {
    oxidizedPeptideFilteringMethod = Some(OxidizedPeptideFilteringMethod.DISCARD_ALL_FORMS)
  }
  // Workaround for jackson support of default values
  if( missCleavedPeptideFilteringMethod.isEmpty ) {
    missCleavedPeptideFilteringMethod = Some(MissCleavedPeptideFilteringMethod.DISCARD_ALL_FORMS)
  }
  if(profileClusteringMethod.isEmpty) {
    profileClusteringMethod = Some(MqPeptidesClusteringMethod.QUANT_PROFILE)
  }
  if( abundanceSummarizerMethod == null) {
    abundanceSummarizerMethod = AbundanceSummarizerMethod.MEAN
  }
  // force QUANT_PEPTIDE_IONS if Summarizer is LFQ
  if (abundanceSummarizerMethod == AbundanceSummarizerMethod.LFQ.toString) {
    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDE_IONS)
  }
  
  if (summarizingBasedOn.isEmpty) {
    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDES)
  }
}