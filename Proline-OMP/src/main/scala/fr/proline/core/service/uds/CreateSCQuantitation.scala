package fr.proline.core.service.uds

import scala.collection.mutable.HashMap
import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.{
  BiologicalGroup => UdsBiologicalGroup,
  BiologicalSample => UdsBiologicalSample,
  Dataset => UdsDataset,
  MasterQuantitationChannel => UdsMasterQuantitationChannel,
  Project => UdsProject,
  QuantitationChannel => UdsQuantChannel,
  QuantitationLabel => UdsQuantLabel,
  QuantitationMethod => UdsQuantMethod,
  SampleAnalysis => UdsSampleAnalysis
}
import fr.proline.core.orm.uds.Dataset.DatasetType
import fr.profi.util.sql.getTimeAsSQLTimestamp
import javax.persistence.NoResultException
import scala.collection.JavaConversions._
import java.util.ArrayList
import java.util.HashSet

class CreateSCQuantitation(
  executionContext: IExecutionContext,
  name: String,
  description: String,
  projectId: Long,
  // methodId: Long, => SC id = 2
  experimentalDesign: ExperimentalDesign
) extends IService {

  private var _udsQuantitation: UdsDataset = null
  def getUdsQuantitation() = _udsQuantitation

  def runService() : Boolean = {

    // Create entity manager
    val udsEM = executionContext.getUDSDbConnectionContext().getEntityManager()

    // Retrieve some vars
    val biologicalSamples = experimentalDesign.biologicalSamples
    val biologicalGroups = experimentalDesign.groupSetups(0).biologicalGroups
    val masterQuantChannels = experimentalDesign.masterQuantChannels

    val udsProject = udsEM.find(classOf[UdsProject], projectId)
    require(udsProject != null, "undefined project with id=" + udsProject)

    val udsQuantMethod = udsEM.find(classOf[UdsQuantMethod], 2l) //TODO FIXME VDS ATTENTION EN DURE !! 
    require(udsQuantMethod != null, "undefined method with id=" + 2)

    // Retrieve existing quantitations for this project
    // parent_dataset_id is null added:  to avoid to get the quantitations stored in the trash
    var previousQuantNum = 0
    try {
    	val lastQuantNbrObj = udsEM.createNativeQuery("Select  max(number) from data_set where project_id = :pid and type = 'QUANTITATION' and parent_dataset_id is null").setParameter("pid", projectId).getSingleResult()
    	if(lastQuantNbrObj!=null)
    		previousQuantNum = lastQuantNbrObj.asInstanceOf[Int]
    } catch {
    	case e: NoResultException=>  previousQuantNum=0
    }
    
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
        
    udsQuantitation.setBiologicalSamples(seqAsJavaList(udsBioSampleByNum.values.toSeq))
    
    // Store biological group
    val udsBioGroupByNum = new HashMap[Int, UdsBiologicalGroup]
	var bioGroupNumber = 0
	for (biologicalGroup <- biologicalGroups) {
		bioGroupNumber += 1

        // Store biological group
        val udsBioGroup = new UdsBiologicalGroup()
        udsBioGroup.setNumber(bioGroupNumber)
        udsBioGroup.setName(biologicalGroup.name)
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
    

    // Store fractions
    var fractionNumber = 0

    val udsSampleReplicateByKey = new HashMap[String, UdsSampleAnalysis]
    val udsQuantChannelsList = new ArrayList[UdsQuantChannel]()
    val udsMasterQuantChannelsList = new ArrayList[UdsMasterQuantitationChannel]()
    for (masterQuantChannel <- masterQuantChannels) {
      fractionNumber += 1
      val currentMQChQuantChannelsList = new ArrayList[UdsQuantChannel]()
      // Save quantitation fraction
      val udsQf = new UdsMasterQuantitationChannel()
      udsQf.setNumber(fractionNumber)
      udsQf.setName(masterQuantChannel.name.getOrElse(""))
      udsQf.setDataset(udsQuantitation)

      if (masterQuantChannel.lcmsMapSetId.isDefined) {
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
        val quantChannelName = quantChannel.name
        val udsBioSample = udsBioSampleByNum(sampleNum)
        
        // Retrieve replicate number and increment it
        val replicateNum = replicateNumBySampleNum.getOrElseUpdate(sampleNum, 0) + 1
        replicateNumBySampleNum(sampleNum) = replicateNum

        // Retrieve analysis replicate if it already exists
        val contextKey = sampleNum + "." + replicateNum

        if (udsSampleReplicateByKey.contains(contextKey) == false) {

          val udsReplicate = new UdsSampleAnalysis()
          udsReplicate.setDataset(udsQuantitation)
          udsBioSample.addSampleAnalysis(udsReplicate)

          udsEM.persist(udsReplicate)
          udsEM.merge(udsBioSample)

          udsSampleReplicateByKey(contextKey) = udsReplicate
        } else {
        
          val existingSplReplicate = udsSampleReplicateByKey(contextKey)
          udsBioSample.addSampleAnalysis(existingSplReplicate)

          udsEM.merge(existingSplReplicate)
          udsEM.merge(udsBioSample)
        }

        val udsQuantChannel = new UdsQuantChannel()
        udsQuantChannel.setNumber(quantChannelNum)
        udsQuantChannel.setName(quantChannelName)
        udsQuantChannel.setContextKey(contextKey)
        udsQuantChannel.setIdentResultSummaryId(quantChannel.identResultSummaryId)
        udsQuantChannel.setSampleReplicate(udsSampleReplicateByKey(contextKey))
        udsQuantChannel.setBiologicalSample(udsBioSample)
        udsQuantChannel.setMasterQuantitationChannel(udsQf)
        udsQuantChannel.setQuantitationDataset(udsQuantitation)
        
        // TODO: check method type
        if (quantChannel.lcmsMapId.isDefined) {
          udsQuantChannel.setLcmsMapId(quantChannel.lcmsMapId.get)
        } else if (quantChannel.quantLabelId.isDefined) {
          val udsQuantLabel = udsEM.find(classOf[UdsQuantLabel], quantChannel.quantLabelId.get)
          udsQuantChannel.setLabel(udsQuantLabel)
        }
        
        udsEM.persist(udsQuantChannel)
        currentMQChQuantChannelsList.add(udsQuantChannel)
        udsQuantChannelsList.add(udsQuantChannel)
      }
      udsQf.setQuantitationChannels(currentMQChQuantChannelsList)
      udsMasterQuantChannelsList.add(udsQf)      
      udsEM.merge(udsQf)//VDS TO ADD in CreateQuanti ?
    }
    
    udsQuantitation.setSampleReplicates(new HashSet(udsSampleReplicateByKey.values))
    udsQuantitation.setQuantitationChannels(udsQuantChannelsList)
    udsQuantitation.setMasterQuantitationChannels(udsMasterQuantChannelsList)
        
    // Commit transaction
    udsEM.getTransaction().commit()

    // Close entity manager
    //udsEM.close()
    this._udsQuantitation = udsQuantitation
    true
  }

}
