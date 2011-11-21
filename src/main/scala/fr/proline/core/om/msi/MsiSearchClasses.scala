package fr.proline.core.om.msi

/*


package Pairs::Msi::Model::MsiSearch;

use MooseX::Declare;

##############################################################################
# Class definition: Pairs::Msi::Model::MsiSearch
#
class Pairs::Msi::Model::MsiSearch
{
### Load essentials here, other modules loaded on demand later
use Carp;

### Define attributes
has 'id' => (is => 'rw', isa => 'Int', required => 1 );
has 'title' => (is => 'rw', isa => 'Str', required => 1 );
has 'date' => (is => 'rw', isa => 'Str', required => 1 );
has 'result_file_number' => (is => 'rw', isa => 'Int', required => 1 );
has 'result_file_path' => (is => 'rw', isa => 'Str', required => 1 );
has 'user_name' => (is => 'rw', isa => 'Str', required => 1 );
has 'user_email' => (is => 'rw', isa => 'Str', required => 0 );
has 'queries_count' => (is => 'rw', isa => 'Int', required => 1 );
has 'submitted_queries_count' => (is => 'rw', isa => 'Int', required => 1 );
has 'searched_proteins_count' => (is => 'rw', isa => 'Int', required => 1 );
has 'search_settings' => (is => 'rw', isa => 'Pairs::Msi::Model::SearchSettings', required => 1 );
has 'peaklist' => (is => 'rw', isa => 'Pairs::Msi::Model::Peaklist', required => 1 );
has 'properties' => (is => 'rw', isa => 'HashRef', required => 0 );

} ### end of class

1;

package Pairs::Msi::Model::Ms1Search;

use MooseX::Declare;

##############################################################################
# Class definition: Pairs::Msi::Model::Ms1Search
#
class Pairs::Msi::Model::Ms1Search extends Pairs::Msi::Model::MsiSearch
{
### Load essentials here, other modules loaded on demand later
use Carp;

### Define attributes
has 'max_protein_mass' => (is => 'rw', isa => 'Num', required => 0 );
has 'min_protein_mass' => (is => 'rw', isa => 'Int', required => 0 );
has 'protein_pi' => (is => 'rw', isa => 'Num', required => 0 );

} ### end of class

1;

package Pairs::Msi::Model::Ms2Search;

use MooseX::Declare;

##############################################################################
# Class definition: Pairs::Msi::Model::Ms2Search
#
class Pairs::Msi::Model::Ms2Search extends Pairs::Msi::Model::MsiSearch
                                 
{
### Load essentials here, other modules loaded on demand later
use Carp;

### Define attributes
has 'ms2_charge_states' => (is => 'rw', isa => 'Str', required => 1 );
has 'ms2_error_tol' => (is => 'rw', isa => 'Num', required => 1 );
has 'ms2_error_tol_unit' => (is => 'rw', isa => 'Str', required => 1 );

} ### end of class

1;

 */
package MsiSearchClasses {
import fr.proline.core.om.msi.PtmClasses.PtmDefinition
import fr.proline.core.om.msi.InstrumentClasses.Instrument
   
  class SeqDatabase(
                   // Required fields
                   val id: Int,
                   val name: String,
                   val file_path: String,
                   val sequences_count: Int,
                   
                   // Immutable optional fields
                   val version: String = null,
                   val release_date: String = null
                   
                   ) {
      
  }
 
  
  class SearchSettings(
                   // Required fields
                   val software_name: String,
                   val software_version: String,
                   val taxonomy: String,
                   val max_missed_cleavages: Int,
                   val ms1_charge_states: String,
                   val ms1_error_tol: Double,
                   val ms1_error_tol_unit: String,
                   val is_decoy: Boolean,
                   val used_enzymes: Array[String], // TODO: create an enzyme class
                   val variable_ptm_defs: Array[PtmDefinition],
                   val fixed_ptm_defs: Array[PtmDefinition],
                   val seq_databases: Array[SeqDatabase],
                   val instrument: Instrument,
                   
                   // Mutable optional fields
                   var quantitation: String = null
                   
                   ) {
      
  }
  
}