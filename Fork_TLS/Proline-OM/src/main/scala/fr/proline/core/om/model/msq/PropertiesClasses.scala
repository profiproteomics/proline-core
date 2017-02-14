package fr.proline.core.om.model.msq

import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.profi.util.collection._

case class ComputedRatio(
  @BeanProperty var numerator: Float,
  @BeanProperty var denominator: Float,
  @BeanProperty var cv: Option[Float] = None, // only for average of different ratios
  @BeanProperty var state: Int = 0,// -1 means under-abundant, 0 means invariant and +1 means over-abundant
  @BeanProperty var tTestPValue: Option[Double] = None,
  @BeanProperty var zTestPValue: Option[Double] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var zScore: Option[Float] = None
) {
  @transient lazy val ratioValue = if( numerator > 0 && denominator > 0 ) numerator/denominator else Float.NaN
}

case class MasterQuantReporterIonProperties(
  //@BeanProperty var quantReporterIons: Array[QuantReporterIonProperties]
)

case class MasterQuantPeptideIonProperties(
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  @BeanProperty var bestQuantChannelId: Option[Long] = None,
  
  // Key = QuantChannel ID ; Value = PeptideMatch ID
  // TODO: use LongMap if possible
  @JsonDeserialize( keyAs = classOf[java.lang.Long], contentAs = classOf[java.lang.Long] )
  @BeanProperty var bestPeptideMatchIdMap: scala.collection.immutable.HashMap[Long,Long] = scala.collection.immutable.HashMap()
) {
  if( bestPeptideMatchIdMap == null ) bestPeptideMatchIdMap = scala.collection.immutable.HashMap()
}

case class MasterQuantPeptideProfile(
  @BeanProperty var ratios: List[Option[ComputedRatio]] = List()
  //@BeanProperty var mqProtSetProfileIds: Option[Array[Long]] = None
) {
  if( ratios == null ) ratios = List()
}


/** Represents a Peptide quantitative properties across multiple quant channels (runs). 
 * 
 * This information is stored in Proline datastore in MSIdb.master_quant_component.serialized_properties. 
 * An example can be extracted from the datastore by the following SQL Query : 
 * {{{
 * select * 
 * from master_quant_component mqc
 * where mqc.schema_name like '%peptide%' 
 * limit 10	
 * }}}
 *
 * @see MasterQuantPeptide
 */
case class MasterQuantPeptideProperties(
  @BeanProperty var discardingReason: Option[String] = None,
  
  @JsonDeserialize(contentAs = classOf[Array[Long]])
  @BeanProperty var mqProtSetIds: Option[Array[Long]] = None,
  
  // TODO: use LongMap if possible
  @JsonDeserialize( keyAs = classOf[java.lang.Integer], contentAs = classOf[MasterQuantPeptideProfile] )
  private var mqPepProfileByGroupSetupNumber: HashMap[Int,MasterQuantPeptideProfile] = null
) {
  
  if( mqPepProfileByGroupSetupNumber == null ) mqPepProfileByGroupSetupNumber = HashMap()
  
  def getMqPepProfileByGroupSetupNumber(): Option[HashMap[Int,MasterQuantPeptideProfile]] = {
    Option(mqPepProfileByGroupSetupNumber)
  }
  
  def setMqPepProfileByGroupSetupNumber( mqPepProfileMap: Option[HashMap[Int,MasterQuantPeptideProfile]] ) = {
    mqPepProfileByGroupSetupNumber = mqPepProfileMap.orNull
  }
}

case class MasterQuantProteinSetProfile(
  //@BeanProperty var id: Long,
  @BeanProperty var name: String = "",
  @BeanProperty var rawAbundances: Array[Float] = Array(),
  @BeanProperty var abundances: Array[Float] = Array(),
  @BeanProperty var ratios: List[Option[ComputedRatio]] = List(),
  @BeanProperty var mqPeptideIds: Array[Long] = Array(),
  // TODO: fill me
  @BeanProperty var peptideMatchesCounts: Array[Long] = Array()
) {
  if( name == null ) name = ""
  if( rawAbundances == null ) rawAbundances = Array()
  if( abundances == null ) abundances = Array()
  if( ratios == null ) ratios = List()
  if( mqPeptideIds == null ) mqPeptideIds = Array()
  if( peptideMatchesCounts == null ) peptideMatchesCounts = Array()
}
 
