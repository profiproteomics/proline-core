package fr.proline.core.algo.msq.config.profilizer

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.algo.lcms.{PeakelsSummarizingMethod, PeakelsSummarizingMethodRef, MqPepIonAbundanceSummarizingMethod, MqPepIonAbundanceSummarizingMethodRef}

import scala.annotation.meta.field

object MissingAbundancesInferenceMethod extends EnhancedEnum {
  val GAUSSIAN_MODEL = Value // SmartMissingAbundancesInferer
  val PERCENTILE = Value // FixedNoiseMissingAbundancesReplacer
}

case class MissingAbundancesInferenceConfig(
  noisePercentile: Option[Int] = None // should be only defined for PERCENTILE method
) {
  def getNoisePercentile(): Int = noisePercentile.getOrElse(1)
}

class MqPeptidesClusteringMethodRef extends TypeReference[MqPeptidesClusteringMethod.type]

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

class ModifiedPeptideFilteringMethodRef extends TypeReference[ModifiedPeptideFilteringMethod.type]

object ModifiedPeptideFilteringMethod extends EnhancedEnum {
  val DISCARD_ALL_FORMS = Value
  val DISCARD_MODIFIED_FORMS = Value
  val KEEP_MOST_ABUNDANT_FORM = Value
}

class MissCleavedPeptideFilteringMethodRef extends TypeReference[MissCleavedPeptideFilteringMethod.type]

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
  val LFQ = Value // TODO: rename me MEDIAN_RATIO_FITTING
}

class MqPeptideAbundanceSummarizingMethodRef extends TypeReference[MqPeptideAbundanceSummarizingMethod.type]

object MqPeptideAbundanceSummarizingMethod extends EnhancedEnum {
  val BEST_SCORE = Value // has no implementation here, should be called before
  val MAX_ABUNDANCE_SUM = Value // return one single row
  val MEAN = Value
  val MEAN_OF_TOP3 = Value
  val MEDIAN = Value
  val MEDIAN_BIOLOGICAL_PROFILE = Value // has no implementation here, should be called before
  val MEDIAN_PROFILE = Value
  val SUM = Value
  val MEDIAN_RATIO_FITTING = Value
}

class MqPeptidesSelectionMethodRef extends TypeReference[MqPeptidesSelectionMethod.type]

object MqPeptidesSelectionMethod extends EnhancedEnum {
  val ALL_PEPTIDES = Value
  val SPECIFIC = Value
  val RAZOR_AND_SPECIFIC = Value
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

case class ModifiedPeptideFilterConfig(
    var ptmDefinitionIdsToDiscard: Array[Long] = Array.empty[Long],
    var ptmPattern: Option[String] = None
)

case class MqPeptideAbundanceSummarizerConfig(
    var peptideSummarizingBasedOn: Option[String] = None
)

////TODO Remove once PostProcessingConfig is tested and OK
//case class GenericProfilizerConfig(
//    discardMissedCleavedPeptides: Boolean = true,
//    var missCleavedPeptideFilteringMethod: Option[String] = None,
//
//    discardOxidizedPeptides: Boolean = true,
//    var oxidizedPeptideFilteringMethod: Option[String] = None,
//
//    discardModifiedPeptides: Boolean = true,
//    var modifiedPeptideFilteringMethod: Option[String] = None,
//    var ptmDefinitionIdsToDiscard : Array[Long] = Array.empty[Long],
//
//    //discardLowIdentPeptides: Boolean = false,
//    useOnlySpecificPeptides: Boolean = true,
//    discardPeptidesSharingPeakels: Boolean = true,
//
//    applyProfileClustering: Boolean = true,
//    var profileClusteringMethod: Option[String] = None,
//    profileClusteringConfig: Option[MqPeptidesClustererConfig] = None,
//
//    var abundanceSummarizerMethod: String = null,
//
//    peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
//    proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
//
//    var summarizingBasedOn: Option[String] = None,
//    var pepIonAbundanceSummarizingMethod : PepIonAbundanceSummarizingMethod.Value = PepIonAbundanceSummarizingMethod.BEST_ION,
//
//    var isV1Config : Boolean = true
//
//) {
//  // Workaround for jackson support of default values
//  if( oxidizedPeptideFilteringMethod.isEmpty ) {
//    oxidizedPeptideFilteringMethod = Some(OxidizedPeptideFilteringMethod.DISCARD_ALL_FORMS)
//  }
//  // Workaround for jackson support of default values
//  if( missCleavedPeptideFilteringMethod.isEmpty ) {
//    missCleavedPeptideFilteringMethod = Some(MissCleavedPeptideFilteringMethod.DISCARD_ALL_FORMS)
//  }
//  if(modifiedPeptideFilteringMethod.isEmpty){
//    modifiedPeptideFilteringMethod  = Some(ModifiedPeptideFilteringMethod.DISCARD_ALL_FORMS)
//  }
//  if(profileClusteringMethod.isEmpty) {
//    profileClusteringMethod = Some(MqPeptidesClusteringMethod.QUANT_PROFILE)
//  }
//  if( abundanceSummarizerMethod == null) {
//    abundanceSummarizerMethod = AbundanceSummarizerMethod.MEAN
//  }
//  // force QUANT_PEPTIDE_IONS if Summarizer is LFQ
//  if (abundanceSummarizerMethod == AbundanceSummarizerMethod.LFQ.toString) {
//    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDE_IONS)
//  }
//
//  if (summarizingBasedOn.isEmpty) {
//    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDES)
//  }
//}

case class PostProcessingConfigV2(
   val configVersion: String = "2.0",

   discardMissCleavedPeptides: Boolean = true,
   var missCleavedPeptideFilteringMethod: Option[String] = None,

   discardModifiedPeptides: Boolean = true,
   var modifiedPeptideFilteringMethod: Option[String] = None,
   var ptmDefinitionIdsToDiscard : Array[Long] = Array.empty[Long],

   useOnlySpecificPeptides: Boolean = true,
   discardPeptidesSharingPeakels: Boolean = true,

   applyProfileClustering: Boolean = true,
   var profileClusteringMethod: Option[String] = None,
   profileClusteringConfig: Option[MqPeptidesClustererConfig] = None,

   var abundanceSummarizingMethod: String = null,

   peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
   proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),

