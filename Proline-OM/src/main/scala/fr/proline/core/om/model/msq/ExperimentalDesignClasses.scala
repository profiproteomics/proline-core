package fr.proline.core.om.model.msq

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * @param biologicalSamples
 * @param groupSetups
 * @param masterQuantChannels
 */
case class ExperimentalDesign(
  val biologicalSamples: Array[BiologicalSample],
  val groupSetups: Array[GroupSetup],
  val masterQuantChannels: Array[MasterQuantChannel]
) {
  
  @transient lazy val groupSetupByNumber = Map() ++ groupSetups.map( gs => gs.number -> gs )
  
  def getSampleNumbersByGroupNumber( groupSetupNumber: Int ): Map[Int,Array[Int]] = {
    
    val groupSetup = groupSetupByNumber( groupSetupNumber )
    
    Map() ++ groupSetup.biologicalGroups.map { bg =>
      bg.number ->  bg.sampleNumbers
    }
    
  }
  
}


/**
 * @param biologicalSamples
 * @param biologicalGroups
 * @param masterQuantChannels
 */
case class ExperimentalDesign2(
  val biologicalSamples: Array[BiologicalSample],
  val biologicalGroups: Array[BiologicalGroup],
  val masterQuantChannels: Array[MasterQuantChannel]
) {
   
  
}

/**
 * @param id
 * @param number
 * @param name
 */
case class BiologicalSample(
  var id: Long = 0,
  val number: Int,
  val name: String
)

/**
 * @param id
 * @param number
 * @param name
 * @param ratioDefinitions
 * @param biologicalGroups
 */
case class GroupSetup(
  var id: Long = 0,
  val number: Int,
  val name: String,
  val ratioDefinitions: Array[RatioDefinition],
  val biologicalGroups: Array[BiologicalGroup]
)

/**
 * @param id
 * @param number
 * @param numeratorGroupNumber
 * @param denominatorGroupNumber
 */
case class RatioDefinition(
  var id: Long = 0,
  val number: Int,
  val numeratorGroupNumber: Int,
  val denominatorGroupNumber: Int
)

/**
 * @param id
 * @param number
 * @param name
 * @param sampleNumbers
 */
case class BiologicalGroup(
  var id: Long = 0,
  val number: Int,
  val name: String,
  val sampleNumbers: Array[Int]
)

/**
 * @param id
 * @param number
 * @param sampleNumber
 * @param identResultSummaryId
 * @param lcmsMapId
 * @param runId
 * @param quantLabelId
 */
case class QuantChannel(
  var id: Long = 0,
  val number: Int,
  var name: String,
  val sampleNumber: Int,
  val identResultSummaryId: Long,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsMapId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val runId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val quantLabelId: Option[Long] = None
) {
  if( name == null ) name = ""
}

/**
 * @param id
 * @param number
 * @param name
 * @param lcmsMapSetId
 * @param quantChannels
 */
case class MasterQuantChannel(
  var id: Long = 0,
  val number: Int,
  val name: Option[String] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val quantResultSummaryId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsMapSetId: Option[Long] = None,
  val quantChannels: Array[QuantChannel]
)