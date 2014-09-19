package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.jdbc.ResultSetRow
import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.{ SelectQueryBuilder1 }
import fr.proline.core.dal.tables.lcms.{ LcmsDbMapSetTable,LcmsDbMasterFeatureItemTable, LcmsDbProcessedMapTable }
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IMapSetProvider
import fr.profi.util.primitives._
//import fr.profi.util.sql._
  
class SQLMapSetProvider(
  val lcmsDbCtx: DatabaseConnectionContext,
  val loadPeaks: Boolean = false
) extends IMapSetProvider {
  
  val MapSetCols = LcmsDbMapSetTable.columns
  val MftItemCols = LcmsDbMasterFeatureItemTable.columns
  val mapAlnSetProvider = new SQLMapAlignmentSetProvider( lcmsDbCtx )
  val processedMapProvider = new SQLProcessedMapProvider( lcmsDbCtx, loadPeaks )
  
  def getMapSet( mapSetId: Long ): MapSet = {    
    
    DoJDBCReturningWork.withEzDBC(lcmsDbCtx, { ezDBC =>
    
      // Try to retrieve the master map id
      val masterMapIdQuery = new SelectQueryBuilder1(LcmsDbMapSetTable).mkSelectQuery( (t,c) =>
        List(t.MASTER_MAP_ID) -> "WHERE "~ t.ID ~" = "~ mapSetId
      )
      val masterMapId = ezDBC.selectHeadOrElse(masterMapIdQuery) ( _.nextLongOrElse(0L),
        throw new Exception("can't find a map set with id="+mapSetId)
      )
      
      // Load processedMapIds
      val procMapIdQuery = new SelectQueryBuilder1(LcmsDbProcessedMapTable).mkSelectQuery( (t,c) =>
        List(t.ID) -> "WHERE "~ t.MAP_SET_ID ~" = "~ mapSetId
      )
      val processedMapIds = ezDBC.selectLongs( procMapIdQuery )
      
      // Load some objects related to the map set
      val lcmsMaps = processedMapProvider.getProcessedMaps( processedMapIds )
      val childMaps = lcmsMaps.filter( ! _.isMaster )
      var mapAlnSets = mapAlnSetProvider.getMapAlignmentSets( mapSetId )
      if( mapAlnSets.length == 0 ) mapAlnSets = null
      
      var masterMap: ProcessedMap = null
      if( masterMapId != 0 ) {    
        // Load the master map
        masterMap = this._rebuildMasterMap( ezDBC, masterMapId, lcmsMaps )
      }
      
      val mapSetQuery = new SelectQueryBuilder1(LcmsDbMapSetTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" = "~ mapSetId
      )
      ezDBC.selectHead(mapSetQuery) { mapSetRecord =>
        // Build and return the map set
        buildMapSet( mapSetRecord, childMaps, masterMap, mapAlnSets )
      }
    
    })
    
  }
  
  def buildMapSet( mapSetRecord: ResultSetRow,
                   childMaps: Array[ProcessedMap],
                   masterMap: ProcessedMap,                   
                   mapAlnSets: Array[MapAlignmentSet] ): MapSet = {
    
    new MapSet(
      id = toLong(mapSetRecord.getAny(MapSetCols.ID)),
      name = mapSetRecord.getString(MapSetCols.NAME),
      creationTimestamp = mapSetRecord.getTimestamp(MapSetCols.CREATION_TIMESTAMP),
      childMaps = childMaps,
      masterMap = masterMap,
      alnReferenceMapId = toLong(mapSetRecord.getAny(MapSetCols.ALN_REFERENCE_MAP_ID)),
      mapAlnSets = mapAlnSets
    )
  }
  
  /*
  ##############################################################################
  # Method: get_map_set()
  #
  method get_map_set( Int $map_set_id! ) {
    
    ### Load RDB map set
    my $rdb_map_set = new Pairs::Lcms::RDBO::MapSet( id => $map_set_id, db => $self->lcms_rdb );
    die "can't find a map set with id='$map_set_id'" if not $rdb_map_set->load( speculative => 1 );
    
    ### Check if the master map has been created
    my $master_map_id = $rdb_map_set->master_map_id;
    die "the master map must be created first" if !defined $master_map_id;
    
    ### Retrieve map ids
    my $rdb_processed_maps = $rdb_map_set->processed_maps;
    my @map_ids = map { $_->id } @$rdb_processed_maps;
    
    ### Load the maps
    my $maps = $self->_get_map_set_maps(\@map_ids);
    my @child_maps = grep { not $_->is_master } @$maps;
    
    ### Load map alignments
    my $map_aln_sets = $self->_get_map_aln_sets( $map_set_id );
    
    ### Load normalization factors
    my $nf_by_map_id = $self->_get_nf_by_map_id( $map_set_id );
    
    ### Load the master map
    my $master_map = $self->_rebuild_master_map( $master_map_id, $maps );
    
    ### Convert RDB map set into map set model  
    my $map_set = new Pairs::Lcms::Model::MapSet(
                        id => $map_set_id,
                        name => $rdb_map_set->name,
                        aln_ref_map_id => $rdb_map_set->aln_reference_map_id,
                        creation_timestamp => $rdb_map_set->creation_timestamp->epoch,
                        properties => $rdb_map_set->properties,
                        child_maps => \@child_maps,
                        normalization_factors => $nf_by_map_id,
                        master_map => $master_map,
                        map_aln_sets => $map_aln_sets,
                        );
    
    return $map_set;

  }
  
  ##############################################################################
  # Method: _get_map_aln_sets()
  #
  method _get_map_aln_sets( Int $map_set_id! ) {
    return $self->map_aln_set_loader->get_map_alignment_sets($map_set_id);
  }
  
  ##############################################################################
  # Method: _get_nf_by_map_id()
  #
  method _get_nf_by_map_id( Int $map_set_id! ) {
    
    ### Retrieve simple DBI access to the database
    my $sdbi = $self->sdbi;
    
    ### Load normalization factors
    my %nf_by_map_id = $sdbi->select( 'processed_map', [qw/id normalization_factor/], { map_set_id => $map_set_id } )->map;
    
    return \%nf_by_map_id;
  }
  
  ##############################################################################
  # Method: _get_map_set_maps()
  #
  method _get_map_set_maps( ArrayRef $map_ids! ) {
    return $self->processed_map_loader->get_maps( $map_ids );
  }
  */
  
  private def _rebuildMasterMap(
    ezDBC: EasyDBC,
    masterMapId: Long,
    lcmsMaps: Array[ProcessedMap]
  ): ProcessedMap = {
    
    // Retrieve master map and its child maps
    var masterMap: ProcessedMap = null
    val nbChildMaps = lcmsMaps.length - 1
    var childMaps = new ArrayBuffer[ProcessedMap](nbChildMaps)
    
    for( lcmsMap <- lcmsMaps) {
      if( lcmsMap.id == masterMapId) { masterMap = lcmsMap }
      else { childMaps += lcmsMap }
    }
    if( masterMap == null ) throw new Exception("the provided list of maps doesn't contain a master map")
    
    // Retrieve master map features
    val masterFeatures = masterMap.features
    masterMap.rawMapReferences = childMaps map { cm => RawMapIdentifier(cm.getRawMapIds().apply(0)) }
    
    // Check if master feature children are already loaded in memory 
    if( masterFeatures(0).children != null ) {
      throw new Exception("master feature children were already loaded")
    }
    
    // Map master feature children by their id
    val childFtById = Map() ++ childMaps.flatMap( _.features.map( ft => ft.id -> ft ) )
    
    val childFtBufferByMftId = new HashMap[Long,ArrayBuffer[Feature]]
    //val bestChildIdByMftId = new HashMap[Int,Int]
    
    // Iterate over master feature items
    val mftItemQuery = new SelectQueryBuilder1(LcmsDbMasterFeatureItemTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.MASTER_MAP_ID ~" = "~ masterMapId
    )
    val mftItemRecords = ezDBC.selectAndProcess( mftItemQuery ) { mftItemRecord =>      
      
      // Retrieve master and child FT IDs
      val masterFtId = toLong(mftItemRecord.getAny(MftItemCols.MASTER_FEATURE_ID))
      val childFtId = toLong(mftItemRecord.getAny(MftItemCols.CHILD_FEATURE_ID))
      
      // Retrieve child feature      
      val childFt = childFtById(childFtId)
      
      // Check if child feature is defined
      if( childFt == null ) throw new Exception("undefined child feature with id=" + childFtId)
      
      childFtBufferByMftId.getOrElseUpdate(masterFtId, new ArrayBuffer[Feature](nbChildMaps)) += childFt
    }
    
    // Map master feature by their id
    val masterFtById = masterFeatures map { mft => ( mft.id -> mft ) } toMap
    
    for( mftId <- childFtBufferByMftId.keys ) {
      
      // Retrieve master feature
      val masterFt = masterFtById.getOrElse(mftId, null )
      if( masterFt == null ) throw new Exception("undefined master feature with id=" + mftId)
      
      masterFt.children = childFtBufferByMftId(mftId).toArray
      //masterFt.bestChildId = 
      
    }

    masterMap
    
  }
  
}