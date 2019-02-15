package fr.proline.core.om.storer.lcms.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.lcms.{  LcmsDbScanTable, LcmsDbScanSequenceTable }
import fr.proline.core.om.model.lcms.LcMsScanSequence
import fr.proline.core.om.provider.uds.impl.SQLInstrumentProvider
import fr.proline.core.om.storer.lcms.IScanSequenceStorer

class SQLScanSequenceStorer(lcmsDbCtx: LcMsDbConnectionContext) extends IScanSequenceStorer with LazyLogging {
  
  val instrumentProvider = new SQLInstrumentProvider(lcmsDbCtx)

  def storeScanSequence(scanSeq: LcMsScanSequence) = {    

    DoJDBCWork.withEzDBC(lcmsDbCtx) { lcmsEzDBC =>


      // Store the run corresponding to this scan sequence
      lcmsEzDBC.executePrepared(LcmsDbScanSequenceTable.mkInsertQuery(), false) { statement =>
        
        statement.executeWith(
          scanSeq.runId,
          scanSeq.rawFileIdentifier,
          scanSeq.minIntensity,
          scanSeq.maxIntensity,
          scanSeq.ms1ScansCount,
          scanSeq.ms2ScansCount,
          scanSeq.properties.map( ProfiJson.serialize(_) )
        )
      }
  
      // Store the scans
      // TODO: use PgCopy to make this insert faster
      lcmsEzDBC.executeInBatch(LcmsDbScanTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID))) { statement =>
        scanSeq.scans.foreach { scan =>
          statement.executeWith(
            scan.initialId,
            scan.cycle,
            scan.time,
            scan.msLevel,
            scan.tic,
            scan.basePeakMoz,
            scan.basePeakIntensity,
            scan.precursorMoz,
            scan.precursorCharge,
            scan.properties.map( ProfiJson.serialize(_) ),
            scanSeq.runId
          )
          
          //scan.id = statement.generatedLong
        }
      }
    
      // Prepare SQL query to retrieve generated scan IDs
      val scanIdSqlQuery = new fr.proline.core.dal.tables.SelectQueryBuilder1(LcmsDbScanTable).mkSelectQuery { (t,c) =>
        List(t.INITIAL_ID, t.ID) -> " WHERE " ~ t.SCAN_SEQUENCE_ID ~ " = "~ scanSeq.runId
    }

      // Retrieve and update scan.id property
      val scanByInitialId = scanSeq.scans.mapByLong(_.initialId)
      lcmsEzDBC.selectAndProcess(scanIdSqlQuery) { r =>
        scanByInitialId(r.nextInt).id = r.nextLong
  }

    }

  }

}