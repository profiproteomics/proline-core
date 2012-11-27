package fr.proline.core.service.uds

import collection.mutable.HashMap
import collection.JavaConversions.collectionAsScalaIterable
import com.codahale.jerkson.JsonSnakeCase
import fr.proline.api.service.IService
import fr.proline.core.dal.DatabaseManagement
import fr.proline.util.sql.getTimeAsSQLTimestamp
import fr.proline.core.orm.uds.{ BiologicalGroup => UdsBiologicalGroup,
                                 BiologicalSample => UdsBiologicalSample,
                                 GroupSetup => UdsGroupSetup,
                                 Project => UdsProject,                                 
                                 QuantitationChannel => UdsQuantChannel,
                                 QuantitationLabel => UdsQuantLabel,
                                 QuantitationMethod => UdsQuantMethod,
                                 Quantitation => UdsQuantitation,
                                 QuantitationFraction => UdsQuantitationFraction,
                                 RatioDefinition => UdsRatioDefinition,
                                 SampleAnalysisReplicate => UdsSampleAnalysisReplicate }

class CreateQuantitation(
        dbManager: DatabaseManagement,
        name: String,
        description: String,
        projectId: Int,
        methodId: Int,
        experimentalDesign: ExperimentalDesign
      ) extends IService {
  
  var udsQuantitation: UdsQuantitation = null
  
  def runService() = {
    
    // Open UDSdb connection
    val udsEM = dbManager.udsEMF.createEntityManager()    
    
    // Retrieve some vars
    val biologicalSamples = experimentalDesign.biologicalSamples
    val groupSetups = experimentalDesign.groupSetups
    val fractions = experimentalDesign.fractions 
    
    val udsProject = udsEM.find(classOf[UdsProject], projectId)    
    require( udsProject != null, "undefined project with id=" + udsProject )
    
    val udsQuantMethod = udsEM.find(classOf[UdsQuantMethod], methodId)    
    require( udsQuantMethod != null, "undefined method with id=" + udsQuantMethod )
    
    // Retrieve existing quantitations for this project
    // TODO: add JPA method getQuantitations to the project entity
    val existingQuants = udsEM.createQuery("FROM fr.proline.core.orm.uds.Quantitation WHERE project_id = " + projectId,
                                           classOf[fr.proline.core.orm.uds.Quantitation] ).getResultList().toList
    
    var previousQuantNum = 0
    if ( existingQuants.length != 0 ) { previousQuantNum = existingQuants.last.getNumber() }
    
    // Begin new transaction
    udsEM.getTransaction().begin()
    
    val fractionCount = fractions.length
    
    // Create new quantitation
    this.udsQuantitation = new UdsQuantitation( udsProject )
    udsQuantitation.setNumber( previousQuantNum + 1 )
    udsQuantitation.setName( name )
    udsQuantitation.setDescription( description )
    udsQuantitation.setCreationTimestamp( getTimeAsSQLTimestamp )
    udsQuantitation.setFractionCount( fractionCount )
    udsQuantitation.setMethod( udsQuantMethod )
    udsEM.persist( udsQuantitation )
    
    // Store biological samples
    val udsBioSampleByNum = new HashMap[Int,UdsBiologicalSample]
    var bioSampleNum = 0
    for( bioSample <- biologicalSamples ) {
      bioSampleNum += 1
      
      val udsBioSample = new UdsBiologicalSample()
      udsBioSample.setNumber( bioSampleNum )
      udsBioSample.setName( bioSample.name )
      udsBioSample.setQuantitation( udsQuantitation )
      udsEM.persist( udsBioSample )
      
      udsBioSampleByNum(bioSampleNum) = udsBioSample
    }
    
    // Store group setups
    for( groupSetup <- groupSetups ) {
      
      // Save group setup
      val udsGroupSetup = new UdsGroupSetup()
      udsGroupSetup.setName(groupSetup.name)
      udsGroupSetup.setQuantitation(udsQuantitation)
      udsEM.persist( udsGroupSetup )
      
      // Retrieve biological groups
      val biologicalGroups = groupSetup.biologicalGroups
      
      // Store biological groups
      val udsBioGroupByNum = new HashMap[Int,UdsBiologicalGroup]
      var bioGroupNumber = 0
      for( biologicalGroup <- biologicalGroups ) {
        bioGroupNumber += 1
        
        // Store biological group
        val udsBioGroup = new UdsBiologicalGroup()
        udsBioGroup.setNumber( bioGroupNumber )
        udsBioGroup.setName( biologicalGroup.name )
        udsBioGroup.setGroupSetup( udsGroupSetup )
        udsEM.persist( udsBioGroup )
        
        // Map the group id by the group number
        udsBioGroupByNum(bioGroupNumber) = udsBioGroup    
        
        // Retrieve the list of biological samples belonging to this biological group
        val sampleNumbers = biologicalGroup.sampleNumbers
        
        val udsBioSampleSet = new java.util.HashSet[UdsBiologicalSample]
        for( sampleNumber <- sampleNumbers ) {
          
          if( udsBioSampleByNum.contains(sampleNumber) == false ) {
            throw new Exception("can't map the biological group named '"+biologicalGroup.name+"' with the sample #"+sampleNumber)
          }
          
          udsBioSampleSet.add( udsBioSampleByNum(sampleNumber) )
        }
        
        // Link biological group to corresponding biological samples
        udsBioGroup.setBiologicalSamples( udsBioSampleSet )
        udsEM.persist( udsBioGroup )
      }
      
      // Retrieve ratio definitions
      val ratioDefinitions = groupSetup.ratioDefinitions
      
      // Store ratio definitions
      var ratioDefNumber = 0
      for( ratioDefinition <- ratioDefinitions ) {
        ratioDefNumber += 1
        
        val udsRatioDef = new UdsRatioDefinition()
        udsRatioDef.setNumber(ratioDefNumber )
        udsRatioDef.setNumerator( udsBioGroupByNum( ratioDefinition.numeratorGroupNumber ) )
        udsRatioDef.setDenominator( udsBioGroupByNum( ratioDefinition.denominatorGroupNumber ) )
        udsRatioDef.setGroupSetup( udsGroupSetup )
        udsEM.persist( udsRatioDef )
        
      }
      
    }
    
    // Store fractions
    var fractionNumber = 0
    
    val udsSampleReplicateByKey = new HashMap[String,UdsSampleAnalysisReplicate]
    for( fraction <- fractions ) {
      fractionNumber += 1
      
      // Save quantitation fraction
      val udsQf = new UdsQuantitationFraction()
      udsQf.setNumber( fractionNumber )
      udsQf.setName( fraction.name.getOrElse("") )
      udsQf.setQuantitation( udsQuantitation )
      
      if( fraction.lcmsMapSetId != None ) {
        udsQf.setLcmsMapSetId( fraction.lcmsMapSetId.get )
      }
      
      udsEM.persist( udsQf )
      
      val quantChannels = fraction.quantChannels
      var quantChannelNum = 0
      
      // Iterate over each fraction quant channel
      val replicateNumBySampleNum = new HashMap[Int,Int]
      for( quantChannel <- quantChannels ) {
        
        // Retrieve some vars
        val sampleNum = quantChannel.sampleNumber
        val udsBioSample = udsBioSampleByNum(sampleNum)
        
        // Retrieve replicate number and increment it
        val replicateNum = replicateNumBySampleNum.getOrElseUpdate(sampleNum, 0 ) + 1
        replicateNumBySampleNum(sampleNum) = replicateNum
        
        // Retrieve analysis replicate if it already exists
        val contextKey = sampleNum+"."+replicateNum        
        
        if( udsSampleReplicateByKey.contains(contextKey) == false ) {
          
          //val rdbReplicate = udsAnalysisReplicateByKey(contextKey)
          // Store sample replicate
          val udsReplicate = new UdsSampleAnalysisReplicate()
          udsReplicate.setNumber( replicateNum )
          udsReplicate.setBiologicalSample( udsBioSample )
          udsReplicate.setQuantitation( udsQuantitation )
          udsEM.persist( udsReplicate )
          
          udsSampleReplicateByKey(contextKey) = udsReplicate
        }
        
        val udsQuantChannel = new UdsQuantChannel()
        udsQuantChannel.setNumber( quantChannelNum )
        udsQuantChannel.setName( "" )
        udsQuantChannel.setContextKey( contextKey )
        udsQuantChannel.setIdentResultSummaryId( quantChannel.identResultSummaryId )
        udsQuantChannel.setSampleReplicate( udsSampleReplicateByKey(contextKey) )
        udsQuantChannel.setBiologicalSample( udsBioSample )
        udsQuantChannel.setQuantitationFraction( udsQf )
        udsQuantChannel.setQuantitation( udsQuantitation )
        
        // TODO: check method type
        if( quantChannel.lcmsMapId != None ) {
          udsQuantChannel.setLcmsMapId( quantChannel.lcmsMapId.get )
        }
        else if( quantChannel.quantLabelId != None ) {
          val udsQuantLabel = udsEM.find(classOf[UdsQuantLabel], quantChannel.quantLabelId)    
          udsQuantChannel.setLabel( udsQuantLabel )
        }
        
        udsEM.persist( udsQuantChannel )
        
        quantChannelNum += 1
      }
    }
    
    //rdbQuantitation.quantitationFractions(quantiFrations)
    //rdbQuantitation.analyses (analyses)
    
    // Commit transaction
    udsEM.getTransaction().commit()    
    
    true
  }

}

