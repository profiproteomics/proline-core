package fr.proline.core.dal.helper

import fr.proline.core.dal.MsiDb

class MsiDbHelper( msiDb: MsiDb ) {

/* ##############################################################################
# Method: get_target_decoy_result_sets()
#
method get_target_decoy_result_sets( Int $target_result_set_id! ) {
  
  my @result_sets;
  
  ### Retrieve target result set
  require Pairs::Msi::RDBO::ResultSet;
  my $target_result_set = new Pairs::Msi::RDBO::ResultSet( id => $target_result_set_id, db => $self->msi_rdb );
  $target_result_set->load();
  push( @result_sets, $target_result_set );
  
  ### Retrieve decoy result set
  my $decoy_result_set = $target_result_set->decoy_result_set;
  push( @result_sets, $decoy_result_set ) if defined $decoy_result_set;

  return \@result_sets;
  }*/

/*##############################################################################
# Method: get_decoy_rs_id()
#
method get_decoy_rs_id( Int $target_result_set_id! ) {
  
  #### Retrieve target result set
  #require Pairs::Msi::RDBO::ResultSet;
  #my $target_result_set = new Pairs::Msi::RDBO::ResultSet( id => $target_result_set_id );
  #$target_result_set->load();
  #
  #### Retrieve decoy result set
  #my $decoy_result_set = $target_result_set->decoy_result_set;
  #croak "undefined decoy result set" if !defined $decoy_result_set;
  #my $decoy_result_set_id = $decoy_result_set->id;
  
  my $decoy_result_set = $self->get_target_decoy_result_sets($target_result_set_id)->[1];
  
  return defined $decoy_result_set ? $decoy_result_set->id : undef;
  }*/

  def getResultSetsMsiSearchIds( rsIds: Seq[Int] ): Array[Int] = {
    
    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val msiSearchIds = this.msiDb.getOrCreateTransaction.select(
                         "SELECT msi_search_id FROM result_set " +
                         "WHERE id IN ("+  rsIds.mkString(",") +")" ) { r => r.nextInt.get }
    
    msiSearchIds.distinct.toArray
  }
  
  /*def getMsiSearchIdsByResultSetId( rsIds: Seq[Int] ): Seq[Int] = {
    

  }*/

/*##############################################################################
# Method: get_msi_search_peaklist_map()
#
multi method get_msi_search_peaklist_map( ArrayRef[Int] $msi_search_ids! ) {
  
  require Pairs::Msi::RDBO::MsiSearch::Manager;
  my $rdb_msi_searches = Pairs::Msi::RDBO::MsiSearch::Manager->get_msi_searches( query => [ id => $msi_search_ids ], db => $self->msi_rdb );
  
  my $rdb_peaklist_map = $self->get_msi_search_peaklist_ids( $rdb_msi_searches );
  
  return $rdb_peaklist_map;
}*/

/*##############################################################################
# Method: get_msi_search_peaklist_map()
#
multi method get_msi_search_peaklist_map( ArrayRef[Object] $rdb_msi_searches! ) {

  require Pairs::Msi::RDBO::Peaklist::Manager;
  
  my %parent_peaklist_map = map { $_->peaklist->id => $_->peaklist } @$rdb_msi_searches;
  my @parent_peaklist_ids = keys(%parent_peaklist_map);
  
  ### Retrieve an instance SDBI connected to the MSI-DB
  my $msi_sdbi = $self->msi_rdb->sdbi;

  my %rdb_peaklist_map;
  while( my( $parent_peaklist_id, $rdb_peaklist ) = each(%parent_peaklist_map) ) {
    my @child_peaklist_ids = $msi_sdbi->select( 'peaklist_relation', 'child_peaklist_id', { parent_peaklist_id => { -in => \@parent_peaklist_ids } } )->flat;
    
    ### Check if the peaklist has children => merged peaklist
    if( scalar(@child_peaklist_ids) > 0 ) {
      my $rdb_child_peaklists = Pairs::Msi::RDBO::Peaklist::Manager->get_peaklists( query => [ id => \@child_peaklist_ids ], db => $self->msi_rdb );
      $rdb_peaklist_map{$_->id} = $_ for @$rdb_child_peaklists;
      }
    else { $rdb_peaklist_map{$parent_peaklist_id} = $rdb_peaklist; }
  }
  
  return \%rdb_peaklist_map;
}*/

/*##############################################################################
# Method: get_search_engine()
#
method get_search_engine( Int $target_result_set_id! ) {
  
  my $msi_search_ids = $self->get_rs_msi_search_ids( [$target_result_set_id] );
  
  ### Retrieve MSI search
  require Pairs::Msi::RDBO::MsiSearch::Manager;
  my $msi_searches = Pairs::Msi::RDBO::MsiSearch::Manager->get_msi_searches(
                        query => [ id => $msi_search_ids->[0] ],
                        with_obects => [ 'search_settings' ],
                        db => $self->msi_rdb
                      );
  
  ### Retrieve search settings
  my $search_settings = $msi_searches->[0]->search_settings;
  
  return $search_settings->software_name;  
  }*/

