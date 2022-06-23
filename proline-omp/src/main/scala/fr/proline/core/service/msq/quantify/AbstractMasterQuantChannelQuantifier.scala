package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.{DatabaseConnectionContext, IExecutionContext, MsiDbConnectionContext}
import fr.proline.core.algo.msi.ResultSummaryAdder
import fr.proline.core.algo.msi.scoring.{PepSetScoring, PeptideSetScoreUpdater}
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{MasterQuantComponent => MsiMasterQuantComponent, MasterQuantPeptideIon => MsiMasterQuantPepIon, MasterQuantReporterIon => MsiMasterQuantRepIon, ObjectTree => MsiObjectTree, ObjectTreeSchema => MsiObjectTreeSchema, PeptideInstance => MsiPeptideInstance, PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap, PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK, PeptideMatch => MsiPeptideMatch, PeptideMatchRelation => MsiPeptideMatchRelation, PeptideMatchRelationPK => MsiPeptideMatchRelationPK, PeptideReadablePtmString => MsiPeptideReadablePtmString, PeptideReadablePtmStringPK => MsiPeptideReadablePtmStringPK, PeptideSet => MsiPeptideSet, PeptideSetPeptideInstanceItem => MsiPeptideSetItem, PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK, PeptideSetProteinMatchMap => MsiPepSetProtMatchMap, PeptideSetProteinMatchMapPK => MsiPepSetProtMatchMapPK, ProteinMatch => MsiProteinMatch, ProteinSet => MsiProteinSet, ProteinSetProteinMatchItem => MsiProtSetProtMatchItem, ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK, ResultSet => MsiResultSet, ResultSummary => MsiResultSummary, Scoring => MsiScoring, SequenceMatch => MsiSequenceMatch}
import fr.proline.core.orm.uds.MasterQuantitationChannel

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap, LongMap}

abstract class AbstractMasterQuantChannelQuantifier extends LazyLogging {

  // Required fields
  val executionContext: IExecutionContext
  val udsMasterQuantChannel: MasterQuantitationChannel
  val experimentalDesign: ExperimentalDesign

  // Instantiated fields
  protected val udsDbCtx = executionContext.getUDSDbConnectionContext
  protected val udsEm = udsDbCtx.getEntityManager
  protected val msiDbCtx = executionContext.getMSIDbConnectionContext
  protected val msiEm = msiDbCtx.getEntityManager

  protected val masterQc = experimentalDesign.masterQuantChannels.find(_.id == udsMasterQuantChannel.getId).get

  protected lazy val mergedResultSummary = getMergedResultSummary(msiDbCtx)

  protected lazy val entityCache = new MasterQuantChannelEntityCache(executionContext, udsMasterQuantChannel)

  protected lazy val curSQLTime = new java.sql.Timestamp(new java.util.Date().getTime)

  /**
   * Main method of the quantifier.
   * This method wraps the quantifyMasterChannel method which has to be implemented
   * in each specific quantifier.
   */
  def quantify() = {

    this.logger.info(s"Quantification of master quant channel with id=${udsMasterQuantChannel.getId} has started !")
    this.quantifyMasterChannel()
    this.logger.info(s"Master quant channel with id=${udsMasterQuantChannel.getId} has been quantified !")

  }

  // Define the interface required to implement the trait
  protected def quantifyMasterChannel(): Unit
  protected def quantPeptidesObjectTreeSchema: MsiObjectTreeSchema
  protected def quantPeptideIonsObjectTreeSchema: MsiObjectTreeSchema

