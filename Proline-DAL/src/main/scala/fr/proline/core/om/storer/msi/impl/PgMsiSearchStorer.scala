package fr.proline.core.om.storer.msi.impl

import com.weiglewilczek.slf4s.Logging
import org.postgresql.copy.{CopyIn,CopyManager}
import org.postgresql.core.BaseConnection
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.MsiDb
import fr.proline.core.dal.{MsiDbMsQueryTable,MsiDbPtmSpecificityTable}
import fr.proline.core.utils.sql._
import fr.proline.core.om.model.msi._
import fr.proline.core.dal.MsiDbUsedPtmTable


class PgMsiSearchStorer( val msiDb: MsiDb ) extends SQLiteMsiSearchStorer( msiDb ) with Logging {
  
  val bulkCopyManager = new CopyManager( msiDb.getOrCreateConnection().asInstanceOf[BaseConnection] )
  
  override def storeMsQueries( msiSearch: MSISearch,
                               msQueries: Seq[MsQuery],
                               spectrumIdByTitle: Map[String,Int] ): Unit = {
    
    val msiSearchId = msiSearch.id    
    
    // Create TMP table
    val tmpMsQueryTableName = "tmp_ms_query_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpMsQueryTableName +"'..." )
    
    val stmt = this.msiDb.connection.createStatement()
    stmt.executeUpdate("CREATE TEMP TABLE "+tmpMsQueryTableName+" (LIKE ms_query)")    
    
    // Bulk insert of MS queries
    logger.info( "BULK insert of MS queries" )
    
    val msQueryTableCols = MsiDbMsQueryTable.getColumnsAsStrList().filter( _ != "id" ).mkString(",")    
    val pgBulkLoader = bulkCopyManager.copyIn("COPY "+ tmpMsQueryTableName +" ( id, "+ msQueryTableCols + " ) FROM STDIN" )
    
    for( msQuery <- msQueries ) {
      
      msQuery.msLevel match {
        case 1 => this._copyMsQuery( pgBulkLoader, msQuery.asInstanceOf[Ms1Query], msiSearchId, Option.empty[Int] )
        case 2 => {
          val ms2Query = msQuery.asInstanceOf[Ms2Query]
          // FIXME: it should not be null
          var spectrumId = Option.empty[Int]
          if( spectrumIdByTitle != null ) {
            ms2Query.spectrumId = spectrumIdByTitle(ms2Query.spectrumTitle)
            spectrumId = Some(ms2Query.spectrumId)
          }
          this._copyMsQuery( pgBulkLoader, msQuery, msiSearchId, spectrumId )
        }
      }
      
    }
    
    // End of BULK copy
    val nbInsertedMsQueries = pgBulkLoader.endCopy()
    
    // Move TMP table content to MAIN table
    logger.info( "move TMP table "+ tmpMsQueryTableName +" into MAIN ms_query table" )
    stmt.executeUpdate("INSERT into ms_query ("+msQueryTableCols+") "+
                       "SELECT "+msQueryTableCols+" FROM "+tmpMsQueryTableName )
    
    // Retrieve generated spectrum ids
    val msQueryIdByInitialId = this.msiDb.getOrCreateTransaction.select(
                                 "SELECT initial_id, id FROM ms_query WHERE msi_search_id = " + msiSearchId ) { r => 
                                   (r.nextInt.get -> r.nextInt.get)
                                 } toMap
    
    // Iterate over MS queries to update them
    msQueries.foreach { msQuery => msQuery.id = msQueryIdByInitialId( msQuery.initialId ) }
     
  }
  
  private def _copyMsQuery( pgBulkLoader: CopyIn, msQuery: MsQuery, msiSearchId: Int, spectrumId: Option[Int] ): Unit = {
    
    import com.codahale.jerkson.Json.generate
    
    //if( spectrumId <= 0 )
      //throw new Exception("spectrum must first be persisted")
    
    val spectrumIdAsStr = if( spectrumId == None ) "" else spectrumId.get.toString
    val msqPropsAsJSON = if( msQuery.properties != None ) generate(msQuery.properties.get) else ""
    
    // Build a row containing MS queries values
    val msQueryValues = List(
                            msQuery.id,
                            msQuery.initialId,
                            msQuery.charge,
                            msQuery.moz,
                            msqPropsAsJSON,
                            spectrumIdAsStr,
                            msiSearchId
                            )
    
    // Store MS query
    val msQueryBytes = encodeRecordForPgCopy( msQueryValues )
    pgBulkLoader.writeToCopy( msQueryBytes, 0, msQueryBytes.length )
    
  }
  
  
  override protected def _insertUsedPTM( ssId: Int, ptmDef: PtmDefinition, isFixed: Boolean ): Unit = {
    
    // Check if the PTM specificity exists in the MSIdb
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    val count = msiDbTx.selectInt( "SELECT count(*) FROM ptm_specificity WHERE id =" + ptmDef.id )
    
    // Insert PTM specificity if it doesn't exist in the MSIdb
    if( count == 0 ) {
      val ptmSpecifColsList = MsiDbPtmSpecificityTable.getColumnsAsStrList()
      val ptmSpecifInsertQuery = MsiDbPtmSpecificityTable.makeInsertQuery( ptmSpecifColsList )      
      
      msiDbTx.executeBatch( ptmSpecifInsertQuery, false ) { stmt =>{        
        val residueAsStr = if(ptmDef.residue == '\0') "" else ptmDef.residue.toString
        logger.debug(" Execute "+ptmSpecifInsertQuery+" with "+ ptmDef.id+" - "+ptmDef.location+" - "+residueAsStr)
        stmt.executeWith(
              ptmDef.id,
              ptmDef.location,
              residueAsStr,
              Option(null)
              )
      }
      }
    }
    
    // Link used PTMs to search settings
    val usedPtmColsList = MsiDbUsedPtmTable.getColumnsAsStrList()
    val usedPtmInsertQuery = MsiDbUsedPtmTable.makeInsertQuery( usedPtmColsList )
    msiDbTx.executeBatch( usedPtmInsertQuery, false ) { stmt =>
      stmt.executeWith(
            ssId,
            ptmDef.id,
            ptmDef.names.shortName,
            isFixed,
            Option(null)
            )
            
    }
    
  }
}


