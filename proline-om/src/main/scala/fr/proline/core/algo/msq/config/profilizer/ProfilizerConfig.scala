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
//  val LFQ = Value // TODO: rename me MEDIAN_RATIO_FITTING
  val MEDIAN_RATIO_FITTING = Value
}

//class MqPeptideAbundanceSummarizingMethodRef extends TypeReference[MqPeptideAbundanceSummarizingMethod.type]
//
//object MqPeptideAbundanceSummarizingMethod extends EnhancedEnum {
//  val BEST_SCORE = Value // has no implementation here, should be called before
//  val MAX_ABUNDANCE_SUM = Value // return one single row
//  val MEAN = Value
//  val MEAN_OF_TOP3 = Value
//  val MEDIAN = Value
//  val MEDIAN_BIOLOGICAL_PROFILE = Value // has no implementation here, should be called before
//  val MEDIAN_PROFILE = Value
//  val SUM = Value
//  val MEDIAN_RATIO_FITTING = Value
//}

class MqPeptidesSelectionMethodRef extends TypeReference[MqPeptidesSelectionMethod.type]

object MqPeptidesSelectionMethod extends EnhancedEnum {
  val ALL_PEPTIDES = Value
  val SPECIFIC = Value
  val RAZOR_AND_SPECIFIC = Value
}

object RazorStrategyMethod extends EnhancedEnum {
    val MOST_SPECIFIC_PEP_SELECTION = Value
}

case class MqPeptidesSelectionConfig(
  razorStrategyMethod : RazorStrategyMethod.Value = RazorStrategyMethod.MOST_SPECIFIC_PEP_SELECTION
)

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
    abundanceSummarizingMethod = MqPeptideAbundanceSummarizingMethod.MEAN
  }
  // force QUANT_PEPTIDE_IONS if Summarizer is LFQ
  if (abundanceSummarizingMethod.equalsIgnoreCase("LFQ")) {
    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDE_IONS)
  }

  if (summarizingBasedOn.isEmpty) {
    summarizingBasedOn = Some(QuantComponentItem.QUANT_PEPTIDES)
  }

  def toPostProcessingConfig: PostProcessingConfig = {
    PostProcessingConfig(
      discardPepIonsSharingPeakels = discardPeptidesSharingPeakels,
      pepIonAbundanceSummarizingMethod = pepIonAbundanceSummarizingMethod,
      pepIonApplyNormalization = peptideStatConfig.applyNormalization,
      peptidesSelectionMethod = if (useOnlySpecificPeptides) MqPeptidesSelectionMethod.SPECIFIC else MqPeptidesSelectionMethod.ALL_PEPTIDES,
      peptidesSelectionConfig = None,
      discardMissCleavedPeptides = discardMissCleavedPeptides,
      missCleavedPeptideFilteringMethod = if (missCleavedPeptideFilteringMethod.isDefined) Some(MissCleavedPeptideFilteringMethod.withName(missCleavedPeptideFilteringMethod.get)) else None,
      discardModifiedPeptides = discardModifiedPeptides,
      modifiedPeptideFilteringMethod = if (modifiedPeptideFilteringMethod.isDefined) Some(ModifiedPeptideFilteringMethod.withName(modifiedPeptideFilteringMethod.get)) else None,
      modifiedPeptideFilterConfig = Some(ModifiedPeptideFilterConfig(ptmDefinitionIdsToDiscard)),
      applyProfileClustering = applyProfileClustering,
      profileClusteringMethod = if (profileClusteringMethod.isDefined) Some(MqPeptidesClusteringMethod.withName(profileClusteringMethod.get)) else None,
      profileClustererConfig =  profileClusteringConfig,
      peptideAbundanceSummarizingMethod =  if (abundanceSummarizingMethod.equalsIgnoreCase("LFQ")) MqPeptideAbundanceSummarizingMethod.MEDIAN_RATIO_FITTING else MqPeptideAbundanceSummarizingMethod.withName(abundanceSummarizingMethod),
      peptideAbundanceSummarizerConfig = Some(MqPeptideAbundanceSummarizerConfig(peptideSummarizingBasedOn = summarizingBasedOn)),
      proteinSetApplyNormalization = proteinStatConfig.applyNormalization
      )
  }

}

