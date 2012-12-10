package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.MsiDbMsQueryTable
import fr.proline.core.om.model.msi.{MsQuery,Ms1Query,Ms2Query,MsQueryProperties}
import fr.proline.core.om.provider.msi.IMsQueryProvider

class SQLMsQueryProvider( val sqlExec: SQLQueryExecution ) extends IMsQueryProvider {
  
  import fr.proline.util.primitives.LongOrIntAsInt._
  import scala.collection.mutable.ArrayBuffer
  val MsQueryCols = MsiDbMsQueryTable.columns

  def getMsiSearchesMsQueries( msiSearchIds: Seq[Int] ): Array[MsQuery] = {
    
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val parentPeaklistIds = sqlExec.select( "SELECT peaklist_id FROM msi_search" ) { r => r.nextInt }    
    
    // Retrieve child peaklist ids if they exist
    val pklIds = new ArrayBuffer[Int]    
    for( parentPeaklistId <- parentPeaklistIds ) {

      // Retrieve child peaklist ids corresponding to the current peaklist id
      val childPeaklistIds = sqlExec.select( "SELECT child_peaklist_id FROM peaklist_relation WHERE parent_peaklist_id = " + parentPeaklistId ) { r =>
                                        r.nextInt
                                      }
      
      if( childPeaklistIds.length > 0 ) { pklIds ++= childPeaklistIds }
      else { pklIds += parentPeaklistId }
    }
    
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val spectrumHeaders = sqlExec.select( "SELECT id, title FROM spectrum WHERE peaklist_id IN ("+ pklIds.mkString(",") +")" ) { r => 
                                    (r.nextInt, r.nextString)
                                  }
    
    val spectrumTitleById = spectrumHeaders.toMap
    
    // Load MS queries corresponding to the provided MSI search ids
    val msQueries = sqlExec.select( "SELECT * FROM ms_query WHERE msi_search_id IN ("+ msiSearchIds.mkString(",") +")" ) { r =>
      val rs = r.rs
           
      // Parse properties if they exist
      //my $serialized_properties = $ms_query_attrs->{serialized_properties};
      //$ms_query_attrs->{properties} = decode_json( $serialized_properties ) if not is_empty_string($serialized_properties);
      val spectrumId = rs.getInt(MsQueryCols.spectrumId)
      
      // Decode JSON properties
      val propertiesAsJSON = rs.getString(MsQueryCols.serializedProperties)
      var properties = Option.empty[MsQueryProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[MsQueryProperties](propertiesAsJSON) )
      }
      
      val msQueryId: Int = rs.getObject(MsQueryCols.id).asInstanceOf[AnyVal]
      
      // Build the MS query object
      var msQuery: MsQuery = null
      if( spectrumId != 0 ) { // we can assume it is a MS2 query
        val spectrumTitle = spectrumTitleById( spectrumId )
        msQuery = new Ms2Query( id = msQueryId,
                                initialId = rs.getInt(MsQueryCols.initialId),
                                moz = rs.getDouble(MsQueryCols.moz),
                                charge = rs.getInt(MsQueryCols.charge),
                                spectrumTitle = spectrumTitle,
                                spectrumId = spectrumId,
                                properties = properties
                              )

      } else { 
         msQuery = new Ms1Query( id = msQueryId, 
                                 initialId = rs.getInt(MsQueryCols.initialId),
                                 moz = rs.getDouble(MsQueryCols.moz),
                                 charge = rs.getInt(MsQueryCols.charge),
                                 properties = properties
                               )
      }
  
      msQuery
    }
    
    
    msQueries.toArray
  }
  
}

