package fr.proline.core.service.uds

import java.util.HashSet

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.HashMap

import com.codahale.jerkson.JsonSnakeCase

import fr.proline.api.service.IService
import fr.proline.core.orm.uds.{
  BiologicalGroup => UdsBiologicalGroup,
  BiologicalSample => UdsBiologicalSample,
  Dataset => UdsDataset }
import fr.proline.core.orm.uds.{
  GroupSetup => UdsGroupSetup,
  MasterQuantitationChannel => UdsMasterQuantitationChannel,
  Project => UdsProject,
  QuantitationChannel => UdsQuantChannel,
  QuantitationLabel => UdsQuantLabel,
  QuantitationMethod => UdsQuantMethod,
  RatioDefinition => UdsRatioDefinition,
  Run => UdsRun,
  SampleAnalysis => UdsSampleAnalysis
}
import fr.proline.core.orm.uds.Dataset.DatasetType
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.util.sql.getTimeAsSQLTimestamp

class CreateQuantitation(
  dbManager: IDataStoreConnectorFactory,
  name: String,
  description: String,
  projectId: Int,
  methodId: Int,
  experimentalDesign: ExperimentalDesign) extends IService {

  private var _udsQuantitation: UdsDataset = null
  def getUdsQuantitation() = _udsQuantitation

  def runService() = {

    // Create entity manager
    val udsEM = dbManager.getUdsDbConnector.getEntityManagerFactory.createEntityManager()

    // Retrieve some vars
    val biologicalSamples = experimentalDesign.biologicalSamples
    val groupSetups = experimentalDesign.groupSetups
    val masterQuantChannels = experimentalDesign.masterQuantChannels

    val udsProject = udsEM.find(classOf[UdsProject], projectId)
    require(udsProject != null, "undefined project with id=" + udsProject)

    val udsQuantMethod = udsEM.find(classOf[UdsQuantMethod], methodId)
    require(udsQuantMethod != null, "undefined method with id=" + udsQuantMethod)

    // Retrieve existing quantitations for this project
    // TODO: add JPA method getQuantitations to the project entity
    val existingQuants = udsEM.createQuery("FROM fr.proline.core.orm.uds.Dataset WHERE project_id = " + projectId,
      classOf[fr.proline.core.orm.uds.Dataset]).getResultList().toList

    var previousQuantNum = 0
    if (existingQuants.length != 0) { previousQuantNum = existingQuants.last.getNumber() }

    // Begin new transaction
    udsEM.getTransaction().begin()

    val mqcCount = masterQuantChannels.length

    // Create new quantitation
    val udsQuantitation = new UdsDataset(udsProject)
    udsQuantitation.setNumber(previousQuantNum + 1)
    udsQuantitation.setName(name)
    udsQuantitation.setDescription(description)
    udsQuantitation.setType(DatasetType.QUANTITATION)
    udsQuantitation.setCreationTimestamp(getTimeAsSQLTimestamp)
    udsQuantitation.setChildrenCount(0)
    udsQuantitation.setMethod(udsQuantMethod)
    udsEM.persist(udsQuantitation)

    this._udsQuantitation = udsQuantitation

    // Store biological samples
    val udsBioSampleByNum = new HashMap[Int, UdsBiologicalSample]
    var bioSampleNum = 0
    for (bioSample <- biologicalSamples) {
      bioSampleNum += 1

      val udsBioSample = new UdsBiologicalSample()
      udsBioSample.setNumber(bioSampleNum)
      udsBioSample.setName(bioSample.name)
      udsBioSample.setDataset(udsQuantitation)
      udsEM.persist(udsBioSample)

      udsBioSampleByNum(bioSampleNum) = udsBioSample
    }

    // Store group setups
    for (groupSetup <- groupSetups) {

      // Save group setup
      val udsGroupSetup = new UdsGroupSetup()
      udsGroupSetup.setName(groupSetup.name)
      udsGroupSetup.setQuantitationDataset(udsQuantitation)
      udsEM.persist(udsGroupSetup)

      // Create a set of group setups
      val udsGroupSetups = new HashSet[UdsGroupSetup]()
      udsGroupSetups.add(udsGroupSetup)

      // Retrieve biological groups
      val biologicalGroups = groupSetup.biologicalGroups

      // Store biological groups
      val udsBioGroupByNum = new HashMap[Int, UdsBiologicalGroup]
      var bioGroupNumber = 0
      for (biologicalGroup <- biologicalGroups) {
        bioGroupNumber += 1

        // Store biological group
        val udsBioGroup = new UdsBiologicalGroup()
        udsBioGroup.setNumber(bioGroupNumber)
        udsBioGroup.setName(biologicalGroup.name)
        udsBioGroup.setGroupSetups(udsGroupSetups)
        udsBioGroup.setQuantitationDataset(udsQuantitation)
        udsEM.persist(udsBioGroup)

        // Map the group id by the group number
        udsBioGroupByNum(bioGroupNumber) = udsBioGroup

        // Retrieve the list of biological samples belonging to this biological group
        val sampleNumbers = biologicalGroup.sampleNumbers

        val udsBioSampleSet = new java.util.ArrayList[UdsBiologicalSample]
        for (sampleNumber <- sampleNumbers) {

          if (udsBioSampleByNum.contains(sampleNumber) == false) {
            throw new Exception("can't map the biological group named '" + biologicalGroup.name + "' with the sample #" + sampleNumber)
          }

          udsBioSampleSet.add(udsBioSampleByNum(sampleNumber))
        }

        // Link biological group to corresponding biological samples
        udsBioGroup.setBiologicalSamples(udsBioSampleSet)
        udsEM.persist(udsBioGroup)
      }

      // Retrieve ratio definitions
      val ratioDefinitions = groupSetup.ratioDefinitions

      // Store ratio definitions
      var ratioDefNumber = 0
      for (ratioDefinition <- ratioDefinitions) {
        ratioDefNumber += 1

        val udsRatioDef = new UdsRatioDefinition()
        udsRatioDef.setNumber(ratioDefNumber)
        udsRatioDef.setNumerator(udsBioGroupByNum(ratioDefinition.numeratorGroupNumber))
        udsRatioDef.setDenominator(udsBioGroupByNum(ratioDefinition.denominatorGroupNumber))
        udsRatioDef.setGroupSetup(udsGroupSetup)
        udsEM.persist(udsRatioDef)

      }

    }

    // Store fractions
    var fractionNumber = 0

    val udsSampleReplicateByKey = new HashMap[String, UdsSampleAnalysis]
    for (masterQuantChannel <- masterQuantChannels) {
      fractionNumber += 1

      // Save quantitation fraction
      val udsQf = new UdsMasterQuantitationChannel()
      udsQf.setNumber(fractionNumber)
      udsQf.setName(masterQuantChannel.name.getOrElse(""))
      udsQf.setDataset(udsQuantitation)

      if (masterQuantChannel.lcmsMapSetId != None) {
        udsQf.setLcmsMapSetId(masterQuantChannel.lcmsMapSetId.get)
      }

      udsEM.persist(udsQf)

      val quantChannels = masterQuantChannel.quantChannels
      var quantChannelNum = 0

      // Iterate over each fraction quant channel
      val replicateNumBySampleNum = new HashMap[Int, Int]
      for (quantChannel <- quantChannels) {
        quantChannelNum += 1
        
        // Retrieve some vars
        val sampleNum = quantChannel.sampleNumber
        val udsBioSample = udsBioSampleByNum(sampleNum)
        
        // Retrieve replicate number and increment it
        val replicateNum = replicateNumBySampleNum.getOrElseUpdate(sampleNum, 0) + 1
        replicateNumBySampleNum(sampleNum) = replicateNum

        // Retrieve analysis replicate if it already exists
        val contextKey = sampleNum + "." + replicateNum

        if (udsSampleReplicateByKey.contains(contextKey) == false) {

          //val rdbReplicate = udsAnalysisReplicateByKey(contextKey)
          // Store sample replicate
          val udsReplicate = new UdsSampleAnalysis()
          udsReplicate.setNumber(replicateNum)
          val bioSpl = new java.util.ArrayList[UdsBiologicalSample]()
          bioSpl.add(udsBioSample)
          udsReplicate.setBiologicalSample(bioSpl)
          udsReplicate.setDataset(udsQuantitation)
          udsEM.persist(udsReplicate)

          udsSampleReplicateByKey(contextKey) = udsReplicate
        }

        val udsQuantChannel = new UdsQuantChannel()
        udsQuantChannel.setNumber(quantChannelNum)
        udsQuantChannel.setName("")
        udsQuantChannel.setContextKey(contextKey)
        udsQuantChannel.setIdentResultSummaryId(quantChannel.identResultSummaryId)
        udsQuantChannel.setSampleReplicate(udsSampleReplicateByKey(contextKey))
        udsQuantChannel.setBiologicalSample(udsBioSample)
        udsQuantChannel.setMasterQuantitationChannel(udsQf)
        udsQuantChannel.setQuantitationDataset(udsQuantitation)
        
        if( quantChannel.runId.isDefined ) {
          val udsRun = udsEM.find(classOf[UdsRun], quantChannel.runId.get)
          udsQuantChannel.setRun(udsRun)
        }

        // TODO: check method type
        if (quantChannel.lcmsMapId != None) {
          udsQuantChannel.setLcmsMapId(quantChannel.lcmsMapId.get)
        } else if (quantChannel.quantLabelId != None) {
          val udsQuantLabel = udsEM.find(classOf[UdsQuantLabel], quantChannel.quantLabelId)
          udsQuantChannel.setLabel(udsQuantLabel)
        }

        udsEM.persist(udsQuantChannel)

      }
    }

    //rdbQuantitation.quantitationFractions(quantiFrations)
    //rdbQuantitation.analyses (analyses)

    // Commit transaction
    udsEM.getTransaction().commit()

    // Close entity manager
    udsEM.close()

    true
  }

}