/** 
* @param biologicalSamples
* @param groupSetups
* @param fractions
**/
@JsonSnakeCase
case class ExperimentalDesign (
  val biologicalSamples: Array[BiologicalSample],
  val groupSetups: Array[GroupSetup],
  val fractions: Array[QuantFraction]
)

/** 
* @param name
**/
@JsonSnakeCase
case class BiologicalSample (
  val name: String
)

/** 
* @param name
* @param ratioDefinitions
* @param biologicalGroups
**/
@JsonSnakeCase
case class GroupSetup (
  val name: String,
  val ratioDefinitions: Array[RatioDefinition],
  val biologicalGroups: Array[BiologicalGroup]
)

/** 
* @param numeratorGroupNumber
* @param denominatorGroupNumber
**/
@JsonSnakeCase
case class RatioDefinition (
  val numeratorGroupNumber: Int,
  val denominatorGroupNumber: Int
)

/** 
* @param name
* @param sampleNumbers
**/
@JsonSnakeCase
case class BiologicalGroup (
  val name: String,
  val sampleNumbers: Array[Int]
)

/** 
* @param sampleNumber
* @param identResultSummaryId
* @param lcmsMapId
* @param quantLabelId
**/
@JsonSnakeCase
case class QuantChannel (
  val sampleNumber: Int,
  val identResultSummaryId: Int,
  val lcmsMapId: Option[Int],
  val quantLabelId: Option[Int]
)

/** 
* @param name
* @param lcmsMapSetId
* @param quantChannels
**/
@JsonSnakeCase
case class QuantFraction (
  val name: Option[String] = None,
  val lcmsMapSetId: Option[Int] = None,
  val quantChannels: Array[QuantChannel]
)