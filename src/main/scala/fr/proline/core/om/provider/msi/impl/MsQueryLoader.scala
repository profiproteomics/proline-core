package fr.proline.core.om.provider.msi.impl

import net.noerd.prequel.DatabaseConfig

class MsQueryLoader( val msiDb: DatabaseConfig ) {
  
  import _root_.fr.proline.core.om.msi.MsQueryClasses._
  import scala.collection.mutable.ArrayBuffer

  def getMsQueries( msiSearchIds: Seq[Int] ): Array[MsQuery] = {
    
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val parentPeaklistIds = msiDb.transaction { tx =>       
      tx.select( "SELECT peaklist_id FROM msi_search" ) { r => r.nextInt.get }          
    }
    
    // Retrieve child peaklist ids if they exist
    val peaklistIds = new ArrayBuffer[Int]    
    for( parentPeaklistId <- parentPeaklistIds ) {

      // Retrieve child peaklist ids corresponding to the current peaklist id
      val childPeaklistIds = msiDb.transaction { tx =>       
        tx.select( "SELECT child_peaklist_id FROM peaklist_relation WHERE parent_peaklist_id = " + parentPeaklistId ) { r =>
          r.nextInt.get
        }          
      }
      
      if( childPeaklistIds.length > 0 ) {  peaklistIds ++= childPeaklistIds }
      else { peaklistIds += parentPeaklistId }
    }
    
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val spectrumHeaders = msiDb.transaction { tx =>       
      tx.select( "SELECT id, title FROM spectrum WHERE peaklist_id IN (" +
                 peaklistIds.mkString(",") + ")" ) { r => 
        (r.nextInt.get, r.nextString.get)
      }
    }
    
    val spectrumTitleById = spectrumHeaders.toMap
    
    // Load MS queries corresponding to the provided MSI search ids
    val msQueries = msiDb.transaction { tx =>       
      tx.select( "SELECT * FROM ms_query WHERE msi_search_id IN (" +
                 msiSearchIds.mkString(",") + ")"  ) { r =>
        val rs = r.rs
             
        // TODO: Parse properties if they exist
        //my $serialized_properties = $ms_query_attrs->{serialized_properties};
        //$ms_query_attrs->{properties} = decode_json( $serialized_properties ) if not is_empty_string($serialized_properties);
        val spectrumId = rs.getInt("spectrum_id")
        
        // Build the MS query object
        var msQuery: MsQuery = null
        if( spectrumId != 0 ) { // we can assume it is a MS2 query
          val spectrumTitle = spectrumTitleById( spectrumId )
          msQuery = new Ms2Query( id = rs.getInt("id"), 
                                  initialId = rs.getInt("initial_id"),
                                  moz = rs.getDouble("moz"),
                                  charge = rs.getInt("charge"),
                                  spectrumTitle = spectrumTitle,
                                  spectrumId = spectrumId                                    
                                )

        } else { 
           msQuery = new MsQuery( id = rs.getInt("id"), 
                                  initialId = rs.getInt("initial_id"),
                                  moz = rs.getDouble("moz"),
                                  charge = rs.getInt("charge")
                                 )
        }
    
        msQuery
      }
    }
    
    msQueries.toArray
  }
  
}

