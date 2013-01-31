package fr.proline.core.service.msq

import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import collection.mutable.{ HashMap, HashSet }
import collection.JavaConversions.{ collectionAsScalaIterable, setAsJavaSet }
import collection.JavaConverters.{ asJavaCollectionConverter }
import fr.proline.core.algo.msi.ResultSummaryMerger
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.uds.MasterQuantitationChannel
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
  //PeptideSetProteinMatchMap => MsiPepSetProtMatchMap,
  ProteinSetProteinMatchItem => MsiProtSetProtMatchItem,
  ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK,
  ProteinMatch => MsiProteinMatch,
  ProteinSet => MsiProteinSet,
  ResultSet => MsiResultSet,
  ResultSummary => MsiResultSummary,
  SequenceMatch => MsiSequenceMatch
}
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.repository.IDatabaseConnector
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.dal.ContextFactory

trait IQuantifier extends Logging {

  // Required fields
  val dbManager: IDataStoreConnectorFactory
  val udsMasterQuantChannel: MasterQuantitationChannel

  // Instantiated fields
  val projectId = udsMasterQuantChannel.getDataset.getProject.getId
  val msiDbConnector = dbManager.getMsiDbConnector(projectId)
  val msiDbCtx = new DatabaseConnectionContext(msiDbConnector)
  val msiEm = msiDbConnector.getEntityManagerFactory().createEntityManager()

  protected val msiSqlCtx = ContextFactory.buildDbConnectionContext(msiDbConnector, false).asInstanceOf[SQLConnectionContext] // SQL Context  

  val psDbConnector = dbManager.getPsDbConnector
  val psDbCtx = new DatabaseConnectionContext(psDbConnector)

  protected val psSqlCtx = ContextFactory.buildDbConnectionContext(psDbConnector, false).asInstanceOf[SQLConnectionContext] // SQL Context  

  val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
  val quantChannelIds = udsQuantChannels.map { _.getId } toArray

  val rsmIds = udsQuantChannels.map { udsQuantChannel =>
    val qcId = udsQuantChannel.getId()
    val identRsmId = udsQuantChannel.getIdentResultSummaryId
    assert(identRsmId != 0,
      "the quant_channel with id='" + qcId + "' is not assocciated with an identification result summary")

    identRsmId.toInt

  } toSeq

  val identRsIdByRsmId = {
    msiSqlCtx.ezDBC.select("SELECT id, result_set_id FROM result_summary WHERE id IN(" + rsmIds.mkString(",") + ")") { r =>
      (r.nextInt -> r.nextInt)
    } toMap
  }