   var summarizingBasedOn: Option[String] = None,
   @(JsonScalaEnumeration @field)(classOf[MqPepIonAbundanceSummarizingMethodRef])
   var pepIonAbundanceSummarizingMethod : MqPepIonAbundanceSummarizingMethod.Value = MqPepIonAbundanceSummarizingMethod.BEST_ION

) {
  // Workaround for jackson support of default values
  if( modifiedPeptideFilteringMethod.isEmpty ) {
    modifiedPeptideFilteringMethod = Some(ModifiedPeptideFilteringMethod.DISCARD_ALL_FORMS)
  }
  // Workaround for jackson support of default values
  if( missCleavedPeptideFilteringMethod.isEmpty ) {
    missCleavedPeptideFilteringMethod = Some(MissCleavedPeptideFilteringMethod.DISCARD_ALL_FORMS)
  }
  if(profileClusteringMethod.isEmpty) {
    profileClusteringMethod = Some(MqPeptidesClusteringMethod.QUANT_PROFILE)
  }
  if( abundanceSummarizingMethod == null) {
    abundanceSummarizingMethod = AbundanceSummarizerMethod.MEAN
  }
  // force QUANT_PEPTIDE_IONS if Summarizer is LFQ
  if (abundanceSummarizingMethod == AbundanceSummarizerMethod.LFQ.toString) {
    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDE_IONS)
  }

  if (summarizingBasedOn.isEmpty) {
    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDES)
  }

  def toPostProcessingConfig: PostProcessingConfig = {
    PostProcessingConfig(
      discardPepIonsSharingPeakels = discardPeptidesSharingPeakels,
      pepIonAbundanceSummarizingMethod = pepIonAbundanceSummarizingMethod,
      peptideStatConfig = peptideStatConfig,
      peptidesSelcetionMethod = if (useOnlySpecificPeptides) MqPeptidesSelectionMethod.SPECIFIC else MqPeptidesSelectionMethod.ALL_PEPTIDES,
      discardMissCleavedPeptides = discardMissCleavedPeptides,
      missCleavedPeptideFilteringMethod = if (missCleavedPeptideFilteringMethod.isDefined) Some(MissCleavedPeptideFilteringMethod.withName(missCleavedPeptideFilteringMethod.get)) else None,
      discardModifiedPeptides = discardModifiedPeptides,
      modifiedPeptideFilteringMethod = if (modifiedPeptideFilteringMethod.isDefined) Some(ModifiedPeptideFilteringMethod.withName(modifiedPeptideFilteringMethod.get)) else None,
      modifiedPeptideFilterConfig = Some(ModifiedPeptideFilterConfig(ptmDefinitionIdsToDiscard)),
      applyProfileClustering = applyProfileClustering,
      profileClusteringMethod = if (profileClusteringMethod.isDefined) Some(MqPeptidesClusteringMethod.withName(profileClusteringMethod.get)) else None,
      profileClustererConfig =  profileClusteringConfig,
      peptideAbundanceSummarizingMethod =  MqPeptideAbundanceSummarizingMethod.withName(abundanceSummarizingMethod),
      peptideAbundanceSummarizerConfig = Some(MqPeptideAbundanceSummarizerConfig(peptideSummarizingBasedOn = summarizingBasedOn)),
      proteinStatConfig = proteinStatConfig
      )
  }

}

