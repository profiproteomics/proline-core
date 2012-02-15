package fr.proline.core.om.provider.sql.lcms

import fr.proline.core.LcmsDb
  
class MapSetLoader( val lcmsDb: LcmsDb, val loadPeaks: Boolean = false )  {
  
  import java.util.HashMap
  import scala.collection.mutable.ArrayBuffer
  import fr.proline.core.om.helper.SqlUtils._
  import fr.proline.core.om.lcms._
  
  val mapAlnSetLoader = new MapAlignmentSetLoader( lcmsDb )
  val processedMapLoader = new ProcessedMapLoader( lcmsDb, loadPeaks )
  
  def getMapSet( mapSetId: Int ): MapSet = {
    
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    var mapSetcolNames: Seq[String] = null
    var mapSetRecord: Map[String,Any] = null
    lcmsDbTx.selectAndProcess( "SELECT * FROM map_set WHERE id = " + mapSetId  ) { r => 
        if( mapSetcolNames == null ) { mapSetcolNames = r.columnNames }
        mapSetRecord = mapSetcolNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        ()
      }
    if( mapSetRecord == null ) throw new Exception("can't find a map set with id="+mapSetId)
       
    // Load processedMapIds
    val processedMapIds = lcmsDbTx.select( "SELECT id FROM processed_map WHERE map_set_id = " + mapSetId  ) { _.nextInt.get }
    
    // Load some objects related to the map set
    val lcmsMaps = processedMapLoader.getMaps( processedMapIds )
    val childMaps = lcmsMaps.filter( ! _.isMaster )
    var mapAlnSets = mapAlnSetLoader.getMapAlignmentSets( mapSetId )
    if( mapAlnSets.length == 0 ) mapAlnSets = null
    
    // Try to load master map id
    var masterMapId = 0
    lcmsDbTx.selectAndProcess( "SELECT master_map_id FROM map_set WHERE id = " + mapSetId ) { r =>
      masterMapId = r.nextInt.getOrElse(0)
    }
    
    var masterMap: ProcessedMap = null
    if( masterMapId != 0) {
      // Load master feature items as records
      var mtItemcolNames: Seq[String] = null
      val mftItemRecords = lcmsDbTx.select( "SELECT * FROM master_feature_item WHERE master_map_id = " + masterMapId  ) { r => 
        if( mtItemcolNames == null ) { mtItemcolNames = r.columnNames }
        mtItemcolNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      }
  
      // Load the master map
      masterMap = this.rebuildMasterMap( masterMapId, lcmsMaps, mftItemRecords )
    }
    
    // Build and return the map set
    buildMapSet( mapSetRecord, childMaps, masterMap, mapAlnSets )
    
  }
  
  def buildMapSet( mapSetRecord: Map[String,Any],
                   childMaps: Array[ProcessedMap],
                   masterMap: ProcessedMap,                   
                   mapAlnSets: Array[MapAlignmentSet] ): MapSet = {
    
    import java.util.Date
    
    new MapSet( id = mapSetRecord("id").asInstanceOf[Int],
                name = mapSetRecord("name").asInstanceOf[String],
                creationTimestamp = new Date(), // TODO: parse date
                childMaps = childMaps,
                masterMap = masterMap,
                alnReferenceMapId = mapSetRecord("aln_reference_map_id").asInstanceOf[Int],
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
  
  private def rebuildMasterMap( masterMapId: Int,
                                lcmsMaps: Array[ProcessedMap],
                                mftItemRecords: Seq[Map[String,Any]]
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
    masterMap.runMapIds = childMaps map { _.runMapIds(0) } toArray
    
    // Check if master feature children are already loaded in memory 
    if( masterFeatures(0).children != null ) {
      throw new Exception("master feature children were already loaded")
    }
    
    // Map master feature children by their id
    val childFtById = new java.util.HashMap[Int,Feature]
    for( childMap <- childMaps ) {
      for( ft <- childMap.features) {
        childFtById.put( ft.id, ft )
      }
    }
    
    val childFtBufferByMftId = new java.util.HashMap[Int,ArrayBuffer[Feature]]
    val bestChildIdByMftId = new java.util.HashMap[Int,Int]
    
    for( mftItemRecord <- mftItemRecords ) {
      
      // Retrieve master ID
      val masterFtId = mftItemRecord("master_feature_id").asInstanceOf[Int]
      
      // Retrieve child feature
      val childFtId = mftItemRecord("child_feature_id").asInstanceOf[Int]
      val childFt = childFtById.get(childFtId)
      
      // Check if child feature is defined
      if( childFt == null ) throw new Exception("undefined child feature with id=" + childFtId)
      
      if( !childFtBufferByMftId.containsKey(masterFtId) ) {
        childFtBufferByMftId.put(masterFtId, new ArrayBuffer[Feature](nbChildMaps) )
      }
      
      childFtBufferByMftId.get(masterFtId) += childFt
    }
    
    // Map master feature by their id
    val masterFtById = masterFeatures map { mft => ( mft.id -> mft ) } toMap
    
    for( mftId <- childFtBufferByMftId.keySet().toArray() ) {
      
      // Retrieve master feature
      val masterFt = masterFtById.getOrElse(mftId.asInstanceOf[Int], null )
      if( masterFt == null ) throw new Exception("undefined master feature with id=" + mftId)
      
      masterFt.children = childFtBufferByMftId.get(mftId).toArray[Feature]
      //masterFt.bestChildId = 
      
    }

    masterMap
    
  }
  
}