case class PostProcessingConfig(
           val configVersion: String = "4.0",

           //IONS level
           var discardPepIonsSharingPeakels: Boolean = true,

           @(JsonScalaEnumeration @field)(classOf[PeakelsSummarizingMethodRef])
           var peakelsSummarizingMethod : PeakelsSummarizingMethod.Value = PeakelsSummarizingMethod.APEX_INTENSITY,

           //PEPTIDES level

           @(JsonScalaEnumeration @field)(classOf[MqPepIonAbundanceSummarizingMethodRef])
           var pepIonAbundanceSummarizingMethod : MqPepIonAbundanceSummarizingMethod.Value = MqPepIonAbundanceSummarizingMethod.BEST_ION,

           // !!!  NORMALIZATION WARNING !!!  : Peptide config normalization config is currently used for Peptide IONS normalisation .... To be changed
           pepIonApplyNormalization: Boolean = true,
           peptideApplyNormalization: Boolean = false,

           //PROTEINS level
           @(JsonScalaEnumeration @field)(classOf[MqPeptidesSelectionMethodRef])
           peptidesSelectionMethod: MqPeptidesSelectionMethod.Value = MqPeptidesSelectionMethod.ALL_PEPTIDES, //alt: SPECIFIC, RAZOR_AND_SPECIFIC
           var peptidesSelectionConfig: Option[MqPeptidesSelectionConfig] = None,

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

           proteinSetApplyNormalization: Boolean = false
       ) {

  //if peptidesSelectionConfig not specified and RAZOR_AND_SPECIFIC is specified, set to default method
  if(peptidesSelectionMethod.equals(MqPeptidesSelectionMethod.RAZOR_AND_SPECIFIC) && peptidesSelectionConfig.isEmpty){
    peptidesSelectionConfig = Some(MqPeptidesSelectionConfig(razorStrategyMethod = RazorStrategyMethod.MOST_SPECIFIC_PEP_SELECTION))
  }

  //if discardMissCleavedPeptides selected with no specified missCleavedPeptideFilteringMethod, set method to default
  if(discardMissCleavedPeptides && missCleavedPeptideFilteringMethod.isEmpty ){
    missCleavedPeptideFilteringMethod = Some(MissCleavedPeptideFilteringMethod.DISCARD_MISS_CLEAVED_FORMS)
  }


  def isMqPeptideAbundanceSummarizerBasedOn(component: QuantComponentItem.Value): Boolean = {
    if (peptideAbundanceSummarizerConfig.isDefined) {
      (peptideAbundanceSummarizerConfig.get.peptideSummarizingBasedOn.get == component.toString)
    } else {
      ((peptideAbundanceSummarizingMethod == MqPeptideAbundanceSummarizingMethod.MEDIAN_RATIO_FITTING) && (component == QuantComponentItem.QUANT_PEPTIDE_IONS)) ||
        ((peptideAbundanceSummarizingMethod != MqPeptideAbundanceSummarizingMethod.MEDIAN_RATIO_FITTING) && (component == QuantComponentItem.QUANT_PEPTIDES))
    }
  }

}