  protected lazy val quantReporterIonsObjectTreeSchema: MsiObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.QUANT_REPORTER_IONS.toString())
  }

  protected lazy val quantProteinSetsSchema: MsiObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.QUANT_PROTEIN_SETS.toString())
  }

  protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
    if (masterQc.identResultSummaryId.isEmpty) {
      createMergedResultSummary(msiDbCtx)
    } else {
      val identRsmId = masterQc.identResultSummaryId.get

      this.logger.debug("Read Merged RSM with ID " + identRsmId)

      // Instantiate an RSM provider
      val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
      val identRsmOpt = rsmProvider.getResultSummary(identRsmId, loadResultSet = true)
      assert( identRsmOpt.isDefined, "can't load the result summary with id=" + identRsmId)

      // Link identification RSM to MQC
      udsMasterQuantChannel.setIdentResultSummaryId(identRsmId)

      if (masterQc.identDatasetId.isDefined) {
        val identDsId = masterQc.identDatasetId.get
        val udsIdentDs = udsEm.find(classOf[fr.proline.core.orm.uds.Dataset], identDsId)
        udsMasterQuantChannel.setIdentDataset(udsIdentDs)
      }

      identRsmOpt.get
    }
  }
  
  protected def storeMsiQuantResultSet(): MsiResultSet = {

    val msiIdentResultSets = if (masterQc.identResultSummaryId.isEmpty) {
      entityCache.quantChannelMsiResultSets
    }  else {
      val identRSM = msiEm.find(classOf[MsiResultSummary], masterQc.identResultSummaryId.get)
      identRSM.getResultSet.getChildren().asScala.toList
    }
    _storeMsiQuantResultSet(msiIdentResultSets)
  }

  protected def storeMsiQuantResultSet(childrenRSIds: List[Long]): MsiResultSet = {
    val rs: List[MsiResultSet] = for(rsId <- childrenRSIds) yield msiEm.find(classOf[MsiResultSet],rsId)
    _storeMsiQuantResultSet(rs)
  }

  private def _storeMsiQuantResultSet(childrenRS: List[MsiResultSet]): MsiResultSet = {
    // TODO: provide the RS name in the parameters
    val msiQuantResultSet = new MsiResultSet()
    msiQuantResultSet.setName("")
    msiQuantResultSet.setType(MsiResultSet.Type.QUANTITATION)
    msiQuantResultSet.setCreationTimestamp(curSQLTime)
    msiQuantResultSet.setChildren(childrenRS.toSet[MsiResultSet].asJava)
    msiEm.persist(msiQuantResultSet)

    msiQuantResultSet
  }


  protected def storeMsiQuantResultSummary(msiQuantResultSet: MsiResultSet, childrenRSMIds: Array[Long]) : MsiResultSummary = {

    val msiQuantRSM = new MsiResultSummary()
    msiQuantRSM.setModificationTimestamp(curSQLTime)
    msiQuantRSM.setResultSet(msiQuantResultSet)
    // Retrieve children ResultSummary and link them to the msiQuantRSM
    val rsms: Array[MsiResultSummary] = for(rsmId <- childrenRSMIds) yield msiEm.find(classOf[MsiResultSummary],rsmId)
    msiQuantRSM.setChildren(new java.util.HashSet(rsms.toSet.asJavaCollection))
    msiEm.persist(msiQuantRSM)

    msiQuantRSM
  }

  protected def storeMsiQuantResultSummary(msiQuantResultSet: MsiResultSet) : MsiResultSummary = {
    val childrenRsmIds = {
      if (masterQc.identResultSummaryId.isEmpty) {
        entityCache.quantChannelResultSummaries.map(_.id)
      } else {
        val identRSM = msiEm.find(classOf[MsiResultSummary], masterQc.identResultSummaryId.get)
        identRSM.getChildren().asScala.map(_.getId).toArray
      }
    }
    storeMsiQuantResultSummary(msiQuantResultSet, childrenRsmIds)
  }

  // TODO: rename into storeMasterQuantEntities
  protected def storeMasterQuantPeptidesAndProteinSets(
    msiQuantRSM: MsiResultSummary,
    mqPeptides: Array[MasterQuantPeptide],
    mqProtSets: Array[MasterQuantProteinSet]
  ) {
    
    this.logger.info("storing master quant peptides...")
    
    val mqPepIdByTmpId = new LongMap[Long](mqPeptides.length)
    val mqPepIonIdByTmpId = new LongMap[Long](mqPeptides.length * 2)

    // Iterate over master quant peptides to store them
    val msiMqPepById = mqPeptides.toLongMapWith { mqPeptide =>
      val msiMasterPepInstId = mqPeptide.peptideInstance.map( _.id ) 
      
      // Retrieve TMP ids
      val tmpMqPepId = mqPeptide.id
      val tmpMqPepIonIdByCharge = new LongMap[Long](mqPeptide.masterQuantPeptideIons.length)
      for (mqPepIon <- mqPeptide.masterQuantPeptideIons) {
        tmpMqPepIonIdByCharge.put(mqPepIon.charge,mqPepIon.id)
      }
      
      val msiMqPep = this.storeMasterQuantPeptide(mqPeptide, msiQuantRSM, msiMasterPepInstId)
      
      // Map entities by TMP ids
      mqPepIdByTmpId.put(tmpMqPepId, mqPeptide.id)
      for (mqPepIon <- mqPeptide.masterQuantPeptideIons) {
        val tmpId = tmpMqPepIonIdByCharge(mqPepIon.charge)
        mqPepIonIdByTmpId.put(tmpId,mqPepIon.id)
      }

      // Update the master quant peptide properties ion ids
      if(mqPeptide.properties.isDefined && mqPeptide.properties.get.mqPepIonAbundanceSummarizingConfig.isDefined){
        val mqPepProps = mqPeptide.properties.get
        val tmpMqPepIonIdToSLevel = mqPepProps.mqPepIonAbundanceSummarizingConfig.get.mqPeptideIonSelLevelById
        //update peptideIons ids
        val mqPeptideIonSelLById = new mutable.HashMap[Long, Int]()
        tmpMqPepIonIdToSLevel.foreach( e => {
          mqPeptideIonSelLById.put(mqPepIonIdByTmpId(e._1),e._2)
        })
        mqPepProps.mqPepIonAbundanceSummarizingConfig.get.setMqPeptideIonSelLevelById(mqPeptideIonSelLById)
        // Update the OM
        mqPeptide.properties = Some( mqPepProps )

        // Update the ORM
        msiMqPep.setSerializedProperties( ProfiJson.serialize(mqPepProps) )

      }

      mqPeptide.id -> msiMqPep
    }
    
    // Update the master quant protein set properties
    for (mqProtSet <- mqProtSets) {
      
      val mqProtSetProps = mqProtSet.properties.getOrElse( MasterQuantProteinSetProperties() )
      
      // Update the selectionLevelBymasterQuantPeptideId properties
      val selectionLevelMQPepTmpIdsOpt = mqProtSetProps.getSelectionLevelByMqPeptideId()
      if (selectionLevelMQPepTmpIdsOpt.isDefined) {
        val selectedMQPepIds = selectionLevelMQPepTmpIdsOpt.get.map{ case (k,v) => (mqPepIdByTmpId(k), v) }
        mqProtSetProps.setSelectionLevelByMqPeptideId(Some(selectedMQPepIds))
      }
      
      // Update the selectionLevelBymasterQuantPeptideIonId properties
      val selectionLevelMQPepIonTmpIdsOpt = mqProtSetProps.getSelectionLevelByMqPeptideIonId()
      if (selectionLevelMQPepIonTmpIdsOpt.isDefined) {
        val selectedMQPepIonIds = selectionLevelMQPepIonTmpIdsOpt.get.map{ case (k,v) => (mqPepIonIdByTmpId(k), v) }
        mqProtSetProps.setSelectionLevelByMqPeptideIonId(Some(selectedMQPepIonIds))
      }
      
    }

    this.logger.info("storing master quant protein sets...")

    // Iterate over master quant protein sets to store them
    val mqProtSetIdsByMqPep = new HashMap[MasterQuantPeptide,ArrayBuffer[Long]]
    
    for (mqProtSet <- mqProtSets) {
       val msiMasterProtSetId =  mqProtSet.proteinSet.id        
       this.storeMasterQuantProteinSet(mqProtSet, msiMasterProtSetId, msiQuantRSM)
       
      // Add this protein set ids to the list of protein sets mapped to this peptide
      for( mqPep <- mqProtSet.masterQuantPeptides ) {
        mqProtSetIdsByMqPep.getOrElseUpdate(mqPep, new ArrayBuffer[Long]) += mqProtSet.id
      }
    }
    
    // Update the mqProtSetIds property of master quant peptides
    for ( mqPep <- mqPeptides; mqProtSetIds <- mqProtSetIdsByMqPep.get(mqPep) ) {
      
      // Build properties
      val mqPepProps = mqPep.properties.getOrElse( MasterQuantPeptideProperties() )
      mqPepProps.setMqProtSetIds( Some(mqProtSetIds.toArray) )
      
      // Update the OM
      mqPep.properties = Some( mqPepProps )
      
      // Update the ORM
      val msiMqPep = msiMqPepById(mqPep.id)
      msiMqPep.setSerializedProperties( ProfiJson.serialize(mqPepProps) )
    }
    
  }

  protected def storeMasterQuantPeptide(
    mqPep: MasterQuantPeptide,
    msiRSM: MsiResultSummary,
    masterPepInstIdAsOpt: Option[Long]
  ): MsiMasterQuantComponent = {

    val msiMQCompObjectTree = this.buildMasterQuantPeptideObjectTree(mqPep)
    this.msiEm.persist(msiMQCompObjectTree)

    // Store master quant component for this master quant peptide
    val msiMQComp = buildMasterQuantComponent(
      msiMQCompObjectTree,
      mqPep.selectionLevel,
      mqPep.properties.map(ProfiJson.serialize(_)),
      msiRSM
    )
    this.msiEm.persist(msiMQComp)
    
    // Update master quant peptide id
    mqPep.id = msiMQComp.getId

    // If this master quant peptide has been identified by a master peptide instance
    if (masterPepInstIdAsOpt.isDefined) {
      // Link master peptide instance to the corresponding master quant component
      val msiPepInst = msiEm.find(classOf[MsiPeptideInstance], masterPepInstIdAsOpt.get)
      msiPepInst.setMasterQuantComponentId(msiMQComp.getId)
      this.msiEm.persist(msiPepInst)
    }

    for (mqPepIon <- mqPep.masterQuantPeptideIons) {
      this.storeMasterQuantPeptideIon(mqPepIon, mqPep, msiRSM)
    }

    msiMQComp
  }
  
  protected def buildMasterQuantPeptideObjectTree(mqPep: MasterQuantPeptide): MsiObjectTree = {

    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = entityCache.quantChannelIds.map { quantPeptideMap.getOrElse(_, null) }

    buildObjectTree(quantPeptidesObjectTreeSchema, ProfiJson.serialize(quantPeptides))
  }

  protected def storeMasterQuantPeptideIon(
    mqPepIon: MasterQuantPeptideIon,
    mqPep: MasterQuantPeptide,
    msiRSM: MsiResultSummary
  ): MsiMasterQuantPepIon = {

    val msiMQCompObjectTree = this.buildMasterQuantPeptideIonObjectTree(mqPepIon)
    this.msiEm.persist(msiMQCompObjectTree)
    
    // Store master quant component for this master quant peptide ion
    val msiMQComp = buildMasterQuantComponent(
      msiMQCompObjectTree,
      mqPepIon.selectionLevel,
      // TODO: decide what to store in the master quant component properties
      None,
      msiRSM
    )
    this.msiEm.persist(msiMQComp)

    // Store master quant peptide ion
    val msiMQPepIon = new MsiMasterQuantPepIon()
    msiMQPepIon.setCharge(mqPepIon.charge)
    msiMQPepIon.setMoz(mqPepIon.unlabeledMoz)
    msiMQPepIon.setElutionTime(mqPepIon.elutionTime)
    msiMQPepIon.setPeptideMatchCount(mqPepIon.peptideMatchesCount)
    msiMQPepIon.setMasterQuantComponent(msiMQComp)
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
    
    // Store reporter ions if they exists
    for( mqRepIon <- mqPepIon.masterQuantReporterIons ) {
      this.storeMasterQuantReporterIon(mqRepIon,msiMQPepIon,msiRSM)
    }
    
    msiMQPepIon
  }
  
  protected def buildMasterQuantPeptideIonObjectTree(mqPepIon: MasterQuantPeptideIon): MsiObjectTree = {

    val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
    val quantPepIons = entityCache.quantChannelIds.map { quantPeptideIonMap.getOrElse(_, null) }

    buildObjectTree(quantPeptideIonsObjectTreeSchema, ProfiJson.serialize(quantPepIons))
  }
  
  protected def storeMasterQuantReporterIon(
    mqRepIon: MasterQuantReporterIon,
    msiMQPepIon: MsiMasterQuantPepIon,
    msiRsm: MsiResultSummary
  ): MsiMasterQuantRepIon = {

    val msiMQCompObjectTree = this.buildMasterQuantReporterIonObjectTree(mqRepIon)
    this.msiEm.persist(msiMQCompObjectTree)

    // Store master quant component for this master quant reporter ion
    val msiMQComp = buildMasterQuantComponent(
      msiMQCompObjectTree,
      mqRepIon.selectionLevel,
      // TODO: decide what to store in the master quant component properties
      None,
      msiRsm
    )
    this.msiEm.persist(msiMQComp)

    // Store master quant peptide ion
    val msiMQRepIon = new MsiMasterQuantRepIon()
    if (mqRepIon.properties.isDefined) msiMQRepIon.setSerializedProperties(ProfiJson.serialize(mqRepIon.properties.get))
    msiMQRepIon.setMasterQuantComponent(msiMQComp)
    msiMQRepIon.setMasterQuantPeptideIon(msiMQPepIon)
    msiMQRepIon.setMsQueryId(mqRepIon.msQueryId)
    msiMQRepIon.setResultSummary(msiRsm)

    this.msiEm.persist(msiMQRepIon)
    
    // Update master quant reporter ion id
    mqRepIon.id = msiMQRepIon.getId
    
    msiMQRepIon
  }
  
  protected def buildMasterQuantReporterIonObjectTree(mqRepIon: MasterQuantReporterIon): MsiObjectTree = {
    val quantRepIonMap = mqRepIon.quantReporterIonMap
    val quantRepIons = entityCache.quantChannelIds.map { quantRepIonMap.getOrElse(_, null) }

    buildObjectTree(quantReporterIonsObjectTreeSchema, ProfiJson.serialize(quantRepIons))
  }
  
  protected def storeMasterQuantProteinSet(
    mqProtSet: MasterQuantProteinSet,
    masterProtSetId: Long,
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
    val msiMasterProtSet = msiEm.find(classOf[MsiProteinSet], masterProtSetId)
    msiMasterProtSet.setMasterQuantComponentId(msiMQC.getId)
    
    msiMQC
  }

  protected def buildMasterQuantProteinSetObjectTree(mqProtSet: MasterQuantProteinSet): MsiObjectTree = {

    val quantProtSetMap = mqProtSet.quantProteinSetMap
    val quantProtSets = entityCache.quantChannelIds.map { quantProtSetMap.getOrElse(_, null) }

    buildObjectTree(quantProteinSetsSchema, ProfiJson.serialize(quantProtSets) )
  }
  
  protected def buildObjectTree(schema: MsiObjectTreeSchema, data: String): MsiObjectTree = {

    // Create the object tree
    val msiMQPepObjectTree = new MsiObjectTree()
    msiMQPepObjectTree.setSchema(schema)
    msiMQPepObjectTree.setClobData(data)

    msiMQPepObjectTree
  }
  
  protected def buildMasterQuantComponent(
    msiObjectTree: MsiObjectTree,
    selectionLevel: Int,
    properties: Option[String],
    msiRsm: MsiResultSummary
  ): MsiMasterQuantComponent = {

    val msiMqc = new MsiMasterQuantComponent()
    msiMqc.setSelectionLevel(selectionLevel)
    if( properties.isDefined ) msiMqc.setSerializedProperties(properties.get)
    msiMqc.setObjectTreeId(msiObjectTree.getId)
    msiMqc.setSchemaName(msiObjectTree.getSchema.getName)
    msiMqc.setResultSummary(msiRsm)
    
    msiMqc
  }

  protected def createMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    createMergedResultSummary(msiDbCtx, entityCache.quantChannelResultSummaries)
  }

  protected def createMergedResultSummary(msiDbCtx: DatabaseConnectionContext, identRsms: Array[ResultSummary]): ResultSummary = {
    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    val firstIdentRsm = identRsms(0)
    val approxProtMatchesCount = if( firstIdentRsm.resultSet.isDefined) firstIdentRsm.resultSet.get.proteinMatches.length * identRsms.length else  (20000 * identRsms.length)
    val tmpIdentProteinIds = new ArrayBuffer[Long](approxProtMatchesCount)

    for (identRSM <- identRsms) {
      val rsOpt = identRSM.resultSet
      require(rsOpt.isDefined," No ResultSet loaded for ResultSummary "+identRSM.id)
      // 	Retrieve protein ids
      val rs = rsOpt.get
      for( protMatch <- rs.proteinMatches if protMatch.getProteinId != 0) {
        tmpIdentProteinIds += protMatch.getProteinId
      }
    }

    // Retrieve sequence length mapped by the corresponding protein id
    val seqLengthByProtId = msiDbHelper.getSeqLengthByBioSeqId(tmpIdentProteinIds.distinct)

    // FIXME: check that all peptide sets have the same scoring ???
    val pepSetScoring = PepSetScoring.withName(firstIdentRsm.peptideSets(0).scoreType)
    val pepSetScoreUpdater = PeptideSetScoreUpdater(pepSetScoring)

    // Merge result summaries
    this.logger.info("merging result summaries...")
    
    val rsmBuilder = new ResultSummaryAdder(
      ResultSummary.generateNewId(),
      false,
      pepSetScoreUpdater
    )

    for (identRsm <- identRsms) {
      rsmBuilder.addResultSummary(identRsm)
    }

    rsmBuilder.toResultSummary()
  }

}