case class MasterQuantProteinSetProperties(
  
  // TODO: use LongMap if possible
  @JsonDeserialize( keyAs = classOf[java.lang.Integer], contentAs = classOf[Array[MasterQuantProteinSetProfile]] )
  var mqProtSetProfilesByGroupSetupNumber: HashMap[Int, Array[MasterQuantProteinSetProfile]] = null,
  //@BeanProperty var specificSampleId: Option[Long] = None, // defined if the protein has been seen in a single sample
  
  //TODO : to be removed and replaced by selectionLevelBymasterQuantPeptideId
  @JsonDeserialize(contentAs = classOf[Array[Long]] )
  @deprecated(message="Replaced by mqPeptideSelLevelById", since="1.1.0")
  @BeanProperty var selectedMasterQuantPeptideIds: Option[Array[Long]] = None,
  
  //TODO : to be removed and replaced by selectionLevelBymasterQuantPeptideIonId
  @JsonDeserialize(contentAs = classOf[Array[Long]] )
  @deprecated(message="Replaced by mqPeptideIonSelLevelById", since="1.1.0")
  @BeanProperty var selectedMasterQuantPeptideIonIds: Option[Array[Long]] = None,
  
  // TODO: use LongMap if possible
  @JsonDeserialize( keyAs = classOf[java.lang.Long], contentAs = classOf[java.lang.Integer] )
  var mqPeptideSelLevelById: HashMap[Long, Int] = null,
  
  // TODO: use LongMap if possible
  @JsonDeserialize( keyAs = classOf[java.lang.Long], contentAs = classOf[java.lang.Integer] )
  var mqPeptideIonSelLevelById: HashMap[Long, Int] = null
) {
  
  /* --- Getter/setter for mqProtSetProfilesByGroupSetupNumber --- */
  
  // Workaround for lack of default values support in jackson
  if( mqProtSetProfilesByGroupSetupNumber == null ) mqProtSetProfilesByGroupSetupNumber = HashMap()
    
  def getMqProtSetProfilesByGroupSetupNumber(): Option[HashMap[Int, Array[MasterQuantProteinSetProfile]]] = {
    Option(mqProtSetProfilesByGroupSetupNumber)
  }
  
  def setMqProtSetProfilesByGroupSetupNumber( mqProtSetProfilesMap: Option[HashMap[Int, Array[MasterQuantProteinSetProfile]]] ) = {
    mqProtSetProfilesByGroupSetupNumber = mqProtSetProfilesMap.orNull
  }
  
  /* --- Getter/setter for mqPeptideSelLevelById --- */
  
  // Workaround for lack of default values support in jackson
  if( mqPeptideSelLevelById == null ) mqPeptideSelLevelById = HashMap()
  
  def getSelectionLevelByMqPeptideId(): Option[HashMap[Long, Int]] = {
    Option(mqPeptideSelLevelById)
  }
  
  def setSelectionLevelByMqPeptideId( mqPeptideSelLevelMap: Option[HashMap[Long, Int]] ) = {
    mqPeptideSelLevelById = mqPeptideSelLevelMap.orNull
  }
  
  /* --- Getter/setter for mqPeptideIonSelLevelById --- */
  
  // Workaround for lack of default values support in jackson
  if( mqPeptideIonSelLevelById == null ) mqPeptideIonSelLevelById = HashMap()
  
  def getSelectionLevelByMqPeptideIonId(): Option[HashMap[Long, Int]] = {
    Option(mqPeptideIonSelLevelById)
  }
  
  def setSelectionLevelByMqPeptideIonId( mqPeptideIonSelLevelMap: Option[HashMap[Long, Int]] ) = {
    mqPeptideIonSelLevelById = mqPeptideIonSelLevelMap.orNull
  }
  
}


case class MasterQuantChannelProperties(
  // TODO: add to table
  @JsonDeserialize(contentAs = classOf[java.lang.Long])
  @BeanProperty var identDatasetId: Option[Long] = None,
  
  // TODO: add to table
  @JsonDeserialize(contentAs = classOf[java.lang.Long])
  @BeanProperty var identResultSummaryId: Option[Long] = None,

  // TODO: remove me => it should be stored in dedicated object tree
  @JsonDeserialize(contentAs = classOf[SpectralCountProperties])
  @BeanProperty var spectralCountProperties: Option[SpectralCountProperties] = None
)

case class SpectralCountProperties (
  //@JsonDeserialize(contentAs = classOf[java.lang.Long] )
  // TODO: rename into weightsRefRsmIds => this is the correct CamelCase syntax
  @BeanProperty var weightsRefRSMIds: Array[Long] = Array()
) {
  if(weightsRefRSMIds == null) weightsRefRSMIds = Array()
}