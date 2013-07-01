package fr.proline.core.dal.helper

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCReturningWork, DoJDBCWork }
import fr.proline.util.primitives._

class MsiDbHelper(msiDbCtx: DatabaseConnectionContext) {

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

  def getDecoyRsId(targetResultSetId: Long): Option[Long] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select(
        "SELECT decoy_result_set_id FROM result_set WHERE id = " + targetResultSetId
      ) { _.nextLongOption }(0)
    })
  }

  def getDecoyRsIds(targetResultSetIds: Seq[Long]): Array[Long] = {

    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT decoy_result_set_id FROM result_set WHERE id in " +
          targetResultSetIds.mkString("(", ", ", ")"))
    })

  }

  def getResultSetsMsiSearchIds(rsIds: Seq[Long]): Array[Long] = {

    val parentMsiSearchIds = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT msi_search_id FROM result_set " +
          "WHERE id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      )
    })

    val childMsiSearchIds = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectLongs(
        "SELECT DISTINCT msi_search_id FROM result_set, result_set_relation " +
          "WHERE result_set.id = result_set_relation.child_result_set_id " +
          "AND result_set_relation.parent_result_set_id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      )
    })

    parentMsiSearchIds ++ childMsiSearchIds
  }

  def getMsiSearchIdsByParentResultSetId(rsIds: Seq[Long]): Map[Long, Set[Long]] = {

    val msiSearchIdsByParentResultSetId = new HashMap[Long, HashSet[Long]]

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT id, msi_search_id FROM result_set " +
          "WHERE id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      ) { r =>
          val id: Long = toLong(r.nextAny)
          msiSearchIdsByParentResultSetId.getOrElseUpdate(id, new HashSet[Long]) += toLong(r.nextAny)
        }
    })

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectAndProcess(
        "SELECT result_set_relation.parent_result_set_id, result_set.msi_search_id FROM result_set, result_set_relation " +
          "WHERE result_set.id = result_set_relation.child_result_set_id " +
          "AND result_set_relation.parent_result_set_id IN (" + rsIds.mkString(",") + ") " +
          "AND msi_search_id IS NOT NULL"
      ) { r =>
          msiSearchIdsByParentResultSetId.getOrElseUpdate(toLong(r.nextAny), new HashSet[Long]) += toLong(r.nextAny)
        }
    })

    Map() ++ msiSearchIdsByParentResultSetId.map(t => (t._1 -> t._2.toSet))
  }

  def getResultSetIdByResultSummaryId(rsmIds: Seq[Long]): Map[Long, Long] = {

    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select(
        "SELECT id, result_set_id FROM result_summary " +
          "WHERE id IN (" + rsmIds.mkString(",") + ")") { r => (toLong(r.nextAny), toLong(r.nextAny)) } toMap
    })
  }

  def getMsiSearchesPtmSpecificityIds(msiSearchIds: Seq[Long]): Array[Long] = {

    // Retrieve parent peaklist ids corresponding to the provided MSI search ids
    val ptmSpecifIds = DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select(
        "SELECT DISTINCT ptm_specificity_id FROM used_ptm, search_settings, msi_search " +
          "WHERE used_ptm.search_settings_id = search_settings.id " +
          "AND search_settings.id = msi_search.search_settings_id " +
          "AND msi_search.id IN (" + msiSearchIds.mkString(",") + ")") { v => toLong(v.nextAny) }
    })

    ptmSpecifIds.distinct.toArray
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
  def getScoringTypeById(): Map[Long, String] = {
    Map() ++ _getScorings.map { scoring => (scoring.id -> (scoring.search_engine + ":" + scoring.name)) }
  }

  def getScoringIdByType(): Map[String, Long] = {
    Map() ++ _getScorings.map { scoring => ((scoring.search_engine + ":" + scoring.name) -> scoring.id) }
  }

  private case class ScoringRecord(id: Long, search_engine: String, name: String)

  /** Load and return scorings as records */
  private def _getScorings(): Seq[ScoringRecord] = {
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select("SELECT id, search_engine, name FROM scoring") { r =>
        ScoringRecord(toLong(r.nextAny), r.nextString, r.nextString)
      }
    })
  }

  def getScoringByResultSummaryIds(resultSummaryId: Seq[Long]): Seq[String] = {
	DoJDBCReturningWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.select("select scoring.search_engine, scoring.name " + 
      		"from scoring, peptide_set " + 
      		"where peptide_set.scoring_id = scoring.id AND peptide_set.result_summary_id IN (" +resultSummaryId.mkString(",") + ")" + 
      		"group by scoring.search_engine, scoring.name") { r => r.nextString+":"+r.nextString }
    })
  }
  
  def getSeqLengthByBioSeqId(bioSeqIds: Iterable[Long]): Map[Long, Int] = {

    val seqLengthByProtIdBuilder = Map.newBuilder[Long, Int]

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      val maxNbIters = ezDBC.getInExpressionCountLimit

      // Iterate over groups of peptide ids
      bioSeqIds.grouped(maxNbIters).foreach {
        tmpBioSeqIds =>
          {
            // Retrieve peptide PTMs for the current group of peptide ids
            ezDBC.selectAndProcess("SELECT id, length FROM bio_sequence WHERE id IN (" + tmpBioSeqIds.mkString(",") + ")") { r =>
              seqLengthByProtIdBuilder += (toLong(r.nextAny) -> r.nextInt)
            }
          }
      }
    })

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

  // TODO: add number field to the table
  def getSpectrumNumberById(pklIds: Seq[Long]): Map[Long, Int] = {

    val specNumById = new HashMap[Long, Int]
    var specCount = 0

    DoJDBCWork.withEzDBC(msiDbCtx, { ezDBC =>
      ezDBC.selectAndProcess("SELECT id FROM spectrum WHERE peaklist_id IN (" + pklIds.mkString(",") + ")") { r =>
        val spectrumId: Long = toLong(r.nextAny)
        specNumById += (spectrumId -> specCount)
        specCount += 1
      }
    })

    Map() ++ specNumById
  }
}