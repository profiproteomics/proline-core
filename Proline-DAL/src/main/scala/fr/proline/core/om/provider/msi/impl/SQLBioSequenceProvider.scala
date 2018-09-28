package fr.proline.core.om.provider.msi.impl

import fr.profi.util.bytes._
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbBioSequenceTable
import fr.proline.core.om.builder.BioSequenceBuilder
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.provider.msi.ISpectrumProvider
import fr.proline.repository.ProlineDatabaseType
import fr.proline.core.om.model.msi.Protein
import fr.proline.core.om.model.msi.BioSequence
import fr.proline.core.dal.tables.msi.MsiDbBioSequenceColumns

class SQLBioSequenceProvider(val msiDbCtx: MsiDbConnectionContext) {
  
  private val BioSeqCols = MsiDbBioSequenceColumns
  private val bioSeqQB = new SelectQueryBuilder1(MsiDbBioSequenceTable)
  
  // TODO: return a IBioSequence (instantiate the appropriate sequence considering the alphabet)
  def getBioSequences( bioSeqIds: Seq[Long], loadSequence: Boolean = true): Array[BioSequence] = {
    if (bioSeqIds.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx, { msiEzDBC =>
      
      val whereClause = "WHERE " ~ BioSeqCols.ID ~ " IN(" ~ bioSeqIds.mkString(",") ~ ")"
      
    	val bioSeqQuery = if( loadSequence ) {
      	bioSeqQB.mkSelectQuery( (t,c) => List(t.*) -> whereClause )
    	} else {
      	bioSeqQB.mkSelectQuery( (t,c) => c.filter( _ != t.SEQUENCE ) -> whereClause )
    	}
    	
    	BioSequenceBuilder.buildBioSequences( msiEzDBC.select(bioSeqQuery), setSequence = loadSequence ).toArray
    	
    }, false)
  }
  
}