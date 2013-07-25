package fr.proline.core.om.storer.lcms.impl

import com.codahale.jerkson.Json
import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.{ LcmsDbInstrumentTable, LcmsDbRunTable, LcmsDbScanTable }
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.lcms.LcMsScanSequence
import fr.proline.core.om.provider.uds.impl.SQLInstrumentProvider
import fr.proline.core.om.storer.lcms.IScanSequenceStorer

class SQLScanSequenceStorer(lcmsDbCtx: DatabaseConnectionContext) extends IScanSequenceStorer {
  
  val instrumentProvider = new SQLInstrumentProvider(lcmsDbCtx)

  def storeScanSequence(scanSeq: LcMsScanSequence) = {    
    require(scanSeq.instrument.isDefined, "an instrument must be specified for this scan sequence")

    DoJDBCWork.withEzDBC(lcmsDbCtx, { lcmsEzDBC =>
      
      val instrument = scanSeq.instrument.get
      
      // Store instrument in LC-MS database if it is not found
      val lcmsInstrumentOpt = instrumentProvider.getInstrumentAsOption(instrument.id)
      if( lcmsInstrumentOpt.isEmpty ) {
        
        val instrumentInsertQuery = LcmsDbInstrumentTable.mkInsertQuery()
        
        lcmsEzDBC.execute(
          instrumentInsertQuery,
          instrument.id,
          instrument.name,
          instrument.source,
          instrument.properties.map(Json.generate(_))
        )

      }
      
      // Store the run corresponding to this scan sequence
      var runId: Long = 0L
      lcmsEzDBC.executePrepared(LcmsDbRunTable.mkInsertQuery(), true) { statement =>
        
        statement.executeWith(
          scanSeq.runId,
          scanSeq.rawFileName,
          scanSeq.minIntensity,
          scanSeq.maxIntensity,
          scanSeq.ms1ScansCount,
          scanSeq.ms2ScansCount,
          scanSeq.properties.map( Json.generate(_) ),
          scanSeq.instrument.get.id
        )
        runId = statement.generatedLong
      }
  
      // Store the scans
      lcmsEzDBC.executePrepared(LcmsDbScanTable.mkInsertQuery( (t,c) => c.filter(_ != t.ID)), true) { statement =>
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
            scan.properties.map( Json.generate(_) ),
            runId
          )
          
          scan.id = statement.generatedLong
        }
      }
    
    })

  }

}