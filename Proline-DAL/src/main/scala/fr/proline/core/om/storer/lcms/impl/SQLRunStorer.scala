package fr.proline.core.om.storer.lcms.impl

import fr.proline.core.om.storer.lcms.IRunStorer
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.dal.LcmsDb
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import com.codahale.jerkson.Json.generate
import fr.proline.core.om.model.lcms.Instrument
import java.io.File
  
class SQLRunStorer(lcmsDb : LcmsDb) extends IRunStorer{
  
  def storeLcmsRun(run: LcmsRun, instrument: Instrument) = {
    
    //implicit def string2File(tsvFile: String) = new File(tsvFile) 
    
    val lcmsDbConn = lcmsDb.getOrCreateConnection()
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    var runId = 0
    lcmsDbTx.executeBatch("INSERT INTO run VALUES (?, ?, ?, ?, ?, ?, ?, ?)") { statement =>
    	statement.executeWith(
    	    Option.empty[Int],
            run.rawFileName,
            run.minIntensity,
            run.maxIntensity,
            run.ms1ScanCount,
            run.ms2ScanCount,
            if( run.properties != None ) Some(generate(run.properties)) else Option.empty[String],
            instrument.id
        )
        runId = lcmsDb.extractGeneratedInt( statement.wrapped ) 
    }
    
    
    lcmsDbTx.executeBatch("INSERT INTO scan VALUES ("+ ("?") * 12 +")") { statement =>
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
      			if( scan.properties != None ) Some(generate(scan.properties)) else Option.empty[String],
      			runId
      		)
      		}
    }
    
  }

}