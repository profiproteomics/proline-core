package fr.proline.core.om.model.msq

import scala.collection.mutable.HashMap
import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class ComputedRatio (
  @BeanProperty var numerator: Float,
  @BeanProperty var denominator: Float,
  @BeanProperty var state: Int = 0,// -1 means under-abundant, 0 means invariant and +1 means over-abundant
  @BeanProperty var tTestPValue: Option[Double] = None,
  @BeanProperty var zTestPValue: Option[Double] = None
) {
  @transient lazy val ratioValue = if( numerator > 0 && denominator > 0 ) numerator/denominator else Float.NaN
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantReporterIonProperties (
  //@BeanProperty var quantReporterIons: Array[QuantReporterIonProperties]
)

/*
@JsonSnakeCase
case class QuantPeptideIonProperties (
  @BeanProperty val quantChannelId: Long,
  @BeanProperty val rawAbundance: Float,
  @BeanProperty var abundance: Float,
  @BeanProperty var selectionLevel: Int,
  @BeanProperty var moz: Double,
  @BeanProperty var elutionTime: Option[Float] = None,
  @BeanProperty var scanNumber: Option[Int] = None,
  @BeanProperty var predictedElutionTime: Option[Float] = None,
  @BeanProperty var predictedScanNumber: Option[Int] = None,
  @BeanProperty var peptideMatchesCount: Int,
  @BeanProperty var bestPeptideMatchScore: Option[Float] = None,
  @BeanProperty var bestPeptideMatchId: Option[Int] = None,
  @BeanProperty var peptideId: Option[Int] = None,
  @BeanProperty var unmodifiedPeptideIonId: Option[Int] = None,  
  @BeanProperty var peptideInstanceId: Option[Int] = None,
  @BeanProperty var msQueryIds: Array[Int],
  @BeanProperty var lcmsFeatureId: Long
) extends QuantComponent*/

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideIonProperties (
  @BeanProperty var bestQuantChannelId: Option[Long] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideProfile (
  @BeanProperty var ratios: List[Option[ComputedRatio]]
  //@BeanProperty var mqProtSetProfileIds: Option[Array[Long]] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideProperties (
  @BeanProperty var mqProtSetIds: Option[Array[Long]] = None,
  @BeanProperty var mqPepProfileByGroupSetupNumber: Option[HashMap[String,MasterQuantPeptideProfile]] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantProteinSetProfile (
  //@BeanProperty var id: Long,
  @BeanProperty var abundances: Array[Float],
  @BeanProperty var ratios: List[Option[ComputedRatio]],
  @BeanProperty var mqPeptideIds: Array[Long]
)
 
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantProteinSetProperties (
  @BeanProperty var mqProtSetProfilesByGroupSetupNumber: Option[HashMap[String,Array[MasterQuantProteinSetProfile]]] = None,
  //@BeanProperty var specificSampleId: Option[Long] = None, // defined if the protein has been seen in a single sample
  @BeanProperty var selectedMasterQuantPeptideIds: Option[Array[Long]] = None,
  @BeanProperty var selectedMasterQuantPeptideIonIds: Option[Array[Long]] = None
)