package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives.toLong
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.{IExecutionContext, MsiDbConnectionContext}
import fr.proline.core.algo.msq.config.{PeptidesSCDescription, ProteinSetSCDescription, SpectralCountConfig}
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder.any2ClauseAdd
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.dal.tables.uds.UdsDbDataSetTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.RsmDuplicator
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{ResultSummary => MsiResultSummary}
import fr.proline.core.orm.uds.MasterQuantitationChannel

import scala.collection.JavaConversions.{asScalaSet, iterableAsScalaIterable}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap, LongMap}


/**
 * @author VDS
 * Merge SpectralCountQuantifier and WeightedSCCalculator
 *
 * Compute Spectral Count : Basic, Specific and weighted for RSM referenced in Quantitation Channel.
 * The parent RSM where proteins list and peptide specificity is determined is specified in  SpectralCountConfig or will be the RSM
 * resulting of the merge of all identication RSMs.
 * The shared peptide Spectral Count Weight is computed at the parent level or at the RSM level specified in SpectralCountConfig : the levels used is the peptide ref RSM 
 * The RSM should be in the same hierarchy as identification RSMs
 * 
 */
class WeightedSpectralCountQuantifier(
  val executionContext: IExecutionContext,
  val experimentalDesign: ExperimentalDesign,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val quantConfig: SpectralCountConfig
) extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  
  protected val identRSMIds = entityCache.quantChannelRsmIds
  protected var quantClonedMergedRSM : fr.proline.core.om.model.msi.ResultSummary  = _
  
  /**
   * "{"spectral_count_result":{[
   * { "rsm_id":Long, "proteins_spectral_counts":[ { "protein_accession"=Acc, "prot_match_id"=Long, "prot_set_id"=Long, "prot_status"=String,"pep_nbr"=Int,"bsc"=Float,"ssc"=Float,"wsc"=Float}, {...} ] },
   * 	{ "rsm_id"... }
   * ]}}"
   */

