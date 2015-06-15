package fr.proline.core.service.msq.quantify

import fr.profi.util.primitives._
import fr.proline.core.util.ResidueUtils._
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
import fr.profi.util.MathUtils
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
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.repository.util.JDBCWork
import java.sql.Connection
import fr.proline.core.dal.tables.SelectQueryBuilder.any2ClauseAdd
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.algo.msq.PeptidesSCDescription
import fr.proline.core.algo.msq.ProteinSetSCDescription
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryRelationTable
import fr.proline.core.orm.msi.PeptideMatchRelationPK

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
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val scConfig: SpectralCountConfig) extends AbstractMasterQuantChannelQuantifier with Logging {

  protected val msiMasterPepInstByMergedPepInstId = new HashMap[Long, MsiPeptideInstance]
  protected val msiMasterProtSetByMergedProtSetId = new HashMap[Long, MsiProteinSet]

  /**
   * "{"spectral_count_result":{[
   * { "rsm_id":Long, "proteins_spectral_counts":[ { "protein_accession"=Acc, "prot_match_id"=Long, "prot_set_id"=Long, "prot_status"=String,"pep_nbr"=Int,"bsc"=Float,"ssc"=Float,"wsc"=Float}, {...} ] },
   * 	{ "rsm_id"... }
   * ]}}"
   */

  private var _resultAsJSON: String = null
  def getResultAsJSON() = { _resultAsJSON }

  def quantifyMasterChannel(): Unit = {

    logger.info("-- Starting spectral count quantifier")

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

    var start = System.currentTimeMillis()

    // Store master quant result summary
    this.cloneAndStoreMasterQuantRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet)

    var end = System.currentTimeMillis()
    logger.debug("-- Clone IDF RSM to Quant RSM : " + (end - start) + " ms")
    
    val weightRefRSMDefined = (scConfig.weightRefRSMIds != null && !scConfig.weightRefRSMIds.isEmpty)

    //Create Map between peptide ref RSM and identification RSM where SC is to be calculated   
	val identRSMsIdByWeightRefRSMId : HashMap[Long, ArrayBuffer[Long]] = if(!weightRefRSMDefined) {
         val resultMap = new HashMap[Long, ArrayBuffer[Long]]()          
    	 resultMap += (mergedResultSummary.id -> (new ArrayBuffer[Long]()  ++= identRSMIds) )
    	 resultMap
    } else {      
    	createRSMHierarchyMap(identRSMIds)
    }
        
    logger.debug(identRSMsIdByWeightRefRSMId.size+" Weight reference RSMs  ")
    if(identRSMsIdByWeightRefRSMId.size>1){
      identRSMsIdByWeightRefRSMId.foreach(entry=>{
        logger.debug(" Weight reference RSMs  "+entry._1+" ==> "+entry._2)
        
      })
    }

	 val allRSMsByID : Map[Long, ResultSummary] = loadAndUpdateRSMs().map( rsm => rsm.id -> rsm).toMap

    
    // -- Create ProteinSetSCDescription (ProteinSet,typicalPMAcc,PeptidesSCDescription[pepSpecific, nbr PSM specific, weightByPeptideId])  from reference RSM
    val proteinSetSCStructsByProtSetId = createProteinSetSCDescription(identRSMsIdByWeightRefRSMId, allRSMsByID)

    var end2 = System.currentTimeMillis()
    logger.debug("-- Create ProteinSetSCDescription  " + (end2 - end) + " ms. Found "
      + proteinSetSCStructsByProtSetId.size + " proteins to calculate SC for (versus " + mergedResultSummary.proteinSets.length + " in merged RSM)")

    // Compute master quant peptides
    // !! Warning : Returned values are linked to Identification RSM (OM Objects) and not to Quantitation RSM (ORM Objects)
    val (mqPeptides, mqProtSets) = computeMasterQuantValues(
      udsMasterQuantChannel,
      this.mergedResultSummary,
      this.identResultSummaries,
      identRSMsIdByWeightRefRSMId, 
      proteinSetSCStructsByProtSetId
    )

    end = System.currentTimeMillis()
    logger.debug("-- computeMasterQuantValues : " + (end - end2) + " ms")

    this.logger.info("storing " + mqPeptides.size + " master peptide quant data...")

    // Iterate over master quant peptides to store corresponding spectral counts
    for (mqPeptide <- mqPeptides) {
      this.storeMasterQuantPeptide(mqPeptide, msiQuantRSM, Some(msiMasterPepInstByMergedPepInstId(mqPeptide.peptideInstance.get.id)))
    }

    end2 = System.currentTimeMillis()
    logger.debug("-- storing master peptide quant data : " + (end2 - end) + " ms")

    this.logger.info("storing " + mqProtSets.size + " master proteins set quant data...")

    // Iterate over master quant protein sets to store corresponding spectral counts
    for (mqProtSet <- mqProtSets) {

      val msiProtSet = msiMasterProtSetByMergedProtSetId.getOrElse(mqProtSet.proteinSet.id, null)
      if (msiProtSet != null)
        this.storeMasterQuantProteinSet(mqProtSet, msiMasterProtSetByMergedProtSetId(mqProtSet.proteinSet.id), msiQuantRSM)
      else
        logger.warn(" !! No Master Quant data found for protein set id " + mqProtSet.proteinSet.id + " !! ")
    }

    end = System.currentTimeMillis()
    logger.debug("-- storing master proteins quant dat : " + (end - end2) + " ms")

    _resultAsJSON = createJSonOutputResult(msiQuantRSM, mqProtSets, proteinSetSCStructsByProtSetId)

    end2 = System.currentTimeMillis()
    logger.debug("-- createJSonOutputResult : " + (end2 - end) + " ms")

    // Commit ORM transaction
    msiDbCtx.commitTransaction()
    udsDbCtx.commitTransaction()

  }

  
  // TODO : Case where child don't have parent !!! Should not occur : At least mergedResultSummary should be found ! 
  private def createRSMHierarchyMap(childsIds: Seq[Long], tmpChildPerParentMap : HashMap[Long, ArrayBuffer[Long]] = new HashMap[Long, ArrayBuffer[Long]]()) : HashMap[Long, ArrayBuffer[Long]] = {
    
		val resultMap = new HashMap[Long, ArrayBuffer[Long]]()
		val childToSearchIdsBuilder = Seq.newBuilder[Long]
		//test if childsIds are peptide Ref RSM or mergedResultSummary => Don't search in hierarchy !
		// Should only occur on first call of createRSMHierarchyMap... 
	    childsIds.foreach(	childId =>{
	    	if(scConfig.weightRefRSMIds.contains(childId) || mergedResultSummary.id.equals(childId)){ //child is a peptide Ref RSM or mergedResultSummary !
	    		val childList = resultMap.getOrElseUpdate(childId, new ArrayBuffer[Long]()) // Get Child already associated to this parent
				childList += (childId) //add child from temporary Map to final Map !
				resultMap.put(childId,childList )				
	    	} else {
	    	  childToSearchIdsBuilder += childId //Else search in hierarchy
	    	}
	     
	    })
	    
		
		val childsPerParent = new HashMap[Long, ArrayBuffer[Long]]()
		val childToSearchIds = childToSearchIdsBuilder.result
		if(!childToSearchIds.isEmpty){
			DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
				val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryRelationTable).mkSelectQuery( (t,c) =>
					List(t.PARENT_RESULT_SUMMARY_ID,t.CHILD_RESULT_SUMMARY_ID) -> "WHERE "~ t.CHILD_RESULT_SUMMARY_ID ~" IN("~ childToSearchIds.mkString(",") ~")" )
				msiEzDBC.select(sqlQuery) { r => {
					val parent = toLong(r.nextAny) 
					val child = toLong(r.nextAny) 
					val childList = childsPerParent.getOrElseUpdate(parent, new ArrayBuffer[Long]())
					childList += child
				  }
				}
			})
		}
			
		val newChildsBuilder = Seq.newBuilder[Long]
		childsPerParent.foreach(entry =>{
		  val parentID = entry._1
		  val childIDs = entry._2
		  if(scConfig.weightRefRSMIds.contains(parentID) || mergedResultSummary.id.equals(parentID)){ //Parent is a peptide Ref RSM or mergedResultSummary !
		    childIDs.foreach(childId =>{
		    	if(tmpChildPerParentMap.contains(childId)){ // Current child was identified as a parent of original identification RSM
		    		val childList = resultMap.getOrElseUpdate(parentID, new ArrayBuffer[Long]()) // Get Child already associated to this parent
    				childList ++= tmpChildPerParentMap(childId) //Move child from temporary Map to final Map !
    				resultMap.put(parentID,childList )
    				tmpChildPerParentMap.remove(childId)
		    	} else { //Current Child is an identification RSM		    	  
		    		val childList = resultMap.getOrElseUpdate(parentID, new ArrayBuffer[Long]()) // Get Child already associated to this parent
    				childList += (childId) //add child from temporary Map to final Map !
    				resultMap.put(parentID,childList )
		    	} 
		    })		    

		  } else { // Parent is not a peptide Ref : Search in hierarchy 
		    newChildsBuilder += parentID
		    childIDs.foreach(childId =>{
		    	if(tmpChildPerParentMap.contains(childId)){ // Current child was identified as a parent of original identification RSM.
		    		val childList = tmpChildPerParentMap(childId) //Change Parent ref for these childs
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
		if(!newChilds.isEmpty){
		  resultMap ++=  createRSMHierarchyMap(newChilds, tmpChildPerParentMap)
		}
              
		resultMap
  }
  
  override protected def storeMasterQuantPeptide(
    mqPep: MasterQuantPeptide,
    msiRSM: MsiResultSummary,
    msiMasterPepInstAsOpt: Option[MsiPeptideInstance]): MsiMasterQuantComponent = {

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

    msiMQC
  }

  protected def storeMasterQuantPeptideIon(
    mqPepIon: MasterQuantPeptideIon,
    mqPep: MasterQuantPeptide,
    msiMasterPepInst: MsiPeptideInstance,
    msiRSM: MsiResultSummary): MsiMasterQuantPepIon = {

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
    msiMQPepIon.setPeptideInstance(msiMasterPepInst)
    msiMQPepIon.setPeptideId(msiMasterPepInst.getPeptide().getId())

    if (mqPepIon.properties.isDefined) msiMQPepIon.setSerializedProperties(ProfiJson.serialize(mqPepIon.properties))

    if (mqPepIon.lcmsMasterFeatureId.isDefined) msiMQPepIon.setLcmsMasterFeatureId(mqPepIon.lcmsMasterFeatureId.get)
    if (mqPepIon.bestPeptideMatchId.isDefined) msiMQPepIon.setBestPeptideMatchId(mqPepIon.bestPeptideMatchId.get)
    if (mqPepIon.unmodifiedPeptideIonId.isDefined) msiMQPepIon.setUnmodifiedPeptideIonId(mqPepIon.unmodifiedPeptideIonId.get)

    this.msiEm.persist(msiMQPepIon)

    // Update master quant peptide ion id
    mqPepIon.id = msiMQPepIon.getId

    msiMQPepIon
  }

  private def createJSonOutputResult(msiQuantRSM: MsiResultSummary, mqProtSetsVal: Array[MasterQuantProteinSet], proteinSetWeightStructsById: Map[Long, ProteinSetSCDescription]): String = {

    val jsonBuilder: StringBuilder = new StringBuilder(" \"{")
    jsonBuilder.append(SpectralCountsJSONProperties.rootPropName).append(":{[")

    val qChannels = udsMasterQuantChannel.getQuantitationChannels()

    var firstQChOcc = true
    qChannels.foreach(nextQCh => {
      val rsmId = nextQCh.getIdentResultSummaryId()
      val currentIdRSM = this.identResultSummaries.filter(_.id.equals(rsmId))(0)
      val rsmProtSetById = currentIdRSM.getProteinSetById

      if (!firstQChOcc) { jsonBuilder.append(",") } else { firstQChOcc = false }
      jsonBuilder.append("{").append(SpectralCountsJSONProperties.rsmIDPropName).append(":").append(rsmId).append(",") //save current RSM Id

      // -- Save prots SC for current RSM          	
      var firstPACOcc = true
      jsonBuilder.append(SpectralCountsJSONProperties.protSCsListPropName).append(":[")

      mqProtSetsVal.foreach(mqps => {
        //Go through All ProteinSets and extract only data for current QuantChanel    		 
        val protAC = proteinSetWeightStructsById.get(mqps.proteinSet.id).get.typicalPMAcc
        val protQuant = mqps.quantProteinSetMap.get(nextQCh.getId())

        if (protQuant.isDefined) {
          if (!firstPACOcc) {
            jsonBuilder.append(",")
          } else {
            firstPACOcc = false
          }

          var protSetId: Long = -1
          var protMatchId: Long = -1
          var protMatchStatus: String = null
          var protMatchPepNbr = -1
          if (protQuant.get.proteinSetId.isDefined) {
            protSetId = protQuant.get.proteinSetId.get
            protMatchId = protQuant.get.proteinMatchId.getOrElse(-1)

            val protSet = if (rsmProtSetById.get(protSetId).isDefined) rsmProtSetById.get(protSetId).get else null
            protMatchStatus = if (protSet != null && protSet.getRepresentativeProteinMatchId.equals(protMatchId)) {
              "Typical"
            } else {
              if (protSet != null && protSet.getSameSetProteinMatchIds.contains(protMatchId))
                "Sameset"
              else if (protSet != null)
                "Subset"
              else
                "NOT FOUND !" + protSetId
            }
          }
          //Read Nbr Pep for Protein
          val pepNbrQueryJdbcWork = new JDBCWork() {
            override def execute(con: Connection) {
              //---- Read Prot Status
              val getPepCount = "SELECT peptide_count from peptide_set_protein_match_map pspmm, peptide_set " +
                "WHERE pspmm.protein_match_id = ? and pspmm.result_summary_id = ?  and peptide_set.id = pspmm.peptide_set_id"
              val pStmt2 = con.prepareStatement(getPepCount)
              pStmt2.setLong(1, protMatchId)
              pStmt2.setLong(2, rsmId)
              val sqlResultSet2 = pStmt2.executeQuery()
              if (sqlResultSet2.next) {
                protMatchPepNbr = sqlResultSet2.getInt("peptide_count")
              }
              pStmt2.close()
            }
          } // End of jdbcWork anonymous inner class    
          executionContext.getMSIDbConnectionContext().doWork(pepNbrQueryJdbcWork, false)

          jsonBuilder.append("{").append(SpectralCountsJSONProperties.protACPropName).append("=").append(protAC).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.protMatchId).append("=").append(protMatchId).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.protSetId).append("=").append(protSetId).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.protMatchStatus).append("=").append(protMatchStatus).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.pepNbr).append("=").append(protMatchPepNbr).append(",")
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

  /**
   * 
   *  Create ProteinSetSCDescription for each ProteinSet of merged RSM.
   *  ProteinSetSCDescription contains following info :
   *  - ProteinSet in Ref RSM,
   *  - typical ProteinMatch Accession,
   *  - Map for each peptide ref RSM : RefRSM -> PeptidesSCInfo : list of specific peptides, nbr of specific PSM, map of SC weight by PeptideId
   */

  private def createProteinSetSCDescription(identRSMsByPepRefRSM : HashMap[Long, ArrayBuffer[Long]], refRSMsByID : Map[Long, ResultSummary]): Map[Long, ProteinSetSCDescription] = {

    //--- Update MergedRSM SpectralCount if necessary
    // TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information ! 
//    if (mergedResultSummary.peptideInstances(0).totalLeavesMatchCount < 0) {
//      logger.debug("  --- updatePepInstanceSC for mergedResultSummary " + mergedResultSummary.id)
//      PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(mergedResultSummary, executionContext)
//      mergedResultSummary.peptideInstances.foreach(pepI => {
//        val ormPepInst = this.msiEm.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepI.id)
//        ormPepInst.setTotalLeavesMatchCount(pepI.totalLeavesMatchCount)
//      })
//    }

	 //ProteinSetSCDescription for each Merged RSM ProteinSet referenced by ProteinSet id  
    val proteinSetSCDescriptionByProtSetIdBuilder = Map.newBuilder[Long, ProteinSetSCDescription]
    // Map each peptide to the list of identified ProteinSet id for each RSM (merged RSM and peptideRef RSM)
    val protSetIdByPepIdPerRSMId = new HashMap[Long, HashMap[Long, ArrayBuffer[Long]]]()

    
    //****  For each validated ProteinSet, initialize a ProteinSetSCDescription and create Maps
    mergedResultSummary.proteinSets.filter(_.isValidated).foreach(protSet => {

      //-- Get Typical Protein Match Accession 
      val pmAccession: String = if (protSet.getRepresentativeProteinMatch != null && protSet.getRepresentativeProteinMatch.isDefined) {
        protSet.getRepresentativeProteinMatch.get.accession
      } else {
        val typicalPM = msiEm.find(classOf[fr.proline.core.orm.msi.ProteinMatch], protSet.getRepresentativeProteinMatchId)
        typicalPM.getAccession()
      }

      
      //** MERGED RSM: Get PeptideInfo for SC
      val pepSpecif = Seq.newBuilder[Long]
      val weightByPepId = new HashMap[Long, Float]() 

      //-- Get peptide specific PSM count and create Map : peptide => List ProtSet.Id identified by peptide for further weight computation
      var nbrPSMSpecif: Int = 0
      protSet.peptideSet.getPeptideInstances.foreach(pepI => {
        val proSetIds = protSetIdByPepIdPerRSMId.getOrElseUpdate(mergedResultSummary.id, new HashMap[Long, ArrayBuffer[Long]]()).getOrElseUpdate(pepI.peptideId, new ArrayBuffer[Long])
        proSetIds += protSet.id
        if (pepI.validatedProteinSetsCount == 1) {
          pepSpecif += pepI.peptideId
          nbrPSMSpecif += pepI.totalLeavesMatchCount
        }
        weightByPepId += pepI.peptideId -> 0.0f //will be computed later see computePeptideWeight method
      })
      val mergedRSMPepSCInfo = new PeptidesSCDescription(pepSpecificIds = pepSpecif.result, nbrPSMSpecific = nbrPSMSpecif, weightByPeptideId = weightByPepId)
      val protSetSCDescr = new ProteinSetSCDescription(proteinSet = protSet, typicalPMAcc = pmAccession, refRSMPeptidesInfo = mergedRSMPepSCInfo)

      if (identRSMsByPepRefRSM.contains(mergedResultSummary.id)){ // ref RSM is also ref peptide SC RSM
    	  protSetSCDescr.peptideInfoByRSMId += (mergedResultSummary.id -> mergedRSMPepSCInfo)
      }
        
      //** Peptide Weight Ref RSMs :Get PeptideInfo for SC
      if (scConfig.weightRefRSMIds != null) {
        // create peptide specific count for next RefRSM
        scConfig.weightRefRSMIds.foreach(rsmId => {
          if (!rsmId.equals(mergedResultSummary.id)) { //Already done      
            val currentRSMpepSpecif = Seq.newBuilder[Long]
            var currentNbrPSMSpecif: Int = 0
            val currentWeightByPepId = new HashMap[Long, Float]() 
            val pepSet : PeptideSet = getPeptideSet(refRSMsByID(rsmId), pmAccession)           
            //Go through Peptide Instance in peptide set identifying merged RSM typical protein in current peptide reference RSM 
            if(pepSet != null) {
	            pepSet.getPeptideInstances.foreach( pepInst =>{
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
      computePeptideWeight(rsmId, proteinSetSCDescriptionByProtSetId, protSetIdByPepIdPerRSMId.get(rsmId).get)
    })

    proteinSetSCDescriptionByProtSetId
  }

  /**
   * Load specified RSMs if necessary and update totalLeaveMatchCount (Basic SC) for all if needed  
   * Return all loaded RSMs 
   */
  private def loadAndUpdateRSMs(): Array[fr.proline.core.om.model.msi.ResultSummary] = {
    
    val weigtRefRsmIds = if(scConfig.weightRefRSMIds != null) scConfig.weightRefRSMIds else Seq.empty[Long]
    
    val loadedRSMIds = identRSMIds :+ mergedResultSummary.id
    val updatedRSMs = Seq.newBuilder[ResultSummary]
    val rsmsForSCUpdateBuilder = Seq.newBuilder[ResultSummary]
    

    val unloadedRSMIds =  weigtRefRsmIds.filterNot(loadedRSMIds.contains(_))

    //Test loaded RSM SC 
    loadedRSMIds.foreach(rsmId => {
      val newPepRefRSM = if (rsmId.equals(mergedResultSummary.id)) {
        mergedResultSummary
      } else {
        identResultSummaries.filter(_.id.equals(rsmId))(0)
      }
      
       // TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information !
  	  //SC Update needed
      if (newPepRefRSM.peptideInstances(0).totalLeavesMatchCount < 0) { 
    	  rsmsForSCUpdateBuilder += newPepRefRSM
      }
    
      updatedRSMs += newPepRefRSM

    })
    
   
    // Load result summaries 
	this.logger.info("loading result summaries...")

    // Instantiate a RSM provider
    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx)
    val newlyLoadedRSMs = rsmProvider.getResultSummaries(unloadedRSMIds, true)
    newlyLoadedRSMs.foreach( rsm => {      
    	// TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information !
    	//SC Update needed
    	if (rsm.peptideInstances(0).totalLeavesMatchCount < 0) { 
    		rsmsForSCUpdateBuilder += rsm
    	}    
    })
    
    
      // Load result summaries 
    this.logger.info("updatePepInstanceSC for result summaries as needed...")
    val rsmsForSCUpdate = rsmsForSCUpdateBuilder.result
    PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsmsForSCUpdate, executionContext)
    rsmsForSCUpdate.foreach(rsmToSave =>{
    	rsmToSave.peptideInstances.foreach(pepI => {
    		val ormPepInst = this.msiEm.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepI.id)
			ormPepInst.setTotalLeavesMatchCount(pepI.totalLeavesMatchCount)
    	})
    })
	      
	      
    updatedRSMs ++= newlyLoadedRSMs
    updatedRSMs.result.toArray
        
  }
  
  private def getPeptideSet(rsm : ResultSummary, typicalProteinAcc : String ) : PeptideSet = {
    
      //--- Get RSM Peptide Match/Protein Match information 	     
      // map : list of ProtMatch Id,accession by PeptideSet     
      val protMatchesAccListByPepSet: Map[PeptideSet, Seq[Pair[Long, String]]] = createProtMatchesAccByPeptideSet(rsm)

	  var peptideSetForPM: PeptideSet = null
	  var foundPMIDandAcc: Pair[Long, String] = null

	  val pepSetByPMIt = protMatchesAccListByPepSet.iterator
	  while (pepSetByPMIt.hasNext) { // && peptideSetForPM == null) {
          val nextEntry: (PeptideSet, Seq[Pair[Long, String]]) = pepSetByPMIt.next
          nextEntry._2.foreach(pmIdAndAcc => {
            if (pmIdAndAcc._2.equals(typicalProteinAcc)) {
              if (peptideSetForPM != null)
                logger.warn(" --- !!  FOUND AN OTHER MATCH FOR " + typicalProteinAcc + " => " + peptideSetForPM.isSubset + " and " + nextEntry._1.id)
              peptideSetForPM = nextEntry._1
              foundPMIDandAcc = pmIdAndAcc
            }
          })
        }

        return peptideSetForPM 
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
	          protSetIdByPep.get(pepId).get.foreach(protSetId => {
	            if (proteinWeightStructByProtSetId.contains(protSetId))
	              sumNbrSpecificPSMs += proteinWeightStructByProtSetId(protSetId).peptideInfoByRSMId(rsmId).nbrPSMSpecific
	          })
	
	          if (sumNbrSpecificPSMs > 0)
	            pepInfo.weightByPeptideId.put(pepId, (pepInfo.nbrPSMSpecific.toFloat / sumNbrSpecificPSMs.toFloat))
	          else
	            pepInfo.weightByPeptideId.put(pepId, 0)
	        }
	      }) //End go through ProteinSet Peptides
	  	}
    }) //	 End go through  ProteinSet (ProteinPepsWeightStruct)
  }

  protected lazy val spectralCountingPeptidesSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.SPECTRAL_COUNTING_PEPTIDES.toString())
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

  protected lazy val spectralCountingQuantPepIonsSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.SPECTRAL_COUNTING_QUANT_PEPTIDE_IONS.toString())
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
      val idfRSM = rsmProvider.getResultSummary(scConfig.parentRSMId.get, true).get

    		//TODO fix BUG #12336
//        val spectralCountProperties = if(scConfig.weightRefRSMIds != null && !scConfig.weightRefRSMIds.isEmpty) { 
//        	Some( new SpectralCountProperties(weightsRefRSMIds =  scConfig.weightRefRSMIds.toArray.map(toLong(_))))    	     	 
//        } else None
//            
//        if(spectralCountProperties.isDefined){
//        	logger.debug("SpectralCountProperties content : ")
//        	spectralCountProperties.get.getPeptidesSpectralCountRefRSMIds.foreach({ l=>
//        	logger.debug(" Next LONG : {} "+ l.toString)
//        	})
//		}
        
//      val mqchProperties =  new MasterQuantChannelProperties(identResultSummaryId=scConfig.parentRSMId,identDatasetId= scConfig.parentDSId, spectralCountProperties=spectralCountProperties)
      val mqchProperties = new MasterQuantChannelProperties(identResultSummaryId = scConfig.parentRSMId, identDatasetId = scConfig.parentDSId)
      udsMasterQuantChannel.setSerializedProperties(ProfiJson.serialize(mqchProperties))
      idfRSM
    }

  }

  /**
   * Compute BSC and SSC for each peptide instance of each resultsummary and store information in
   * QuantPeptide and MasterQuantPeptide
   * Compute WSC for each ref RSM typical protein in each rsm and store information in
   * QuantProteinSet and MasterQuantProteinSet
   *
   * @param mergedRSM : RSM de départ et pas celui de quanti ? TODO
   */
  def computeMasterQuantValues(udsMasterQuantChannel: MasterQuantitationChannel,
                               mergedRSM: ResultSummary,
                               resultSummaries: Seq[ResultSummary],
                               identRSMsByPepRefRSM: HashMap[Long, ArrayBuffer[Long]],
                               protSetWeightStructsByProtSetId: Map[Long, ProteinSetSCDescription]): (Array[MasterQuantPeptide], Array[MasterQuantProteinSet]) = {

    // Map quant channel id by resultSummary id    
    val qcIdByRsmId = udsMasterQuantChannel.getQuantitationChannels().map(qc => qc.getIdentResultSummaryId() -> qc.getId).toMap

    val refPepInstanceByPepId = mergedRSM.peptideInstances.map(pi => pi.peptideId -> pi).toMap

    //     val qPepIonsMapsByrsmId = new HashMap[Long,Map[Long, Array[QuantPeptideIon]]]
    val forMasterQPepByPepId = new HashMap[Long, scala.collection.mutable.Map[Long, QuantPeptide]]
    val forMasterQProtSetByProtSet = new HashMap[ProteinSet, scala.collection.mutable.Map[Long, QuantProteinSet]]

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
      val weightRefRSMId = weightRefRSMIdByIdentRSMId(rsm.id) //TODO get valide one !!!! 

      val quantPepByPepID: scala.collection.mutable.Map[Long, QuantPeptide] = scala.collection.mutable.Map[Long, QuantPeptide]()

      //--- Update RSM SpectralCount if necessary
      // TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information ! Should be wrong see issue #7984
//      if (rsm.peptideInstances(0).totalLeavesMatchCount < 0) {
//        logger.debug("  --- updatePepInstanceSC for rsm " + rsm.id)
//        PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsm, executionContext)
//        rsm.peptideInstances.foreach(pepI => {
//          val ormPepInst = this.msiEm.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepI.id)
//          ormPepInst.setTotalLeavesMatchCount(pepI.totalLeavesMatchCount)
//        })
//      }

      //--- Get RSM Peptide Match/Protein Match information 	     
      // map   list of ProtMatch accession by PeptideSet
      //      logger.trace("  --- map   list of ProtMatch accession by PeptideSet for rsm "+rsm.id)      
      val protMatchesAccListByPepSet: Map[PeptideSet, Seq[Pair[Long, String]]] = createProtMatchesAccByPeptideSet(rsm)

      var unidentifiedProteinsInRSM: Int = 0

      //--- Calculate SCs for each Ref RSM ProtSet
      protSetWeightStructsByProtSetId.foreach(entry => {	    
        val currentProteinSetWeightStruct = entry._2
        
        //Get PeptideSet containing protein in current RSM if exist
        var peptideSetForPM: PeptideSet = null
        var foundPMIDandAcc: Pair[Long, String] = null

        val pepSetByPMIt = protMatchesAccListByPepSet.iterator
        while (pepSetByPMIt.hasNext) { // && peptideSetForPM == null) {
          val nextEntry: (PeptideSet, Seq[Pair[Long, String]]) = pepSetByPMIt.next
          nextEntry._2.foreach(pmIdAndAcc => {
            if (pmIdAndAcc._2.equals(currentProteinSetWeightStruct.typicalPMAcc)) {
              if (peptideSetForPM != null)
                logger.warn(" --- !!  FOUND AN OTHER MATCH FOR " + currentProteinSetWeightStruct.typicalPMAcc + " => " + peptideSetForPM.isSubset + " and " + nextEntry._1.id)
              peptideSetForPM = nextEntry._1
              foundPMIDandAcc = pmIdAndAcc
            }

          })
        }

        // ProteinMatch Spectral Count
        var protWSC: Float = 0.0f
        var protSSC: Float = 0.0f
        var protBSC: Int = 0

        if (peptideSetForPM != null) { //  Current Typical from Ref RSM found in current RSM

        	val currentWeightRefRSMPepInfo = currentProteinSetWeightStruct.peptideInfoByRSMId(weightRefRSMId)

          
          //Go through peptides instances,  compute SC and create QuantPeptide
          peptideSetForPM.getPeptideInstances.foreach(pepInst => {
            if (currentWeightRefRSMPepInfo.weightByPeptideId.get(pepInst.peptideId).isEmpty) {
              logger.warn("************ NOT FOUND Pep " + pepInst.peptideId + " in Parent but exist in RSM " + pepInst.resultSummaryId)
            } else {
              val weight = currentWeightRefRSMPepInfo.weightByPeptideId.get(pepInst.peptideId).get
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
                forMasterQPepByPepId.getOrElseUpdate(pepInst.peptideId, new HashMap[Long, QuantPeptide]()).put(qcId, qp)

                qp
              }

              protBSC += qPep.peptideMatchesCount
              if (isPepSpecific)
                protSSC += qPep.rawAbundance
              protWSC += (qPep.peptideMatchesCount.toFloat * weight)
            }
          }) //End go through PeptideInstance of ProtSet's PeptideSet

          var protSetId = peptideSetForPM.getProteinSetId
          if (protSetId == 0) { //Subset. Not defined
            val currentIdRSM = this.identResultSummaries.filter(_.id.equals(rsm.id))(0)
            protSetId = foundProtSetOf(currentIdRSM, peptideSetForPM.id)
          }

          val quantProteinSet = new QuantProteinSet(
            rawAbundance = protSSC,
            abundance = protWSC,
            peptideMatchesCount = protBSC,
            proteinSetId = Some(protSetId),
            proteinMatchId = Some(foundPMIDandAcc._1),
            quantChannelId = qcId,
            selectionLevel = 2
          )

          //Update complete Map to be used for MasterQuantProtei	nSet creation
          forMasterQProtSetByProtSet.getOrElseUpdate(currentProteinSetWeightStruct.proteinSet, new HashMap[Long, QuantProteinSet]()).put(qcId, quantProteinSet)

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

    logger.debug("  --- Create MasterQuantProteinSet  ")
    forMasterQProtSetByProtSet.foreach(entry => {
      mqProtSets += new MasterQuantProteinSet(
        proteinSet = entry._1,
        quantProteinSetMap = entry._2.toMap,
        selectionLevel = 2
      )
    })

    return (mqPeptides.toArray, mqProtSets.toArray)
  }

  private def foundProtSetOf(currentRSM: ResultSummary, pepSetId: Long): Long = {
    val pepSetIt = currentRSM.peptideSets.iterator

    while (pepSetIt.hasNext) {
      val nextPepSet = pepSetIt.next
      if (nextPepSet.strictSubsetIds != null && nextPepSet.strictSubsetIds.contains(pepSetId)) {
        if (nextPepSet.getProteinSetId != 0) {
          return nextPepSet.getProteinSetId
        } else { //Search parent of parent ... 
          return foundProtSetOf(currentRSM, nextPepSet.id)
        }
      }
    } //End go through pepSet
    0l
  } // End foundProtSetOf method definition

  private def createProtMatchesAccByPeptideSet(rsm: ResultSummary): Map[PeptideSet, Seq[Pair[Long, String]]] = {
    val rs = rsm.resultSet.get
    val protMById = rs.getProteinMatchById
    val result = scala.collection.mutable.Map[PeptideSet, Seq[Pair[Long, String]]]()
    val pepSetById = rsm.peptideSets.map(pepSet => pepSet.id -> pepSet).toMap

    rsm.proteinSets.filter(_.isValidated).foreach(protSet => {

      //Do SameSet PeptideSet
      val seqBuilder = Seq.newBuilder[Pair[Long, String]]
      protSet.peptideSet.proteinMatchIds.foreach(pmId => {
        val acc = protMById(pmId).accession
        seqBuilder += new Pair(pmId, acc)
      })
      result.put(protSet.peptideSet, seqBuilder.result)

      //Do Subset PeptideSet
      protSet.peptideSet.getStrictSubsetIds.foreach(psId => {
        val subPepSet = pepSetById(psId)
        val subSeqBuilder = Seq.newBuilder[Pair[Long, String]]
        subPepSet.proteinMatchIds.foreach(pmId => {
          val acc = protMById(pmId).accession
          subSeqBuilder += new Pair(pmId, acc)
        })
        result.put(subPepSet, subSeqBuilder.result)
      })

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
    val mergedPepMatchById = mergedRSM.resultSet.get.getPeptideMatchById

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
      mergedPepMatch.properties.map(props => msiMasterPepMatch.setSerializedProperties(ProfiJson.serialize(props)))

      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)

      val msiMasterPepMatchId = msiMasterPepMatch.getId

      // Map master peptide match id by in memory merged peptide match id
      masterQuantPepMatchIdByMergedPepMatchId(mergedPepMatch.id) = msiMasterPepMatchId

      //Retrieve ORM Peptide 
      val ormPep = this.msiEm.find(classOf[fr.proline.core.orm.msi.Peptide], peptideId)

      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(mergedPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(mergedPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(mergedPepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(mergedPepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(mergedPepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptide(ormPep)
      msiMasterPepInstance.setBestPeptideMatchId(msiMasterPepMatchId)
      msiMasterPepInstance.setResultSummary(msiQuantRSM)
      msiEm.persist(msiMasterPepInstance)

      val msiMasterPepInstanceId = msiMasterPepInstance.getId

      // Map the peptide instance by the peptide id
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
      // VDS TODO ? IF IDF hierarcghy <> from Quanti ??
      if (mergedPepMatch.getChildrenIds != null) {
        for (childPepMatchId <- mergedPepMatch.getChildrenIds) {

          val msiPepMatchRelationPK = new PeptideMatchRelationPK()
          msiPepMatchRelationPK.setChildPeptideMatchId(childPepMatchId)
          msiPepMatchRelationPK.setParentPeptideMatchId(msiMasterPepMatchId)
             
          val msiPepMatchRelation = new MsiPeptideMatchRelation()
          msiPepMatchRelation.setId(msiPepMatchRelationPK)
          msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)

          val childPM: fr.proline.core.orm.msi.PeptideMatch = msiEm.find(classOf[fr.proline.core.orm.msi.PeptideMatch], childPepMatchId)
          msiPepMatchRelation.setChildPeptideMatch(childPM)

          // FIXME: rename to setParentResultSet
          msiPepMatchRelation.setParentResultSetId(msiQuantRS)
                   
    	  msiEm.persist(msiPepMatchRelation)
          
        }
      }
    }

    // Retrieve some vars
    val mergedPeptideSets = mergedRSM.peptideSets
    this.logger.debug("number of grouped peptide sets: " + mergedPeptideSets.length + " sameset " + mergedPeptideSets.filter(!_.isSubset).length)
    val mergedProteinSets = mergedRSM.proteinSets
    this.logger.debug("number of grouped protein sets: " + mergedProteinSets.length)
    val mergedProtSetById = mergedRSM.getProteinSetById
    val mergedProtMatchById = mergedRSM.resultSet.get.getProteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets and protein sets...")
    for (mergedPeptideSet <- mergedPeptideSets) {

      val msiMasterProtMatchIdByMergedId = new HashMap[Long, Long]
      val mergedProtMatchIdByMasterId = new HashMap[Long, Long]

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
        if (mergedProtMatch.getProteinId > 0) msiMasterProtMatch.setBioSequenceId(mergedProtMatch.getProteinId)
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
        var mergedTypicalProtMatchId = mergedProteinSet.getRepresentativeProteinMatchId

        if (mergedTypicalProtMatchId <= 0) {
          val typicalProtMatchTmpId = mergedProteinSet.samesetProteinMatchIds.reduce { (a, b) =>
            if (mergedProtMatchById(a).coverage > mergedProtMatchById(b).coverage) a else b
          }         
        }
        val typicalProtMatchId = msiMasterProtMatchIdByMergedId(mergedTypicalProtMatchId)

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

        for (msiMasterProtMatch <- msiMasterProtMatches) {

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
          val mergedProtMatch = mergedProtMatchById(mergedProtMatchIdByMasterId(msiMasterProtMatchId))
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

