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
use String::CamelCase qw/camelize/;
no warnings;

my $namespace = '';
my $packageName = 'fr.profi.mzdb.db_model';
my $xmlFile = 'mzDB_120913.architect';
my $outputDir = 'mzdb_java_enums';

my $tables = get_tables( $xmlFile );
my @sorted_tables = sort { $a->name cmp $b->name } @$tables;

my( %table_name_by_col_id, %col_name_by_id );
for my $table (@sorted_tables) {
  for my $col (@{$table->columns}) {
    $table_name_by_col_id{$col->id} = $table->name;
    $col_name_by_id{$col->id} = $col->name;
  }
}

my $space = '  ';
for my $table (@sorted_tables) {
  
  my $tableName = $table->name;
  my $objectName = $namespace.camelize($tableName).'Table';
  
  my $outputFile = $outputDir.'/'.$objectName.'.java';
  open( FILE, ">", $outputFile  ) or die $!;
  
  printf FILE "package $packageName;\n\n";
  
  printf FILE "public enum %s {\n\n", $objectName;
  
  my @colsAsStrings;
  for my $col (@{$table->columns}) {
    my $enumEntryName = uc($col->name);
    
    my $colAsString = $space . sprintf( '%s("%s")', $enumEntryName, $col->name );    
    push( @colsAsStrings, $colAsString );
  } 
  
  print FILE join(",\n",@colsAsStrings) . ";\n  \n";
  
  print FILE <<"end_of_enum";
  public static String tableName = "$tableName";
  private final String columnName;
  
  private $objectName(String colName) {
    this.columnName = colName;
  }
  
  public String getValue() {
    return columnName;
  }
end_of_enum

  print FILE "\n}\n";
  
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
                              CLOB => 'TEXT'
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

