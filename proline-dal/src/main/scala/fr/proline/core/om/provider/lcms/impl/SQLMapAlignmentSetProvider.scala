package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.ResultSetRow
import fr.profi.util.misc.MapIfNotNull
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.lcms.LcmsDbMapAlignmentTable
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IMapAlignmentSetProvider

import scala.collection.mutable.ArrayBuffer

class SQLMapAlignmentSetProvider( val lcmsDbCtx: LcMsDbConnectionContext ) extends IMapAlignmentSetProvider {
  
  val MapAlnCols = LcmsDbMapAlignmentTable.columns
  
  def getMapAlignmentSet( fromMapId: Long,toMapId: Long): Unit = {
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
  
  def getMapAlignmentSets( mapSetId: Long ): Array[MapAlignmentSet] = {
    
    // Load processed map features
    val mapAlns = DoJDBCReturningWork.withEzDBC(lcmsDbCtx) { ezDBC =>
      
      val mapAlnQuery = new SelectQueryBuilder1(LcmsDbMapAlignmentTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.MAP_SET_ID ~" = "~ mapSetId
      )
      
      ezDBC.select( mapAlnQuery ) { buildMapAlignment( _ ) }
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
  
  def buildMapAlignment( mapAlnRecord: ResultSetRow ): MapAlignment = {

    // Sometimes timeList or deltaTimeList can be empty and the mapping to float values will raise an exception
    // we do not need to keep such alignments, so instead we return null (and we filter null alignments later on)
    val massStart = mapAlnRecord.getFloat(MapAlnCols.MASS_START)
    val massEnd = mapAlnRecord.getFloat(MapAlnCols.MASS_END)
    val timeList = mapAlnRecord.getString(MapAlnCols.TIME_LIST).split(" ").filter(!_.isEmpty) map { _.toFloat }
    val deltaTimeList = mapAlnRecord.getString(MapAlnCols.DELTA_TIME_LIST).split(" ").filter(!_.isEmpty) map { _.toFloat }
    val propertiesAsJSON = mapAlnRecord.getString(MapAlnCols.SERIALIZED_PROPERTIES)
    val properties = MapIfNotNull(propertiesAsJSON) { ProfiJson.deserialize[MapAlignmentProperties](_) }

    new MapAlignment(
      refMapId = toLong(mapAlnRecord.getAny(MapAlnCols.FROM_MAP_ID)),
      targetMapId = toLong(mapAlnRecord.getAny(MapAlnCols.TO_MAP_ID)),
      massRange = (massStart,massEnd),
      timeList = timeList,
      deltaTimeList = deltaTimeList,
      properties = properties
    )
  }
  
}