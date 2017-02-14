package fr.proline.core.om.storer.lcms.impl

import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.storer.lcms.{IRunStorer,IScanSequenceStorer}

class SQLRunStorer(
  val udsDbCtx: DatabaseConnectionContext,
  val scanSeqStorer: Option[IScanSequenceStorer]
) extends IRunStorer {

  def storeLcMsRun(run: LcMsRun) = {
    
    if( scanSeqStorer.isDefined && run.scanSequence.isDefined ) {
      scanSeqStorer.get.storeScanSequence(run.scanSequence.get)
    }
    
    // TODO: store data in the UDSdb run table

    /*
    DoJDBCWork.withEzDBC(udsDbCtx, { ezDBC =>
      
      var runId = 0
      ezDBC.executePrepared(LcmsDbRunTable.mkInsertQuery,true) { statement =>
        statement.executeWith(
          Option.empty[Int],
          run.rawFileName,
          run.minIntensity,
          run.maxIntensity,
          run.ms1ScansCount,
          run.ms2ScansCount,
          run.properties.map( ProfiJson.serialize(_) ),
          run.rawFile.instrument.map( _.id )
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
            scan.properties.map( ProfiJson.serialize(_) ),
            runId
          )
        }
      }
    
    })*/

  }

}