package fr.proline.core.om.model.msq

import scala.collection.mutable.HashMap
import scala.beans.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

case class ComputedRatio (
  @BeanProperty var numerator: Float,
  @BeanProperty var denominator: Float,
  @BeanProperty var state: Int = 0,// -1 means under-abundant, 0 means invariant and +1 means over-abundant
  @BeanProperty var tTestPValue: Option[Double] = None,
  @BeanProperty var zTestPValue: Option[Double] = None
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
  @JsonDeserialize( keyAs = classOf[java.lang.Long] )
  var bestPeptideMatchIdMap: scala.collection.immutable.HashMap[Long,Long] = scala.collection.immutable.HashMap()
)

case class MasterQuantPeptideProfile (
  @BeanProperty var ratios: List[Option[ComputedRatio]]
  //@BeanProperty var mqProtSetProfileIds: Option[Array[Long]] = None
)

case class MasterQuantPeptideProperties (
  @JsonDeserialize(contentAs = classOf[Array[Long]])
  @BeanProperty var mqProtSetIds: Option[Array[Long]] = None,
  @BeanProperty var mqPepProfileByGroupSetupNumber: Option[HashMap[String,MasterQuantPeptideProfile]] = None
)

case class MasterQuantProteinSetProfile (
  //@BeanProperty var id: Long,
  @BeanProperty var abundances: Array[Float],
  @BeanProperty var ratios: List[Option[ComputedRatio]],
  @BeanProperty var mqPeptideIds: Array[Long]
)
 
case class MasterQuantProteinSetProperties (
  @BeanProperty var mqProtSetProfilesByGroupSetupNumber: Option[HashMap[String, Array[MasterQuantProteinSetProfile]]] = None,
  //@BeanProperty var specificSampleId: Option[Long] = None, // defined if the protein has been seen in a single sample
  
  @JsonDeserialize(contentAs = classOf[Array[Long]] )
  @BeanProperty var selectedMasterQuantPeptideIds: Option[Array[Long]] = None,
  
  @JsonDeserialize(contentAs = classOf[Array[Long]] )
  @BeanProperty var selectedMasterQuantPeptideIonIds: Option[Array[Long]] = None
)

case class MasterQuantChannelProperties (
    @JsonDeserialize(contentAs = classOf[java.lang.Long])
	@BeanProperty var identResultSummaryId: Option[Long],
	@JsonDeserialize(contentAs = classOf[java.lang.Long])
	@BeanProperty var identDatasetId: Option[Long]
)