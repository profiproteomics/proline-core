package fr.proline.core.service.msq.quantify

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.primitives.toLong
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.ResultSummaryAdder
import fr.proline.core.algo.msi.scoring.PepSetScoring
import fr.proline.core.algo.msi.scoring.PeptideSetScoreUpdater
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder.any2ClauseAdd
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq.MasterQuantPeptide
import fr.proline.core.om.model.msq.MasterQuantPeptideIon
import fr.proline.core.om.model.msq.MasterQuantProteinSet
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.msi.{MasterQuantComponent => MsiMasterQuantComponent}
import fr.proline.core.orm.msi.{MasterQuantPeptideIon => MsiMasterQuantPepIon}
import fr.proline.core.orm.msi.{ObjectTree => MsiObjectTree}
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.Peptide
import fr.proline.core.orm.msi.{PeptideInstance => MsiPeptideInstance}
import fr.proline.core.orm.msi.{PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap}
import fr.proline.core.orm.msi.{PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK}
import fr.proline.core.orm.msi.{PeptideMatch => MsiPeptideMatch}
import fr.proline.core.orm.msi.{PeptideMatchRelation => MsiPeptideMatchRelation}
import fr.proline.core.orm.msi.{PeptideMatchRelationPK => MsiPeptideMatchRelationPK}
import fr.proline.core.orm.msi.{PeptideSet => MsiPeptideSet}
import fr.proline.core.orm.msi.{PeptideSetPeptideInstanceItem => MsiPeptideSetItem}
import fr.proline.core.orm.msi.{PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK}
import fr.proline.core.orm.msi.{PeptideSetProteinMatchMap => MsiPepSetProtMatchMap}
import fr.proline.core.orm.msi.{PeptideSetProteinMatchMapPK => MsiPepSetProtMatchMapPK}
import fr.proline.core.orm.msi.{ProteinMatch => MsiProteinMatch}
import fr.proline.core.orm.msi.{ProteinSet => MsiProteinSet}
import fr.proline.core.orm.msi.{ProteinSetProteinMatchItem => MsiProtSetProtMatchItem}
import fr.proline.core.orm.msi.{ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK}
import fr.proline.core.orm.msi.ResultSet
import fr.proline.core.orm.msi.{ResultSet => MsiResultSet}
import fr.proline.core.orm.msi.{ResultSummary => MsiResultSummary}
import fr.proline.core.orm.msi.{Scoring => MsiScoring}
import fr.proline.core.orm.msi.{SequenceMatch => MsiSequenceMatch}
import fr.proline.core.orm.msi.{PeptideReadablePtmString => MsiPeptideReadablePtmString}
import fr.proline.core.orm.msi.{PeptideReadablePtmStringPK => MsiPeptideReadablePtmStringPK}

import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.util.ResidueUtils.scalaCharToCharacter

abstract class AbstractMasterQuantChannelQuantifier extends LazyLogging {

  // Required fields
  val executionContext: IExecutionContext
  val udsMasterQuantChannel: MasterQuantitationChannel

  // Instantiated fields
  protected val udsDbCtx = executionContext.getUDSDbConnectionContext
  protected val udsEm = udsDbCtx.getEntityManager
  protected val msiDbCtx = executionContext.getMSIDbConnectionContext
  protected val msiEm = msiDbCtx.getEntityManager
  protected val psDbCtx = executionContext.getPSDbConnectionContext

  protected val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
  protected lazy val quantChannelIds = udsQuantChannels.map { _.getId } toArray
  
  protected val identRSMIds = udsQuantChannels.map { udsQuantChannel =>
    val qcId = udsQuantChannel.getId()
    val identRsmId = udsQuantChannel.getIdentResultSummaryId
    require(identRsmId != 0, "the quant_channel with id='" + qcId + "' is not assocciated with an identification result summary")

    identRsmId

  } toSeq

  require(identRSMIds.length > 0, "result sets have to be validated first")

