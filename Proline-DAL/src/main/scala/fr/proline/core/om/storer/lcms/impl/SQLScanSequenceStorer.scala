package fr.proline.core.om.storer.lcms.impl

import com.codahale.jerkson.Json.generate

import fr.profi.jdbc.easy._

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.{ LcmsDbRunTable, LcmsDbScanTable }
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.lcms.LcMsScanSequence
import fr.proline.core.om.storer.lcms.IScanSequenceStorer

class SQLScanSequenceStorer(lcmsDbCtx: DatabaseConnectionContext) extends IScanSequenceStorer {

  def storeScanSequence(scanSeq: LcMsScanSequence) = {

    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      var runId: Long = 0L
      ezDBC.executePrepared(LcmsDbRunTable.mkInsertQuery,true) { statement =>
        statement.executeWith(
          Option.empty[Int],
          scanSeq.rawFileName,
          scanSeq.minIntensity,
          scanSeq.maxIntensity,
          scanSeq.ms1ScansCount,
          scanSeq.ms2ScansCount,
          scanSeq.properties.map( generate(_) ),
          scanSeq.instrumentId
        )
        runId = statement.generatedLong
      }
  
      ezDBC.executePrepared(LcmsDbScanTable.mkInsertQuery,true) { statement =>
        scanSeq.scans.foreach { scan =>
          statement.executeWith(
            Option.empty[Int],
            scan.initialId,
            scan.cycle,
            scan.time,
            scan.msLevel,
            scan.tic,
            scan.basePeakMoz,
            scan.basePeakIntensity,
            scan.precursorMoz,
            scan.precursorCharge,
            scan.properties.map( generate(_) ),
            runId
          )
          
          scan.id = statement.generatedLong
        }
      }
    
    })

  }

}