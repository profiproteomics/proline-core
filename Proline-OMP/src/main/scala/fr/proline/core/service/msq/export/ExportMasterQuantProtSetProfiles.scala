package fr.proline.core.service.msq.export

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msq.ComputedRatio
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.model.msq.MasterQuantProteinSetProfile

class ExportMasterQuantProtSetProfiles(
  val execCtx: IExecutionContext,
  val masterQuantChannelId: Long,
  val outputFile: File,
  val expDesign: ExperimentalDesign,
  val exportBestProfile: Boolean = true
)  extends AbstractQuantRsmExporter {
  
  // TODO: retrieve the right value
  val groupSetupNumber = 1
  
  val mqProtSetProfileHeaders = "peptides_count".split(" ")
  val ratioDefs = expDesign.groupSetupByNumber(groupSetupNumber).ratioDefinitions

  def writeRows( fileWriter: PrintWriter ) {
    
    val mqPepById = quantRSM.masterQuantPeptides.map( mqPep => mqPep.id -> mqPep ).toMap
    
    // Iterate over master quant peptides to export them
    quantRSM.masterQuantProteinSets.foreach { mqProtSet =>
      
      def exportProfile( profile: MasterQuantProteinSetProfile ) {
        
        val row = new ArrayBuffer[Any]
        
        // Append protein set data
        appendProtSetCells(row: ArrayBuffer[Any], Some(mqProtSet.proteinSet) )
        
        // Add number of peptides
        row += profile.mqPeptideIds.length
        
        // Add abundances
        row ++= profile.abundances.map( a => if( a.isNaN ) "" else a.toString )
        
        // Sum the PSM count for each quant channel of this quantitative profile
        val pepMatchCountByQcId = new HashMap[Long,Int]
        profile.getMqPeptideIds().foreach { mqPepId =>
          val mqPep = mqPepById(mqPepId)
          for( (qcId,qPep) <- mqPep.quantPeptideMap ) {
            val count = pepMatchCountByQcId.getOrElseUpdate(qcId, 0)
            pepMatchCountByQcId(qcId) = count + qPep.peptideMatchesCount
          }
        }
        
        // Add PSM counts to the row
        for( qcId <- this.qcIds ) {
          row += pepMatchCountByQcId.getOrElse(qcId, 0)
        }
        
        // Add some statistics
        val stats = this.stringifyRatiosStats(profile.getRatios)
        row ++= stats
        
        fileWriter.println(row.mkString("\t"))
        fileWriter.flush()
      }
        
      if( mqProtSet.proteinSet.isValidated ) {
        if( exportBestProfile ) {
          val bestProfile = mqProtSet.getBestProfile(groupSetupNumber)
          if( bestProfile.isDefined ) exportProfile( bestProfile.get )
        } else {
          
          // Iterate over all profiles to eacport them
          for( props <- mqProtSet.properties;
               profileByGSNum <- props.getMqProtSetProfilesByGroupSetupNumber;
               profiles <- profileByGSNum.get(groupSetupNumber.toString);
               profile <- profiles
             ) {
            exportProfile( profile )
          }
        }
      }
    }

  }
  
  def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] ++ protSetHeaders ++ mqProtSetProfileHeaders
    
    for( i <- 1 to quantChannelCount ) rowHeaders += "abundance_"+i
    for( i <- 1 to quantChannelCount ) rowHeaders += "psm_count_"+i
    for( r <- ratioDefs ) rowHeaders ++= statHeaders.map( _ + ("_g" + r.numeratorGroupNumber +" _vs_g"+ r.denominatorGroupNumber) )
    
    rowHeaders.mkString("\t")
  }
  
}