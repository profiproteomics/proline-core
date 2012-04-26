package fr.proline.core.service.msq

trait IQuantifier {
  
  def quantify(): Unit
  
/*
  ### Define attributes
  has 'rdb_quantitation_fraction' => ( is => 'ro', isa => 'Object', required => 1 );
  has 'msi_rdb'  => (is => 'ro', isa => 'Object', lazy_build => 1, required => 0, init_arg => undef );
  
  ### Define attribute builders
  sub _build_msi_rdb {
    my $self = shift;
    return Pairs::Msi::RDB->new_or_cached_connection( $self->rdb_quantitation_fraction->quantitation->project_id );
  }
*/
  
}