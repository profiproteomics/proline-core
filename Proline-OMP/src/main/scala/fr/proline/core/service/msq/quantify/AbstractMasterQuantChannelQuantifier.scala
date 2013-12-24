package fr.proline.core.service.msq.quantify

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import com.weiglewilczek.slf4s.Logging

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.ResultSummaryMerger
import fr.proline.core.algo.msi.scoring.{PepSetScoring,PeptideSetScoreUpdater}
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.msi.{
  MasterQuantPeptideIon => MsiMasterQuantPepIon,
  MasterQuantComponent => MsiMasterQuantComponent,
  ObjectTree => MsiObjectTree,
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
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.util.primitives._

import fr.proline.core.utils.ResidueUtils._

abstract class AbstractMasterQuantChannelQuantifier extends Logging {

  // Required fields
  val executionContext: IExecutionContext
  val udsMasterQuantChannel: MasterQuantitationChannel

  // Instantiated fields
  protected val udsEm = executionContext.getUDSDbConnectionContext.getEntityManager
  protected val msiDbCtx = executionContext.getMSIDbConnectionContext
  protected val msiEm = msiDbCtx.getEntityManager
  protected val psDbCtx = executionContext.getPSDbConnectionContext

  protected val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
  protected val quantChannelIds = udsQuantChannels.map { _.getId } toArray

  protected val rsmIds = udsQuantChannels.map { udsQuantChannel =>
    val qcId = udsQuantChannel.getId()
    val identRsmId = udsQuantChannel.getIdentResultSummaryId
    require(identRsmId != 0, "the quant_channel with id='" + qcId + "' is not assocciated with an identification result summary")

    identRsmId

  } toSeq

  require(rsmIds.length > 0, "result sets have to be validated first")

  protected val identRsIdByRsmId = {    
    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.RESULT_SET_ID) -> "WHERE "~ t.ID ~" IN("~ rsmIds.mkString(",") ~")"
      )
      msiEzDBC.select(sqlQuery) { r => toLong(r.nextAny) -> toLong(r.nextAny) } toMap
    })
  }

  protected val msiIdentResultSets = {    
    val identRsIds = identRsIdByRsmId.values.asJavaCollection
    msiEm.createQuery("FROM fr.proline.core.orm.msi.ResultSet WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.msi.ResultSet])
      .setParameter("ids", identRsIds).getResultList().toList
  }

  protected val identResultSummaries = {

    // Load the result summaries corresponding to the quant channels
    this.logger.info("loading result summaries...")

    // Instantiate a RSM provider
    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx)
    rsmProvider.getResultSummaries(rsmIds, true)
  }

  protected val mergedResultSummary = getMergedResultSummary(msiDbCtx : DatabaseConnectionContext)
   
  protected lazy val curSQLTime = new java.sql.Timestamp(new java.util.Date().getTime)

  private var _quantified = false

  /**
   * Main method of the quantifier.
   * This method wraps the quantifyMasterChannel method which has to be implemented
   * in each specific quantifier.
   */
  def quantify() = {

    // Check if the quantification has been already performed
    if (this._quantified) {
      throw new Exception("this fraction has been already quantified")
    }

    // Run the quantification process
    this.quantifyMasterChannel()

    // Close entity managers
    //this.msiEm.close()

    this._quantified = true
    this.logger.info("fraction has been quantified !")
  }

  // Define the interface required to implement the trait
  protected def quantifyMasterChannel(): Unit
  protected def buildMasterQuantPeptideObjectTree(mqPep: MasterQuantPeptide): MsiObjectTree
  protected def buildMasterQuantPeptideIonObjectTree(mqPepIon: MasterQuantPeptideIon): MsiObjectTree
  protected def getMergedResultSummary(msiDbCtx : DatabaseConnectionContext): ResultSummary
  //protected def buildMasterQuantProteinSetObjectTree( mqProtSet: MasterQuantProteinSet ): MsiObjectTree

  // TODO: load the schema
  protected def loadOrCreateObjectTreeSchema(schemaName: String): fr.proline.core.orm.msi.ObjectTreeSchema = {
    var objTreeSchema : fr.proline.core.orm.msi.ObjectTreeSchema=  msiEm.find(classOf[fr.proline.core.orm.msi.ObjectTreeSchema], schemaName)
    if(objTreeSchema == null) {
	    objTreeSchema = new fr.proline.core.orm.msi.ObjectTreeSchema()
	    objTreeSchema.setName(schemaName)
	    objTreeSchema.setType("JSON")
	    objTreeSchema.setVersion("0.1")
	    objTreeSchema.setSchema("")
    }
    objTreeSchema
  }

  protected def storeMsiQuantResultSet(msiIdentResultSets: List[MsiResultSet]): MsiResultSet = {

    // TODO: provide the RS name in the parameters
    val msiQuantResultSet = new MsiResultSet()
    msiQuantResultSet.setName("")
    msiQuantResultSet.setType(MsiResultSet.Type.QUANTITATION)
    msiQuantResultSet.setModificationTimestamp(curSQLTime)

    // Link this quantitative result set to the ident target MSI searches
    /*val rdbIdentMsiSearches = this.msiIdentResultSets.map { _.getMsiSearch() }
    for( rdbIdentMsiSearch <- rdbIdentMsiSearches ) {
      setMsiSearch
      new Pairs::Msi::RDBO::ResultSetMsiSearchMap(
                              result_set_id = quantRsId,
                              msi_search_id = rdbIdentMsiSearch.id,
                              db = msiRdb
                            ).save
    }*/

    // Link this quantitative result set to the identification result sets
    msiQuantResultSet.setChildren(msiIdentResultSets.toSet[MsiResultSet])
    msiEm.persist(msiQuantResultSet)

    msiQuantResultSet
  }

  protected def storeMsiQuantResultSummary(msiQuantResultSet: MsiResultSet): MsiResultSummary = {
    val msiQuantRSM = new MsiResultSummary()
    msiQuantRSM.setModificationTimestamp(curSQLTime)
    msiQuantRSM.setResultSet(msiQuantResultSet)
    msiEm.persist(msiQuantRSM)

    msiQuantRSM
  }

  // Define some vars
  protected val masterPepInstByPepId = new HashMap[Long, PeptideInstance]
  protected val msiMasterPepInstById = new HashMap[Long, MsiPeptideInstance]
  protected val msiMasterProtSetById = new HashMap[Long, MsiProteinSet]

  protected def storeMasterQuantResultSummary(masterRSM: ResultSummary,
                                              msiQuantRSM: MsiResultSummary,
                                              msiQuantRS: MsiResultSet) {

    // Retrieve result summary and result set ids
    val quantRsmId = msiQuantRSM.getId
    val quantRsId = msiQuantRS.getId

    // Retrieve peptide instances of the merged result summary
    val masterPepInstances = masterRSM.peptideInstances
    val masterPepMatchById = masterRSM.resultSet.get.peptideMatchById
    
    // TODO: load scoring from MSIdb
    val msiScoring = new MsiScoring()
    msiScoring.setId(4)

    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info("storing master quant peptide instances...")

    // Define some vars
    val masterQuantPepMatchIdByTmpPepMatchId = new HashMap[Long, Long]

    for (masterPepInstance <- masterPepInstances) {

      val peptideId = masterPepInstance.peptide.id
      val masterPepInstPepMatchIds = masterPepInstance.getPeptideMatchIds
      assert(masterPepInstPepMatchIds.length == 1, "peptide matches have not been correctly merged")

      // TODO: Retrieve the best peptide match
      //val identParentPepMatches = masterPepInstPepMatchIds.map { masterPepMatchById(_) }
      //val bestParentPepMatch = identParentPepMatches.reduce { (a,b) => if( a.score > b.score ) a else b } 
      val bestParentPepMatch = masterPepMatchById(masterPepInstPepMatchIds(0))

      // Create a quant peptide match which correspond to the best peptide match of this peptide instance
      val msiMasterPepMatch = new MsiPeptideMatch()
      msiMasterPepMatch.setCharge(bestParentPepMatch.msQuery.charge)
      msiMasterPepMatch.setExperimentalMoz(bestParentPepMatch.msQuery.moz)
      msiMasterPepMatch.setScore(bestParentPepMatch.score)
      msiMasterPepMatch.setRank(bestParentPepMatch.rank)
      msiMasterPepMatch.setDeltaMoz(bestParentPepMatch.deltaMoz)
      msiMasterPepMatch.setMissedCleavage(bestParentPepMatch.missedCleavage)
      msiMasterPepMatch.setFragmentMatchCount(bestParentPepMatch.fragmentMatchesCount)
      msiMasterPepMatch.setIsDecoy(false)
      msiMasterPepMatch.setPeptideId(bestParentPepMatch.peptide.id)

      // FIXME: retrieve the right scoring_id
      msiMasterPepMatch.setScoringId(1)

      // FIXME: change the ORM to allow these mappings
      //msiMasterPepMatch.setBestPeptideMatchId(bestPepMatch.id) 
      //msiMasterPepMatch.setMsQueryId(bestPepMatch.msQueryId)

      // FIXME: remove this mapping when the ORM is updated
      val msiMSQFake = new fr.proline.core.orm.msi.MsQuery
      msiMSQFake.setId(bestParentPepMatch.msQuery.id)
      msiMasterPepMatch.setMsQuery(msiMSQFake)

      msiMasterPepMatch.setResultSet(msiQuantRS)
      bestParentPepMatch.properties.map( props => msiMasterPepMatch.setSerializedProperties(ProfiJson.serialize(props)) )

      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)

      val masterPepMatchId = msiMasterPepMatch.getId
      
      // Map master peptide match id by in memory merged peptide match id
      masterQuantPepMatchIdByTmpPepMatchId(bestParentPepMatch.id) = masterPepMatchId

      // Update the best parent peptide match id
      bestParentPepMatch.id = masterPepMatchId
      
      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(masterPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(masterPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(masterPepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(masterPepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(masterPepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptideId(peptideId)
      msiMasterPepInstance.setBestPeptideMatchId(masterPepMatchId)
      msiMasterPepInstance.setResultSummary(msiQuantRSM)
      msiEm.persist(msiMasterPepInstance)

      val masterPepInstanceId = msiMasterPepInstance.getId

      // Update the peptide instance id and some FKs
      masterPepInstance.id = masterPepInstanceId
      masterPepInstance.peptideMatchIds = Array(masterPepMatchId)
      masterPepInstance.bestPeptideMatchId = masterPepMatchId
      
      // Map the peptide instance by the peptide id
      masterPepInstByPepId(peptideId) = masterPepInstance
      // TODO: remove this mapping when ORM is updated
      msiMasterPepInstById(masterPepInstanceId) = msiMasterPepInstance

      // Link the best master peptide match to the quant peptide instance
      val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
      msiPepInstMatchPK.setPeptideInstanceId(masterPepInstanceId)
      msiPepInstMatchPK.setPeptideMatchId(masterPepMatchId)

      val msiPepInstMatch = new MsiPepInstPepMatchMap()
      msiPepInstMatch.setId(msiPepInstMatchPK)
      msiPepInstMatch.setPeptideInstance(msiMasterPepInstance)
      msiPepInstMatch.setPeptideMatch(msiMasterPepMatch)      
      msiPepInstMatch.setResultSummary(msiQuantRSM)

      //msiMasterPepInstance.setPeptidesMatches(Set(msiMasterPepMatch))
      msiEm.persist(msiPepInstMatch)

      // Map this quant peptide match to identified child peptide matches
      //FIXME No children specified Yet !? 
      if(bestParentPepMatch.getChildrenIds !=null){
	      for (childPepMatchId <- bestParentPepMatch.getChildrenIds) {
	
	        val msiPepMatchRelation = new MsiPeptideMatchRelation()
	        msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
	        // FIXME: allows to set the child peptide match id
	        msiPepMatchRelation.setChildPeptideMatch(msiMasterPepMatch) // childPepMatchId
	        // FIXME: rename to setParentResultSet
	        msiPepMatchRelation.setParentResultSetId(msiQuantRS)
	      }
      }
    }

    // Retrieve some vars
    val masterPeptideSets = masterRSM.peptideSets
    this.logger.info("number of grouped peptide sets: " + masterPeptideSets.length)
    val masterProteinSets = masterRSM.proteinSets
    this.logger.info("number of grouped protein sets: " + masterProteinSets.length)
    val masterProtSetByTmpId = masterRSM.proteinSetById
    val masterProtMatchByTmpId = masterRSM.resultSet.get.proteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets...")
    for (masterPeptideSet <- masterPeptideSets) {
      
      val masterProtMatchIdByTmpId = new HashMap[Long,Long]
      val masterProtMatchTmpIdById = new HashMap[Long,Long]
      
      // Store master protein matches
      val msiMasterProtMatches = masterPeptideSet.proteinMatchIds.map { protMatchId =>

        val masterProtMatch = masterProtMatchByTmpId(protMatchId)

        val msiMasterProtMatch = new MsiProteinMatch()
        msiMasterProtMatch.setAccession(masterProtMatch.accession)
        msiMasterProtMatch.setDescription(masterProtMatch.description)
        msiMasterProtMatch.setGeneName(masterProtMatch.geneName)
        msiMasterProtMatch.setScore(masterProtMatch.score)
        msiMasterProtMatch.setCoverage(masterProtMatch.coverage)
        msiMasterProtMatch.setPeptideCount(masterProtMatch.sequenceMatches.length)
        msiMasterProtMatch.setPeptideMatchCount(masterProtMatch.peptideMatchesCount)
        msiMasterProtMatch.setIsDecoy(masterProtMatch.isDecoy)
        msiMasterProtMatch.setIsLastBioSequence(masterProtMatch.isLastBioSequence)
        msiMasterProtMatch.setTaxonId(masterProtMatch.taxonId)
        if( masterProtMatch.getProteinId > 0 ) msiMasterProtMatch.setBioSequenceId(masterProtMatch.getProteinId)
        // FIXME: retrieve the right scoring id
        msiMasterProtMatch.setScoringId(3)
        msiMasterProtMatch.setResultSet(msiQuantRS)
        msiEm.persist(msiMasterProtMatch)

        val masterProtMatchId = msiMasterProtMatch.getId
        
        // Map new protein match id by TMP id
        masterProtMatchIdByTmpId += masterProtMatch.id -> masterProtMatchId
        // Map protein match TMP id by the new id
        masterProtMatchTmpIdById += masterProtMatchId -> masterProtMatch.id

        // Update master protein match id
        masterProtMatch.id = masterProtMatchId

        // TODO: map protein_match to seq_databases
        
        msiMasterProtMatch
      }
      
      // Map MSI protein matches by their id
      //val msiMasterProtMatchById = Map() ++ msiMasterProtMatches.map( pm => pm.getId -> pm )
      
      // Update master peptide set protein match ids
      masterPeptideSet.proteinMatchIds = masterPeptideSet.proteinMatchIds.map(masterProtMatchIdByTmpId(_))

      // TODO: find what to do with subsets
      if (masterPeptideSet.isSubset == false) {

        val masterProteinSetOpt = masterProtSetByTmpId.get(masterPeptideSet.getProteinSetId)
        assert(masterProteinSetOpt.isDefined, "missing protein set with id=" + masterPeptideSet.getProteinSetId)
        
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
        val masterProteinSet = masterProteinSetOpt.get
        var typicalProtMatchId = masterProteinSet.getTypicalProteinMatchId
        
        if (typicalProtMatchId <= 0) {
          val typicalProtMatchTmpId = masterProteinSet.proteinMatchIds.reduce { (a, b) =>
            if (masterProtMatchByTmpId(a).coverage > masterProtMatchByTmpId(b).coverage) a else b
          }          
          typicalProtMatchId = masterProtMatchIdByTmpId(typicalProtMatchTmpId)
        }
        
        // Update master protein set protein match ids        
        masterProteinSet.proteinMatchIds = masterProteinSet.peptideSet.proteinMatchIds

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(true)
        msiMasterProteinSet.setSelectionLevel(2)
        msiMasterProteinSet.setProteinMatchId(typicalProtMatchId)
        msiMasterProteinSet.setResultSummary(msiQuantRSM)
        msiEm.persist(msiMasterProteinSet)

        val masterProteinSetId = msiMasterProteinSet.getId
        msiMasterProtSetById(masterProteinSetId) = msiMasterProteinSet

        // Update the master protein set id
        masterProteinSet.id = masterProteinSetId

        // Retrieve peptide set items
        val samesetItems = masterPeptideSet.items

        // Store master peptide set
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset(false)
        msiMasterPeptideSet.setPeptideCount(samesetItems.length)
        msiMasterPeptideSet.setPeptideMatchCount(masterPeptideSet.peptideMatchesCount)
        msiMasterPeptideSet.setProteinSet(msiMasterProteinSet)
        // FIXME: retrieve the right scoring id
        msiMasterPeptideSet.setScore(masterPeptideSet.score)
        msiMasterPeptideSet.setScoring(msiScoring)
        msiMasterPeptideSet.setResultSummaryId(quantRsmId)
        msiEm.persist(msiMasterPeptideSet)

        val masterPeptideSetId = msiMasterPeptideSet.getId

        // Update the master peptide set id
        masterPeptideSet.id = masterPeptideSetId

        // Link master peptide set to master peptide instances
        for (samesetItem <- samesetItems) {
          val tmpPepInstance = samesetItem.peptideInstance
          val msiMasterPepInst = msiMasterPepInstById(tmpPepInstance.id)

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
          
          val masterProtMatchId = msiMasterProtMatch.getId
          
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
          msiPepSetProtMatchMapPK.setProteinMatchId(masterProtMatchId)
          
          val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          msiPepSetProtMatchMap.setId(msiPepSetProtMatchMapPK)
          msiPepSetProtMatchMap.setPeptideSet(msiMasterPeptideSet)
          msiPepSetProtMatchMap.setProteinMatch(msiMasterProtMatch)
          msiPepSetProtMatchMap.setResultSummary(msiQuantRSM)
          msiEm.persist(msiPepSetProtMatchMap)

          // Link master protein match to master peptide matches using master sequence matches
          val masterProtMatch = masterProtMatchByTmpId( masterProtMatchTmpIdById(masterProtMatchId) )
          val seqMatches = masterProtMatch.sequenceMatches
          val mappedMasterPepMatchesIdSet = new HashSet[Long]

          for (seqMatch <- seqMatches) {

            val bestPepMatchId = seqMatch.getBestPeptideMatchId
            if (masterQuantPepMatchIdByTmpPepMatchId.contains(bestPepMatchId)) {
              val masterPepMatchId = masterQuantPepMatchIdByTmpPepMatchId(bestPepMatchId)

              // Update seqMatch best peptide match id
              seqMatch.bestPeptideMatchId = masterPepMatchId

              if (mappedMasterPepMatchesIdSet.contains(masterPepMatchId) == false) {
                mappedMasterPepMatchesIdSet(masterPepMatchId) = true

                val msiMasterSeqMatchPK = new fr.proline.core.orm.msi.SequenceMatchPK()
                msiMasterSeqMatchPK.setProteinMatchId(masterProtMatchId)
                msiMasterSeqMatchPK.setPeptideId(seqMatch.getPeptideId)
                msiMasterSeqMatchPK.setStart(seqMatch.start)
                msiMasterSeqMatchPK.setStop(seqMatch.end)

                val msiMasterSeqMatch = new MsiSequenceMatch()
                msiMasterSeqMatch.setId(msiMasterSeqMatchPK)
                msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(seqMatch.residueBefore))
                msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(seqMatch.residueAfter))
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

  protected def storeMasterQuantPeptide(
    mqPep: MasterQuantPeptide,
    msiRSM: MsiResultSummary,
    msiMasterPepInstAsOpt: Option[MsiPeptideInstance]) = {

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

    // If this master quant peptide has been identified by a master peptide instance
    if (msiMasterPepInstAsOpt.isDefined) {
      // Link master peptide instance to the corresponding master quant component
      msiMasterPepInstAsOpt.get.setMasterQuantComponentId(msiMQC.getId)
      this.msiEm.persist(msiMasterPepInstAsOpt.get)
    }

    for (mqPepIon <- mqPep.masterQuantPeptideIons) {
      this.storeMasterQuantPeptideIon(mqPepIon, mqPep, msiRSM)
    }

  }

  protected def storeMasterQuantPeptideIon(
    mqPepIon: MasterQuantPeptideIon,
    mqPep: MasterQuantPeptide,
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

    if (mqPepIon.properties.isDefined) msiMQPepIon.setSerializedProperties(ProfiJson.serialize(mqPepIon.properties))
    if (mqPep.peptideInstance.isDefined) {
      msiMQPepIon.setPeptideInstanceId(mqPep.peptideInstance.get.id)
      msiMQPepIon.setPeptideId(mqPep.getPeptideId.get)
    }
    if (mqPepIon.lcmsMasterFeatureId.isDefined) msiMQPepIon.setLcmsMasterFeatureId(mqPepIon.lcmsMasterFeatureId.get)
    if (mqPepIon.bestPeptideMatchId.isDefined) msiMQPepIon.setBestPeptideMatchId(mqPepIon.bestPeptideMatchId.get)
    if (mqPepIon.unmodifiedPeptideIonId.isDefined) msiMQPepIon.setUnmodifiedPeptideIonId(mqPepIon.unmodifiedPeptideIonId.get)

    this.msiEm.persist(msiMQPepIon)
    
    // Update master quant peptide ion id
    mqPepIon.id = msiMQPepIon.getId
  }

  protected def storeMasterQuantProteinSet(
    mqProtSet: MasterQuantProteinSet,
    msiMasterProtSet: MsiProteinSet,
    msiRSM: MsiResultSummary) = {

    val msiMQCObjectTree = this.buildMasterQuantProteinSetObjectTree(mqProtSet)
    this.msiEm.persist(msiMQCObjectTree)

    // Store master quant component
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqProtSet.selectionLevel)
    if (mqProtSet.properties.isDefined) msiMQC.setSerializedProperties(ProfiJson.serialize(mqProtSet.properties))
    msiMQC.setObjectTreeId(msiMQCObjectTree.getId)
    msiMQC.setSchemaName(msiMQCObjectTree.getSchema.getName)
    msiMQC.setResultSummary(msiRSM)
    this.msiEm.persist(msiMQC)

    // Link master quant protein set to the corresponding master quant component
    msiMasterProtSet.setMasterQuantComponentId(msiMQC.getId)
  }

  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val quantProteinSetsSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.quant_protein_sets")
  }

  protected def buildMasterQuantProteinSetObjectTree(mqProtSet: MasterQuantProteinSet): MsiObjectTree = {

    val quantProtSetMap = mqProtSet.quantProteinSetMap
    val quantProtSets = this.quantChannelIds.map { quantProtSetMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQProtSetObjectTree = new MsiObjectTree()
    msiMQProtSetObjectTree.setSchema(quantProteinSetsSchema)
    //msiMQProtSetObjectTree.setClobData(generate[Array[QuantProteinSet]](quantProtSets))
    msiMQProtSetObjectTree.setClobData(ProfiJson.serialize(quantProtSets))

    msiMQProtSetObjectTree
  }
  
  protected def createMergedResultSummary(msiDbCtx : DatabaseConnectionContext) : ResultSummary = {
		  val msiDbHelper = new MsiDbHelper(msiDbCtx)
		  val tmpIdentProteinIdSet = new collection.mutable.HashSet[Long]()
		  
		  for (identRSM <- identResultSummaries) {
			  // 	Retrieve protein ids
			  val rs = identRSM.resultSet.get
			  rs.proteinMatches.foreach { p => if (p.getProteinId != 0) tmpIdentProteinIdSet += p.getProteinId }
		  }

		  // Retrieve sequence length mapped by the corresponding protein id
		  val seqLengthByProtId = msiDbHelper.getSeqLengthByBioSeqId(tmpIdentProteinIdSet.toList)
   
		  // FIXME: check that all peptide sets have the same scoring
		  val pepSetScoring = PepSetScoring.withName( this.identResultSummaries(0).peptideSets(0).scoreType )
		  val pepSetScoreUpdater = PeptideSetScoreUpdater(pepSetScoring)
    
		  // Merge result summaries
		  val resultSummaryMerger = new ResultSummaryMerger(pepSetScoreUpdater)
		  this.logger.info("merging result summaries...")
		  resultSummaryMerger.mergeResultSummaries(identResultSummaries, seqLengthByProtId)

   }

}