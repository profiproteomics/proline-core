package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.SQLQueryExecution
import fr.proline.util.primitives._

class MapAlignmentSetLoader( val sqlExec: SQLQueryExecution ) {
  
  import java.util.HashMap
  import scala.collection.mutable.ArrayBuffer
  
  import fr.proline.core.om.model.lcms._
  
  def getMapAlignmentSet( fromMapId: Int,toMapId: Int): Unit = {
    // TODO: implement this loader
/*
##############################################################################
  # Method: get_map_alignment_set()
  #
  method get_map_alignment_set( Int $from_map_id!, Int $to_map_id! ) {
    
    ### Retrieve Map alignments
    my $rdb_map_alns = Pairs::Lcms::RDBO::MapAlignment::Manager->get_map_alignments(
                          query => [ from_map_id => $from_map_id , to_map_id => $to_map_id],
                          db => $self->lcms_rdb
                          );
    return undef if !defined $rdb_map_alns or scalar(@$rdb_map_alns) == 0;
    
    return $self->_rdb_alns_to_model_aln_set($rdb_map_alns);
    }
  */
    ()
  }
  
  def getMapAlignmentSets( mapSetId: Int ): Array[MapAlignmentSet] = {
    
    var colNames: Seq[String] = null
    
    // Load processed map features
    val mapAlns = sqlExec.select( "SELECT * FROM map_alignment WHERE map_set_id = " + mapSetId ) { r =>
        
      if( colNames == null ) { colNames = r.columnNames }
      
      // Build the map record
      val mapAlnRecord = colNames.map( colName => ( colName -> r.nextAnyRef ) ).toMap
      buildMapAlignment( mapAlnRecord )
      
    }
    
    val mapAlnByKey = mapAlns.groupBy( mapAln => mapAln.refMapId -> mapAln.targetMapId )
    
    val mapAlnSetBuffer = new ArrayBuffer[MapAlignmentSet](mapAlnByKey.size)
    for( ( alnSetKey, mapAlns) <- mapAlnByKey) {
      val refMapId = alnSetKey._1
      val targetMapId = alnSetKey._2
      
      mapAlnSetBuffer += new MapAlignmentSet( refMapId = refMapId, targetMapId = targetMapId, mapAlignments = mapAlns.toArray )
    }
    
    mapAlnSetBuffer.toArray
    
  }
  
  def buildMapAlignment( mapAlnRecord: Map[String,Any] ): MapAlignment = {
    
    val massStart = mapAlnRecord("mass_start").asInstanceOf[Double]
    val massEnd = mapAlnRecord("mass_end").asInstanceOf[Double]
    val timeList = mapAlnRecord("time_list").asInstanceOf[String].split(" ") map { _.toFloat }
    val deltaTimeList = mapAlnRecord("delta_time_list").asInstanceOf[String].split(" ") map { _.toFloat }
    
    new MapAlignment( refMapId = toLong(mapAlnRecord("from_map_id")),
                      targetMapId = toLong(mapAlnRecord("to_map_id")),
                      massRange = (massStart,massEnd),
                      timeList = timeList,
                      deltaTimeList = deltaTimeList
                    )
  }
  
}