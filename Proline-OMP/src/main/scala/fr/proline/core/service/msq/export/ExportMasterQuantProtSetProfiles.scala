package fr.proline.core.service.msq.export

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.model.msq.MasterQuantProteinSetProfile

class ExportMasterQuantProtSetProfiles(
  val execCtx: IExecutionContext,
  val masterQuantChannelId: Long,
  val outputFile: File,
  val expDesign: ExperimentalDesign,
  val exportBestProfile: Boolean = true
)  extends XQuantRsmExporter {
  
  // TODO: retrieve the right value
  val groupSetupNumber = 1
  
  val mqProtSetProfileHeaders = "peptides_count".split(" ")
  val qProtSetProfileHeaders = "abundance".split(" ")
  val ratioDefs = expDesign.groupSetupByNumber(groupSetupNumber).ratioDefinitions

  // TODO: process then export the table of prot set profiles using the profilizer
  
  def writeRows( fileWriter: PrintWriter ) {
    
    // Compute the prot set profiles using the profilizer
    import fr.proline.core.algo.msq.Profilizer
    import fr.proline.core.orm.uds.MasterQuantitationChannel
    val udsEM = execCtx.getUDSDbConnectionContext().getEntityManager()
    val udsMQC = udsEM.find(classOf[MasterQuantitationChannel], masterQuantChannelId)
    val profilizer = new Profilizer( expDesign, 1, udsMQC.getNumber() )
    /*profilizer.computeMasterQuantPeptideProfiles(quantRSM.masterQuantPeptides, 0.01f)*/
    profilizer.computeMasterQuantProtSetProfiles(quantRSM.masterQuantProteinSets, 0.01f)
        
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
        
        // Add ratios
        row ++= profile.ratios.map(_.map(_.state.toString).getOrElse("") )
        
        fileWriter.println(row.mkString("\t"))
        fileWriter.flush()
      }
        
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
  
  def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] ++ protSetHeaders ++ mqProtSetProfileHeaders
    for( i <- 1 to quantChannelCount ) rowHeaders ++= ( qProtSetProfileHeaders.map(_+"_"+i) )
    for( r <- ratioDefs ) rowHeaders += ("ratio_g" + r.numeratorGroupNumber +" _vs_g"+ r.denominatorGroupNumber)
    rowHeaders.mkString("\t")
  }
  
}