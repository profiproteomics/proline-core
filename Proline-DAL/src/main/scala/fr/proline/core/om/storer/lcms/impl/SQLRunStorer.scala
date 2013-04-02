package fr.proline.core.om.storer.lcms.impl

import com.codahale.jerkson.Json.generate

import fr.profi.jdbc.easy._

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.{ LcmsDbRunTable, LcmsDbScanTable }
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.storer.lcms.IRunStorer

class SQLRunStorer(lcmsDbCtx: DatabaseConnectionContext) extends IRunStorer {

  def storeLcmsRun(run: LcMsRun, instrument: Instrument) = {

    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      var runId = 0
      ezDBC.executePrepared(LcmsDbRunTable.mkInsertQuery,true) { statement =>
        statement.executeWith(
          Option.empty[Int],
          run.rawFileName,
          run.minIntensity,
          run.maxIntensity,
          run.ms1ScanCount,
          run.ms2ScanCount,
          run.properties.map( generate(_) ),
          instrument.id
        )
        runId = statement.generatedInt
      }
  
      ezDBC.executePrepared(LcmsDbScanTable.mkInsertQuery,true) { statement =>
        run.scans.foreach { scan =>
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
        }
      }
    
    })

  }

}