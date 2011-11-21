package fr.profi.core.om.factory
import fr.proline.core.om.msi.PtmClasses.PtmDefinition
import fr.proline.core.om.msi.PtmClasses.PtmEvidence
import fr.proline.core.om.msi.PtmClasses.PtmNames

class PtmDefinitionLoader() {

  def buildPtmDefinition( ptmSpecifRecord: Map[String,Any], 
                         ptmRecord: Map[String,Any],
                         ptmClassifRecord: Map[String,Any],
                         ptmEvidenceRecords: Seq[Map[String,Any]] ) : PtmDefinition = {
    
    val ptmEvArray = new Array[PtmEvidence](ptmEvidenceRecords.length)

    for (i <- 0 until ptmEvidenceRecords.length - 1) {
      val ptmEvidenceRecord = ptmEvidenceRecords(i);
            
      val ptmEv = new PtmEvidence( name = ptmEvidenceRecord("name").asInstanceOf[String],
                                   monoMass = ptmEvidenceRecord("mono_mass").asInstanceOf[Double],
                                   averageMass = ptmEvidenceRecord("average_mass").asInstanceOf[Double],
                                   isRequired = ptmEvidenceRecord.getOrElse("is_required",false).asInstanceOf[Boolean]
                                 )

      ptmEvArray(i) = ptmEv
    }
    
    return new PtmDefinition(
                          id = ptmSpecifRecord("id").asInstanceOf[Int],
                          ptmId = ptmRecord("id").asInstanceOf[Int],
                          location = ptmSpecifRecord("location").asInstanceOf[String],
                          residue = ptmSpecifRecord("residue").asInstanceOf[Char],
                          classification = ptmClassifRecord("name").asInstanceOf[String],
                          names = new PtmNames( shortName = ptmClassifRecord("short_name").asInstanceOf[String],
                                                 fullName = ptmClassifRecord("full_name").asInstanceOf[String] ),
                          ptmEvidences = ptmEvArray
                          )
  }

  /*def buildLocatedPtm( pepSeq: String, ptmDef: PtmDefinition, seqPos: Int ): LocatedPtm = {
    
    val isNterm, isCterm = false;
    
    return new LocatedPtm()
  }*/
/*
##############################################################################
# Method: build_located_ptm()
# TODO: put this method in the peptide class
#
method build_located_ptm( Str $pep_sequence, Object $ptm_definition!, Int $seq_pos! ) {
  
  my $precursor_delta = $ptm_definition->precursor_delta;
  my %located_ptm_attrs = ( definition => $ptm_definition,
                            seq_position => $seq_pos,
                            mono_mass => $precursor_delta->mono_mass,
                            average_mass => $precursor_delta->average_mass,
                            composition => $precursor_delta->composition,
                          );
  
  ### N-term locations are: Any N-term or Protein N-term
  if( $ptm_definition->location =~ /.+N-term$/ ) {
    die "sequence position must be '0' because it's a N-Term PTM" if $seq_pos != 0;
    
    $located_ptm_attrs{is_nterm} = 1;    
    }
  ### C-term locations are: Any C-term, Protein C-term
  elsif( $ptm_definition->location =~ /.+C-term$/ ) {
    #my $nb_residues = length($pep_sequence);
    #die "sequence postion must be '$nb_residues' because it's a C-Term PTM" if $seq_pos != $nb_residues;
    die "sequence position must be '-1' because it's a C-Term PTM" if $seq_pos != -1;
    
    $located_ptm_attrs{is_cterm} = 1;
    }
  
  ### Build a located ptm
  my $located_ptm = new Pairs::Msi::Model::LocatedPtm( %located_ptm_attrs );
  
  return $located_ptm;
  }*/
}