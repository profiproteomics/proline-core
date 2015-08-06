package fr.proline.core.service.msq.quantify

import javax.persistence.EntityManager
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.HashMap
import fr.profi.util.primitives._
import fr.profi.jdbc.easy._
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.lcms.LabelFreeQuantConfig
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.{Peptide, PeptideMatch}
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.profi.mzdb.MzDbReader
import fr.profi.mzdb.utils.ms.MsUtils

class Ms2DrivenLabelFreeFeatureQuantifier(
  val executionContext: IExecutionContext,
  val experimentalDesign: ExperimentalDesign,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val quantConfig: LabelFreeQuantConfig
) extends AbstractLabelFreeFeatureQuantifier {
  
  try {
    // Check that spectrum.first_scan column is filled
    this.checkSpectraHaveFirstScanId()
  } catch {
    // TODO: don't catch me
    case e: Exception => logger.error("Error during update of spectrum.first_scan column", e)
  }
  
  def checkSpectraHaveFirstScanId() {
    
    val specCols = MsiDbSpectrumTable.columns
    val idColName = specCols.ID
    val precMozColName = specCols.PRECURSOR_MOZ
    val firstScanColName = specCols.FIRST_SCAN
    val firstCycleColName = specCols.FIRST_CYCLE
    val peaklistIdColName = specCols.PEAKLIST_ID
    val ms2SHs = this.loadMs2SpectrumHeaders()
    
    // Create a mapping between cycles and scan ids
    val incompleteShCycleByPklIdAndSpecId = new HashMap[Long,HashMap[Long,Int]]()
    
    for (sh <- ms2SHs) {

      if( sh(firstScanColName) == null ) {
        val shId = sh(idColName)
        val shCycle = sh(firstCycleColName)
        require(
          shCycle != null,
          "at least scan id or a cycle number must be defined for MS2 spectrum id="+shId
        )
        
        val peaklistId = toLong(sh(peaklistIdColName))
        val incompleteShCycleBySpecId = incompleteShCycleByPklIdAndSpecId.getOrElseUpdate(peaklistId, new HashMap[Long,Int])

        incompleteShCycleBySpecId += toLong(shId) -> toInt(shCycle)
      }
    }
    
    if( incompleteShCycleByPklIdAndSpecId.isEmpty == false ) {
      logger.warn("Spectrum table has missing first scan ids and will be now updated, cross fingers...")
      
      // Create some mappings
      val precMzBySpecId = ms2SHs.view.map { ms2Sh => toLong(ms2Sh(idColName)) -> toDouble(ms2Sh(precMozColName)) } toMap
      val peaklistIdByIdentRsId = msiIdentResultSets.view.map { rs => rs.getId -> rs.getMsiSearch.getPeaklist.getId } toMap
      
      val mzDbFilePathByRawFileName = quantConfig.lcMsRuns.view.map { lcMsRun =>
        lcMsRun.rawFile.name -> lcMsRun.rawFile.getMzdbFilePath().get
      } toMap
      
      val scanIdBySpecId = new HashMap[Long,Int]()
      
      // Iterate over quant channels to update incomplete spectra headers
      for( udsQuantChannel <- udsQuantChannels ) {
        val identRsId = identRsIdByRsmId( udsQuantChannel.getIdentResultSummaryId() )
        val peaklistId = peaklistIdByIdentRsId(identRsId)
        
        val incompleteShCycleBySpecIdOpt = incompleteShCycleByPklIdAndSpecId.get(peaklistId)
        if( incompleteShCycleBySpecIdOpt.isDefined ) {
          val incompleteShCycleBySpecId = incompleteShCycleBySpecIdOpt.get
          val cycles = incompleteShCycleBySpecId.values.toArray
          
          val mzDbFilePath = mzDbFilePathByRawFileName(udsQuantChannel.getRun().getRawFile().getRawFileName())
          val mzDbReader = new MzDbReader(mzDbFilePath, true)
          
          try {
            val mzDbScanHeaders = mzDbReader.getMs2ScanHeaders()
            
            val mzDbScanHeadersByCycle = mzDbScanHeaders.groupBy(_.getCycle())
            for( (specId,cycle) <- incompleteShCycleBySpecId ) {
              require( mzDbScanHeadersByCycle.contains(cycle), s"can't find cycle $cycle in mzDB file: " + mzDbFilePath)
              
              val mzDbSHsInCurCycle = mzDbScanHeadersByCycle(cycle)
              val matchingMzDbScanHeaders = mzDbSHsInCurCycle.filter { mzDbSH =>
                val precMz = mzDbSH.getPrecursorMz()
                val ms2MatchingMzTolDa = MsUtils.ppmToDa(precMz, quantConfig.ftMappingParams.mozTol)
                
                math.abs(precMz - precMzBySpecId(specId)) <= ms2MatchingMzTolDa
              }
              
              require( matchingMzDbScanHeaders.length < 2, "multiple mzDB spectra are matching this MSIdb spectrum header" )
              require( matchingMzDbScanHeaders.length > 0, "can't find a mzDB spectrum matching this MSIdb spectrum header" )
              
              scanIdBySpecId += specId -> matchingMzDbScanHeaders.head.getInitialId()
            }
          } finally {
            mzDbReader.close()
          }

        }
      }
      
      DoJDBCReturningWork.withEzDBC( msiDbCtx, { msiEzDBC =>
        
        val updateSqlQuery = s"UPDATE ${MsiDbSpectrumTable.name} SET ${specCols.FIRST_SCAN} = ? WHERE ${specCols.ID} = ?"

        msiEzDBC.executePrepared(updateSqlQuery) { stmt =>
          for( (specId,scanId) <- scanIdBySpecId ) {
            stmt.executeWith(scanId,specId)
          }
        }
      })
    }

  }
  
  lazy val runIdByRsmId = {
    Map() ++ udsMasterQuantChannel.getQuantitationChannels().map { udsQC =>
      udsQC.getIdentResultSummaryId() -> udsQC.getRun().getId()
    }
  }
  
  // TODO: try to handle PSMs with rank > 1
  lazy val (peptideByRunIdAndScanNumber, psmByRunIdAndScanNumber) = {
    
    val peptideMap = new collection.mutable.HashMap[Long, HashMap[Int, Peptide]]()
    val psmMap = new collection.mutable.HashMap[Long, HashMap[Int, PeptideMatch]]()
    
    for( rsm <- this.identResultSummaries ) {
      val runId = runIdByRsmId(rsm.id)
      val valPepMatchIds = rsm.peptideInstances.flatMap( _.getPeptideMatchIds )
      val pepMatchById = rsm.resultSet.get.getPeptideMatchById
      
      for( valPepMatchId <- valPepMatchIds ) {
        val valPepMatch = pepMatchById(valPepMatchId)
        // FIXME: how to deal with other ranked PSMs ?
        if( valPepMatch.rank == 1 ) {
          val spectrumId = valPepMatch.getMs2Query.spectrumId

          val scanNumber = this.scanNumberBySpectrumId(spectrumId)
          psmMap.getOrElseUpdate(runId, new HashMap[Int, PeptideMatch])(scanNumber) = valPepMatch
          peptideMap.getOrElseUpdate(runId, new HashMap[Int, Peptide])(scanNumber) = valPepMatch.peptide
        } else {
          this.logger.trace(s"Peptide ${valPepMatch.peptide.sequence} id=${valPepMatch.peptideId} will be ignored (rank > 1)")
        }
      }
    }
    
    (peptideMap.toMap, psmMap.toMap)
  }
  
  // Extract the LC-MS map set
  lazy val lcmsMapSet: MapSet = {
    val mapSetExtractor = new ExtractMapSet(this.lcmsDbCtx,quantConfig, Some(peptideByRunIdAndScanNumber), Some(psmByRunIdAndScanNumber) )
    mapSetExtractor.run()
    mapSetExtractor.extractedMapSet
  }
  
  // Add processings specific to the MS2 driven strategy here
  override protected def quantifyMasterChannel(): Unit = {
    
    // Retrieve LC-MS maps ids mapped by the run id
    val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map( lcmsMap => lcmsMap.runId.get -> lcmsMap.id )
    
    // Update the LC-MS map id of each master quant channel
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    for( udsQuantChannel <- udsQuantChannels) {
      udsQuantChannel.setLcmsMapId( lcMsMapIdByRunId( udsQuantChannel.getRun().getId ) )
      udsEm.merge(udsQuantChannel)
    }
    
    // Update the map set id of the master quant channel
    udsMasterQuantChannel.setLcmsMapSetId(lcmsMapSet.id)
    udsEm.merge(udsMasterQuantChannel)
    
    super.quantifyMasterChannel()
  }

}