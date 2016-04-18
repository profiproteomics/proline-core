package fr.proline.core.om.model.msq

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.profi.util.misc.InMemoryIdGen

// TODO: remove me when SC service use the same ExperimentalDesign than other quantifications
case class SimplifiedExperimentalDesign(
  val biologicalSamples: Array[BiologicalSample],
  val biologicalGroups: Array[BiologicalGroup],
  val masterQuantChannels: Array[MasterQuantChannel],
  /** Used for fractionated samples **/
  val parentMasterQuantChannel: Option[MasterQuantChannel] = None
) {
  def toExperimentalDesign() = {
    ExperimentalDesign(
      biologicalSamples = biologicalSamples,
      groupSetups = Array(
        GroupSetup(
          id = GroupSetup.generateNewId(),
          number = 1,
          name = "",
          biologicalGroups = biologicalGroups,
          ratioDefinitions = Array()
        )
      ),
      masterQuantChannels = masterQuantChannels,
      parentMasterQuantChannel = parentMasterQuantChannel
    )
  }
}

/**
 * @param biologicalSamples
 * @param groupSetups
 * @param masterQuantChannels
 */
case class ExperimentalDesign(
  val biologicalSamples: Array[BiologicalSample],
  val groupSetups: Array[GroupSetup],
  val masterQuantChannels: Array[MasterQuantChannel],
  /** Used for fractionated samples **/
  val parentMasterQuantChannel: Option[MasterQuantChannel] = None
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
 * @param id
 * @param number
 * @param name
 */
case class BiologicalSample(
  var id: Long = 0,
  val number: Int,
  val name: String
)

object GroupSetup extends InMemoryIdGen

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
  val biologicalGroups: Array[BiologicalGroup],
  val ratioDefinitions: Array[RatioDefinition] = Array()
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

object QuantChannel extends InMemoryIdGen

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
  var lcmsMapId: Option[Long] = None,
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
  // TODO: remove from MasterQuantChannelProperties
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val identDatasetId: Option[Long] = None,
  // TODO: remove from MasterQuantChannelProperties
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val identResultSummaryId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val quantResultSummaryId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsMapSetId: Option[Long] = None,
  val quantChannels: Array[QuantChannel]
)