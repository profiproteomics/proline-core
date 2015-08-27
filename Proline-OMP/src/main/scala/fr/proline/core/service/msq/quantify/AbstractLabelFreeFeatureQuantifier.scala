package fr.proline.core.service.msq.quantify

import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.slf4j.Logging
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.msq.IQuantifierAlgo
import fr.proline.core.algo.msq.LabelFreeFeatureQuantifier
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureMs2EventTable
import fr.proline.core.dal.tables.lcms.LcmsDbRawMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbScanTable
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.orm.msi.{ObjectTree => MsiObjectTree}
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.om.model.msi.ResultSummary
import fr.profi.util.primitives._
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository

abstract class AbstractLabelFreeFeatureQuantifier extends AbstractMasterQuantChannelQuantifier with Logging {
  
  val experimentalDesign: ExperimentalDesign
  val quantConfig: ILabelFreeQuantConfig
  val lcmsMapSet: MapSet

  //val lcmsDbConnector = dsConnectorFactory.getLcMsDbConnector(projectId)
  val lcmsDbCtx = executionContext.getLCMSDbConnectionContext
  //val lcmsEzDBC = ProlineEzDBC(lcmsDbConnector.getDataSource.getConnection, lcmsDbConnector.getDriverType)  

  // Retrieve corresponding peaklist ids
  // TODO: update the ORM definition so that peaklistId is available from msiSearch object    
  val identRsIdByPeaklistId = msiIdentResultSets map { rs => rs.getMsiSearch.getPeaklist.getId -> rs.getId } toMap
  val peaklistIds = this.identRsIdByPeaklistId.keys
  //val peaklistIds = msiIdentResultSets map { _.getMsiSearch().getPeaklist().getId() }

