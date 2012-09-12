package fr.proline.core.om.model.msq

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class RatioDataMapProperty (
  @BeanProperty var ratio: Float,
  @BeanProperty var numerator: Double,
  @BeanProperty var denominator: Double
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantReporterIonProperties (
  //@BeanProperty var quantReporterIons: Array[QuantReporterIonProperties]
)

/*
@JsonSnakeCase
case class QuantPeptideIonProperties (
  @BeanProperty val quantChannelId: Int,
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
  @BeanProperty var lcmsFeatureId: Int
) extends QuantComponent*/

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideIonProperties (
  @BeanProperty var bestQuantChannelId: Option[Int] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideProperties (
  @BeanProperty var masterQuantProteinSetIds: Option[Array[Int]] = None,
  @BeanProperty var quantClusterId: Option[Int] = None,
  @BeanProperty var ratioDataMap: Option[Map[Int,RatioDataMapProperty]] = None
)
 
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantProteinSetProperties (
  @BeanProperty var specificSampleId: Option[Int] = None, // defined if the protein has been seen in a single sample
  @BeanProperty var ratioDataMap: Option[Map[Int,RatioDataMapProperty]] = None,
  @BeanProperty var selectedMasterQuantPeptideIds: Option[Array[Int]] = None,
  @BeanProperty var selectedMasterQuantPeptideIonIds: Option[Array[Int]] = None
)
 