// !!!  NORMALIZATION WARNING !!!  : Peptide config normalization config is currently used for Peptide IONS normalisation .... To be changed
case class PostProcessingConfigV3(
    val configVersion: String = "3.0",

    //IONS level
    var discardPepIonsSharingPeakels: Boolean = true,

    @(JsonScalaEnumeration @field)(classOf[PeakelsSummarizingMethodRef])
    var peakelsSummarizingMethod : PeakelsSummarizingMethod.Value = PeakelsSummarizingMethod.APEX_INTENSITY,

    //PEPTIDES level

    @(JsonScalaEnumeration @field)(classOf[MqPepIonAbundanceSummarizingMethodRef])
    var pepIonAbundanceSummarizingMethod : MqPepIonAbundanceSummarizingMethod.Value = MqPepIonAbundanceSummarizingMethod.BEST_ION,

    // !!!  NORMALIZATION WARNING !!!  : Peptide config normalization config is currently used for Peptide IONS normalisation .... To be changed
    peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),

     //PROTEINS level
     @(JsonScalaEnumeration @field)(classOf[MqPeptidesSelectionMethodRef])
    peptidesSelectionMethod: MqPeptidesSelectionMethod.Value = MqPeptidesSelectionMethod.ALL_PEPTIDES, //alt: SPECIFIC, RAZOR_AND_SPECIFIC
    var peptidesSelectionConfig: Option[MqPeptidesSelectionConfig] = None,

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

  //if peptidesSelectionConfig not specified and RAZOR_AND_SPECIFIC is specified, set to default method
  if(peptidesSelectionMethod.equals(MqPeptidesSelectionMethod.RAZOR_AND_SPECIFIC) && peptidesSelectionConfig.isEmpty){
    peptidesSelectionConfig = Some(MqPeptidesSelectionConfig(razorStrategyMethod = RazorStrategyMethod.MOST_SPECIFIC_PEP_SELECTION))
  }

  //if discardMissCleavedPeptides selected with no specified missCleavedPeptideFilteringMethod, set method to default
  if(discardMissCleavedPeptides && missCleavedPeptideFilteringMethod.isEmpty ){
    missCleavedPeptideFilteringMethod = Some(MissCleavedPeptideFilteringMethod.DISCARD_MISS_CLEAVED_FORMS)
  }


  def isMqPeptideAbundanceSummarizerBasedOn(component: QuantComponentItem.Value): Boolean = {
    if (peptideAbundanceSummarizerConfig.isDefined) {
      (peptideAbundanceSummarizerConfig.get.peptideSummarizingBasedOn.get == component.toString)
    } else {
      ((peptideAbundanceSummarizingMethod == MqPeptideAbundanceSummarizingMethod.MEDIAN_RATIO_FITTING) && (component == QuantComponentItem.QUANT_PEPTIDE_IONS)) ||
        ((peptideAbundanceSummarizingMethod != MqPeptideAbundanceSummarizingMethod.MEDIAN_RATIO_FITTING) && (component == QuantComponentItem.QUANT_PEPTIDES))
    }
  }

  def toPostProcessingConfig: PostProcessingConfig = {
    PostProcessingConfig(
      discardPepIonsSharingPeakels = discardPepIonsSharingPeakels,
      pepIonAbundanceSummarizingMethod = pepIonAbundanceSummarizingMethod,
      pepIonApplyNormalization = peptideStatConfig.applyNormalization,
      peptidesSelectionMethod = peptidesSelectionMethod,
      peptidesSelectionConfig = peptidesSelectionConfig,
      discardMissCleavedPeptides = discardMissCleavedPeptides,
      missCleavedPeptideFilteringMethod = missCleavedPeptideFilteringMethod,
      discardModifiedPeptides = discardModifiedPeptides,
      modifiedPeptideFilteringMethod = modifiedPeptideFilteringMethod,
      modifiedPeptideFilterConfig = modifiedPeptideFilterConfig,
      applyProfileClustering = applyProfileClustering,
      profileClusteringMethod =profileClusteringMethod,
      profileClustererConfig =  profileClustererConfig,
      peptideAbundanceSummarizingMethod =  peptideAbundanceSummarizingMethod,
      peptideAbundanceSummarizerConfig = peptideAbundanceSummarizerConfig,
      proteinSetApplyNormalization = proteinStatConfig.applyNormalization
    )
  }
}


