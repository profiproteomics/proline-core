package fr.proline.core.om.storer.msi.impl

import fr.proline.core.MsiDb
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi._

private[msi] class GenericRsStorer( val msiDb1: MsiDb // Main DB connection                        
                                  ) extends IRsStorer {
  
  def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ):  Map[String,Int] = null
  
  def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide] = null
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = null
  
  def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein] = null
  
  def storeNewProteins( proteins: Seq[Protein] ): Array[Protein] = null
  
  def storeRsPeptideMatches( rs: ResultSet ): Int = 0
  
  def storeRsProteinMatches( rs: ResultSet ): Int = 0
  
  def storeRsSequenceMatches( rs: ResultSet ): Int = 0
  
}