  protected val identRsIdByRsmId = {    
    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.RESULT_SET_ID) -> "WHERE "~ t.ID ~" IN("~ identRSMIds.mkString(",") ~")"
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
    rsmProvider.getResultSummaries(identRSMIds, true)
  }

  protected lazy val mergedResultSummary = getMergedResultSummary(msiDbCtx : DatabaseConnectionContext)
   
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
  def getResultAsJSON(): String
  
  
  //protected def buildMasterQuantProteinSetObjectTree( mqProtSet: MasterQuantProteinSet ): MsiObjectTree


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
  protected val msiMasterPepInstById = new HashMap[Long, MsiPeptideInstance]
  protected val msiMasterProtSetById = new HashMap[Long, MsiProteinSet]
  protected val msiMasterPepInstByMergedPepInstId = new HashMap[Long, MsiPeptideInstance]
  protected val msiMasterProtSetByMergedProtSetId = new HashMap[Long, MsiProteinSet]
  
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
      msiMasterPepMatch.setCDPrettyRank(mergedPepMatch.cdPrettyRank)
      msiMasterPepMatch.setSDPrettyRank(mergedPepMatch.sdPrettyRank)
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
      msiMasterPepInstByMergedPepInstId += (mergedPepInstance.id -> msiMasterPepInstance)
      msiMasterPepInstById += (msiMasterPepInstanceId -> msiMasterPepInstance) 
           
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
      
      // PeptideReadablePTMString
      if (mergedPepInstance.peptide.readablePtmString != null && !mergedPepInstance.peptide.readablePtmString.isEmpty()){
        val msiPeptideReadablePtmStringPK = new MsiPeptideReadablePtmStringPK()
        msiPeptideReadablePtmStringPK.setPeptideId(mergedPepMatch.peptide.id)
        msiPeptideReadablePtmStringPK.setResultSetId(msiQuantRS.getId())

        val msiPeptideReadablePtmString = new MsiPeptideReadablePtmString()
        msiPeptideReadablePtmString.setId(msiPeptideReadablePtmStringPK)
        msiPeptideReadablePtmString.setResultSet(msiQuantRS)
        msiPeptideReadablePtmString.setPeptide(ormPep)
        msiPeptideReadablePtmString.setReadablePtmString(mergedPepInstance.peptide.readablePtmString)

        // Save PeptideReadablePTMString
        msiEm.persist(msiPeptideReadablePtmString)
      }

      // Map this quant peptide match to identified child peptide matches
      if (mergedPepMatch.getChildrenIds != null) {
        for (childPepMatchId <- mergedPepMatch.getChildrenIds) {

          val msiPepMatchRelationPK = new MsiPeptideMatchRelationPK()
          msiPepMatchRelationPK.setChildPeptideMatchId(childPepMatchId)
          msiPepMatchRelationPK.setParentPeptideMatchId(msiMasterPepMatchId)

          val msiPepMatchRelation = new MsiPeptideMatchRelation()
          msiPepMatchRelation.setId(msiPepMatchRelationPK)
          msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)

          val childPM: MsiPeptideMatch = msiEm.find(classOf[MsiPeptideMatch], childPepMatchId)
          msiPepMatchRelation.setChildPeptideMatch(childPM)

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
           mergedTypicalProtMatchId = mergedProteinSet.samesetProteinMatchIds.reduce { (a, b) =>
            if (mergedProtMatchById(a).coverage > mergedProtMatchById(b).coverage) a else b
          }         
        }
        val typicalProtMatchId = msiMasterProtMatchIdByMergedId(mergedTypicalProtMatchId)

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(mergedProteinSet.isValidated)
        msiMasterProteinSet.setIsDecoy(mergedProteinSet.isDecoy)
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
        msiMasterPeptideSet.setSequenceCount(mergedPeptideSet.sequencesCount)
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
                msiMasterSeqMatch.setResidueAfter(scalaCharToCharacter(mergedSeqMatch.residueAfter))
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

  
  
  
  protected def storeMasterQuantResultSummary(
    masterRSM: ResultSummary,
    msiQuantRSM: MsiResultSummary,
    msiQuantRS: MsiResultSet
  ) {

    // Retrieve result summary and result set ids
    val quantRsmId = msiQuantRSM.getId
    val quantRsId = msiQuantRS.getId

    // Retrieve peptide instances of the merged result summary
    val masterPepInstances = masterRSM.peptideInstances
    val masterPepMatchById = masterRSM.resultSet.get.getPeptideMatchById
    
    // TODO: load scoring from MSIdb
    val msiScoring = new MsiScoring()
    msiScoring.setId(4)

    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info("storing master quant peptide instances... ("+masterPepInstances.size+")")

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
      msiMasterPepMatch.setCDPrettyRank(bestParentPepMatch.cdPrettyRank)
      msiMasterPepMatch.setSDPrettyRank(bestParentPepMatch.sdPrettyRank)
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
      bestParentPepMatch.id = masterPepMatchId //VDS FIXME !!! CHANGE MERGE RSM !!!! 
      
      //Retrieve ORM Peptide 
      val msiPep = this.msiEm.find(classOf[fr.proline.core.orm.msi.Peptide],peptideId)
      
      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(masterPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(masterPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(masterPepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(masterPepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(masterPepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptide(msiPep)
      msiMasterPepInstance.setBestPeptideMatchId(masterPepMatchId)
      msiMasterPepInstance.setResultSummary(msiQuantRSM)
      msiEm.persist(msiMasterPepInstance)

      val masterPepInstanceId = msiMasterPepInstance.getId

      //VDS FIXME !!! CHANGE MERGE RSM !!!! 
      // Update the peptide instance id and some FKs
      masterPepInstance.id = masterPepInstanceId
      masterPepInstance.peptideMatchIds = Array(masterPepMatchId)
      masterPepInstance.bestPeptideMatchId = masterPepMatchId
      
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
      
      // PeptideReadablePTMString
      if (masterPepInstance.peptide.readablePtmString != null && !masterPepInstance.peptide.readablePtmString.isEmpty()){
        val msiPeptideReadablePtmStringPK = new MsiPeptideReadablePtmStringPK()
        msiPeptideReadablePtmStringPK.setPeptideId(masterPepInstance.peptide.id)
        msiPeptideReadablePtmStringPK.setResultSetId(msiQuantRS.getId())

        val msiPeptideReadablePtmString = new MsiPeptideReadablePtmString()
        msiPeptideReadablePtmString.setId(msiPeptideReadablePtmStringPK)
        msiPeptideReadablePtmString.setResultSet(msiQuantRS)
        msiPeptideReadablePtmString.setPeptide(msiPep)
        msiPeptideReadablePtmString.setReadablePtmString(masterPepInstance.peptide.readablePtmString)

        // Save PeptideReadablePTMString
        msiEm.persist(msiPeptideReadablePtmString)
      }

      // Map this quant peptide match to identified child peptide matches    
      if(bestParentPepMatch.getChildrenIds !=null){
	      for (childPepMatchId <- bestParentPepMatch.getChildrenIds) {
             val msiPepMatchRelationPK = new MsiPeptideMatchRelationPK()
             msiPepMatchRelationPK.setChildPeptideMatchId(childPepMatchId)
             msiPepMatchRelationPK.setParentPeptideMatchId(masterPepMatchId)                 

	    	  val childPM=  msiEm.find(classOf[MsiPeptideMatch], childPepMatchId)
    		  val msiPepMatchRelation = new MsiPeptideMatchRelation()
              msiPepMatchRelation.setId(msiPepMatchRelationPK)
	    	  msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
	    	  msiPepMatchRelation.setChildPeptideMatch(childPM) 
	    	  msiPepMatchRelation.setParentResultSetId(msiQuantRS)
	    	  msiEm.persist(msiPepMatchRelation)
 	      }
      }
    }

    // Retrieve some vars
    val masterPeptideSets = masterRSM.peptideSets
    this.logger.info("number of grouped peptide sets: " + masterPeptideSets.length)
    val masterProteinSets = masterRSM.proteinSets
    this.logger.info("number of grouped protein sets: " + masterProteinSets.length)
    val masterProtSetByTmpId = masterRSM.getProteinSetById
    val masterProtMatchByTmpId = masterRSM.resultSet.get.getProteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets and protein sets...")
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

        //VDS FIXME !!! CHANGE MERGE RSM !!!! 
        // Update master protein match id
        masterProtMatch.id = masterProtMatchId

        // TODO: map protein_match to seq_databases
        
        msiMasterProtMatch
      }
      
      // Map MSI protein matches by their id
      //val msiMasterProtMatchById = Map() ++ msiMasterProtMatches.map( pm => pm.getId -> pm )
      
      // Update master peptide set protein match ids
      //VDS FIXME !!! CHANGE MERGE RSM !!!! 
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
        var reprProtMatchId = masterProteinSet.getRepresentativeProteinMatchId
        
        if (reprProtMatchId <= 0) {
          val reprProtMatchTmpId = masterProteinSet.samesetProteinMatchIds.reduce { (a, b) =>
            if (masterProtMatchByTmpId(a).coverage > masterProtMatchByTmpId(b).coverage) a else b
          }          
          reprProtMatchId = masterProtMatchIdByTmpId(reprProtMatchTmpId)
        }
        
        // Update master protein set protein match ids        
        masterProteinSet.samesetProteinMatchIds = masterProteinSet.peptideSet.proteinMatchIds

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(masterProteinSet.isValidated)
        msiMasterProteinSet.setSelectionLevel(2)
        msiMasterProteinSet.setProteinMatchId(reprProtMatchId)
        msiMasterProteinSet.setResultSummary(msiQuantRSM)
        msiEm.persist(msiMasterProteinSet)

        val masterProteinSetId = msiMasterProteinSet.getId
        msiMasterProtSetById(masterProteinSetId) = msiMasterProteinSet

        // Update the master protein set id
        //VDS FIXME !!! CHANGE MERGE RSM !!!! 
        masterProteinSet.id = masterProteinSetId

        // Retrieve peptide set items
        val samesetItems = masterPeptideSet.items

        // Store master peptide set
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset(false)
        msiMasterPeptideSet.setPeptideCount(samesetItems.length)
        msiMasterPeptideSet.setPeptideMatchCount(masterPeptideSet.peptideMatchesCount)
        msiMasterPeptideSet.setSequenceCount(masterPeptideSet.sequencesCount)
        msiMasterPeptideSet.setProteinSet(msiMasterProteinSet)
        // FIXME: retrieve the right scoring id
        msiMasterPeptideSet.setScore(masterPeptideSet.score)
        msiMasterPeptideSet.setScoring(msiScoring)
        msiMasterPeptideSet.setResultSummaryId(quantRsmId)
        msiEm.persist(msiMasterPeptideSet)

        val masterPeptideSetId = msiMasterPeptideSet.getId

        //VDS FIXME !!! CHANGE MERGE RSM !!!! 
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
              //VDS FIXME !!! CHANGE MERGE RSM !!!! 
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
                msiMasterSeqMatch.setResidueAfter(scalaCharToCharacter(seqMatch.residueAfter))
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
    msiMasterPepInstAsOpt: Option[MsiPeptideInstance]
  ): MsiMasterQuantComponent = {

    val msiMQCObjectTree = this.buildMasterQuantPeptideObjectTree(mqPep)
    this.msiEm.persist(msiMQCObjectTree)

    // Store master quant component for this master quant peptide
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqPep.selectionLevel)
    if( mqPep.properties.isDefined ) msiMQC.setSerializedProperties(ProfiJson.serialize(mqPep.properties.get) )
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

    msiMQC
  }

  protected def storeMasterQuantPeptideIon(
    mqPepIon: MasterQuantPeptideIon,
    mqPep: MasterQuantPeptide,
    msiRSM: MsiResultSummary
  ): MsiMasterQuantPepIon = {

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

    if (mqPepIon.properties.isDefined) msiMQPepIon.setSerializedProperties(ProfiJson.serialize(mqPepIon.properties.get))
    if (mqPep.peptideInstance.isDefined) {
      val msiPepInst = this.msiEm.find(classOf[MsiPeptideInstance],mqPep.peptideInstance.get.id)
      msiMQPepIon.setPeptideInstance(msiPepInst)
      msiMQPepIon.setPeptideId(mqPep.getPeptideId.get)
    }
    if (mqPepIon.lcmsMasterFeatureId.isDefined) msiMQPepIon.setLcmsMasterFeatureId(mqPepIon.lcmsMasterFeatureId.get)
    if (mqPepIon.bestPeptideMatchId.isDefined) msiMQPepIon.setBestPeptideMatchId(mqPepIon.bestPeptideMatchId.get)
    if (mqPepIon.unmodifiedPeptideIonId.isDefined) msiMQPepIon.setUnmodifiedPeptideIonId(mqPepIon.unmodifiedPeptideIonId.get)

    this.msiEm.persist(msiMQPepIon)
    
    // Update master quant peptide ion id
    mqPepIon.id = msiMQPepIon.getId
    
    msiMQPepIon
  }

  protected def storeMasterQuantProteinSet(
    mqProtSet: MasterQuantProteinSet,
    msiMasterProtSet: MsiProteinSet,
    msiRSM: MsiResultSummary
  ): MsiMasterQuantComponent = {

    val msiMQCObjectTree = this.buildMasterQuantProteinSetObjectTree(mqProtSet)
    this.msiEm.persist(msiMQCObjectTree)

    // Store master quant component
    val msiMQC = new MsiMasterQuantComponent()
    msiMQC.setSelectionLevel(mqProtSet.selectionLevel)
    if (mqProtSet.properties.isDefined) msiMQC.setSerializedProperties(ProfiJson.serialize(mqProtSet.properties.get))
    msiMQC.setObjectTreeId(msiMQCObjectTree.getId)
    msiMQC.setSchemaName(msiMQCObjectTree.getSchema.getName)
    msiMQC.setResultSummary(msiRSM)
    this.msiEm.persist(msiMQC)

    // Link master quant protein set to the corresponding master quant component
    msiMasterProtSet.setMasterQuantComponentId(msiMQC.getId)
    
    msiMQC
  }

  protected lazy val quantProteinSetsSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.QUANT_PROTEIN_SETS.toString())
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

  protected def createMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val tmpIdentProteinIdSet = new collection.mutable.HashSet[Long]()

    for (identRSM <- identResultSummaries) {
      // 	Retrieve protein ids
      val rs = identRSM.resultSet.get
      rs.proteinMatches.foreach { p => if (p.getProteinId != 0) tmpIdentProteinIdSet += p.getProteinId }
    }

    // Retrieve sequence length mapped by the corresponding protein id
    val seqLengthByProtId = msiDbHelper.getSeqLengthByBioSeqId(tmpIdentProteinIdSet.toList)

    // FIXME: check that all peptide sets have the same scoring ???
    val pepSetScoring = PepSetScoring.withName(this.identResultSummaries(0).peptideSets(0).scoreType)
    val pepSetScoreUpdater = PeptideSetScoreUpdater(pepSetScoring)

    // Merge result summaries
    this.logger.info("merging result summaries...")
    val rsmBuilder = new ResultSummaryAdder(
      ResultSummary.generateNewId(),
      false,
      pepSetScoreUpdater,
      Some(seqLengthByProtId)
    )

    for (identRSM <- identResultSummaries) {
      rsmBuilder.addResultSummary(identRSM)
    }

    rsmBuilder.toResultSummary
  }

}