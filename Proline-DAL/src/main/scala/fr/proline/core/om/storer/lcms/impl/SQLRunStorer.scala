package fr.proline.core.om.storer.lcms.impl

import com.codahale.jerkson.Json.generate

import fr.profi.jdbc.SQLQueryExecution
import fr.profi.jdbc.easy._

import fr.proline.core.om.model.lcms.Instrument
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.storer.lcms.IRunStorer

class SQLRunStorer(lcmsDb: SQLQueryExecution) extends IRunStorer {

  def storeLcmsRun(run: LcmsRun, instrument: Instrument) = {

    //implicit def string2File(tsvFile: String) = new File(tsvFile) 

    val lcmsDbConn = lcmsDb.connection

    var runId = 0
    lcmsDb.executePrepared("INSERT INTO run VALUES (?, ?, ?, ?, ?, ?, ?, ?)") { statement =>
      statement.executeWith(
        Option.empty[Int],
        run.rawFileName,
        run.minIntensity,
        run.maxIntensity,
        run.ms1ScanCount,
        run.ms2ScanCount,
        if (run.properties != None) Some(generate(run.properties)) else Option.empty[String],
        instrument.id
      )
      runId = statement.generatedInt
    }

    lcmsDb.executePrepared("INSERT INTO scan VALUES (" + ("?") * 12 + ")") { statement =>
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
          if (scan.properties != None) Some(generate(scan.properties)) else Option.empty[String],
          runId
        )
      }
    }

  }

}