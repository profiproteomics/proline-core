package fr.proline.core.service.msq.quantify

import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.mzdb.MzDbReader
import fr.profi.mzdb.utils.ms.MsUtils
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.algo.lcms._
import fr.proline.core.algo.msq.config._
import fr.proline.core.algo.msq.summarizing.LabelFreeEntitiesSummarizer
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.LcmsDbFeatureMs2EventTable
import fr.proline.core.dal.tables.lcms.LcmsDbRawMapTable
import fr.proline.core.dal.tables.lcms.LcmsDbScanTable
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.core.om.provider.msi.impl.SQLSpectrumProvider
import fr.proline.core.orm.msi.{ObjectTree => MsiObjectTree, ResultSummary => MsiResultSummary}
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.profi.util.primitives._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.storer.msi.impl.ResetIdsRsmDuplicator
import fr.proline.core.om.storer.msi.impl.ReadBackRsmDuplicator

abstract class AbstractLabelFreeFeatureQuantifier extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  val experimentalDesign: ExperimentalDesign
  val quantConfig: ILabelFreeQuantConfig
  val lcmsMapSet: MapSet
  
  protected val lcmsDbCtx = executionContext.getLCMSDbConnectionContext
  
  protected val masterQc = experimentalDesign.masterQuantChannels.find(_.id == udsMasterQuantChannel.getId).get
    
  protected def quantifyMasterChannel(): Unit = {

    require(udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require(msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet(entityCache.msiIdentResultSets)
    val quantRsId = msiQuantResultSet.getId()

    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)

    // Store master quant result summary
    //this.storeMasterQuantResultSummary(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet)
    // Store master quant result summary
    val quantRSM = if(!isMergedRsmProvided) {
        
        ResetIdsRsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet, msiEm) 
      } else {
        val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)
        val rsmDuplicator = new ReadBackRsmDuplicator(rsmProvider)
        rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet, msiEm) 
    }
    
    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRSM, quantRSM)
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()

    ()
  }
  
  protected def computeAndStoreQuantEntities(msiQuantRSM: MsiResultSummary, quantRSM: ResultSummary) {
    
    val ms2ScanNumbersByFtId = {
      val rawMapIds = lcmsMapSet.getRawMapIds()
      val lcMsScans = entityCache.getLcMsScans(rawMapIds)
      entityCache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)
    }
    
    val entitiesSummarizer = new LabelFreeEntitiesSummarizer(
      lcmsMapSet = lcmsMapSet,
      spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber,
      ms2ScanNumbersByFtId = ms2ScanNumbersByFtId
    )
    
    // Compute master quant peptides and master quant protein sets
    val (mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      masterQc,
      quantRSM,
      entityCache.identResultSummaries
    )
    
    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRSM,mqPeptides,mqProtSets)
  }
  
  protected lazy val quantPeptidesObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDES.toString())
  }

  protected lazy val quantPeptideIonsObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.LABEL_FREE_QUANT_PEPTIDE_IONS.toString())
  }
  
  protected def getMergedResultSummary(msiDbCtx : DatabaseConnectionContext): ResultSummary = {
    isMergedRsmProvided = false
    createMergedResultSummary(msiDbCtx)
  }
   
  def getResultAsJSON(): String = {
    return "Not Yet Implemented"
  }

}