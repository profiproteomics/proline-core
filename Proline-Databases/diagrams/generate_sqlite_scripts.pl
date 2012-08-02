package PowerArchitect::Column;

use Class::XSAccessor {
  constructor => 'new',  
  accessors => {
    id => 'id',
    name => 'name',
    is_pk => 'is_pk',
    is_fk => 'is_fk',
    fk_ref => 'fk_ref',
    type => 'type',
    precision => 'precision',
    scale => 'scale',
    nullable => 'nullable',
    auto_increment => 'auto_increment',
    default_value => 'default_value',
    remarks => 'remarks'
    
  },

};

1;

package PowerArchitect::Table;

use Class::XSAccessor {
  constructor => 'new',  
  accessors => {
    
    ### Define foreign keys
    name => 'name',
    remarks => 'remarks',
    columns => 'columns',
    
  },

};

1;

package main;

use strict;
use XML::Simple;
use Data::Dumper;
use File::Find::Rule;
use File::Basename qw/fileparse/;
no warnings;

my @arch_files = File::Find::Rule->file()
                              ->name( '*.architect' )
                              ->in( './' );
architect2sqlite($_) for @arch_files;

sub architect2sqlite {
  my $archFile = shift;
  my($outputFile,$path,$suffix) = fileparse($archFile,'.architect');
  $outputFile .='.sql';
  
  my $tables = get_tables( $archFile );
  my @sorted_tables = sort { $a->name cmp $b->name } @$tables;
  
  my( %table_name_by_col_id, %col_name_by_id );
  for my $table (@sorted_tables) {
    for my $col (@{$table->columns}) {
      $table_name_by_col_id{$col->id} = $table->name;
      $col_name_by_id{$col->id} = $col->name;
    }
  }
    
  open( FILE, ">", $outputFile ) or die $!;
  
  my $space = '                ';
  for my $table (@sorted_tables) {
    printf FILE "CREATE TABLE %s (\n", $table->name;
    
    my @colsAsStrings;
    for my $col (@{$table->columns}) {
      my $notNullTag = $col->nullable ? '' : ' NOT NULL';
      
      my $colAsString;
      if( $col->auto_increment ) { $colAsString = $space . sprintf( "%s INTEGER PRIMARY KEY AUTOINCREMENT", $col->name ) }   
      elsif( $col->precision > 0 && not $col->auto_increment ) {
        $colAsString = $space . sprintf( "%s %s(%i)%s", $col->name, $col->type, $col->precision, $notNullTag );
      } else {      
        $colAsString = $space . sprintf( "%s %s%s", $col->name, $col->type, $notNullTag );
      }
      
      push( @colsAsStrings, $colAsString );
    }
    
    my( @pkConstraintCols, @fkConstraints );
    for my $col (@{$table->columns}) {
      
      if( $col->is_pk && not $col->auto_increment ) {
        push( @pkConstraintCols, $col->name );
      }
      elsif( $col->is_fk ) {
        my $fk_ref = $col->fk_ref;
        my $ref_table_name = $table_name_by_col_id{$fk_ref};
        my $ref_col_name = $col_name_by_id{$fk_ref};
        push( @fkConstraints, $space. sprintf( "FOREIGN KEY (%s) REFERENCES %s (%s)", $col->name, $ref_table_name, $ref_col_name ) );
      }
    }
    
    if( scalar(@pkConstraintCols) > 0 ) {
      push( @colsAsStrings, $space. sprintf( "PRIMARY KEY (%s)", join(', ', @pkConstraintCols) ) );
    }
    
    push( @colsAsStrings, @fkConstraints );
    
    print FILE join(",\n",@colsAsStrings) . "\n);\n\n";
  
  }
  
  close FILE;
}

sub get_tables {
  my $xmlFilePath = shift;
  
  my %options = ( SuppressEmpty => undef, ForceArray => ['table','relationship'], KeyAttr => [] );
  my $xmlParser = new XML::Simple( %options );
  my $xmlObj = $xmlParser->XMLin($xmlFilePath);
  
  my %colTypeMapper = ( 1 => 'CHAR',
                        4 => 'INTEGER',
                        7 => 'REAL',
                        8 => 'DOUBLE',
                        12 => 'VARCHAR',
                        16 => 'BOOLEAN',
                        #91 => 'DATE',
                        93 => 'TIMESTAMP',
                        2004 => 'BLOB',
                        2005 => 'CLOB',
                        -5 => 'BIGINT',
                        );
  
  my %sqliteColTypeMapper = ( CHAR => 'TEXT',
                              INTEGER => 'INTEGER',
                              REAL => 'REAL',
                              DOUBLE => 'REAL',
                              VARCHAR => 'TEXT',
                              BOOLEAN => 'TEXT',
                              DATE => 'TEXT',
                              TIMESTAMP => 'TEXT',
                              BLOB => 'BLOB',
                              CLOB => 'TEXT',
                              BIGINT => 'INTEGER',
                              );
  
  ### Retrieve columns corresponding to foreign keys
  my %pkByFk;
  
  my $relationships = $xmlObj->{'target-database'}->{relationships}->{relationship};  
  foreach my $relationship ( @$relationships) {
    if( defined $relationship->{'column-mapping'}   ) {
      my $col_mapping = $relationship->{'column-mapping'};
      my $fkColName = $col_mapping->{'fk-column-ref'};
      my $pkColName = $col_mapping->{'pk-column-ref'};
      $pkByFk{$fkColName} = $pkColName;
    }
  }
  
  #print Dumper( \%foreignColumns);
  
  ### Climb tree to extract column infos and write an HTML file
  my $table_nodes = $xmlObj->{'target-database'}->{'table'};
  
  my @tables;
  foreach my $table_node (@$table_nodes) {
    
    my %table_attrs = ( name => $table_node->{name}, remarks => $table_node->{remarks} );
  
    my $column_nodes = $table_node->{folder}->[0]->{column};
    $column_nodes = [$column_nodes] if ref($column_nodes) ne 'ARRAY';
    
    my @columns;
    foreach my $column_node (@$column_nodes) {
      
      my $colType = $colTypeMapper{ $column_node->{'type'} };
      die "unknown type with id = " . $column_node->{'type'} if !defined $colType;
      
      my %col_attrs;
      $col_attrs{$_} = $column_node->{$_} for qw/id name remarks precision scale/;      
      $col_attrs{is_pk} = defined $column_node->{'primaryKeySeq'} ? 1 : 0;
      $col_attrs{type} = $sqliteColTypeMapper{$colType};
      $col_attrs{nullable} = $column_node->{'nullable'} eq '1' ? 1 : 0;
      $col_attrs{auto_increment} = $column_node->{'autoIncrement'} eq 'true' ? 1 : 0;
      
      if( exists $pkByFk{ $column_node->{'id'} } ) {
        $col_attrs{fk_ref} = $pkByFk{ $column_node->{'id'} };
        $col_attrs{is_fk} = 1;
      } else { $col_attrs{is_fk} = 0; }
      
      push( @columns, new PowerArchitect::Column( %col_attrs) );
    }
    
    $table_attrs{columns} = \@columns;
    
    my $table = new PowerArchitect::Table( %table_attrs );
    push( @tables, $table );
  }
  
  return \@tables;
  
}


1;

