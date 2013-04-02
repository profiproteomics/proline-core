package fr.proline.core.om.storer.lcms.impl

import fr.profi.jdbc.easy._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.lcms.{ LcmsDbMapAlignmentTable }
import fr.proline.core.om.storer.lcms.IMapAlnSetStorer
import fr.proline.core.om.model.lcms.MapAlignmentSet
  
class SQLMapAlnSetStorer( lcmsDbCtx: DatabaseConnectionContext ) extends IMapAlnSetStorer {
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit = {
    
    DoJDBCWork.withEzDBC(lcmsDbCtx, { ezDBC =>
      
      // Update map set alignment reference map
      ezDBC.execute( "UPDATE map_set SET aln_reference_map_id = " + alnRefMapId  + " WHERE id = " + mapSetId )
  
      // Update processed reference map
      ezDBC.execute( "UPDATE processed_map SET is_aln_reference = ? WHERE id = ?", true, alnRefMapId )
      
      // Store map alignments
      ezDBC.executePrepared(LcmsDbMapAlignmentTable.mkInsertQuery) { statement => 
        mapAlnSets.foreach { mapAlnSet =>
          mapAlnSet.mapAlignments.foreach { mapAln =>
            statement.executeWith(
              mapAln.fromMapId,
              mapAln.toMapId,
              mapAln.massRange._1,
              mapAln.massRange._2,
              mapAln.timeList.mkString(" "),
              mapAln.deltaTimeList.mkString(" "),
              Option.empty[String],
              mapSetId
            )
          }
        }  
      }
    
    })
  }  
  
}