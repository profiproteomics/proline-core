package fr.proline.core.om.storer.msi

import scala.collection.mutable.HashMap

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.dal.{SQLQueryHelper}
import fr.proline.core.om.model.msi.{ResultSet, Protein, Peptide, IPeaklistContainer}
import fr.proline.core.om.storer.msi.impl.SQLRsStorer

trait IRsWriter extends Logging {
  
  val msiDb1: SQLQueryHelper // Main MSI db connection
  //lazy val msiDb2: MsiDb = new MsiDb( msiDb1.config, maxVariableNumber = 10000 ) // Secondary MSI db connection
  
  val scoringIdByType = new fr.proline.core.dal.helper.MsiDbHelper( msiDb1 ).getScoringIdByType
  
  // TODO: implement as InMemoryProvider
  val peptideByUniqueKey = new HashMap[String,Peptide]()
  val proteinBySequence = new HashMap[String,Protein]()
  
  /**
   * Store specified new ResultSet and all associated data into dbs. 
   * Protein and peptides referenced by the result set will be created as well
   * if necessary.
   */
  def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ): Map[String,Int]
  def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide]
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any]// TODO: use JPA
  
  def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein]
  def storeNewProteins( proteins: Seq[Protein] ): Array[Protein]
  
  def storeRsPeptideMatches( rs: ResultSet ): Int
  def storeRsProteinMatches( rs: ResultSet ): Int 
  def storeRsSequenceMatches( rs: ResultSet ): Int
  
}


