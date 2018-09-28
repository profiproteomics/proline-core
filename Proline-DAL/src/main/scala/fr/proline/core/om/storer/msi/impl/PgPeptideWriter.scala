package fr.proline.core.om.storer.msi.impl

import java.sql.Connection
import org.postgresql.copy.CopyIn
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.sql.encodeRecordForPgCopy
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.MsiDbPeptideTable
import fr.proline.core.dal.tables.msi.MsiDbPeptidePtmTable
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.IPeptideWriter
import fr.proline.repository.util.PostgresUtils

private[msi] object PgPeptideWriter extends IPeptideWriter with LazyLogging {

  private val pepTableName = MsiDbPeptideTable.name
  private val pepPtmTableName = MsiDbPeptidePtmTable.name
    
  def insertPeptides(peptides: Seq[Peptide], context: StorerContext): Unit = {
    
    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>
      
      // Retrieve last inserted peptide id
      val countInsertedPeptideId = msiEzDBC.selectInt("SELECT count(id) FROM " + pepTableName)      
      val lastInsertedPeptideId = if(countInsertedPeptideId > 0) { msiEzDBC.selectLong("SELECT max(id) FROM " + pepTableName) } else {0} 
     
      // Bulk insert of peptides
      logger.info("BULK insert of peptides")

      val peptideTableColsWithoutId = MsiDbPeptideTable.columnsAsStrList.filter(_ != "id").mkString(",")     
      val con = msiEzDBC.connection
      val bulkCopyManager =  PostgresUtils.getCopyManager(con)
      val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $pepTableName ( $peptideTableColsWithoutId ) FROM STDIN")

      for (peptide <- peptides) {

        // Build a row containing peptide values
        val peptideValues = List(
          peptide.sequence,
          Option(peptide.ptmString),
          peptide.calculatedMass,
          peptide.properties.map(ProfiJson.serialize(_)),
          // TODO: handle atom label
          Option.empty[Long]
        )
    
        // Store peptide
        val peptideBytes = encodeRecordForPgCopy(peptideValues)
        pgBulkLoader.writeToCopy(peptideBytes, 0, peptideBytes.length)
      }

      // End of BULK copy
      val nbInsertedPeptides = pgBulkLoader.endCopy()      
      logger.info(nbInsertedPeptides + " inserted peptide records during BULK copy")

      
      val newInsertedPeptidesQuery = new SelectQueryBuilder1(MsiDbPeptideTable).mkSelectQuery( (t,c) => 
        List(t.ID,t.SEQUENCE,t.PTM_STRING) -> "WHERE "~ t.ID ~" > ?"
      )

      // Retrieve generated peptide ids
      val newPepIdByKey = Map() ++ msiEzDBC.select(newInsertedPeptidesQuery, lastInsertedPeptideId) { r =>
        val id = r.nextLong
        val sequence = r.nextString
        val ptmString = r.nextStringOrElse("")
        val uniqueKey = sequence + '%' + ptmString
        (uniqueKey, id)
      }

      // Iterate over peptides to update them
      peptides.foreach { peptide => peptide.id = newPepIdByKey(peptide.uniqueKey) }
      
      // Bulk insert of peptide PTMs
      this._insertPeptidePtms(peptides, msiEzDBC)

    } // End of jdbcWork

    ()
  }
  
  private def _insertPeptidePtms(peptides: Seq[Peptide], msiEzDBC: EasyDBC): Unit = {
      
    val con = msiEzDBC.connection

    // Bulk insert of peptide PTMs
    logger.info("BULK insert of peptide PTMs")

    val peptidePtmTableColsWithoutPK = MsiDbPeptidePtmTable.columnsAsStrList.filter(_ != "id").mkString(",")
    val bulkCopyManager = PostgresUtils.getCopyManager(con)
    val pgBulkLoader = bulkCopyManager.copyIn(s"COPY $pepPtmTableName ($peptidePtmTableColsWithoutPK ) FROM STDIN")

    for (peptide <- peptides; if peptide.ptms != null; locatedPtm <- peptide.ptms) {

      // Build a row containing peptide PTM values
      val peptidePtmValues = List(
        locatedPtm.seqPosition,
        locatedPtm.monoMass,
        locatedPtm.averageMass,
        Option.empty[String],
        peptide.id,
        locatedPtm.definition.id,
        // TODO: handle atom label
        Option.empty[Long]
      )
  
      // Store peptide PTM
      val peptidePtmBytes = encodeRecordForPgCopy(peptidePtmValues)
      pgBulkLoader.writeToCopy(peptidePtmBytes, 0, peptidePtmBytes.length)
    }

    // End of BULK copy
    val nbInsertedPeptidePtms = pgBulkLoader.endCopy()    
    logger.info(nbInsertedPeptidePtms + " inserted peptide PTM records during BULK copy")

    ()
  }

}