case class PostProcessingConfig(
    val configVersion: String = "3.0",

    //IONS level
    var discardPepIonsSharingPeakels: Boolean = true,

    @(JsonScalaEnumeration @field)(classOf[PeakelsSummarizingMethodRef])
    var peakelsSummarizingMethod : PeakelsSummarizingMethod.Value = PeakelsSummarizingMethod.APEX_INTENSITY,

    //PEPTIDES level

    @(JsonScalaEnumeration @field)(classOf[MqPepIonAbundanceSummarizingMethodRef])
    var pepIonAbundanceSummarizingMethod : MqPepIonAbundanceSummarizingMethod.Value = MqPepIonAbundanceSummarizingMethod.BEST_ION,

    peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),

    //PROTEINS level
    @(JsonScalaEnumeration @field)(classOf[MqPeptidesSelectionMethodRef])
    peptidesSelcetionMethod: MqPeptidesSelectionMethod.Value = MqPeptidesSelectionMethod.ALL_PEPTIDES, //alt: SPECIFIC, RAZOR_AND_SPECIFIC

    discardMissCleavedPeptides: Boolean = true,
    @(JsonScalaEnumeration @field)(classOf[MissCleavedPeptideFilteringMethodRef])
    var missCleavedPeptideFilteringMethod: Option[MissCleavedPeptideFilteringMethod.Value] = None,

    discardModifiedPeptides: Boolean = true,
    @(JsonScalaEnumeration @field)(classOf[ModifiedPeptideFilteringMethodRef])
    var modifiedPeptideFilteringMethod: Option[ModifiedPeptideFilteringMethod.Value] = None,
    modifiedPeptideFilterConfig: Option[ModifiedPeptideFilterConfig] = None,

    applyProfileClustering: Boolean = true,
    @(JsonScalaEnumeration @field)(classOf[MqPeptidesClusteringMethodRef])
    var profileClusteringMethod: Option[MqPeptidesClusteringMethod.Value] = None,
    profileClustererConfig: Option[MqPeptidesClustererConfig] = None,

    @(JsonScalaEnumeration @field)(classOf[MqPeptideAbundanceSummarizingMethodRef])
    var peptideAbundanceSummarizingMethod: MqPeptideAbundanceSummarizingMethod.Value = MqPeptideAbundanceSummarizingMethod.MEAN,
    peptideAbundanceSummarizerConfig: Option[MqPeptideAbundanceSummarizerConfig] = None,

    proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig()
  ) {

  def isMqPeptideAbundanceSummarizerBasedOn(component: String): Boolean = {
    if (peptideAbundanceSummarizerConfig.isDefined) {
      (peptideAbundanceSummarizerConfig.get.peptideSummarizingBasedOn.get == component)
    } else {
      ((peptideAbundanceSummarizingMethod == AbundanceSummarizerMethod.LFQ) && (component == QuantComponentItem.QUANT_PEPTIDE_IONS)) ||
        ((peptideAbundanceSummarizingMethod != AbundanceSummarizerMethod.LFQ) && (component == QuantComponentItem.QUANT_PEPTIDES))
    }
  }

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


  def toPostProcessingConfig: PostProcessingConfig = {
    PostProcessingConfig(
      discardPepIonsSharingPeakels = discardPeptidesSharingPeakels,
      pepIonAbundanceSummarizingMethod = MqPepIonAbundanceSummarizingMethod.BEST_ION,
      peptideStatConfig = peptideStatConfig,
      peptidesSelcetionMethod = if (useOnlySpecificPeptides) MqPeptidesSelectionMethod.SPECIFIC else MqPeptidesSelectionMethod.ALL_PEPTIDES,
      discardMissCleavedPeptides = discardMissedCleavedPeptides,
      missCleavedPeptideFilteringMethod = if (missCleavedPeptideFilteringMethod.isDefined) Some(MissCleavedPeptideFilteringMethod.withName(missCleavedPeptideFilteringMethod.get)) else None,
      discardModifiedPeptides = discardOxidizedPeptides,
      modifiedPeptideFilteringMethod = if (oxidizedPeptideFilteringMethod.isDefined) Some(ModifiedPeptideFilteringMethod.withName(oxidizedPeptideFilteringMethod.get)) else None,
      modifiedPeptideFilterConfig = if (discardOxidizedPeptides) Some(ModifiedPeptideFilterConfig(ptmPattern = Some(""".*\[O\]"""))) else None,
      applyProfileClustering = applyProfileClustering,
      profileClusteringMethod = if (profileClusteringMethod.isDefined) Some(MqPeptidesClusteringMethod.withName(profileClusteringMethod.get)) else None,
      profileClustererConfig =  profileClusteringConfig,
      peptideAbundanceSummarizingMethod =  MqPeptideAbundanceSummarizingMethod.withName(abundanceSummarizerMethod),
      peptideAbundanceSummarizerConfig = Some(MqPeptideAbundanceSummarizerConfig(peptideSummarizingBasedOn = summarizingBasedOn)),
      proteinStatConfig = proteinStatConfig
    )
  }

}
