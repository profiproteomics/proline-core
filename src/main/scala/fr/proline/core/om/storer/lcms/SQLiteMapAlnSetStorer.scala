package fr.proline.core.om.storer.lcms

import fr.proline.core.LcmsDb

class SQLiteMapAlnSetStorer( lcmsDb: LcmsDb ) extends IMapAlnSetStorer {
  
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.SQLFormatterImplicits._
  import fr.proline.core.om.lcms.MapClasses.MapAlignmentSet
  import fr.proline.core.om.helper.SqlUtils.BoolToSQLStr
  
  def storeMapAlnSets( mapAlnSets: Seq[MapAlignmentSet], mapSetId: Int, alnRefMapId: Int ): Unit = {
    
    // Retrieve or create transaction
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    
    // Update map set alignment reference map
    lcmsDbTx.execute( "UPDATE map_set SET al_reference_map_id = " + alnRefMapId  + " WHERE id = " + mapSetId )

    // Update processed reference map
    lcmsDbTx.execute( "UPDATE processed_map SET is_aln_reference = " + 
                       BoolToSQLStr(true,lcmsDb.boolStrAsInt)  + " WHERE id = " + alnRefMapId )
    
    // Store map alignments
    lcmsDbTx.executeBatch("INSERT INTO map_alignment VALUES (?,?,?,?,?,?,?,?)") { statement => 
      mapAlnSets.foreach { mapAlnSet =>
        mapAlnSet.mapAlignments.foreach { mapAln =>
          statement.executeWith( mapAln.fromMapId,
                                 mapAln.toMapId,
                                 mapAln.massRange._1,
                                 mapAln.massRange._2,
                                 mapAln.timeList.mkString(" "),
                                 mapAln.deltaTimeList.mkString(" "),
                                 Some(null),
                                 mapSetId
                                )
        }
      }  
    }
    
    ()
  }  
  
}