package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.jdbc.ResultSetRow
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.LcmsDbMapAlignmentTable
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IMapAlignmentSetProvider

class SQLMapAlignmentSetProvider( val lcmsDbCtx: DatabaseConnectionContext ) extends IMapAlignmentSetProvider {
  
  val MapAlnCols = LcmsDbMapAlignmentTable.columns
  
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
    
    // Load processed map features
    val mapAlns = DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      val mapAlnQuery = new SelectQueryBuilder1(LcmsDbMapAlignmentTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.MAP_SET_ID ~" = "~ mapSetId
      )
      
      ezDBC.select( mapAlnQuery ) { buildMapAlignment( _ ) }
    })
    
    val mapAlnByKey = mapAlns.groupBy( mapAln => mapAln.fromMapId + "%" + mapAln.toMapId )
    
    val mapAlnSetBuffer = new ArrayBuffer[MapAlignmentSet](mapAlnByKey.size)
    for( ( alnSetKey, mapAlns) <- mapAlnByKey) {
      val alnSetKeyParts = alnSetKey.split("%")
      val fromMapId = alnSetKeyParts(0).toInt
      val toMapId = alnSetKeyParts(1).toInt
      
      mapAlnSetBuffer += new MapAlignmentSet( fromMapId = fromMapId, toMapId = toMapId, mapAlignments = mapAlns.toArray )
    }
    
    mapAlnSetBuffer.toArray
    
  }
  
  def buildMapAlignment( mapAlnRecord: ResultSetRow ): MapAlignment = {
    
    val massStart = mapAlnRecord.getDouble(MapAlnCols.MASS_START)
    val massEnd = mapAlnRecord.getDouble(MapAlnCols.MASS_END)
    val timeList = mapAlnRecord.getString(MapAlnCols.TIME_LIST).split(" ") map { _.toFloat }
    val deltaTimeList = mapAlnRecord.getString(MapAlnCols.DELTA_TIME_LIST).split(" ") map { _.toFloat }
    
    new MapAlignment(
      fromMapId = mapAlnRecord.getInt(MapAlnCols.FROM_MAP_ID),
      toMapId = mapAlnRecord.getInt(MapAlnCols.TO_MAP_ID),
      massRange = (massStart,massEnd),
      timeList = timeList,
      deltaTimeList = deltaTimeList
    )
  }
  
}