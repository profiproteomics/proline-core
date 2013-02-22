package fr.proline.core.om.storer.lcms.impl

import fr.profi.jdbc.SQLQueryExecution
import fr.profi.jdbc.easy._
import fr.proline.core.om.storer.lcms.IMapAlnSetStorer

class SQLiteMapAlnSetStorer( lcmsDb: SQLQueryExecution ) extends IMapAlnSetStorer {
  
  import fr.proline.core.om.model.lcms.MapAlignmentSet
  import fr.proline.util.sql.BoolToSQLStr
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit = {
    
    // Update map set alignment reference map
    lcmsDb.execute( "UPDATE map_set SET aln_reference_map_id = " + alnRefMapId  + " WHERE id = " + mapSetId )

    // Update processed reference map
    lcmsDb.execute( "UPDATE processed_map SET is_aln_reference = ? WHERE id = ?", true, alnRefMapId )
    
    // Store map alignments
    lcmsDb.executePrepared("INSERT INTO map_alignment VALUES (?,?,?,?,?,?,?,?)") { statement => 
      mapAlnSets.foreach { mapAlnSet =>
        mapAlnSet.mapAlignments.foreach { mapAln =>
          statement.executeWith( mapAln.fromMapId,
                                 mapAln.toMapId,
                                 mapAln.massRange._1,
                                 mapAln.massRange._2,
                                 mapAln.timeList.mkString(" "),
                                 mapAln.deltaTimeList.mkString(" "),
                                 Option(null),
                                 mapSetId
                                )
        }
      }  
    }
    
    ()
  }  
  
}