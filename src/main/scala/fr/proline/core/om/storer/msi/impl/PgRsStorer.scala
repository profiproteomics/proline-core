package fr.proline.core.om.storer.msi.impl

import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.model.msi.ResultSet

class PgRsStorer extends IRsStorer {

   def storeResultSet(rs: ResultSet ): Int = 0 
    
  def fetchExistingPeptides( peptidIds: Seq[Int] ): Array[Any] = Array()
  
  def storeNewPeptides( peptides: Seq[Any] ): Unit = ()
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any] = Array()
  
  def fetchExistingProteins( proteinCRCs: Seq[String] ): Array[Any] = Array()
  
  def storeNewProteins( proteins: Seq[Any] ): Unit = ()
  
  def storeRsPeptideMatches( rs: ResultSet ): Int = 0
  
  def storeRsProteinMatches( rs: ResultSet ): Int = 0
  
  def storeRsSequenceMatches( rs: ResultSet ): Int = 0
  
}