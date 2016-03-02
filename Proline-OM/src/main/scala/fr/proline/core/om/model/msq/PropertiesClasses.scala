package fr.proline.core.om.model.msq

import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

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

case class MasterQuantReporterIonProperties (
  //@BeanProperty var quantReporterIons: Array[QuantReporterIonProperties]
)

case class MasterQuantPeptideIonProperties (
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  @BeanProperty var bestQuantChannelId: Option[Long] = None,
  
  // Key = QuantChannel ID ; Value = PeptideMatch ID
  @JsonDeserialize( keyAs = classOf[java.lang.Long], contentAs = classOf[java.lang.Long] )
  var bestPeptideMatchIdMap: scala.collection.immutable.HashMap[Long,Long] = scala.collection.immutable.HashMap()
)

case class MasterQuantPeptideProfile (
  @BeanProperty var ratios: List[Option[ComputedRatio]]
  //@BeanProperty var mqProtSetProfileIds: Option[Array[Long]] = None
)

case class MasterQuantPeptideProperties (
  @BeanProperty var discardingReason: Option[String] = None,
  
  @JsonDeserialize(contentAs = classOf[Array[Long]])
  @BeanProperty var mqProtSetIds: Option[Array[Long]] = None,
  
  @JsonDeserialize( keyAs = classOf[java.lang.Integer], contentAs = classOf[MasterQuantPeptideProfile] )
  private var mqPepProfileByGroupSetupNumber: HashMap[Int,MasterQuantPeptideProfile] = null
) {
  
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
  @BeanProperty var abundances: Array[Float],
  @BeanProperty var ratios: List[Option[ComputedRatio]],
  @BeanProperty var mqPeptideIds: Array[Long]
)
 
case class MasterQuantProteinSetProperties (
  
  @JsonDeserialize( keyAs = classOf[java.lang.Integer], contentAs = classOf[Array[MasterQuantProteinSetProfile]] )
  var mqProtSetProfilesByGroupSetupNumber: HashMap[Int, Array[MasterQuantProteinSetProfile]] = null,
  //@BeanProperty var specificSampleId: Option[Long] = None, // defined if the protein has been seen in a single sample
  
  @JsonDeserialize(contentAs = classOf[Array[Long]] )
  @BeanProperty var selectedMasterQuantPeptideIds: Option[Array[Long]] = None,
  
  @JsonDeserialize(contentAs = classOf[Array[Long]] )
  @BeanProperty var selectedMasterQuantPeptideIonIds: Option[Array[Long]] = None
) {
  
  def getMqProtSetProfilesByGroupSetupNumber(): Option[HashMap[Int, Array[MasterQuantProteinSetProfile]]] = {
    Option(mqProtSetProfilesByGroupSetupNumber)
  }
  
  def setMqProtSetProfilesByGroupSetupNumber( mqProtSetProfilesMap: Option[HashMap[Int, Array[MasterQuantProteinSetProfile]]] ) = {
    mqProtSetProfilesByGroupSetupNumber = mqProtSetProfilesMap.orNull
  }
  
}


case class MasterQuantChannelProperties (
  @JsonDeserialize(contentAs = classOf[java.lang.Long])
  @BeanProperty var identResultSummaryId: Option[Long],
  @JsonDeserialize(contentAs = classOf[java.lang.Long])
  @BeanProperty var identDatasetId: Option[Long],
  @JsonDeserialize(contentAs = classOf[SpectralCountProperties])
  @BeanProperty var spectralCountProperties: Option[SpectralCountProperties] = None
)

case class SpectralCountProperties (
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  @BeanProperty var weightsRefRSMIds: Array[java.lang.Long] = Array()
)