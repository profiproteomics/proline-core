package fr.proline.core.service.uds

import java.util.HashSet
import scala.collection.JavaConversions.collectionAsScalaIterable
import java.util.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.BuildLazyExecutionContext
import fr.proline.core.dal.context._
import fr.proline.core.om.model.msq._
import fr.proline.core.orm.uds.{
  BiologicalGroup => UdsBiologicalGroup,
  BiologicalSample => UdsBiologicalSample,
  Dataset => UdsDataset,
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
import fr.profi.util.sql.getTimeAsSQLTimestamp
import scala.collection.JavaConversions._
import java.util.ArrayList
import javax.persistence.NoResultException
import fr.proline.core.orm.uds.BiologicalSplSplAnalysisMap
import fr.proline.core.orm.uds.BiologicalSplSplAnalysisMapPK

class CreateQuantitation(
  executionContext: IExecutionContext,
  name: String,
  description: String,
  projectId: Long,
  methodId: Long,
  experimentalDesign: ExperimentalDesign
) extends IService with LazyLogging {
  
  require( executionContext.isJPA,"invalid type of executionContext, JPA type is required")

  private var _hasInitiatedExecContext: Boolean = false
  private var _udsQuantitation: UdsDataset = null
  def getUdsQuantitation() = _udsQuantitation
  
  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    name: String,
    description: String,
    projectId: Long,
    methodId: Long,
    experimentalDesign: ExperimentalDesign
  ) {
    this(
      BuildLazyExecutionContext(dsFactory, projectId, true), // Force JPA context
      name,
      description,
      projectId,
      methodId,
      experimentalDesign
    )
    _hasInitiatedExecContext = true
  }

  def runService() = {

    // Retrieve entity manager
    val udsDbCtx = executionContext.getUDSDbConnectionContext()
    val udsEM = executionContext.getUDSDbConnectionContext().getEntityManager()
    
    // Retrieve some vars
    val biologicalSamples = experimentalDesign.biologicalSamples
    val groupSetups = experimentalDesign.groupSetups
    val masterQuantChannels = experimentalDesign.masterQuantChannels

    val udsProject = udsEM.find(classOf[UdsProject], projectId)
    require(udsProject != null, "undefined project with id=" + projectId)

    val udsQuantMethod = udsEM.find(classOf[UdsQuantMethod], methodId)
    require(udsQuantMethod != null, "undefined method with id=" + methodId)

    // Retrieve existing quantitations for this project
    // TODO: add JPA method getQuantitations to the project entity
    // parent_dataset_id is null added:  to avoid to get the quantitations stored in the trash
    var previousQuantNum = 0
    try {
    	val lastQuantNbrObj = udsEM.createNativeQuery("Select  max(number) from data_set where project_id = :pid and type = 'QUANTITATION' and parent_dataset_id is null").setParameter("pid", projectId).getSingleResult()
    	if(lastQuantNbrObj!=null)
    		previousQuantNum = lastQuantNbrObj.asInstanceOf[Int]
    } catch {
    	case e: NoResultException=>  previousQuantNum=0
    }

    udsDbCtx.tryInTransaction {
       
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
      var bioSampleNumFound = false
      if(biologicalSamples.length>0){
        if(biologicalSamples(0).number > 0)
      	  bioSampleNumFound = true
  	  else
  	    logger.warn("Biological Sample Number not specified, use internal counter ! Hopes it correspond to biological group references. ")
      }
        
      for (bioSample <- biologicalSamples) {
        bioSampleNum += 1
  
        val udsBioSample = new UdsBiologicalSample()
        udsBioSample.setNumber(if(bioSampleNumFound) bioSample.number else bioSampleNum)
        udsBioSample.setName(bioSample.name)
        udsBioSample.setDataset(udsQuantitation)
        udsEM.persist(udsBioSample)
        
        // Update persisted bioSample id
        bioSample.id = udsBioSample.getId
  
        udsBioSampleByNum(udsBioSample.getNumber()) = udsBioSample
      }
  
      // Store group setups
      var groupSetupNumber = 0
      var groupSetupNumberFound = false
       if(groupSetups.length>0){
        if(groupSetups(0).number > 0)
      	  groupSetupNumberFound = true
  	  else
  	    logger.warn("Group number not specified, use internal counter !")
      }
      
      val udsAllGroupSetups = new HashSet[UdsGroupSetup]()
      for (groupSetup <- groupSetups) {
        groupSetupNumber += 1
  
        // Save group setup
        val udsGroupSetup = new UdsGroupSetup()
        udsGroupSetup.setNumber(if(groupSetupNumberFound)groupSetup.number else groupSetupNumber)
        udsGroupSetup.setName(groupSetup.name)
        udsGroupSetup.setQuantitationDataset(udsQuantitation)
        udsEM.persist(udsGroupSetup)
        
        // Update persisted groupSetup id
        groupSetup.id = udsGroupSetup.getId
  
        // Create a set of group setups
        val udsGroupSetups = new HashSet[UdsGroupSetup]()
        udsGroupSetups.add(udsGroupSetup)
        udsAllGroupSetups.add(udsGroupSetup)
        // Retrieve biological groups
        val biologicalGroups = groupSetup.biologicalGroups
  
        // Store biological groups
        val udsBioGroupByNum = new HashMap[Int, UdsBiologicalGroup]
        var bioGroupNumber = 0

        var bioGroupNumberFound = false
        if (biologicalGroups.length > 0) {
          if (biologicalGroups(0).number > 0)
            bioGroupNumberFound = true
          else
            logger.warn("Biological group number not specified, use internal counter !")
        }
        
        for (biologicalGroup <- biologicalGroups) {
          bioGroupNumber += 1
  
          // Store biological group
          val udsBioGroup = new UdsBiologicalGroup()
          udsBioGroup.setNumber(if(bioGroupNumberFound) biologicalGroup.number else bioGroupNumber)
          udsBioGroup.setName(biologicalGroup.name)
          udsBioGroup.setGroupSetups(udsGroupSetups)
          udsBioGroup.setQuantitationDataset(udsQuantitation)
          udsEM.persist(udsBioGroup)
          
          // Update persisted biologicalGroup id
          biologicalGroup.id = udsBioGroup.getId
  
          // Map the group id by the group number
          udsBioGroupByNum(udsBioGroup.getNumber()) = udsBioGroup
  
          // Retrieve the list of biological samples belonging to this biological group
          val sampleNumbers = biologicalGroup.sampleNumbers
  
          val udsBioSampleSet = new java.util.ArrayList[UdsBiologicalSample]
          for (sampleNumber <- sampleNumbers) {
  
            if (udsBioSampleByNum.contains(sampleNumber) == false) {
              throw new Exception(s"Can't map the biological group named '${biologicalGroup.name}' with the sample #$sampleNumber")
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
          
          // Update persisted ratioDefinition id
          ratioDefinition.id = udsRatioDef.getId
        }
  
      }
  
      // Store fractions
      var mqcNumber = 0
      var mqcNumberFound = false
      if (masterQuantChannels.length > 0) {
        if (masterQuantChannels(0).number > 0)
          mqcNumberFound = true
        else
          logger.warn("Master quant channel number not specified, use internal counter !")
      }
      
      val udsSampleReplicateByKey = new HashMap[String, UdsSampleAnalysis]
      val udsQuantChannelsList = new ArrayList[UdsQuantChannel]
      val udsMasterQuantChannelsList = new ArrayList[UdsMasterQuantitationChannel]
      
      for (masterQuantChannel <- masterQuantChannels) {
        mqcNumber += 1
        
        // Retrieve quant channels and check they have unique names
        val quantChannels = masterQuantChannel.quantChannels
        val qcNames = quantChannels.withFilter(_.name.nonEmpty).map(_.name)
        require( qcNames.length == qcNames.distinct.length, "Quant channels must be distinctly named !" )
  
        // Save master quant channel
        val udsMqc = new UdsMasterQuantitationChannel()
        udsMqc.setNumber(if(mqcNumberFound) masterQuantChannel.number else mqcNumber)
        udsMqc.setName(masterQuantChannel.name.getOrElse(""))
        udsMqc.setDataset(udsQuantitation)
  
        if (masterQuantChannel.lcmsMapSetId.isDefined) {
          udsMqc.setLcmsMapSetId(masterQuantChannel.lcmsMapSetId.get)
        }
  
        udsEM.persist(udsMqc)
        
        // Update persisted masterQuantChannel id
        masterQuantChannel.id = udsMqc.getId
        
        udsMasterQuantChannelsList.add(udsMqc)
  
        var quantChannelNum = 0
        var quantChannelNumFound = false
        if (quantChannels.length > 0) {
          if (quantChannels(0).number > 0)
            quantChannelNumFound = true
          else
            logger.warn("Quantchannel number not specified, use internal counter !")
        }
        
        // Iterate over each fraction quant channel
        val replicateNumBySampleNum = new HashMap[Int, Int]
        val udsQuantChannelsForMQCList = new ArrayList[UdsQuantChannel]
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
            val bioSpl = new java.util.ArrayList[UdsBiologicalSample]()
            bioSpl.add(udsBioSample)

            val udsReplicateToSample = new BiologicalSplSplAnalysisMap()
            val udsReplicateToSampleKey = new BiologicalSplSplAnalysisMapPK()
            udsReplicateToSampleKey.setBiologicalSampleId(udsBioSample.getId())
            udsReplicateToSampleKey.setSampleAnalysisId(udsReplicate.getId())
            udsReplicateToSample.setId(udsReplicateToSampleKey)            
            udsReplicateToSample.setSampleAnalysisNumber(replicateNum)
            udsReplicateToSample.setSampleAnalysis(udsReplicate)
            udsReplicateToSample.setBiologicalSample(udsBioSample)

            val allBioSplReplicateList  = new ArrayList[BiologicalSplSplAnalysisMap]()
            allBioSplReplicateList.add(udsReplicateToSample)
            val allBioSplReplicateMap = new HashSet[BiologicalSplSplAnalysisMap](allBioSplReplicateList)
            udsReplicate.setBiologicalSplSplAnalysisMap(allBioSplReplicateMap)
            udsReplicate.setDataset(udsQuantitation)            
            
            val bioSplReplicatList = if( udsBioSample.getBiologicalSplSplAnalysisMap() != null) udsBioSample.getBiologicalSplSplAnalysisMap()  else new ArrayList[BiologicalSplSplAnalysisMap]()
            bioSplReplicatList.addAll(allBioSplReplicateList)
            udsBioSample.setBiologicalSplSplAnalysisMap(bioSplReplicatList)
            
            udsEM.persist(udsReplicate)     
            udsEM.persist(udsReplicateToSample)
            udsEM.merge(udsBioSample)
  
            udsSampleReplicateByKey(contextKey) = udsReplicate
          } else {
            
            val existingSplReplicate = udsSampleReplicateByKey.get(contextKey)
            val udsReplicateToSample = new BiologicalSplSplAnalysisMap()
            val udsReplicateToSampleKey = new BiologicalSplSplAnalysisMapPK()
            udsReplicateToSampleKey.setBiologicalSampleId(udsBioSample.getId())
            udsReplicateToSampleKey.setSampleAnalysisId(existingSplReplicate.getId())
            udsReplicateToSample.setId(udsReplicateToSampleKey)
            udsReplicateToSample.setSampleAnalysisNumber(replicateNum)
            udsReplicateToSample.setSampleAnalysis(existingSplReplicate)
            udsReplicateToSample.setBiologicalSample(udsBioSample)
            
            val allBioSplReplicateList  = new ArrayList[BiologicalSplSplAnalysisMap]()
            allBioSplReplicateList.add(udsReplicateToSample)
            val allBioSplReplicateMap = new HashSet[BiologicalSplSplAnalysisMap](allBioSplReplicateList)
            
            val existingSplReplicateList = if( existingSplReplicate.getBiologicalSplSplAnalysisMap() != null) existingSplReplicate.getBiologicalSplSplAnalysisMap()  else new HashSet[BiologicalSplSplAnalysisMap]()
            existingSplReplicateList.addAll(allBioSplReplicateList)
            existingSplReplicate.setBiologicalSplSplAnalysisMap(existingSplReplicateList)
            
            val bioSplReplicatList = if( udsBioSample.getBiologicalSplSplAnalysisMap() != null) udsBioSample.getBiologicalSplSplAnalysisMap()  else new ArrayList[BiologicalSplSplAnalysisMap]()
            bioSplReplicatList.addAll(allBioSplReplicateList)
            udsBioSample.setBiologicalSplSplAnalysisMap(bioSplReplicatList)

            udsEM.merge(existingSplReplicate)
            udsEM.persist(udsReplicateToSample)
            udsEM.merge(udsBioSample)
          }
  
          val udsQuantChannel = new UdsQuantChannel()
          udsQuantChannel.setNumber(if(quantChannelNumFound) quantChannel.number else quantChannelNum)
          udsQuantChannel.setName(quantChannel.name)
          udsQuantChannel.setContextKey(contextKey)
          udsQuantChannel.setIdentResultSummaryId(quantChannel.identResultSummaryId)
          udsQuantChannel.setSampleReplicate(udsSampleReplicateByKey(contextKey))
          udsQuantChannel.setBiologicalSample(udsBioSample)
          udsQuantChannel.setMasterQuantitationChannel(udsMqc)
          udsQuantChannel.setQuantitationDataset(udsQuantitation)
          
          if( quantChannel.runId.isDefined ) {
            val udsRun = udsEM.find(classOf[UdsRun], quantChannel.runId.get)
            logger.debug(s"Set RUN for UdsQuantChannel contextKey $contextKey run => "+udsRun.getId())
            udsQuantChannel.setRun(udsRun)
          }
  
          // TODO: check quant method type ?
          if (quantChannel.lcmsMapId.isDefined) {
            udsQuantChannel.setLcmsMapId(quantChannel.lcmsMapId.get)
            logger.debug("Set lcmsMapId for UdsQuantChannel contextKey "+contextKey)
          } else if (quantChannel.quantLabelId.isDefined) {
            val udsQuantLabel = udsEM.find(classOf[UdsQuantLabel], quantChannel.quantLabelId.get)
            udsQuantChannel.setLabel(udsQuantLabel)
            logger.debug("Set setLabel for UdsQuantChannel LABEL "+udsQuantLabel.getName)
          }
          
          udsEM.persist(udsQuantChannel)
          
          // Update persisted quantChannel id
          quantChannel.id = udsQuantChannel.getId
  
          udsQuantChannelsList.add(udsQuantChannel)
          udsQuantChannelsForMQCList.add(udsQuantChannel)
        }
        
        udsMqc.setQuantitationChannels(udsQuantChannelsForMQCList)
      }
      
      // TODO: sampleReplicates should be an ArrayList instead of an HashSet
      udsQuantitation.setSampleReplicates(new HashSet(udsSampleReplicateByKey.values))
      udsQuantitation.setBiologicalSamples(new ArrayList(udsBioSampleByNum.values))
      udsQuantitation.setGroupSetups(udsAllGroupSetups)
      udsQuantitation.setQuantitationChannels(udsQuantChannelsList)
      udsQuantitation.setMasterQuantitationChannels(udsMasterQuantChannelsList)
        

    } // End of tryInTransaction
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext ) executionContext.closeAll()

    this.logger.info("Exiting CreateQuantitation service with success.")

    true
  }

}