  def loadMs2SpectrumHeaders(): Array[Map[String,Any]] = {

    // Load MS2 spectrum headers
    this.logger.info("loading MS2 spectrum headers...")
    
    DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.PRECURSOR_MOZ,t.FIRST_CYCLE,t.FIRST_SCAN,t.FIRST_TIME,t.PEAKLIST_ID)
        -> "WHERE "~ t.PEAKLIST_ID ~" IN("~ peaklistIds.mkString(",") ~")"
      )
      msiEzDBC.selectAllRecordsAsMaps(sqlQuery)
    })

  }
  
  val ms2SpectrumHeaders = loadMs2SpectrumHeaders()

  val spectrumIdByRsIdAndScanNumber = {

    val firstScanColName = MsiDbSpectrumTable.columns.FIRST_SCAN
    val peaklistIdColName = MsiDbSpectrumTable.columns.PEAKLIST_ID

    // Map spectrum id by scan number and result set id
    val spectrumIdMap = HashMap[Long, HashMap[Int, Long]]()

    for (spectrumHeader <- this.ms2SpectrumHeaders) {

      require(spectrumHeader(firstScanColName) != null,"a first_scan id must be defined for MS2 spectrum id="+spectrumHeader(MsiDbSpectrumTable.columns.ID))

      val identRsId = toLong(identRsIdByPeaklistId(toLong(spectrumHeader(peaklistIdColName))))
      val scanNumber = spectrumHeader(firstScanColName).asInstanceOf[Int]
      val spectrumId = toLong(spectrumHeader("id"))

      spectrumIdMap.getOrElseUpdate(identRsId, new HashMap[Int, Long])(scanNumber) = spectrumId
    }

    spectrumIdMap.toMap
  }
  
  val scanNumberBySpectrumId = {

    val firstScanColName = MsiDbSpectrumTable.columns.FIRST_SCAN
    val peaklistIdColName = MsiDbSpectrumTable.columns.PEAKLIST_ID

    // Map spectrum id by scan number and result set id
    val scanNumberBySpectrumId = Map.newBuilder[Long, Int]

    for (spectrumHeader <- this.ms2SpectrumHeaders) {
      require(spectrumHeader(firstScanColName) != null,"a scan id must be defined for each MS2 spectrum")

      val scanNumber = spectrumHeader(firstScanColName).asInstanceOf[Int]
      val spectrumId = toLong(spectrumHeader("id"))
      
      scanNumberBySpectrumId += spectrumId -> scanNumber
    }

    scanNumberBySpectrumId.result
  }
  
  //val lcmsMapIds = lcmsMapSet.getChildMapIds

  lazy val lcmsRunIds = {    
    DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { lcmsEzDBC =>
      lcmsEzDBC.selectLongs( new SelectQueryBuilder1(LcmsDbRawMapTable).mkSelectQuery( (t,c) =>
        List(t.SCAN_SEQUENCE_ID) -> "WHERE "~ t.ID ~" IN("~ lcmsMapSet.getRawMapIds.mkString(",") ~")"
      ) )
    })
  }
  
  lazy val lcmsScans = {
    this.logger.info("loading MS2 scan headers...")
    
    val scanSeqProvider = new SQLScanSequenceProvider(lcmsDbCtx)
    scanSeqProvider.getScans(this.lcmsRunIds)
    
    /*DoJDBCReturningWork.withEzDBC( lcmsDbCtx, { lcmsEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(LcmsDbScanTable).mkSelectQuery( (t,c) =>
        List(t.ID,t.INITIAL_ID,t.CYCLE,t.TIME)
        -> "WHERE "~ t.MS_LEVEL ~" = 2 AND "~ t.RUN_ID ~" IN("~ this.lcmsRunIds.mkString(",") ~")"
      )
      lcmsEzDBC.selectAllRecordsAsMaps(sqlQuery)    
    })*/
    
  }

  lazy val ms2ScanNumbersByFtId = {

    /*val ms2ScanNumberById = ms2ScanHeaderRecords.map { r =>
      toLong(r("id")) -> r("initial_id").asInstanceOf[Int]
    } toMap*/
    val ms2ScanNumberById = Map() ++ lcmsScans.map( s => s.id -> s.initialId )

    val lcmsMapSet = this.lcmsMapSet
    val rawMapIds = lcmsMapSet.getRawMapIds
    val transientRawMapIdsCount = rawMapIds.count( _ <= 0 )
    require( transientRawMapIdsCount == 0, "LC-MS map set must contain persisted run map ids" )
    
    this.logger.info("loading MS2 scans/features map...")
    val ms2ScanNumbersByFtId = new HashMap[Long, ArrayBuffer[Int]]

    DoJDBCWork.withEzDBC( lcmsDbCtx, { lcmsEzDBC =>
      
      val sqlQuery = new SelectQueryBuilder1(LcmsDbFeatureMs2EventTable).mkSelectQuery( (t,c) =>
        List(t.FEATURE_ID,t.MS2_EVENT_ID) -> "WHERE "~ t.RAW_MAP_ID ~" IN("~ rawMapIds.mkString(",") ~")"
      )
      
      lcmsEzDBC.selectAndProcess(sqlQuery) { r =>
        val (featureId, ms2ScanId) = (toLong(r.nextAny), toLong(r.nextAny))
        val ms2ScanNumber = ms2ScanNumberById(ms2ScanId)
        ms2ScanNumbersByFtId.getOrElseUpdate(featureId, new ArrayBuffer[Int]) += ms2ScanNumber
      }
    })

    ms2ScanNumbersByFtId.toMap
  }

  val quantifierAlgo: IQuantifierAlgo = {
    new LabelFreeFeatureQuantifier(
      expDesign = experimentalDesign,
      lcmsMapSet = lcmsMapSet,
      spectrumIdByRsIdAndScanNumber = spectrumIdByRsIdAndScanNumber,
      ms2ScanNumbersByFtId = ms2ScanNumbersByFtId,
      mozTolInPPM = quantConfig.extractionParams.mozTol.toFloat,
      statTestsAlpha = 0.01f // TODO: retrieve from quantConfig
    )
  }

  protected def quantifyMasterChannel(): Unit = {
    
    // --- TODO: merge following code with SpectralCountQuantifier ---

    require( udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require( msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet(msiIdentResultSets)
    val quantRsId = msiQuantResultSet.getId()

    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)

    // Store master quant result summary
    this.storeMasterQuantResultSummary(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet)

    // Compute master quant peptides
    val mqPeptides = quantifierAlgo.computeMasterQuantPeptides(
      udsMasterQuantChannel,
      this.mergedResultSummary,
      this.identResultSummaries
    )
    
    this.logger.info("storing master peptide quant data...")

    // Iterate over master quant peptides to store them
    val msiMqPepById = mqPeptides.map { mqPeptide =>
      val msiMasterPepInst = mqPeptide.peptideInstance.map( pi => this.msiMasterPepInstById(pi.id) )
      val msiMqPep = this.storeMasterQuantPeptide(mqPeptide, msiQuantRSM, msiMasterPepInst)
      mqPeptide.id -> msiMqPep
    } toMap
    
    // Compute master quant protein sets
    val mqProtSets = quantifierAlgo.computeMasterQuantProteinSets(
      udsMasterQuantChannel,
      mqPeptides,
      this.mergedResultSummary,
      this.identResultSummaries
    )

    this.logger.info("storing master proteins set quant data...")

    // Iterate over master quant protein sets to store them
    val mqProtSetIdsByMqPep = new HashMap[MasterQuantPeptide,ArrayBuffer[Long]]
    
    for (mqProtSet <- mqProtSets) {
      val msiMasterProtSetOpt = this.msiMasterProtSetById.get(mqProtSet.proteinSet.id)
      // FIXME: msiMasterProtSetOpt should be always defined
      if( msiMasterProtSetOpt.isDefined ) {
        this.storeMasterQuantProteinSet(mqProtSet, msiMasterProtSetOpt.get, msiQuantRSM)
      }
      
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
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()

//    // Commit ORM transaction
//    msiEm.getTransaction().commit()
//    udsEm.getTransaction().commit()

    ()

  }

  protected lazy val labelFreeQuantPeptidesSchema = {
	  ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDES.toString())
  }

  protected def buildMasterQuantPeptideObjectTree(mqPep: MasterQuantPeptide): MsiObjectTree = {

    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = this.quantChannelIds.map { quantPeptideMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQPepObjectTree = new MsiObjectTree()
    msiMQPepObjectTree.setSchema(labelFreeQuantPeptidesSchema)
    msiMQPepObjectTree.setClobData(ProfiJson.serialize(quantPeptides))

    msiMQPepObjectTree
  }

  protected lazy val labelFreeQuantPeptideIonsSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDE_IONS.toString())
  }

  protected def buildMasterQuantPeptideIonObjectTree(mqPepIon: MasterQuantPeptideIon): MsiObjectTree = {

    val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
    val quantPepIons = quantChannelIds.map { quantPeptideIonMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQCObjectTree = new MsiObjectTree()
    msiMQCObjectTree.setSchema(labelFreeQuantPeptideIonsSchema)
    //msiMQCObjectTree.setClobData(generate[Array[QuantPeptideIon]](quantPepIons))
    msiMQCObjectTree.setClobData(ProfiJson.serialize(quantPepIons))

    msiMQCObjectTree
  }
  
   protected def getMergedResultSummary(msiDbCtx : DatabaseConnectionContext) : ResultSummary = {
		 createMergedResultSummary(msiDbCtx)
   }
   
   def getResultAsJSON(): String = {
     return "Not Yet Implemented"
   }

}