package fr.proline.core.om.model.msq

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.profi.util.misc.InMemoryIdGen

// TODO: remove me when SC service use the same ExperimentalDesign than other quantifications
case class SimplifiedExperimentalDesign(
  val biologicalSamples: Array[BiologicalSample],
  val biologicalGroups: Array[BiologicalGroup],
  val masterQuantChannels: Array[MasterQuantChannel]
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
      masterQuantChannels = masterQuantChannels
    )
  }
}

// TODO: rename MasterQuantChannelExpDesign
case class ExperimentalDesignSetup(
  val expDesign: ExperimentalDesign,
  val groupSetupNumber: Int = 1,
  val masterQCNumber: Int
) {

  val groupSetup = expDesign.groupSetups(groupSetupNumber-1)
  val sampleNumbersByGroupNumber = expDesign.getSampleNumbersByGroupNumber(groupSetupNumber)
  
  var minSamplesCountPerGroup = Int.MaxValue
  for( (groupNumber, sampleNumbers) <- sampleNumbersByGroupNumber if sampleNumbers.length < minSamplesCountPerGroup ) {
    minSamplesCountPerGroup = sampleNumbers.length
  }
  
  val masterQC = expDesign.masterQuantChannels.find( _.number == masterQCNumber ).get
  val quantChannels = masterQC.quantChannels
  val qcIds = quantChannels.map( _.id )
  val qcCount = qcIds.length
  val qcIdxById = qcIds.zip( qcIds.indices ).toMap
  val quantChannelsBySampleNumber = quantChannels.groupBy( _.sampleNumber )
  val qcSampleNumbers = quantChannels.map(_.sampleNumber)
  lazy val runGroupNumbers = {
    val groupNumBySampleNum = (for((gNum,sNums) <- sampleNumbersByGroupNumber; sNum <- sNums) yield sNum -> gNum)
    val groupRunPairs = quantChannels.sortBy(_.number).map(qc => (groupNumBySampleNum(qc.sampleNumber), qc.runId.get)).distinct
    groupRunPairs .map(_._1)
  }
  
  val sampleCount = expDesign.biologicalSamples.length
  val samplesQcCount = expDesign.biologicalSamples.map( s => quantChannelsBySampleNumber(s.number).length )

  var minQCsCountPerSample = Int.MaxValue
  for( (sampleNumber, quantChannels) <- quantChannelsBySampleNumber if quantChannels.length < minQCsCountPerSample ) {
    minQCsCountPerSample = quantChannels.length
  }
  
  val allSampleNumbers = expDesign.biologicalSamples.map(_.number)
    
    // Map quant channel indices by the sample number
  val qcIndicesBySampleNum = ( allSampleNumbers ).map { sampleNum =>
      sampleNum -> quantChannelsBySampleNumber(sampleNum).map( qc => qcIdxById(qc.id) )
    }.toMap
    
}

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

  def getMasterQuantChannelExpDesign(masterQcNumber: Int, groupSetupNumber: Int): ExperimentalDesignSetup = {
    ExperimentalDesignSetup(
      expDesign = this,
      groupSetupNumber = groupSetupNumber,
      masterQCNumber = masterQcNumber
    )
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
  var ratioDefinitions: Array[RatioDefinition] = Array()
) {
  if(ratioDefinitions == null) ratioDefinitions = Array()
}

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
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val identDatasetId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val identResultSummaryId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val quantResultSummaryId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsMapSetId: Option[Long] = None,
  val quantChannels: Array[QuantChannel]
)