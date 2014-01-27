package fr.proline.core.service.msq.quantify

import fr.proline.core.utils.ResidueUtils._
import com.typesafe.scalalogging.slf4j.Logging
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.Ms2CountQuantifier
import fr.proline.core.algo.msq.SpectralCountConfig
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.uds.MasterQuantitationChannel
import javax.persistence.EntityManager
import fr.proline.core.om.model.msi.ProteinSet
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import collection.JavaConversions.iterableAsScalaIterable
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.util.MathUtils
import scala.collection.mutable.MapBuilder
import scala.collection.mutable.HashSet

import fr.proline.core.orm.msi.{
  ObjectTree => MsiObjectTree,
  MasterQuantPeptideIon => MsiMasterQuantPepIon,
  MasterQuantComponent => MsiMasterQuantComponent,
  PeptideInstance => MsiPeptideInstance,
  PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap,
  PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK,
  PeptideMatch => MsiPeptideMatch,
  PeptideMatchRelation => MsiPeptideMatchRelation,
  PeptideSet => MsiPeptideSet,
  PeptideSetPeptideInstanceItem => MsiPeptideSetItem,
  PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK,
  PeptideSetProteinMatchMap => MsiPepSetProtMatchMap,
  PeptideSetProteinMatchMapPK => MsiPepSetProtMatchMapPK,
  ProteinSetProteinMatchItem => MsiProtSetProtMatchItem,
  ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK,
  ProteinMatch => MsiProteinMatch,
  ProteinSet => MsiProteinSet,
  ResultSet => MsiResultSet,
  ResultSummary => MsiResultSummary,
  Scoring => MsiScoring,
  SequenceMatch => MsiSequenceMatch
}


/**
 * @author VDS
 * Merge SpectralCountQuantifier and WeightedSCCalculator
 *
 */
class WeightedSpectralCountQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val scConfig: SpectralCountConfig
) extends AbstractMasterQuantChannelQuantifier with Logging {

	protected val msiMasterPepInstByMergedPepInstId = new HashMap[Long, MsiPeptideInstance]
   protected val msiMasterProtSetByMergedProtSetId = new HashMap[Long, MsiProteinSet]

  /**
   *  Spectral Count Result
   *  {RSM ID -> { ProteinMatchAccession -> (Basic SC; Specific SC, Weighted SC)} }
   */

  
  private var _resultAsJSON : String = null
   def getResultAsJSON() =  {_resultAsJSON}
  
  //  private var _wscByProtMatchAccessionByRSM : scala.collection.mutable.Map[Long, Map[String, SpectralCountsStruct]] = scala.collection.mutable.Map.empty[Long, Map[String, SpectralCountsStruct]]

  def quantifyMasterChannel(): Unit = {

    logger.info("Starting spectral count quantifier")
    
    // Begin new ORM transaction
    msiDbCtx.beginTransaction()
    udsDbCtx.beginTransaction()

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet(msiIdentResultSets)
    val quantRsId = msiQuantResultSet.getId()

    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    
    udsEm.persist(udsMasterQuantChannel)
    udsEm.flush()
    
    
    // Store master quant result summary
    this.cloneAndStoreMasterQuantRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet)

    // -- Create ProteinPepsWeightStruct from reference RSM
    val proteinSetWeightStructsById = createProteinPepsWeightStructs(true)

    logger.debug("Found : " +proteinSetWeightStructsById.size+" Prot to calculate SC for ")
    // Compute master quant peptides
    // !! Warning : Returned values are linked to Identification RSM (OM Objects) and not to Quantitation RSM (ORM Objects)
    val (mqPeptides, mqProtSets) = computeMasterQuantValues(
      udsMasterQuantChannel,
      this.mergedResultSummary,
      this.identResultSummaries,
      proteinSetWeightStructsById
    )
   
    this.logger.info("storing "+mqPeptides.size+" master peptide quant data...")

    // Iterate over master quant peptides to store corresponding spectral counts
    for (mqPeptide <- mqPeptides) {    
      this.storeMasterQuantPeptide(mqPeptide, msiQuantRSM, Some(msiMasterPepInstByMergedPepInstId(mqPeptide.peptideInstance.get.id)))
    }

    this.logger.info("storing "+mqProtSets.size+" master proteins set quant data...")

    // Iterate over master quant protein sets to store corresponding spectral counts
    for (mqProtSet <- mqProtSets) {      
      
      val msiProtSet = msiMasterProtSetByMergedProtSetId.getOrElse(mqProtSet.proteinSet.id,null)
      if(msiProtSet!=null)
    	  this.storeMasterQuantProteinSet(mqProtSet, msiMasterProtSetByMergedProtSetId(mqProtSet.proteinSet.id), msiQuantRSM)
      else 
        logger.warn(" !! No Master Quant data found for protein set id "+mqProtSet.proteinSet.id+" !! ")
    }

    _resultAsJSON = createJSonOutputResult(msiQuantRSM, mqProtSets,proteinSetWeightStructsById)
    
    // Commit ORM transaction
    msiDbCtx.commitTransaction()
    udsDbCtx.commitTransaction()

  }
  
 override protected def storeMasterQuantPeptide(
    mqPep: MasterQuantPeptide,
    msiRSM: MsiResultSummary,
    msiMasterPepInstAsOpt: Option[MsiPeptideInstance]) = {

    val msiMasterPepInst = msiMasterPepInstAsOpt.get
    val msiMQCObjectTree = this.buildMasterQuantPeptideObjectTree(mqPep)
    this.msiEm.persist(msiMQCObjectTree)

    // Store master quant component for this master quant peptide
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqPep.selectionLevel)
    if (mqPep.properties.isDefined) msiMQC.setSerializedProperties(ProfiJson.serialize(mqPep.properties))
    msiMQC.setObjectTreeId(msiMQCObjectTree.getId)
    msiMQC.setSchemaName(msiMQCObjectTree.getSchema.getName)
    msiMQC.setResultSummary(msiRSM)
    
    this.msiEm.persist(msiMQC)
    
    // Update master quant peptide id
    mqPep.id = msiMQC.getId

    // Link master peptide instance to the corresponding master quant component
    msiMasterPepInst.setMasterQuantComponentId(msiMQC.getId)
    this.msiEm.persist(msiMasterPepInst)
    	
    for (mqPepIon <- mqPep.masterQuantPeptideIons) {
      this.storeMasterQuantPeptideIon(mqPepIon, mqPep, msiMasterPepInst, msiRSM)
    }

  }

  protected def storeMasterQuantPeptideIon(
    mqPepIon: MasterQuantPeptideIon,
    mqPep: MasterQuantPeptide,
    msiMasterPepInst: MsiPeptideInstance,
    msiRSM: MsiResultSummary) = {

    val msiMQCObjectTree = this.buildMasterQuantPeptideIonObjectTree(mqPepIon)
    this.msiEm.persist(msiMQCObjectTree)

    // Store master quant component
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqPepIon.selectionLevel)
    // TODO: decide what to store in the master quant component properties
    //if( mqPepIon.properties.isDefined ) msiMQC.setSerializedProperties( ProfiJson.serialize(mqPepIon.properties) )
    msiMQC.setObjectTreeId(msiMQCObjectTree.getId)
    msiMQC.setSchemaName(msiMQCObjectTree.getSchema.getName)
    msiMQC.setResultSummary(msiRSM)

    this.msiEm.persist(msiMQC)

    // Store master quant peptide ion
    val msiMQPepIon = new MsiMasterQuantPepIon()
    msiMQPepIon.setCharge(mqPepIon.charge)
    msiMQPepIon.setMoz(mqPepIon.unlabeledMoz)
    msiMQPepIon.setElutionTime(mqPepIon.elutionTime)
    msiMQPepIon.setPeptideMatchCount(mqPepIon.peptideMatchesCount)
    msiMQPepIon.setMasterQuantComponent(msiMQC)
    msiMQPepIon.setMasterQuantPeptideId(mqPep.id)
    msiMQPepIon.setResultSummary(msiRSM)
    msiMQPepIon.setPeptideInstanceId(msiMasterPepInst.getId())
    msiMQPepIon.setPeptideId(msiMasterPepInst.getPeptideId())

    if (mqPepIon.properties.isDefined) msiMQPepIon.setSerializedProperties(ProfiJson.serialize(mqPepIon.properties))
    
    if (mqPepIon.lcmsMasterFeatureId.isDefined) msiMQPepIon.setLcmsMasterFeatureId(mqPepIon.lcmsMasterFeatureId.get)
    if (mqPepIon.bestPeptideMatchId.isDefined) msiMQPepIon.setBestPeptideMatchId(mqPepIon.bestPeptideMatchId.get)
    if (mqPepIon.unmodifiedPeptideIonId.isDefined) msiMQPepIon.setUnmodifiedPeptideIonId(mqPepIon.unmodifiedPeptideIonId.get)

    this.msiEm.persist(msiMQPepIon)
    
    // Update master quant peptide ion id
    mqPepIon.id = msiMQPepIon.getId
  }
  
  private def createJSonOutputResult( msiQuantRSM : MsiResultSummary, mqProtSetsVal : Array[MasterQuantProteinSet], proteinSetWeightStructsById:Map[Long, ProteinSetPeptidesDescription] ):String = {
    
     val jsonBuilder : StringBuilder = new StringBuilder(" \"{")
     jsonBuilder.append(SpectralCountsJSONProperties.rootPropName).append(":{[")
     
     val rsmIdByQChIdbuilder = Map.newBuilder[Long,Long]  
     
     val qChannels = udsMasterQuantChannel.getQuantitationChannels()
     
     var firstQChOcc = true
     qChannels.foreach(nextQCh => {
    	 val rsmId = nextQCh.getIdentResultSummaryId()
		 if(!firstQChOcc){  jsonBuilder.append(",") } else { firstQChOcc = false }
    	 jsonBuilder.append("{").append(SpectralCountsJSONProperties.rsmIDPropName).append(":").append(rsmId).append(",") //save current RSM Id
    	 
    	 // -- Save prots SC for current RSM          	
    	 var firstPACOcc = true
    	 jsonBuilder.append(SpectralCountsJSONProperties.protSCsListPropName).append(":[")   

    	 mqProtSetsVal.foreach(mqps=>{
    	  //Go through All ProteinSets and extract only data for current QuantChanel    		 
    		 val protAC = proteinSetWeightStructsById.get(mqps.proteinSet.id).get.typicalPMAcc    		 
			 val protQuant = mqps.quantProteinSetMap.get(nextQCh.getId())

			 if(protQuant.isDefined){
				 if(!firstPACOcc){  jsonBuilder.append(",") } else { firstPACOcc = false }
    					 
				 jsonBuilder.append("{").append(SpectralCountsJSONProperties.protACPropName).append("=").append(protAC).append(",")
				 jsonBuilder.append(SpectralCountsJSONProperties.bscPropName).append("=").append(protQuant.get.peptideMatchesCount).append(",")
				 jsonBuilder.append(SpectralCountsJSONProperties.sscPropName).append("=").append(protQuant.get.rawAbundance).append(",")
				 jsonBuilder.append(SpectralCountsJSONProperties.wscPropName).append("=").append(protQuant.get.abundance).append("}")
			 }
    	 })
    	 
    	 jsonBuilder.append("]") //End protAC list for current RSM
    	 jsonBuilder.append("}") //End current RSM properties
     })
     
     
       
     jsonBuilder.append("]}}\"") //End SpectralCountResult array properties
     jsonBuilder.result    
        
  }
  
  private def createProteinPepsWeightStructs(referenceForPeptides: Boolean): Map[Long, ProteinSetPeptidesDescription] = {

    //ProteinPepsWeightStruct for each RSM ProteinSet referenced by ProteinSet id  
    val proteinPepsWeightStructsByProtSetId = Map.newBuilder[Long, ProteinSetPeptidesDescription]

    // Map each peptide to the list of identified ProteinSet id 
    val protSetIdByPepId = new HashMap[Long, ArrayBuffer[Long]]()

    //****  For each ProteinSet, initialize a ProteinPepsWeightStruct and create Maps
    mergedResultSummary.proteinSets.foreach(protSet => {

      //Map of weight by peptide Id
      val weightByPepId = new HashMap[Long, Float]()

      //-- Get Typical Protein Match Accession 
      var pmAccession: String = null
      if (protSet.getTypicalProteinMatch != null && protSet.getTypicalProteinMatch.isDefined) {
        pmAccession = protSet.getTypicalProteinMatch.get.accession
      } else {
        val typicalPM = msiEm.find(classOf[fr.proline.core.orm.msi.ProteinMatch], protSet.getTypicalProteinMatchId)
        pmAccession = typicalPM.getAccession()

      }

      //-- Get peptide specific count and create Map : peptide => List ProtSet.Id identified by peptide
      var nbrPepSpecif: Int = 0
      if (referenceForPeptides) {
        protSet.peptideSet.getPeptideInstances.foreach(pepI => {
          val proSetIds = protSetIdByPepId.getOrElseUpdate(pepI.peptideId, new ArrayBuffer[Long])
          proSetIds += protSet.id
          if (pepI.validatedProteinSetsCount == 1) {
            nbrPepSpecif += 1
          }
          weightByPepId += pepI.peptideId -> 0.0f
        })
      }

      proteinPepsWeightStructsByProtSetId += protSet.id -> new ProteinSetPeptidesDescription(proteinSet = protSet, typicalPMAcc = pmAccession, nbrPepSpecific = nbrPepSpecif, weightByPeptideId = weightByPepId)

    }) // End ProteinPepsWeightStruct initialization 

    val resultStruct: Map[Long, ProteinSetPeptidesDescription] = proteinPepsWeightStructsByProtSetId.result

    //**** Compute Peptides Weight if referenceRSM also used for peptide
    if (referenceForPeptides) computePeptideWeight(resultStruct, protSetIdByPepId)

    resultStruct
  }

  /**
   * Compute Peptide's Weight for each identified ProteinSet
   *
   *  If peptide is a specific  ProteinSet, the corresponding weight will be 1
   *  Else if peptide is shared between multiple ProteinSets the weight = # specific pep of ProtSet / Sum ( #specific pep of all ProtSet identified by this pep)
   *
   *  @param  proteinWeightStructByProtSetId Map ProteinPepsWeightStruct by ProteinSetId in peptide reference RSM. ProteinPepsWeightStruct should be updated
   *  @param  protSetIdByPep For each Peptide (id) references list of ProteinSet (Id) identified by this peptide
   */
  private def computePeptideWeight(proteinWeightStructByProtSetId: Map[Long, ProteinSetPeptidesDescription], protSetIdByPep: HashMap[Long, ArrayBuffer[Long]]): Unit = {

    proteinWeightStructByProtSetId.foreach(entry => {
      val currentProteinWeightStruct = entry._2
      //Calculate  weight for each peptide identifying this ProteinSet
      currentProteinWeightStruct.weightByPeptideId.foreach(weightMapEntry => {
        val pepId = weightMapEntry._1
        if (protSetIdByPep.get(pepId).get.length == 1) { // specific peptide, weight =1
          currentProteinWeightStruct.weightByPeptideId(pepId) = 1.0f
        } else {
          //Not specific peptide,  weight = nbr pepSpecific of current ProtSet / Sum ( nbr pepSpecific of all ProtSet identified by this pep)
          var sumNbrSpecificPeptides = 0
          protSetIdByPep.get(pepId).get.foreach(protSetId => {
            sumNbrSpecificPeptides += proteinWeightStructByProtSetId.get(protSetId).get.nbrPepSpecific
          })

          if (sumNbrSpecificPeptides > 0)
            currentProteinWeightStruct.weightByPeptideId.put(pepId, (currentProteinWeightStruct.nbrPepSpecific.toFloat / sumNbrSpecificPeptides.toFloat))
          else
            currentProteinWeightStruct.weightByPeptideId.put(pepId, 0)
        }
      }) //End go through ProteinSet Peptides
    }) // End go through  ProteinSet (ProteinPepsWeightStruct)      
  }

  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingPeptidesSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.spectral_counting_peptides")
  }

  protected def buildMasterQuantPeptideObjectTree(mqPep: MasterQuantPeptide): MsiObjectTree = {

    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = this.quantChannelIds.map { quantPeptideMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQPepObjectTree = new MsiObjectTree()
    msiMQPepObjectTree.setSchema(spectralCountingPeptidesSchema)
    msiMQPepObjectTree.setClobData(ProfiJson.serialize(quantPeptides))

    msiMQPepObjectTree
  }

  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingQuantPepIonsSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.spectral_counting_quant_peptide_ions")
  }

  protected def buildMasterQuantPeptideIonObjectTree(mqPepIon: MasterQuantPeptideIon): MsiObjectTree = {

    val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
    val quantPeptideIons = this.quantChannelIds.map { quantPeptideIonMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQPepIonObjectTree = new MsiObjectTree()
    msiMQPepIonObjectTree.setSchema(spectralCountingQuantPepIonsSchema)
    msiMQPepIonObjectTree.setClobData(ProfiJson.serialize(quantPeptideIons))

    msiMQPepIonObjectTree
  }

  protected def getMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    if (scConfig.parentRSMId.isEmpty)
      createMergedResultSummary(msiDbCtx)
    else {
      this.logger.debug("Read Merged RSM with ID " + scConfig.parentRSMId.get)

      // Instantiate a RSM provider
      val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx = psDbCtx, udsDbCtx = null)
      rsmProvider.getResultSummary(scConfig.parentRSMId.get, true).get
    }

  }

  /**
   * Compute BSC and SSC for each peptide instance of each resultsummary and store information in
   * QuantPeptideIon and MasterQuantPeptide
   * Compute WSC for each ref RSM typical protein in each rsm and store information in
   * QuantProteinSet and MasterQuantProteinSet
   *
   * @param mergedRSM : RSM de départ et pas celui de quanti ? TODO
   */
  def computeMasterQuantValues(udsMasterQuantChannel: MasterQuantitationChannel,
                                 mergedRSM: ResultSummary,
                                 resultSummaries: Seq[ResultSummary],
                                 protSetWeightStructsByProtSetId: Map[Long, ProteinSetPeptidesDescription]): (Array[MasterQuantPeptide], Array[MasterQuantProteinSet]) = {

    val rsIdByRsmId = resultSummaries.map(rsm => { rsm.id -> rsm.getResultSetId  }).toMap

    // Map quant channel id by result set id    
    val qcIdByRsId = udsMasterQuantChannel.getQuantitationChannels().map {
      qc => {
        rsIdByRsmId(qc.getIdentResultSummaryId()) -> qc.getId
        
      }
    } toMap
    val refPepInstanceByPepId = mergedRSM.peptideInstances.map(pi => pi.peptideId -> pi).toMap

    //     val qPepIonsMapsByrsmId = new HashMap[Long,Map[Long, Array[QuantPeptideIon]]]
    val forMasterQPepByPepId = new HashMap[Long, scala.collection.mutable.Map[Long, QuantPeptide]]
    val forMasterQProtSetByProtSet = new HashMap[ProteinSet, scala.collection.mutable.Map[Long, QuantProteinSet]]

    // returnesValues
    val mqPeptides = new ArrayBuffer[MasterQuantPeptide]
    val mqProtSets = new ArrayBuffer[MasterQuantProteinSet]

    // Compute SpectralCount for each RSM
    resultSummaries.foreach(rsm => {

      val qcId = qcIdByRsId(rsm.getResultSetId)

       val quantPepByPepID: scala.collection.mutable.Map[Long, QuantPeptide] = scala.collection.mutable.Map[Long, QuantPeptide]()


      //--- Update RSM SpectralCount if necessary
      // TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information ! Should be wrong see issue #7984
      if (rsm.peptideInstances(0).totalLeavesMatchCount < 0) {
        PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsm, executionContext)
        rsm.peptideInstances.foreach(pepI =>{
        	val ormPepInst  = this.msiEm.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepI.id)
        	ormPepInst.setTotalLeavesMatchCount(pepI.totalLeavesMatchCount)
        })

      }

      //--- Get RSM Peptide Match/Protein Match information 	     
      // map   list of ProtMatch accession by PeptideSet
      val protMatchesAccListByPepSet: Map[PeptideSet, Seq[String]] = createProtMatchesAccByPeptideSet(rsm)

      //--- Calculate SCs for each Ref RSM ProtSet
      protSetWeightStructsByProtSetId.foreach(entry => {

	    val currentProteinSetWeightStruct = entry._2

        //Get PeptideSet containing protein in current RSM if exist
        var peptideSetForPM: PeptideSet = null
        val pepSetIt = protMatchesAccListByPepSet.iterator
        while (pepSetIt.hasNext && peptideSetForPM == null) {
          val nextEntry: (PeptideSet, Seq[String]) = pepSetIt.next
          if (nextEntry._2.contains(currentProteinSetWeightStruct.typicalPMAcc))
            peptideSetForPM = nextEntry._1
        }

        // ProteinMatch Spectral Count
        var protWSC: Float = 0.0f
        var protSSC: Float = 0.0f
        var protBSC: Int = 0

        if (peptideSetForPM != null) { //  Ref RSM current typical found

          //Go through peptides instances,  compute SC and create QuantPeptide
          peptideSetForPM.getPeptideInstances.foreach(pepInst => {

            val weight = currentProteinSetWeightStruct.weightByPeptideId.get(pepInst.peptideId).get
            val isPepSpecific = Math.abs(weight - 1.0f) < MathUtils.EPSILON_FLOAT
            val qPep = if (quantPepByPepID.contains(pepInst.peptideId)) {
              quantPepByPepID(pepInst.peptideId)
            } else {
              //FIXME VDS : OK if we use weight on specificity... Maybe this information (specific or not) should be saved in  ProteinPepsWeightStruct
              val ssc = if (isPepSpecific) { pepInst.totalLeavesMatchCount } else { 0 }
              val qp = new QuantPeptide(
                rawAbundance = ssc,
                abundance = ssc,
                elutionTime = 0,
                peptideMatchesCount = pepInst.totalLeavesMatchCount,
                quantChannelId = qcId,
                peptideId = Some(pepInst.peptideId),
                peptideInstanceId = Some(pepInst.id),
                selectionLevel = 2
              )
              //Update rsm specific map
              quantPepByPepID.put(pepInst.peptideId, qp)
              
              //Update complete Map to be used for MasterQuantPeptide creation
              forMasterQPepByPepId.getOrElseUpdate(pepInst.peptideId, new HashMap[Long, QuantPeptide]()).put(qcId, qp)

              qp
            }

            protBSC += qPep.peptideMatchesCount
            if (isPepSpecific)
              protSSC += qPep.rawAbundance
            protWSC += (qPep.peptideMatchesCount.toFloat * weight)
          }) //End go through PeptideInstance of ProtSet's PeptideSet

          val quantProteinSet = new QuantProteinSet(
            rawAbundance = protSSC,
            abundance = protWSC,
            peptideMatchesCount = protBSC,
            quantChannelId = qcId,
            selectionLevel = 2
          )

          //Update complete Map to be used for MasterQuantProtei	nSet creation
          forMasterQProtSetByProtSet.getOrElseUpdate(currentProteinSetWeightStruct.proteinSet, new HashMap[Long, QuantProteinSet]()).put(qcId, quantProteinSet)

        } //End Protein identified in current RSM
        else {
          logger.debug("Protein "+currentProteinSetWeightStruct.typicalPMAcc+" Not identified in RSM id="+rsm.id)
        }

      }) // End go through  proteinSetWeightStructsById

    }) //End go through RSMs 

    //Create MasterQuant Object
    forMasterQPepByPepId.foreach(entry => {
      mqPeptides += new MasterQuantPeptide(
        id = MasterQuantPeptide.generateNewId,
        peptideInstance = Some(refPepInstanceByPepId(entry._1)),
        quantPeptideMap = entry._2.toMap,
        masterQuantPeptideIons = Array.empty[MasterQuantPeptideIon],
        selectionLevel = 2,
        resultSummaryId = udsMasterQuantChannel.getQuantResultSummaryId()
      )
    })

    forMasterQProtSetByProtSet.foreach(entry => {
      mqProtSets += new MasterQuantProteinSet(
        proteinSet = entry._1,
        quantProteinSetMap = entry._2.toMap,
        selectionLevel = 2
      )
    })

    return (mqPeptides.toArray,mqProtSets.toArray)
  }

  private def createProtMatchesAccByPeptideSet(rsm: ResultSummary): Map[PeptideSet, Seq[String]] = {
    val rs = rsm.resultSet.get
    val protMById = rs.proteinMatchById
    val result = scala.collection.mutable.Map[PeptideSet, Seq[String]]()

    rsm.peptideSets.foreach(pepSet => {
      val seqBuilder = Seq.newBuilder[String]
      pepSet.proteinMatchIds.foreach(pmId => {
    	val acc=protMById(pmId).accession
        seqBuilder += acc
      })
      result.put(pepSet, seqBuilder.result)
    })

    result.toMap
  }
  
   
  // Clone Identification merged RSM and store this new Quantitation RSM 
  protected def cloneAndStoreMasterQuantRSM(mergedRSM: ResultSummary,
		  msiQuantRSM: MsiResultSummary,
		  msiQuantRS: MsiResultSet) {

    // Retrieve result summary and result set ids
    val quantRsmId = msiQuantRSM.getId
    val quantRsId = msiQuantRS.getId

    // Retrieve peptide instances of the merged result summary
    val mergedPepInstances = mergedRSM.peptideInstances
    val mergedPepMatchById = mergedRSM.resultSet.get.peptideMatchById
    
    // TODO: load scoring from MSIdb
    val msiScoring = new MsiScoring()
    msiScoring.setId(4)

    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info("cloning master quant peptide instances...")

    // Define some vars
    val masterQuantPepMatchIdByMergedPepMatchId = new HashMap[Long, Long]

    for (mergedPepInstance <- mergedPepInstances) {

      val peptideId = mergedPepInstance.peptide.id
      val mergedPepInstPepMatchIds = mergedPepInstance.getPeptideMatchIds
      assert(mergedPepInstPepMatchIds.length == 1, "peptide matches have not been correctly merged")

      // TODO: Retrieve the best peptide match
      //val identParentPepMatches = masterPepInstPepMatchIds.map { masterPepMatchById(_) }
      //val bestParentPepMatch = identParentPepMatches.reduce { (a,b) => if( a.score > b.score ) a else b } 
      val mergedPepMatch = mergedPepMatchById(mergedPepInstPepMatchIds(0))

      // Create a quant peptide match which correspond to the best peptide match of this peptide instance
      val msiMasterPepMatch = new MsiPeptideMatch()
      msiMasterPepMatch.setCharge(mergedPepMatch.msQuery.charge)
      msiMasterPepMatch.setExperimentalMoz(mergedPepMatch.msQuery.moz)
      msiMasterPepMatch.setScore(mergedPepMatch.score)
      msiMasterPepMatch.setRank(mergedPepMatch.rank)
      msiMasterPepMatch.setDeltaMoz(mergedPepMatch.deltaMoz)
      msiMasterPepMatch.setMissedCleavage(mergedPepMatch.missedCleavage)
      msiMasterPepMatch.setFragmentMatchCount(mergedPepMatch.fragmentMatchesCount)
      msiMasterPepMatch.setIsDecoy(false)
      msiMasterPepMatch.setPeptideId(mergedPepMatch.peptide.id)

      // FIXME: retrieve the right scoring_id
      msiMasterPepMatch.setScoringId(1)

      // FIXME: change the ORM to allow these mappings
      //msiMasterPepMatch.setBestPeptideMatchId(bestPepMatch.id) 
      //msiMasterPepMatch.setMsQueryId(bestPepMatch.msQueryId)

      // FIXME: remove this mapping when the ORM is updated
      val msiMSQFake = new fr.proline.core.orm.msi.MsQuery
      msiMSQFake.setId(mergedPepMatch.msQuery.id)
      msiMasterPepMatch.setMsQuery(msiMSQFake)

      msiMasterPepMatch.setResultSet(msiQuantRS)
      mergedPepMatch.properties.map( props => msiMasterPepMatch.setSerializedProperties(ProfiJson.serialize(props)) )

      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)

      val msiMasterPepMatchId = msiMasterPepMatch.getId
      
      // Map master peptide match id by in memory merged peptide match id
      masterQuantPepMatchIdByMergedPepMatchId(mergedPepMatch.id) = msiMasterPepMatchId

      
      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(mergedPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(mergedPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(mergedPepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(mergedPepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(mergedPepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptideId(peptideId)
      msiMasterPepInstance.setBestPeptideMatchId(msiMasterPepMatchId)
      msiMasterPepInstance.setResultSummary(msiQuantRSM)
      msiEm.persist(msiMasterPepInstance)

      val msiMasterPepInstanceId = msiMasterPepInstance.getId

      // Map the peptide instance by the peptide id
      masterPepInstByPepId(peptideId) = mergedPepInstance
      msiMasterPepInstByMergedPepInstId(mergedPepInstance.id) = msiMasterPepInstance
      
      
      // Link the best master peptide match to the quant peptide instance
      val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
      msiPepInstMatchPK.setPeptideInstanceId(msiMasterPepInstanceId)
      msiPepInstMatchPK.setPeptideMatchId(msiMasterPepMatchId)

      val msiPepInstMatch = new MsiPepInstPepMatchMap()
      msiPepInstMatch.setId(msiPepInstMatchPK)
      msiPepInstMatch.setPeptideInstance(msiMasterPepInstance)
      msiPepInstMatch.setPeptideMatch(msiMasterPepMatch)      
      msiPepInstMatch.setResultSummary(msiQuantRSM)

      //msiMasterPepInstance.setPeptidesMatches(Set(msiMasterPepMatch))
      msiEm.persist(msiPepInstMatch)

      // Map this quant peptide match to identified child peptide matches
      //FIXME No children specified Yet !? 
      // VDS TODO ? IF IDF hierarcghy <> from Quanti ??
      if(mergedPepMatch.getChildrenIds !=null){
	      for (childPepMatchId <- mergedPepMatch.getChildrenIds) {
	
	        val msiPepMatchRelation = new MsiPeptideMatchRelation()
	        msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
	        
	        val childPM: fr.proline.core.orm.msi.PeptideMatch =  msiEm.find(classOf[fr.proline.core.orm.msi.PeptideMatch], childPepMatchId)
	        msiPepMatchRelation.setChildPeptideMatch(childPM)
	        
	        // FIXME: rename to setParentResultSet
	        msiPepMatchRelation.setParentResultSetId(msiQuantRS)
	      }
      }
    }

    // Retrieve some vars
    val mergedPeptideSets = mergedRSM.peptideSets
//    this.logger.info("number of grouped peptide sets: " + mergedPeptideSets.length)
    val mergedProteinSets = mergedRSM.proteinSets
//    this.logger.info("number of grouped protein sets: " + mergedProteinSets.length)
    val mergedProtSetById = mergedRSM.proteinSetById
    val mergedProtMatchById = mergedRSM.resultSet.get.proteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets and protein sets...")
    for (mergedPeptideSet <- mergedPeptideSets) {
      
      val msiMasterProtMatchIdByMergedId = new HashMap[Long,Long]
      val mergedProtMatchIdByMasterId = new HashMap[Long,Long]
      
      // Store master protein matches
      val msiMasterProtMatches = mergedPeptideSet.proteinMatchIds.map { protMatchId =>

        val mergedProtMatch = mergedProtMatchById(protMatchId)

        val msiMasterProtMatch = new MsiProteinMatch()
        msiMasterProtMatch.setAccession(mergedProtMatch.accession)
        msiMasterProtMatch.setDescription(mergedProtMatch.description)
        msiMasterProtMatch.setGeneName(mergedProtMatch.geneName)
        msiMasterProtMatch.setScore(mergedProtMatch.score)
        msiMasterProtMatch.setCoverage(mergedProtMatch.coverage)
        msiMasterProtMatch.setPeptideCount(mergedProtMatch.sequenceMatches.length)
        msiMasterProtMatch.setPeptideMatchCount(mergedProtMatch.peptideMatchesCount)
        msiMasterProtMatch.setIsDecoy(mergedProtMatch.isDecoy)
        msiMasterProtMatch.setIsLastBioSequence(mergedProtMatch.isLastBioSequence)
        msiMasterProtMatch.setTaxonId(mergedProtMatch.taxonId)
        if( mergedProtMatch.getProteinId > 0 ) msiMasterProtMatch.setBioSequenceId(mergedProtMatch.getProteinId)
        // FIXME: retrieve the right scoring id from OM scoring type 
        msiMasterProtMatch.setScoringId(3)
        msiMasterProtMatch.setResultSet(msiQuantRS)
        msiEm.persist(msiMasterProtMatch)

        val msiMasterProtMatchId = msiMasterProtMatch.getId
        
        // Map new protein match id by Merged protein Match id
        msiMasterProtMatchIdByMergedId += mergedProtMatch.id -> msiMasterProtMatchId
        // Map protein match TMP id by the new id
        mergedProtMatchIdByMasterId += msiMasterProtMatchId -> mergedProtMatch.id


        // TODO: map protein_match to seq_databases
        
        msiMasterProtMatch
      }
      
    
      // TODO: find what to do with subsets
      if (mergedPeptideSet.isSubset == false) {

        val masterProteinSetOpt = mergedProtSetById.get(mergedPeptideSet.getProteinSetId)
        assert(masterProteinSetOpt.isDefined, "missing protein set with id=" + mergedPeptideSet.getProteinSetId)
        
        //////// Check if the protein set has at least a peptide instance with a relevant quantitation
        //val isProteinSetQuantitationRelevant = 0
        //for( tmpPepInstance <- samesetPeptideInstances ) {
        //  val rdbQuantPepInstance = quantPepByIdentPepId( tmpPepInstance.id )
        //  if( rdbQuantPepInstance.isQuantitationRelevant ) {
        //    isProteinSetQuantitationRelevant = 1
        //    last
        //  }
        //}

        // Determine the typical protein match id using the sequence coverage
        val mergedProteinSet = masterProteinSetOpt.get
        var typicalProtMatchId = mergedProteinSet.getTypicalProteinMatchId
        
        if (typicalProtMatchId <= 0) {
          val typicalProtMatchTmpId = mergedProteinSet.proteinMatchIds.reduce { (a, b) =>
            if (mergedProtMatchById(a).coverage > mergedProtMatchById(b).coverage) a else b
          }          
          typicalProtMatchId = msiMasterProtMatchIdByMergedId(typicalProtMatchTmpId)
        }
        

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(true)
        msiMasterProteinSet.setSelectionLevel(2)
        msiMasterProteinSet.setProteinMatchId(typicalProtMatchId)
        msiMasterProteinSet.setResultSummary(msiQuantRSM)
        msiEm.persist(msiMasterProteinSet)

        val msiMasterProteinSetId = msiMasterProteinSet.getId
        msiMasterProtSetById(msiMasterProteinSetId) = msiMasterProteinSet
        msiMasterProtSetByMergedProtSetId(mergedProteinSet.id) = msiMasterProteinSet

        // Retrieve peptide set items
        val samesetItems = mergedPeptideSet.items

        // Store master peptide set
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset(false)
        msiMasterPeptideSet.setPeptideCount(samesetItems.length)
        msiMasterPeptideSet.setPeptideMatchCount(mergedPeptideSet.peptideMatchesCount)
        msiMasterPeptideSet.setProteinSet(msiMasterProteinSet)
        // FIXME: retrieve the right scoring id
        msiMasterPeptideSet.setScore(mergedPeptideSet.score)
        msiMasterPeptideSet.setScoring(msiScoring)
        msiMasterPeptideSet.setResultSummaryId(quantRsmId)
        msiEm.persist(msiMasterPeptideSet)

        val msiMasterPeptideSetId = msiMasterPeptideSet.getId

        // Link master peptide set to master peptide instances
        for (samesetItem <- samesetItems) {
          val mergedSameSetPepInst = samesetItem.peptideInstance
          val msiMasterPepInst = msiMasterPepInstByMergedPepInstId(mergedSameSetPepInst.id)

          val msiPepSetItemPK = new MsiPeptideSetItemPK()
          msiPepSetItemPK.setPeptideSetId(msiMasterPeptideSet.getId)
          msiPepSetItemPK.setPeptideInstanceId(msiMasterPepInst.getId)

          // TODO: change JPA definition to skip this double mapping
          val msiPepSetItem = new MsiPeptideSetItem()
          msiPepSetItem.setId(msiPepSetItemPK)
          msiPepSetItem.setPeptideSet(msiMasterPeptideSet)
          msiPepSetItem.setPeptideInstance(msiMasterPepInst)
          msiPepSetItem.setSelectionLevel(2)
          msiPepSetItem.setResultSummary(msiQuantRSM)

          msiEm.persist(msiPepSetItem)
        }
        
        for( msiMasterProtMatch <- msiMasterProtMatches ) {
          
          val msiMasterProtMatchId = msiMasterProtMatch.getId
          
          // TODO: Map master protein match to master peptide set => ORM has to be fixed
          /*val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          new Pairs::Msi::RDBO::PeptideSetProteinMatchMap(
                                  peptide_set_id = quantPeptideSetId,
                                  protein_match_id = quantProtMatchId,
                                  result_summary_id = quantRsmId,
                                  db = msiRdb
                                ).save*/
 
          // Link master protein match to master protein set
          val msiProtSetProtMatchItemPK = new MsiProtSetProtMatchItemPK()
          msiProtSetProtMatchItemPK.setProteinSetId(msiMasterProteinSet.getId)
          msiProtSetProtMatchItemPK.setProteinMatchId(msiMasterProtMatch.getId)

          // TODO: change JPA definition
          val msiProtSetProtMatchItem = new MsiProtSetProtMatchItem()
          msiProtSetProtMatchItem.setId(msiProtSetProtMatchItemPK)
          msiProtSetProtMatchItem.setProteinSet(msiMasterProteinSet)
          msiProtSetProtMatchItem.setProteinMatch(msiMasterProtMatch)
          msiProtSetProtMatchItem.setResultSummary(msiQuantRSM)
          msiEm.persist(msiProtSetProtMatchItem)
          
          // Link master protein match to master peptide set
          val msiPepSetProtMatchMapPK = new MsiPepSetProtMatchMapPK()
          msiPepSetProtMatchMapPK.setPeptideSetId(msiMasterPeptideSet.getId)
          msiPepSetProtMatchMapPK.setProteinMatchId(msiMasterProtMatchId)
          
          val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          msiPepSetProtMatchMap.setId(msiPepSetProtMatchMapPK)
          msiPepSetProtMatchMap.setPeptideSet(msiMasterPeptideSet)
          msiPepSetProtMatchMap.setProteinMatch(msiMasterProtMatch)
          msiPepSetProtMatchMap.setResultSummary(msiQuantRSM)
          msiEm.persist(msiPepSetProtMatchMap)

          // Link master protein match to master peptide matches using master sequence matches
          val mergedProtMatch = mergedProtMatchById( mergedProtMatchIdByMasterId(msiMasterProtMatchId) )
          val mergedSeqMatches = mergedProtMatch.sequenceMatches
          val mappedMasterPepMatchesIdSet = new HashSet[Long]

          for (mergedSeqMatch <- mergedSeqMatches) {

            val bestPepMatchId = mergedSeqMatch.getBestPeptideMatchId
            if (masterQuantPepMatchIdByMergedPepMatchId.contains(bestPepMatchId)) {
              val masterPepMatchId = masterQuantPepMatchIdByMergedPepMatchId(bestPepMatchId)

              if (mappedMasterPepMatchesIdSet.contains(masterPepMatchId) == false) {
                mappedMasterPepMatchesIdSet(masterPepMatchId) = true

                val msiMasterSeqMatchPK = new fr.proline.core.orm.msi.SequenceMatchPK()
                msiMasterSeqMatchPK.setProteinMatchId(msiMasterProtMatchId)
                msiMasterSeqMatchPK.setPeptideId(mergedSeqMatch.getPeptideId)
                msiMasterSeqMatchPK.setStart(mergedSeqMatch.start)
                msiMasterSeqMatchPK.setStop(mergedSeqMatch.end)

                val msiMasterSeqMatch = new MsiSequenceMatch()
                msiMasterSeqMatch.setId(msiMasterSeqMatchPK)
                msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(mergedSeqMatch.residueBefore))
                msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(mergedSeqMatch.residueAfter))
                msiMasterSeqMatch.setIsDecoy(false)
                msiMasterSeqMatch.setBestPeptideMatchId(masterPepMatchId)
                msiMasterSeqMatch.setResultSetId(quantRsId)
                msiEm.persist(msiMasterSeqMatch)

              }
            }
          }
        }
      }
    }

  }


}

