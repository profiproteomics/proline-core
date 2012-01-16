package fr.proline.core.om.storer.rs

trait IRsStorer {
  
  import fr.proline.core.om.msi.ResultSetClasses.ResultSet

  def fetchExistingPeptides( peptideIds: Seq[Int] ): Array[Any]
  def storeNewPeptides( peptides: Seq[Any] ): Unit
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any]
  
  def fetchExistingProteins( proteinCRCs: Seq[String] ): Array[Any]
  def storeNewProteins( proteins: Seq[Any] ): Unit
  
  def storeRsPeptideMatches( rs: ResultSet ): Int
  def storeRsProteinMatches( rs: ResultSet ): Int
  def storeRsSequenceMatches( rs: ResultSet ): Int
  
 }