//  private var _resultAsJSON: String = null

  def quantifyMasterChannel(): Unit = {

    logger.info("-- Starting spectral count quantifier")        
    var isLocalUdsTx = false //Is transaction started here
    var isLocalMsiTx = false //Is transaction started here
    try {

      if(!msiDbCtx.isInTransaction) {
        //  Begin new ORM transaction
        msiDbCtx.beginTransaction()
        isLocalMsiTx = true
      }
      
      if(!udsDbCtx.isInTransaction) {
        //  Begin new ORM transaction
        udsDbCtx.beginTransaction()
        isLocalUdsTx = true
      }

  
      // Store the master quant result set
      val msiQuantResultSet = this.storeMsiQuantResultSet()
      val quantRsId = msiQuantResultSet.getId

      val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
      val quantRsmId = msiQuantRSM.getId
  
      // Update quant result summary id of the master quant channel
      udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
  
      udsEm.persist(udsMasterQuantChannel) 
      udsEm.flush()
  
      var start = System.currentTimeMillis()
  
      // Store master quant result summary
      val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
      val rsmDuplicator =  new RsmDuplicator(rsmProvider)  
      quantClonedMergedRSM = rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet, eraseSourceIds = false, msiEm)

      var end = System.currentTimeMillis()
      logger.debug("-- Clone IDF RSM to Quant RSM : " + (end - start) + " ms")
      
      val weightRefRSMDefined = (quantConfig.weightsRefRsmIds != null && !quantConfig.weightsRefRsmIds.isEmpty)
  
      //Create Map between peptide ref RSM and identification RSM where SC is to be calculated   
  	val identRSMsIdByWeightRefRSMId : HashMap[Long, ArrayBuffer[Long]] = if(!weightRefRSMDefined) {
           val resultMap = new HashMap[Long, ArrayBuffer[Long]]()          
      	 resultMap += (mergedResultSummary.id -> (new ArrayBuffer[Long]()  ++= identRSMIds) )
      	 resultMap
      } else {      
        // create RSM list belonging to mergedRSM/dataset hierarchy : fixes problem of clear/revalidate/remerge ...       
      	createWeightRsmsIdentRsmsMap()
      }
          
      logger.debug(identRSMsIdByWeightRefRSMId.size+" Weight reference RSMs  ")
      if(identRSMsIdByWeightRefRSMId.size>1){
        identRSMsIdByWeightRefRSMId.foreach(entry=>{
          logger.debug(" Weight reference RSMs  "+entry._1+" ==> "+entry._2)
          
        })
      }
  
          
  	 val allRSMsByID : Map[Long, ResultSummary] = loadAndUpdateRSMs(identRSMsIdByWeightRefRSMId).map( rsm => rsm.id -> rsm).toMap
  
      
      // -- Create ProteinSetSCDescription (ProteinSet,typicalPMAcc,PeptidesSCDescription[pepSpecific, nbr PSM specific, weightByPeptideId])  from reference RSM
      val proteinSetSCStructsByProtSetId = createProteinSetSCDescription(identRSMsIdByWeightRefRSMId, allRSMsByID)
  
      var end2 = System.currentTimeMillis()
      logger.debug("-- Create ProteinSetSCDescription  " + (end2 - end) + " ms. Found "
        + proteinSetSCStructsByProtSetId.size + " proteins to calculate SC for (versus " + mergedResultSummary.proteinSets.length + " in merged RSM)")
  
      // Compute master quant peptides
      // !! Warning : Returned values are linked to Identification RSM (OM Objects) and not to Quantitation RSM (ORM Objects)
      val (mqPeptides, mqProtSets) = computeMasterQuantValues(
        udsMasterQuantChannel,
        entityCache.quantChannelResultSummaries.toSeq,
        identRSMsIdByWeightRefRSMId, 
        proteinSetSCStructsByProtSetId
      )
  
      end = System.currentTimeMillis()
      logger.debug("-- computeMasterQuantValues : " + (end - end2) + " ms")
  
      this.logger.info("storing " + mqPeptides.length + " master peptide quant data...")
  
      // Iterate over master quant peptides to store corresponding spectral counts
      for (mqPeptide <- mqPeptides) {
        this.storeMasterQuantPeptide(mqPeptide, msiQuantRSM, Some(mqPeptide.peptideInstance.get.id))
      }
  
      end2 = System.currentTimeMillis()
      logger.debug("-- storing master peptide quant data : " + (end2 - end) + " ms")
  
      this.logger.info("storing " + mqProtSets.length + " master proteins set quant data...")
  
      // Iterate over master quant protein sets to store corresponding spectral counts
      for (mqProtSet <- mqProtSets) {
          this.storeMasterQuantProteinSet(mqProtSet, mqProtSet.proteinSet.id, msiQuantRSM)
      }
  
      end = System.currentTimeMillis()
      logger.debug("-- storing master proteins quant dat : " + (end - end2) + " ms")
  
  //    _resultAsJSON = createJSonOutputResult(msiQuantRSM, mqProtSets, proteinSetSCStructsByProtSetId)
  
//      end2 = System.currentTimeMillis()
//      logger.debug("-- createJSonOutputResult : " + (end2 - end) + " ms")
    } finally {
        // Commit ORM transaction
      if(isLocalMsiTx)
        msiDbCtx.commitTransaction()
      if(isLocalUdsTx)
        udsDbCtx.commitTransaction()   
    }     

  }

  
  private def createWeightRsmsIdentRsmsMap() : HashMap[Long, ArrayBuffer[Long]] = {
        
    val dsDefined = masterQc.identDatasetId.isDefined

    if(!dsDefined)    {
      //Use RSM hierarchy to create Map : don't work on merge RS !!      
        val rsmIdsInHierarchy = new ArrayBuffer[Long]()
        rsmIdsInHierarchy += mergedResultSummary.id
        rsmIdsInHierarchy ++= getAllowedRSMIdsList(rsmIdsInHierarchy)
      createWeightRsmMapUsingRsms(identRSMIds, rsmIdsInHierarchy)
    } else {     
       //Use DS hierarchy to create Map : should be preferred => set DS iD mandatory ?
      createWeightRsmMapUsingDatasets()

    }
  }

      /*****  Use DS hierarchy to create Map  ****/
  private def createWeightRsmMapUsingDatasets() : HashMap[Long, ArrayBuffer[Long]] = {
      val dsId = masterQc.identDatasetId.get
      var identRsmIdsByWeightRsmIds = new HashMap[Long, ArrayBuffer[Long]]()   
      var currentWRsmId = -1L
      if(quantConfig.weightsRefRsmIds.contains(mergedResultSummary.id)){
        currentWRsmId = mergedResultSummary.id
        val idenRsms = if(identRSMIds.contains(currentWRsmId)) ArrayBuffer(currentWRsmId) else ArrayBuffer.empty[Long]
        identRsmIdsByWeightRsmIds.put(currentWRsmId, idenRsms)      
      }
        
      fillWeightRsmMap(dsId, identRsmIdsByWeightRsmIds,currentWRsmId)
      identRsmIdsByWeightRsmIds
  }
   
  private def fillWeightRsmMap(parentId: Long, identRsmIdsByWeightRsmIds: HashMap[Long, ArrayBuffer[Long]], currentWRsmId: Long) {
    
//    var newWRsmId =  currentWRsmId
    var weightRsmIdBychildDSIds = new HashMap[Long,Long]()
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      val sqlQuery = new SelectQueryBuilder1(UdsDbDataSetTable).mkSelectQuery((t, c) =>
        List(t.ID, t.RESULT_SUMMARY_ID) -> "WHERE " ~ t.PARENT_DATASET_ID ~ " = " ~ parentId)
      udsEzDBC.select(sqlQuery) { r =>
        {
          val child = toLong(r.nextAny)
          val rsmId = toLong(r.nextAny)          
                   
         
          if(quantConfig.weightsRefRsmIds.contains(rsmId))
             weightRsmIdBychildDSIds += (child -> rsmId)
           else
             weightRsmIdBychildDSIds += (child -> currentWRsmId)
        val childWRsmId = weightRsmIdBychildDSIds(child)
        if(identRSMIds.contains(rsmId)) {
          if(childWRsmId <0)
            throw new IllegalArgumentException("Identification Summary "+rsmId+" can't be associated to a weight Identification Summary")
          val associatedIdentRsms = identRsmIdsByWeightRsmIds.getOrElseUpdate(childWRsmId, ArrayBuffer.empty[Long])
          associatedIdentRsms += rsmId          
        }             
      }                 
      }
    }
    
    weightRsmIdBychildDSIds.foreach( entry => {
      val childDsId = entry._1
      val wRsmId=  entry._2
      fillWeightRsmMap(childDsId,identRsmIdsByWeightRsmIds, wRsmId)
    })
    
  }
  
  
      /*****  Use RSM  hierarchy to create Map  ****/
  
  private def getAllowedRSMIdsList(parentId: ArrayBuffer[Long]): Seq[Long] = {
    
    var childs = new ArrayBuffer[Long]()
    DoJDBCReturningWork.withEzDBC( msiDbCtx) { msiEzDBC =>
				val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable).mkSelectQuery( (t,c) =>
					List(t.PARENT_RESULT_SUMMARY_ID,t.CHILD_RESULT_SUMMARY_ID) -> "WHERE "~ t.PARENT_RESULT_SUMMARY_ID ~" IN("~ parentId.mkString(",") ~")" )
				msiEzDBC.select(sqlQuery) { r => {
					val parent = toLong(r.nextAny) 
					val child = toLong(r.nextAny)   	  	
					childs += child
				  }
			}
		}
    if(childs.nonEmpty)
      childs ++= getAllowedRSMIdsList(childs)
    childs
  }
    
  private def createWeightRsmMapUsingRsms(childsRsmIds: Seq[Long], rsmIdsInHierarchy: ArrayBuffer[Long], tmpChildPerParentMap : HashMap[Long, ArrayBuffer[Long]] = new HashMap[Long, ArrayBuffer[Long]]()) : HashMap[Long, ArrayBuffer[Long]] = {
    
		val resultMap = new HashMap[Long, ArrayBuffer[Long]]()
		val childToSearchIdsBuilder = Seq.newBuilder[Long]

    //test if childsIds are peptide Ref RSM or mergedResultSummary => Don't search in hierarchy !
    // Should only occur on first call of createRSMHierarchyMap... 
    childsRsmIds.foreach(childId => {
      if (quantConfig.weightsRefRsmIds.contains(childId) || mergedResultSummary.id.equals(childId)) { //child is a peptide ref. RSM or mergedResultSummary !
        val childList = resultMap.getOrElseUpdate(childId, new ArrayBuffer[Long]()) // Get Child already associated to this parent
        childList += childId //add current child/parent to list
        resultMap.put(childId, childList)
      } else {
        childToSearchIdsBuilder += childId //Else search in hierarchy 
      }
    })
	    
		
		val childsPerParent = new HashMap[Long, ArrayBuffer[Long]]()
		val childToSearchIds = childToSearchIdsBuilder.result
		if(childToSearchIds.nonEmpty){
			DoJDBCReturningWork.withEzDBC( msiDbCtx) { msiEzDBC =>
				val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable).mkSelectQuery( (t,c) =>
					List(t.PARENT_RESULT_SUMMARY_ID,t.CHILD_RESULT_SUMMARY_ID) -> "WHERE "~ t.CHILD_RESULT_SUMMARY_ID ~" IN("~ childToSearchIds.mkString(",") ~")" )
				msiEzDBC.select(sqlQuery) { r => {
					val parent = toLong(r.nextAny) 
					val child = toLong(r.nextAny) 
  	  		if(rsmIdsInHierarchy.contains(parent)) {//Parent in hierarchy, go ahead
					  val childList = childsPerParent.getOrElseUpdate(parent, new ArrayBuffer[Long]())
					  childList += child
		      }
			  }}
			}
		}
			
		val newChildsBuilder = Seq.newBuilder[Long]
		childsPerParent.foreach(entry =>{
		  val parentID = entry._1
		  val childIDs = entry._2
		  if(quantConfig.weightsRefRsmIds.contains(parentID) || mergedResultSummary.id.equals(parentID)){ //Parent is a peptide Ref RSM or mergedResultSummary !
		    childIDs.foreach(childId =>{
		    	if(tmpChildPerParentMap.contains(childId)){ // Current child was identified as a parent of original identification RSM
		    		val childList = resultMap.getOrElseUpdate(parentID, new ArrayBuffer[Long]()) // Get Child already associated to this parent
    				childList ++= tmpChildPerParentMap(childId) //Move child from temporary Map to final Map !
    				resultMap.put(parentID,childList )
    				tmpChildPerParentMap.remove(childId)
		    	} else { //Current Child is an identification RSM		    	
  	    		val childList = resultMap.getOrElseUpdate(parentID, new ArrayBuffer[Long]()) // Get Child already associated to this parent
    				childList += childId //add child from temporary Map to final Map !
    				resultMap.put(parentID,childList )
		    	} 
		    })		    
		  } else { // Parent is not a peptide Ref : Search in hierarchy 
		    newChildsBuilder += parentID

		    childIDs.foreach(childId =>{
		    	if(tmpChildPerParentMap.contains(childId)){ // Current child was identified as a parent of original identification RSM.
		    	  val childList = tmpChildPerParentMap.getOrElseUpdate(parentID, new ArrayBuffer[Long]()) // Get Child already associated to this parent
		    		childList ++= tmpChildPerParentMap(childId) //Change Parent ref for these childs
		    		
		    		tmpChildPerParentMap.put(parentID, childList)
    				tmpChildPerParentMap.remove(childId)
		    	} else {
		    	  val childList =  tmpChildPerParentMap.getOrElseUpdate(parentID, new ArrayBuffer[Long]()) // Get Child already associated to this parent
		    	  childList += childId
		    	  tmpChildPerParentMap.put(parentID, childList)
		    	}
		    })

		  } //End if / else parent is peptide ref RSM
		})
		
		val newChilds = newChildsBuilder.result
		if(newChilds.nonEmpty){
      val returnedResultMap = createWeightRsmMapUsingRsms(newChilds, rsmIdsInHierarchy, tmpChildPerParentMap)
		  returnedResultMap.foreach(entry => {
		    val retParentId = entry._1
        val retChildList = entry._2
        val childList = resultMap.getOrElseUpdate(retParentId, new ArrayBuffer[Long]())
        childList ++=retChildList
        resultMap.put(retParentId,childList )
		  })		  		 
		}
              
		resultMap
  }
  

  /**
   * 
   *  Create ProteinSetSCDescription for each ProteinSet of quantified merged RSM.
   *  ProteinSetSCDescription contains following info :
   *  - ProteinSet in Ref RSM,
   *  - typical ProteinMatch Accession,
   *  - Map for each peptide ref RSM : RefRSM -> PeptidesSCInfo : list of specific peptides, nbr of specific PSM, map of SC weight by PeptideId
   */

  private def createProteinSetSCDescription(
    identRSMsByPepRefRSM: HashMap[Long, ArrayBuffer[Long]],
    refRSMsByID: Map[Long, ResultSummary]
  ): Map[Long, ProteinSetSCDescription] = {

	 //ProteinSetSCDescription for each Merged RSM ProteinSet referenced by ProteinSet id  
    val proteinSetSCDescriptionByProtSetIdBuilder = Map.newBuilder[Long, ProteinSetSCDescription]
    // Map each peptide to the list of identified ProteinSet id for each RSM (merged RSM and peptideRef RSM)
    val protSetIdByPepIdPerRSMId = new HashMap[Long, HashMap[Long, ArrayBuffer[Long]]]()

    
    //****  For each validated ProteinSet, initialize a ProteinSetSCDescription and create Maps
    quantClonedMergedRSM.proteinSets.filter(_.isValidated).foreach(protSet => {

      //-- Get Typical Protein Match Accession 
      val pmAccession: String = if (protSet.getRepresentativeProteinMatch != null && protSet.getRepresentativeProteinMatch().isDefined) {
        protSet.getRepresentativeProteinMatch().get.accession
      } else {
        val typicalPM = msiEm.find(classOf[fr.proline.core.orm.msi.ProteinMatch], protSet.getRepresentativeProteinMatchId())
      typicalPM.getAccession
    }

      val samesetPMAccession : Seq[String] = if(protSet.samesetProteinMatches!= null && protSet.samesetProteinMatches.isDefined){
        protSet.samesetProteinMatches.get.map(prM => prM.accession).toSeq
      } else {
        val sameSetAccsBuilder = mutable.Seq.newBuilder[String]
        protSet.getSameSetProteinMatchIds.foreach( prMId => {
          val typicalPM = msiEm.find(classOf[fr.proline.core.orm.msi.ProteinMatch], protSet.getSameSetProteinMatchIds )
          sameSetAccsBuilder += typicalPM.getAccession
        })
        sameSetAccsBuilder.result()
      }

      
      //** MERGED RSM: Get PeptideInfo for SC
      val pepSpecif = Seq.newBuilder[Long]
      val weightByPepId = new HashMap[Long, Float]() 

      //-- Get peptide specific PSM count and create Map : peptide => List ProtSet.Id identified by peptide for further weight computation
      var nbrPSMSpecif: Int = 0
      protSet.peptideSet.getPeptideInstances().foreach(pepI => {
        val proSetIds = protSetIdByPepIdPerRSMId.getOrElseUpdate(mergedResultSummary.id, new HashMap[Long, ArrayBuffer[Long]]()).getOrElseUpdate(pepI.peptideId, new ArrayBuffer[Long])
        proSetIds += protSet.id
        if (pepI.validatedProteinSetsCount == 1) {
          pepSpecif += pepI.peptideId
          nbrPSMSpecif += pepI.totalLeavesMatchCount
        }
        weightByPepId += pepI.peptideId -> 0.0f //will be computed later see computePeptideWeight method
      })
      val mergedRSMPepSCInfo = new PeptidesSCDescription(pepSpecificIds = pepSpecif.result, nbrPSMSpecific = nbrPSMSpecif, weightByPeptideId = weightByPepId)
      val protSetSCDescr = new ProteinSetSCDescription(proteinSet = protSet, typicalPMAcc = pmAccession, samesetPMAcc = samesetPMAccession, refRSMPeptidesInfo = mergedRSMPepSCInfo)

      if (identRSMsByPepRefRSM.contains(mergedResultSummary.id)){ // ref RSM is also ref peptide SC RSM
    	  protSetSCDescr.peptideInfoByRSMId += (mergedResultSummary.id -> mergedRSMPepSCInfo)
      }
        
      //** Peptide Weight Ref RSMs :Get PeptideInfo for SC
      if (quantConfig.weightsRefRsmIds != null) {
        // create peptide specific count for next RefRSM
        quantConfig.weightsRefRsmIds.foreach(rsmId => {
          if (!rsmId.equals(mergedResultSummary.id)) { //Already done      
            val currentRSMpepSpecif = Seq.newBuilder[Long]
            var currentNbrPSMSpecif: Int = 0
            val currentWeightByPepId = new HashMap[Long, Float]() 
            val pepSet : PeptideSet = getPeptideSet(refRSMsByID(rsmId), pmAccession)           
            //Go through Peptide Instance in peptide set identifying merged RSM typical protein in current peptide reference RSM 
            if(pepSet != null) {
	            pepSet.getPeptideInstances().foreach( pepInst =>{
	            	val proSetIds = protSetIdByPepIdPerRSMId.getOrElseUpdate(rsmId, new HashMap[Long, ArrayBuffer[Long]]()).getOrElseUpdate(pepInst.peptideId, new ArrayBuffer[Long])
	    			proSetIds += protSet.id
	    		
	              if(protSetSCDescr.refRSMPeptidesInfo.pepSpecificIds.contains(pepInst.peptideId)){ //Seen as specific in ref RSM
	                currentRSMpepSpecif += pepInst.peptideId
	                currentNbrPSMSpecif += pepInst.totalLeavesMatchCount
	              }
	              currentWeightByPepId += pepInst.peptideId -> 0.0f //will be computed later see computePeptideWeight method
	            })
	            val pepRefSCInfo = new PeptidesSCDescription(pepSpecificIds = currentRSMpepSpecif.result, nbrPSMSpecific = currentNbrPSMSpecif, weightByPeptideId = currentWeightByPepId)
	            protSetSCDescr.peptideInfoByRSMId += (rsmId -> pepRefSCInfo)
            }
                        
          }
        })
      } // End go through Peptide Weight Ref RSMs

      proteinSetSCDescriptionByProtSetIdBuilder += protSet.id -> protSetSCDescr

    }) // End ProteinSetSCDescription initialization : go through validated ProteinSet

    val proteinSetSCDescriptionByProtSetId: Map[Long, ProteinSetSCDescription] = proteinSetSCDescriptionByProtSetIdBuilder.result

    //**** Compute Peptides Weight 
    identRSMsByPepRefRSM.keySet.foreach(rsmId=>{
      computePeptideWeight(rsmId, proteinSetSCDescriptionByProtSetId, protSetIdByPepIdPerRSMId(rsmId))
    })

    proteinSetSCDescriptionByProtSetId
  }

  /**
   * Load specified RSMs if necessary and update totalLeaveMatchCount (Basic SC) for all if needed  
   * Return all loaded RSMs 
   */
  private def loadAndUpdateRSMs(identRSMsIdByWeightRefRSMId : HashMap[Long, ArrayBuffer[Long]]): Array[ResultSummary] = {
    
    val loadedRSMIds = identRSMIds :+ mergedResultSummary.id
    val loadedRSMIdSet = loadedRSMIds.toSet
    val rsmsForSCUpdate = new ArrayBuffer[ResultSummary](loadedRSMIds.length)
    val upToDateRSMs = new ArrayBuffer[ResultSummary](loadedRSMIds.length)
    
    val weightRefRsmIds = identRSMsIdByWeightRefRSMId.keys
    val notLoadedRSMIds = weightRefRsmIds.filterNot(loadedRSMIdSet.contains(_)).toList

    //Test loaded RSM SC 
    for (rsmId <- loadedRSMIds) {
      
      val newPepRefRSM = if (rsmId == mergedResultSummary.id) {
        mergedResultSummary
      } else {
        entityCache.quantChannelResultSummaries.find(_.id == rsmId).get
      }
      
       // TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information !
  	  //SC Update needed
      if (newPepRefRSM.peptideInstances(0).totalLeavesMatchCount < 0) { 
    	  rsmsForSCUpdate += newPepRefRSM
      }
    
      upToDateRSMs += newPepRefRSM
    }
   
    // Load result summaries 
	  logger.info("loading result summaries...")

    // Instantiate a RSM provider
    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val newlyLoadedRSMs = rsmProvider.getResultSummaries(notLoadedRSMIds, loadResultSet = true)
    
    for (rsm <- newlyLoadedRSMs) {
    	// TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information !
    	//SC Update needed
    	if (rsm.peptideInstances(0).totalLeavesMatchCount < 0) { 
    		rsmsForSCUpdate += rsm
    	}    
    }
    
	  if( rsmsForSCUpdate.nonEmpty ) {
      // Load result summaries 
      logger.info("updatePepInstanceSC for result summaries as needed...")
      
      val pepInstanceFilteringLeafSCUpdater= new PepInstanceFilteringLeafSCUpdater()
      pepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsmsForSCUpdate, executionContext)
      
      for( rsmToSave <- rsmsForSCUpdate; pepI <- rsmToSave.peptideInstances) {
        val ormPepInst = this.msiEm.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepI.id)
  			ormPepInst.setTotalLeavesMatchCount(pepI.totalLeavesMatchCount)
      }
	  }
	      
    upToDateRSMs ++= newlyLoadedRSMs
    
    upToDateRSMs.toArray
  }
  
  private def getPeptideSet(rsm : ResultSummary, typicalProteinAcc : String ) : PeptideSet = {
    
      //--- Get RSM Peptide Match/Protein Match information 	     
      // map : list of ProtMatch Id,accession by PeptideSet
    //VDS TODO : SHOULD be done once !
      val protMatchesAccListByPepSet: Map[PeptideSet, Seq[(Long, String)]] = createProtMatchesAccByPeptideSet(rsm)

	  var peptideSetForPM: PeptideSet = null
	  var foundPMIDandAcc: (Long, String) = null

	  val pepSetByPMIt = protMatchesAccListByPepSet.iterator
	  while (pepSetByPMIt.hasNext) { // && peptideSetForPM == null) {
          val nextEntry: (PeptideSet, Seq[(Long, String)]) = pepSetByPMIt.next
          nextEntry._2.foreach(pmIdAndAcc => {
            if (pmIdAndAcc._2.equals(typicalProteinAcc)) {
              if (peptideSetForPM != null)
                logger.warn(" --- !!  FOUND AN OTHER MATCH FOR " + typicalProteinAcc + " => " + peptideSetForPM.isSubset + " and " + nextEntry._1.id)
              peptideSetForPM = nextEntry._1
              foundPMIDandAcc = pmIdAndAcc
            }
          })
        }

      peptideSetForPM
    }
  
  
  /**
   * Compute Peptide's SC information (Specificity and Weight) for each identified ProteinSet
   *
   *  If peptide is a specific ProteinSet, the corresponding weight will be 1
   *  Else if peptide is shared between multiple ProteinSets the weight = # specific PSM of ProtSet / Sum ( #specific PSM of all ProtSet identified by this pep)
   *
   *  @param  rsmId : ID of the peptide ref RSM to compute peptide SC information for
   *  @param  proteinWeightStructByProtSetId Map ProteinSetSCDescription by ProteinSetId in peptide reference RSM. ProteinSetSCDescription should be updated
   *  @param  protSetIdByPep For each Peptide (id) references list of ProteinSet (Id) identified by this peptide
   */
  private def computePeptideWeight(rsmId: Long, proteinWeightStructByProtSetId: Map[Long, ProteinSetSCDescription], protSetIdByPep: HashMap[Long, ArrayBuffer[Long]]): Unit = {

    proteinWeightStructByProtSetId.foreach(entry => {
      val currentProteinWeightStruct = entry._2
	  if(currentProteinWeightStruct.peptideInfoByRSMId.contains(rsmId)){

	      val pepInfo = currentProteinWeightStruct.peptideInfoByRSMId(rsmId)
	      //Calculate  weight for each peptide identifying this ProteinSet
	      pepInfo.weightByPeptideId.foreach(weightMapEntry => {
	        val pepId = weightMapEntry._1
	        if (currentProteinWeightStruct.refRSMPeptidesInfo.pepSpecificIds.contains(pepId) && pepInfo.pepSpecificIds.contains(pepId)) { // specific peptide in Ref RSM context and exist in current RSM, weight =1
	          pepInfo.weightByPeptideId(pepId) = 1.0f
	        } else {
	          //Not specific peptide,  weight = nbr PSM Specific of current ProtSet / Sum ( nbr PSM Specific of all ProtSet identified by this pep)
	          var sumNbrSpecificPSMs = 0
	          protSetIdByPep(pepId).foreach(protSetId => {
	            if (proteinWeightStructByProtSetId.contains(protSetId))
	              sumNbrSpecificPSMs += proteinWeightStructByProtSetId(protSetId).peptideInfoByRSMId(rsmId).nbrPSMSpecific
	          })
	
	          if (sumNbrSpecificPSMs > 0)
	            pepInfo.weightByPeptideId.put(pepId, pepInfo.nbrPSMSpecific.toFloat / sumNbrSpecificPSMs.toFloat)
	          else
	            pepInfo.weightByPeptideId.put(pepId, 0)
	        }
	      }) //End go through ProteinSet Peptides
	  	}
    }) //	 End go through  ProteinSet (ProteinPepsWeightStruct)
  }

  protected lazy val quantPeptidesObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.SPECTRAL_COUNTING_QUANT_PEPTIDES.toString)
  }

  protected lazy val quantPeptideIonsObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.SPECTRAL_COUNTING_QUANT_PEPTIDE_IONS.toString)
  }

  override protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
    require(masterQc.identResultSummaryId.isDefined, "mergedIdentRsmIdOpt is not defined")
    val identRsm = super.getMergedResultSummary(msiDbCtx)

    // FIXME: it should not be stored here but rather in the dedicated object tree
    val spectralCountProperties = if (quantConfig.weightsRefRsmIds != null && !quantConfig.weightsRefRsmIds.isEmpty) {
        Some(new SpectralCountProperties(weightsRefRsmIds = quantConfig.weightsRefRsmIds))
    } else None

    // Update MQC properties
    val mqcProperties = new MasterQuantChannelProperties(spectralCountProperties)
    udsMasterQuantChannel.setSerializedProperties(ProfiJson.serialize(mqcProperties))
      
    identRsm

  }

  /**
   * Compute BSC and SSC for each peptide instance of each resultsummary and store information in
   * QuantPeptide and MasterQuantPeptide
   * Compute WSC for each ref RSM typical protein in each rsm and store information in
   * QuantProteinSet and MasterQuantProteinSet
   *
   */
  def computeMasterQuantValues(
    udsMasterQuantChannel: MasterQuantitationChannel,   
    resultSummaries: Seq[ResultSummary],
    identRSMsByPepRefRSM: HashMap[Long, ArrayBuffer[Long]],
    protSetWeightStructsByProtSetId: Map[Long, ProteinSetSCDescription]
  ): (Array[MasterQuantPeptide], Array[MasterQuantProteinSet]) = {

    // Map quant channel id by resultSummary id    
    val qcIdByRsmId = udsMasterQuantChannel.getQuantitationChannels.map(qc => qc.getIdentResultSummaryId -> qc.getId).toMap

    val refPepInstanceByPepId = quantClonedMergedRSM.peptideInstances.map(pi => pi.peptideId -> pi).toMap

    //     val qPepIonsMapsByrsmId = new HashMap[Long,Map[Long, Array[QuantPeptideIon]]]
    val quantPepByQChIdsByPepId = new HashMap[Long, scala.collection.mutable.LongMap[QuantPeptide]]
    val quantProtSetByQChIdsByProtSet = new HashMap[ProteinSet, scala.collection.mutable.LongMap[QuantProteinSet]]

    // returnesValues
    val mqPeptides = new ArrayBuffer[MasterQuantPeptide]
    val mqProtSets = new ArrayBuffer[MasterQuantProteinSet]

    //revert identRSMsByPepRefRSM Map 
     val weightRefRSMIdByIdentRSMId = new HashMap[Long, Long]()
     identRSMsByPepRefRSM.foreach(entry =>{
       logger.debug(" weightRSM {} ", entry._1.toString)
       entry._2.foreach( rsmId =>{
          logger.debug(" associated to identRSM {} ", rsmId.toString)
    	   weightRefRSMIdByIdentRSMId.put(rsmId , entry._1)  
       })       
     })
    
    
    // Compute SpectralCount for each RSM
    resultSummaries.foreach(rsm => {
      logger.debug("  --- computeMasterQuantValues for rsm " + rsm.id)
      val qcId = qcIdByRsmId(rsm.id)
      val weightRefRSMId = weightRefRSMIdByIdentRSMId(rsm.id) 

      val quantPepByPepID: scala.collection.mutable.Map[Long, QuantPeptide] = scala.collection.mutable.Map[Long, QuantPeptide]()


      //--- Get RSM Peptide Match/Protein Match information 	     
      // map   list of ProtMatch accession by PeptideSet
      //      logger.trace("  --- map   list of ProtMatch accession by PeptideSet for rsm "+rsm.id)      
      val protMatchesAccListByPepSet: Map[PeptideSet, Seq[(Long, String)]] = createProtMatchesAccByPeptideSet(rsm)

      var unidentifiedProteinsInRSM: Int = 0

      //--- Calculate SCs for each Ref RSM ProtSet
      protSetWeightStructsByProtSetId.foreach  (entry => {	    
        val currentProteinSetWeightStruct = entry._2
        
        //Get PeptideSet containing protein in current RSM if exist
        var peptideSetForPM: PeptideSet = null
        var foundPMIDandAcc: (Long, String) = null
        var foundTypical=false
        var foundSameSet = false

        val pepSetByPMIt = protMatchesAccListByPepSet.iterator
        while (pepSetByPMIt.hasNext) { // && peptideSetForPM == null) {
          val nextEntry: (PeptideSet, Seq[(Long, String)]) = pepSetByPMIt.next
          nextEntry._2.foreach(pmIdAndAcc => {
            if (pmIdAndAcc._2.equals(currentProteinSetWeightStruct.typicalPMAcc)) {
              if (peptideSetForPM != null ) {
                logger.warn(" --- !!  FOUND AN OTHER MATCH FOR " + currentProteinSetWeightStruct.typicalPMAcc + " => is subset ?" + peptideSetForPM.isSubset + " and pepSetId =  " + nextEntry._1.id)
                if(foundSameSet && !foundTypical) {
                  peptideSetForPM = nextEntry._1
                  foundPMIDandAcc = pmIdAndAcc
                  foundTypical = true
                }
              } else { //First "found"
                peptideSetForPM = nextEntry._1
                foundPMIDandAcc = pmIdAndAcc
                foundTypical = true
              }
            } else { //Search from samesets
              if(currentProteinSetWeightStruct.samesetPMAcc.contains(pmIdAndAcc._2)) {
                //found a parent sameset in a leaf
                foundSameSet = true
                if(!foundTypical){
                  peptideSetForPM = nextEntry._1
                  foundPMIDandAcc = pmIdAndAcc
                } else if (peptideSetForPM != null ) // found protein in samesetPMAcc but already define as typicel
                  logger.warn(" --- !!  FOUND AN OTHER MATCH FOR Subset of " + currentProteinSetWeightStruct.typicalPMAcc + " => " + peptideSetForPM.isSubset + " and " + nextEntry._1.id)
              }//End found an entry
            } //End search from samesets

          })
        }

        // ProteinMatch Spectral Count
        var protWSC: Float = 0.0f
        var protSSC: Float = 0.0f
        var protBSC: Int = 0

        if (peptideSetForPM != null && currentProteinSetWeightStruct.peptideInfoByRSMId.contains(weightRefRSMId)) { //  Current Typical from Ref RSM found in current RSM

        	val currentWeightRefRSMPepInfo = currentProteinSetWeightStruct.peptideInfoByRSMId(weightRefRSMId)

          //Go through peptides instances,  compute SC and create QuantPeptide
          peptideSetForPM.getPeptideInstances().foreach(pepInst => {
            if (currentWeightRefRSMPepInfo.weightByPeptideId.get(pepInst.peptideId).isEmpty) {
              logger.warn("************ NOT FOUND Pep " + pepInst.peptideId + " in Parent but exist in RSM " + pepInst.resultSummaryId)
            } else {
              val weight = currentWeightRefRSMPepInfo.weightByPeptideId(pepInst.peptideId)
              val isPepSpecific = currentWeightRefRSMPepInfo.pepSpecificIds.contains(pepInst.peptideId) // Math.abs(weight - 1.0f) < MathUtils.EPSILON_FLOAT
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
                quantPepByQChIdsByPepId.getOrElseUpdate(pepInst.peptideId, new LongMap[QuantPeptide]()).put(qcId, qp)

                qp
              }

              protBSC += qPep.peptideMatchesCount
              if (isPepSpecific)
                protSSC += qPep.rawAbundance
              protWSC += (qPep.peptideMatchesCount.toFloat * weight)
            }
          }) //End go through PeptideInstance of ProtSet's PeptideSet

          var protSetId = peptideSetForPM.getProteinSetId()
          if (protSetId == 0) { //Subset. Not defined
            val currentIdRSM = entityCache.quantChannelResultSummaries.filter(_.id.equals(rsm.id))(0)
            protSetId = searchProtSetOf(currentIdRSM, peptideSetForPM.id)
          }

          val quantProteinSet = new QuantProteinSet(
            rawAbundance = protSSC,
            abundance = protWSC,
            peptideMatchesCount = protBSC,
            peptidesCount = if( peptideSetForPM.items != null) Some(peptideSetForPM.items.length) else None,
            proteinSetId = Some(protSetId),
            proteinMatchId = Some(foundPMIDandAcc._1),
            quantChannelId = qcId,
            selectionLevel = 2
          )

          //Update complete Map to be used for MasterQuantProtei	nSet creation
          quantProtSetByQChIdsByProtSet.getOrElseUpdate(currentProteinSetWeightStruct.proteinSet, new LongMap[QuantProteinSet]()).put(qcId, quantProteinSet)

        } //End Protein identified in current RSM
        else {
          unidentifiedProteinsInRSM += 1
          //logger.debug("Protein " + currentProteinSetWeightStruct.typicalPMAcc + " Not identified in RSM id=" + rsm.id)
        }

      }) // End go through  proteinSetWeightStructsById

      logger.debug("Number of Proteins not identified in RSM id=" + rsm.id + " : " + unidentifiedProteinsInRSM)

    }) //End go through RSMs 

    //Create MasterQuant Object
    logger.debug("  --- Create MasterQuantPeptide  ")
    quantPepByQChIdsByPepId.foreach(entry => {
      mqPeptides += new MasterQuantPeptide(
        id = MasterQuantPeptide.generateNewId,
        peptideInstance = Some(refPepInstanceByPepId(entry._1)),
        quantPeptideMap = entry._2,
        masterQuantPeptideIons = Array.empty[MasterQuantPeptideIon],
        selectionLevel = 2,
        resultSummaryId = udsMasterQuantChannel.getQuantResultSummaryId()
      )
    })

    logger.debug("  --- Create MasterQuantProteinSet  ")
    quantProtSetByQChIdsByProtSet.foreach(entry => {
      mqProtSets += new MasterQuantProteinSet(
        proteinSet = entry._1,
        quantProteinSetMap = entry._2,
        selectionLevel = 2
      )
    })

    (mqPeptides.toArray, mqProtSets.toArray)
  }

  private def searchProtSetOf(currentRSM: ResultSummary, pepSetId: Long): Long = {
    val pepSetIt = currentRSM.peptideSets.iterator

    while (pepSetIt.hasNext) {
      val nextPepSet = pepSetIt.next
      if (nextPepSet.strictSubsetIds != null && nextPepSet.strictSubsetIds.contains(pepSetId)) {
        if (nextPepSet.getProteinSetId != 0) {
          return nextPepSet.getProteinSetId()
        } else { //Search parent of parent ... 
          return searchProtSetOf(currentRSM, nextPepSet.id)
        }
      }
    } //End go through pepSet
    0l
  } // End searchProtSetOf method definition

  private def createProtMatchesAccByPeptideSet(rsm: ResultSummary): Map[PeptideSet, Seq[(Long, String)]] = {
     val rs = rsm.resultSet.get
    val protMById = rs.getProteinMatchById()
    val result = scala.collection.mutable.Map[PeptideSet, Seq[(Long, String)]]()
    val pepSetById = rsm.peptideSets.map(pepSet => pepSet.id -> pepSet).toMap

//    rsm.proteinSets.withFilter(_.isValidated).foreach(protSet => {
    // VDS ticket #14394: Don't filter valid proteins any more!  
    rsm.proteinSets.foreach(protSet => { 
    
      
      //Do SameSet PeptideSet
      val seqBuilder = Seq.newBuilder[(Long, String)]
      protSet.peptideSet.proteinMatchIds.foreach(pmId => {
        val acc = protMById(pmId).accession
        seqBuilder += Tuple2(pmId, acc)
      })
      result.put(protSet.peptideSet, seqBuilder.result)

      //Do Subset PeptideSet
      protSet.peptideSet.getStrictSubsetIds().foreach(psId => {
        val subPepSet = pepSetById(psId)
        val subSeqBuilder = Seq.newBuilder[(Long, String)]
        subPepSet.proteinMatchIds.foreach(pmId => {
          val acc = protMById(pmId).accession
          subSeqBuilder += Tuple2(pmId, acc)
        })
        result.put(subPepSet, subSeqBuilder.result)
      })

    })


    result.toMap
  }

}