/**
 * @param biologicalSamples
 * @param groupSetups
 * @param masterQuantChannels
 */
@JsonSnakeCase
case class ExperimentalDesign(
  val biologicalSamples: Array[BiologicalSample],
  val groupSetups: Array[GroupSetup],
  val masterQuantChannels: Array[MasterQuantChannel])

/**
 * @param name
 */
@JsonSnakeCase
case class BiologicalSample(
  val name: String)

/**
 * @param name
 * @param ratioDefinitions
 * @param biologicalGroups
 */
@JsonSnakeCase
case class GroupSetup(
  val name: String,
  val ratioDefinitions: Array[RatioDefinition],
  val biologicalGroups: Array[BiologicalGroup])

/**
 * @param numeratorGroupNumber
 * @param denominatorGroupNumber
 */
@JsonSnakeCase
case class RatioDefinition(
  val numeratorGroupNumber: Int,
  val denominatorGroupNumber: Int)

/**
 * @param name
 * @param sampleNumbers
 */
@JsonSnakeCase
case class BiologicalGroup(
  val name: String,
  val sampleNumbers: Array[Int])

/**
 * @param sampleNumber
 * @param identResultSummaryId
 * @param lcmsMapId
 * @param quantLabelId
 */
@JsonSnakeCase
case class QuantChannel(
  val sampleNumber: Int,
  val identResultSummaryId: Int,
  val lcmsMapId: Option[Int] = None,
  val runId: Option[Int] = None,
  val quantLabelId: Option[Int] = None
)

/**
 * @param name
 * @param lcmsMapSetId
 * @param quantChannels
 */
@JsonSnakeCase
case class MasterQuantChannel(
  val name: Option[String] = None,
  val lcmsMapSetId: Option[Int] = None,
  val quantChannels: Array[QuantChannel])