  val msiIdentResultSets = {
    val identRsIds = identRsIdByRsmId.values.asJavaCollection
    msiEm.createQuery("FROM fr.proline.core.orm.msi.ResultSet WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.msi.ResultSet])
      .setParameter("ids", identRsIds).getResultList().toList
  }

  val identResultSummaries = {

    // Load the result summaries corresponding to the quant channels
    this.logger.info("loading result summaries...")

    // Instantiate a RSM provider
    val rsmProvider = new SQLResultSummaryProvider(msiSqlCtx, psSqlCtx)
    rsmProvider.getResultSummaries(rsmIds, true)
  }

  val msiDbHelper = new MsiDbHelper(msiSqlCtx.ezDBC)

  val seqLengthByProtId = {
    val tmpIdentProteinIdSet = new collection.mutable.HashSet[Int]()

    for (identRSM <- identResultSummaries) {
      // Retrieve protein ids
      val rs = identRSM.resultSet.get
      rs.proteinMatches.foreach { p => if (p.getProteinId != 0) tmpIdentProteinIdSet += p.getProteinId }
    }

    // Retrieve sequence length mapped by the corresponding protein id
    this.msiDbHelper.getSeqLengthByBioSeqId(tmpIdentProteinIdSet.toList)
  }

  val mergedResultSummary = {
    // Merge result summaries
    val resultSummaryMerger = new ResultSummaryMerger()
    this.logger.info("merging result summaries...")
    resultSummaryMerger.mergeResultSummaries(this.identResultSummaries, this.seqLengthByProtId)
  }

  lazy val curSQLTime = new java.sql.Timestamp(new java.util.Date().getTime)

  private var _quantified = false

  /**
   * Main method of the quantifier.
   * This method wraps the quantifyFraction method which has to be implemented
   * in each specific quantifier.
   */
  def quantify() = {

    // Check if the quantification has been already performed
    if (this._quantified) {
      throw new Exception("this fraction has been already quantified")
    }

    // Run the quantification process
    this.quantifyFraction()

    // Close entity managers
    this.msiEm.close()

    this._quantified = true
    this.logger.info("fraction has been quantified !")
  }

  protected def quantifyFraction(): Unit

  protected def storeMsiQuantResultSet(msiIdentResultSets: List[MsiResultSet]): MsiResultSet = {

    // TODO: provide the RS name in the parameters
    val msiQuantResultSet = new MsiResultSet()
    msiQuantResultSet.setName("")
    msiQuantResultSet.setType(MsiResultSet.Type.QUANTITATION)
    msiQuantResultSet.setModificationTimestamp(curSQLTime)
    //msiEm.persist(msiQuantResultSet)

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
  // TODO: define a QuantStorerContext ?
  protected val masterPepInstByPepId = new HashMap[Int, PeptideInstance]
  protected val msiMasterPepInstById = new HashMap[Int, MsiPeptideInstance]
  protected val msiMasterProtSetById = new HashMap[Int, MsiProteinSet]

  protected def storeMasterQuantResultSummary(masterRSM: ResultSummary,
    msiQuantRSM: MsiResultSummary,
    msiQuantRS: MsiResultSet) {

    // Retrieve result summary and result set ids
    val quantRsmId = msiQuantRSM.getId
    val quantRsId = msiQuantRS.getId

    // Retrieve peptide instances of the merged result summary
    val masterPepInstances = masterRSM.peptideInstances
    val masterPepMatchById = masterRSM.resultSet.get.peptideMatchById

    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info("storing master quant peptide instances...")

    // Define some vars
    val masterQuantPepMatchIdByTmpPepMatchId = new HashMap[Int, Int]

    for (masterPepInstance <- masterPepInstances) {

      val peptideId = masterPepInstance.peptide.id
      val masterPepInstPepMatchIds = masterPepInstance.getPeptideMatchIds
      assert(masterPepInstPepMatchIds.length == 1,
        "peptide matches have not been correctly merged")

      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(masterPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(masterPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(masterPepInstance.proteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptideId(peptideId)
      msiMasterPepInstance.setBestPeptideMatchId(masterPepInstance.bestPeptideMatchId)
      msiMasterPepInstance.setResultSummary(msiQuantRSM)
      msiEm.persist(msiMasterPepInstance)

      val masterPepInstanceId = msiMasterPepInstance.getId

      // Update the peptide instance id
      masterPepInstance.id = masterPepInstanceId

      // Map the peptide instance by the peptide id
      masterPepInstByPepId(peptideId) = masterPepInstance
      // TODO: remove this mapping when ORM is updated
      msiMasterPepInstById(masterPepInstanceId) = msiMasterPepInstance

      // Retrieve the best peptide match
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
      if (bestParentPepMatch.properties != None) msiMasterPepMatch.setSerializedProperties(generate(bestParentPepMatch.properties))

      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)

      val quantPepMatchId = msiMasterPepMatch.getId

      // Update the best parent peptide match id
      bestParentPepMatch.id = quantPepMatchId

      // Map this quant peptide match to the quant peptide instance
      val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
      msiPepInstMatchPK.setPeptideInstanceId(masterPepInstanceId)
      msiPepInstMatchPK.setPeptideMatchId(quantPepMatchId)

      val msiPepInstMatch = new MsiPepInstPepMatchMap()
      msiPepInstMatch.setId(msiPepInstMatchPK)
      msiPepInstMatch.setPeptideInstance(msiMasterPepInstance)
      msiPepInstMatch.setResultSummary(msiQuantRSM)

      //msiMasterPepInstance.setPeptidesMatches(Set(msiMasterPepMatch))
      msiEm.persist(msiMasterPepInstance)

      // Map quant peptide match id by in memory merged peptide match id
      masterQuantPepMatchIdByTmpPepMatchId(bestParentPepMatch.id) = quantPepMatchId

      // Map this quant peptide match to identified child peptide matches
      for (childPepMatchId <- bestParentPepMatch.getChildrenIds) {

        val msiPepMatchRelation = new MsiPeptideMatchRelation()
        msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
        // FIXME: allows to set the child peptide match id
        msiPepMatchRelation.setChildPeptideMatch(msiMasterPepMatch) // childPepMatchId
        // FIXME: rename to setParentResultSet
        msiPepMatchRelation.setParentResultSetId(msiQuantRS)
      }
    }

    // Retrieve some vars
    val masterPeptideSets = masterRSM.peptideSets
    this.logger.info("number of grouped peptide sets: " + masterPeptideSets.length)
    val masterProteinSets = masterRSM.proteinSets
    this.logger.info("number of grouped protein sets: " + masterProteinSets.length)
    val masterProtSetById = masterRSM.proteinSetById
    val masterProtMatchById = masterRSM.resultSet.get.proteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets...")
    for (masterPeptideSet <- masterPeptideSets) {

      if (masterPeptideSet.isSubset == false) {

        val masterProteinSet = masterProtSetById.get(masterPeptideSet.getProteinSetId)
        assert(masterProteinSet != None, "missing protein set with id=" + masterPeptideSet.getProteinSetId)

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
        var typicalProtMatchId = masterProteinSet.get.getTypicalProteinMatchId // if( idfProteinSet == None ) 0 else 
        if (typicalProtMatchId == 0) {
          typicalProtMatchId = masterPeptideSet.proteinMatchIds.reduce { (a, b) =>
            if (masterProtMatchById(a).coverage > masterProtMatchById(b).coverage) a else b
          }
        }

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(true)
        msiMasterProteinSet.setSelectionLevel(2)
        msiMasterProteinSet.setProteinMatchId(typicalProtMatchId)
        // FIXME: retrieve the right scoring id
        msiMasterProteinSet.setScoringId(4)
        msiMasterProteinSet.setResultSummary(msiQuantRSM)
        msiEm.persist(msiMasterProteinSet)

        val masterProteinSetId = msiMasterProteinSet.getId
        msiMasterProtSetById(masterProteinSetId) = msiMasterProteinSet

        // Update the master protein set id
        masterProteinSet.get.id = masterProteinSetId

        // Retrieve peptide set items
        val samesetItems = masterPeptideSet.items

        // Store master peptide set      
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset(false)
        msiMasterPeptideSet.setPeptideCount(samesetItems.length)
        msiMasterPeptideSet.setPeptideMatchCount(masterPeptideSet.peptideMatchesCount)
        msiMasterPeptideSet.setProteinSet(msiMasterProteinSet)
        msiMasterPeptideSet.setResultSummaryId(quantRsmId)
        msiEm.persist(msiMasterPeptideSet)

        val masterPeptideSetId = msiMasterPeptideSet.getId

        // Update the master peptide set id
        masterPeptideSet.id = masterPeptideSetId

        // Link master peptide set to master peptide instances

        masterPeptideSet.items
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
          msiPepSetItem.setResultSummary(msiQuantRSM)

          msiEm.persist(msiPepSetItem)
        }

        // Store master protein matches
        for (protMatchId <- masterPeptideSet.proteinMatchIds) {

          val masterProtMatch = masterProtMatchById(protMatchId)

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
          msiMasterProtMatch.setBioSequenceId(masterProtMatch.getProteinId)
          // FIXME: retrieve the right scoring id
          msiMasterProtMatch.setScoringId(3)
          msiMasterProtMatch.setResultSet(msiQuantRS)
          msiEm.persist(msiMasterProtMatch)

          val masterProtMatchId = msiMasterProtMatch.getId

          // Update master protein match id
          masterProtMatch.id = masterProtMatchId

          // TODO: map protein_match to seq_databases

          // TODO: Map master protein match to master peptide set => ORM has to be fixed
          /*val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          new Pairs::Msi::RDBO::PeptideSetProteinMatchMap(
                                  peptide_set_id = quantPeptideSetId,
                                  protein_match_id = quantProtMatchId,
                                  result_summary_id = quantRsmId,
                                  db = msiRdb
                                ).save*/

          // Map master protein match to master protein set
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

          // Map master protein match to master peptide matches using master sequence matches
          val seqMatches = masterProtMatch.sequenceMatches
          val mappedMasterPepMatchesIdSet = new HashSet[Int]

          for (seqMatch <- seqMatches) {

            val bestPepMatchId = seqMatch.getBestPeptideMatchId
            if (masterQuantPepMatchIdByTmpPepMatchId.contains(bestPepMatchId)) {
              val masterPepMatchId = masterQuantPepMatchIdByTmpPepMatchId(bestPepMatchId)

              if (mappedMasterPepMatchesIdSet.contains(masterPepMatchId) == false) {
                mappedMasterPepMatchesIdSet(masterPepMatchId) = true

                val msiMasterSeqMatchPK = new fr.proline.core.orm.msi.SequenceMatchPK()
                msiMasterSeqMatchPK.setProteinMatchId(masterProtMatchId)
                msiMasterSeqMatchPK.setPeptideId(seqMatch.getPeptideId)
                msiMasterSeqMatchPK.setStart(seqMatch.start)
                msiMasterSeqMatchPK.setStop(seqMatch.end)

                val msiMasterSeqMatch = new MsiSequenceMatch()
                msiMasterSeqMatch.setId(msiMasterSeqMatchPK)
                msiMasterSeqMatch.setResidueBefore(seqMatch.residueBefore.toString) // TODO: change ORM mapping to Char
                msiMasterSeqMatch.setResidueBefore(seqMatch.residueAfter.toString) // TODO: change ORM mapping to Char
                msiMasterSeqMatch.setIsDecoy(false)
                msiMasterSeqMatch.setBestPeptideMatchId(seqMatch.getBestPeptideMatchId)
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