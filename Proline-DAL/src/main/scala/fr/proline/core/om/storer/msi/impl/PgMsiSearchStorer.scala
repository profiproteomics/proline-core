package fr.proline.core.om.storer.msi.impl

import com.weiglewilczek.slf4s.Logging
import org.postgresql.copy.{CopyIn,CopyManager}
import org.postgresql.core.BaseConnection

import fr.proline.core.dal.{SQLQueryHelper,MsiDbMsQueryTable,MsiDbPtmSpecificityTable}
import fr.proline.util.sql._
import fr.proline.core.om.model.msi._
import fr.proline.core.dal.MsiDbUsedPtmTable


class PgMsiSearchStorer( val msiDb: SQLQueryHelper ) extends SQLiteMsiSearchStorer( msiDb ) with Logging {
  
  val connection = msiDb.ezDBC.connection
  val bulkCopyManager = new CopyManager( msiDb.ezDBC.connection.asInstanceOf[BaseConnection] )
  
  override def storeMsQueries( msiSearchId: Int, msQueries: Seq[MsQuery], context: StorerContext ): StorerContext = {
    
    // Create TMP table
    val tmpMsQueryTableName = "tmp_ms_query_" + ( scala.math.random * 1000000 ).toInt
    logger.info( "creating temporary table '" + tmpMsQueryTableName +"'..." )
    
    val stmt = connection.createStatement()
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
          if( context.spectrumIdByTitle != null ) {
            ms2Query.spectrumId = context.spectrumIdByTitle(ms2Query.spectrumTitle)
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
    val msQueryIdByInitialId = msiDb.ezDBC.select(
                                 "SELECT initial_id, id FROM ms_query WHERE msi_search_id = " + msiSearchId ) { r => 
                                   (r.nextInt -> r.nextInt)
                                 } toMap
    
    // Iterate over MS queries to update them
    msQueries.foreach { msQuery => msQuery.id = msQueryIdByInitialId( msQuery.initialId ) }
                            
    context
  }
  
  private def _copyMsQuery( pgBulkLoader: CopyIn, msQuery: MsQuery, msiSearchId: Int, spectrumId: Option[Int] ): Unit = {
    
    import com.codahale.jerkson.Json.generate
    
    //if( spectrumId <= 0 )
      //throw new Exception("spectrum must first be persisted")
    
    //val spectrumIdAsStr = if( spectrumId == None ) "" else spectrumId.get.toString
    val msqPropsAsJSON = if( msQuery.properties != None ) Some( generate(msQuery.properties.get) ) else None
    
    // Build a row containing MS queries values
    val msQueryValues = List(
                            msQuery.id,
                            msQuery.initialId,
                            msQuery.charge,
                            msQuery.moz,
                            msqPropsAsJSON,
                            spectrumId,
                            msiSearchId
                            )
    
    // Store MS query
    val msQueryBytes = encodeRecordForPgCopy( msQueryValues )
    pgBulkLoader.writeToCopy( msQueryBytes, 0, msQueryBytes.length )
    
  }
  
}


