package fr.proline.core.om.model.msq

import com.codahale.jerkson.JsonSnakeCase

/**
 * @param biologicalSamples
 * @param groupSetups
 * @param masterQuantChannels
 */
@JsonSnakeCase
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
 * @param id
 * @param number
 * @param name
 */
@JsonSnakeCase
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
@JsonSnakeCase
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
@JsonSnakeCase
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
@JsonSnakeCase
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
@JsonSnakeCase
case class QuantChannel(
  var id: Long = 0,
  val number: Int,
  val sampleNumber: Int,
  val identResultSummaryId: Long,
  val lcmsMapId: Option[Long] = None,
  val runId: Option[Long] = None,
  val quantLabelId: Option[Long] = None
)

/**
 * @param id
 * @param number
 * @param name
 * @param lcmsMapSetId
 * @param quantChannels
 */
@JsonSnakeCase
case class MasterQuantChannel(
  var id: Long = 0,
  val number: Int,
  val name: Option[String] = None,
  val lcmsMapSetId: Option[Long] = None,
  val quantChannels: Array[QuantChannel]
)