  /** Build score types (search_engine:score_name) and map them by id */
  def getScoringTypeById(): Map[Int,String] = {
  
    Map() ++ _getScorings.map { scoring => ( scoring.id -> (scoring.search_engine + ":" + scoring.name) ) }
    
  }
  
  def getScoringIdByType(): Map[String,Int] = {
  
    Map() ++ _getScorings.map { scoring => ( (scoring.search_engine + ":" + scoring.name) -> scoring.id ) }
    
  }

/*##############################################################################
# Method: get_scoring_id_by_score_type()
# Returns a hash mapping scoring id by score type
#
method get_scoring_id_by_score_type( ) {
  
  ### Build score types (search_engine:score_name) and map them by id
  my %score_type_map = map { join(':', @$_{qw/search_engine name/}) => $_->{id} } @{$self->_get_scoring_hashes};
    
  return \%score_type_map;  
  }
*/
  
  
  private case class ScoringRecord( id: Int, search_engine: String, name: String )
  
  /** Load and return scorings as records */
  private def _getScorings(): Seq[ScoringRecord] = {

    this.msiDb.getOrCreateTransaction.select( "SELECT id, search_engine, name FROM scoring" ) { r =>
      ScoringRecord( r.nextInt.get, r.nextString.get, r.nextString.get )
    }
    
  }

  def getSeqLengthByBioSeqId( bioSeqIds: Iterable[Int] ): Map[Int,Int] = {
    
    val maxNbIters = this.msiDb.maxVariableNumber
    val msiDbTx = this.msiDb.getOrCreateTransaction()
    
    val seqLengthByProtIdBuilder = Map.newBuilder[Int,Int]
    
    // Iterate over groups of peptide ids
    bioSeqIds.grouped(maxNbIters).foreach {
      tmpBioSeqIds => {      
        // Retrieve peptide PTMs for the current group of peptide ids
        msiDbTx.selectAndProcess("SELECT id, length FROM bio_sequence WHERE id IN ("+tmpBioSeqIds.mkString(",")+")" ) { r =>
          seqLengthByProtIdBuilder += ( r.nextInt.get -> r.nextInt.get )
        }
      }
    }
    
    seqLengthByProtIdBuilder.result()
    
  }


  /*
  ##############################################################################
  # Method: get_seq_by_pep_id()
  #
  method get_seq_by_pep_id( Object $rsm ) {
    
    my $peptide_instances = $rsm->peptide_instances;
    my @peptide_ids = map { $_->peptide_id } @$peptide_instances;
    
    ### Retrieve the database connection
    my $sdbi = $self->msi_rdb->sdbi;
    
    my $seq_by_pep_id;
    
    ### Check if database driver is SQLite
    if( $self->msi_rdb->driver eq 'sqlite' and scalar(@peptide_ids) > 999 ) {
      
      ### Clusterize ids (999 items by cluster) to optimize SQL query
      require List::AllUtils;
      my $peptide_id_iter = List::AllUtils::natatime 999, @peptide_ids;
      
      ### Iterate over peptide id clusters    
      while( my @tmp_peptide_ids = $peptide_id_iter->() ) {
        
        my $tmp_seq_by_pep_id = $sdbi->select( 'peptide', [qw/id sequence/], { id => { -in =>\@tmp_peptide_ids } } )->map;
        @$seq_by_pep_id{ keys(%$tmp_seq_by_pep_id) } = values(%$tmp_seq_by_pep_id);
      }
      
    } else { $seq_by_pep_id = $sdbi->select( 'peptide', [qw/id sequence/], { id => { -in => \@peptide_ids } } )->map; }
    
    return $seq_by_pep_id;
  }*/
}