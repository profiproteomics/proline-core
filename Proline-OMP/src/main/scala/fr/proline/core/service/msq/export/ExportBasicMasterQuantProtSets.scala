package fr.proline.core.service.msq.export

import java.io.File
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msq.ComputedRatio
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.model.msq.MasterQuantProteinSetProfile
import fr.proline.core.om.model.msq.MasterQuantProteinSet

class ExportBasicMasterQuantProtSets(
  val execCtx: IExecutionContext,
  val masterQuantChannelId: Long,
  val outputFile: File,
  val expDesign: ExperimentalDesign)  extends AbstractQuantRsmExporter {
  
  def writeRows( fileWriter: PrintWriter ) {
    
    val mqPepById = quantRSM.masterQuantPeptides.map( mqPep => mqPep.id -> mqPep ).toMap
    
    // Iterate over master quant peptides to export them
    quantRSM.masterQuantProteinSets.foreach { mqProtSet =>
      
      def exportMQProtSetData( mqProtSet: MasterQuantProteinSet ) {
        
        val row = new ArrayBuffer[Any]
        
        // Append protein set data
        appendProtSetCells(row: ArrayBuffer[Any], Some(mqProtSet.proteinSet) )
        

        // Add abundances
        qcIds.foreach( qcId => {
          var qcAbun = if(mqProtSet.quantProteinSetMap.contains(qcId)) {
            mqProtSet.quantProteinSetMap(qcId).abundance.toString
          } else {
            ""
          }
          
          row += qcAbun
        })
        
         // Add PSM_Count
        qcIds.foreach( qcId => {
          var qcPSMCount = if(mqProtSet.quantProteinSetMap.contains(qcId)) {
            mqProtSet.quantProteinSetMap(qcId).peptideMatchesCount.toString
          } else {
            ""
          }
          
          row += qcPSMCount
        })
        
        
//        // Add some statistics
//        val stats = this.stringifyRatiosStats(profile.getRatios)
//        row ++= stats
//        
        fileWriter.println(row.mkString("\t"))
        fileWriter.flush()
      }
        
      if( mqProtSet.proteinSet.isValidated ) {
        exportMQProtSetData(mqProtSet)
      }
    }

  }
  
  def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] ++ protSetHeaders //++ mqProtSetProfileHeaders
    
    qcIds.foreach( qcId => {
      rowHeaders += "abundance_"+qcId
    } )
    qcIds.foreach( qcId => {
      rowHeaders += "psm_count_"+qcId
    } )

    rowHeaders.mkString("\t")
  }
  
}