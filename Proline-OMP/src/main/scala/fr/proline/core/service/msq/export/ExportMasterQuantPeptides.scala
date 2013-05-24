package fr.proline.core.service.msq.export

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.api.service.IService
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.context.IExecutionContext


class ExportMasterQuantPeptides( execCtx: IExecutionContext, quantRsmId: Int, outputFile: File ) extends IService {
  
  val locale = java.util.Locale.ENGLISH

  val proSetHeaders = "AC description".split(" ")
  val pepHeaders = "sequence".split(" ")
  val mqPepHeaders = "quant_peptide_id selection_level".split(" ")  
  val qPepHeaders = "raw_abundance".split(" ") 

  
  def runService() = {
    
    val quantRsmProvider = new SQLQuantResultSummaryProvider(
      execCtx.getMSIDbConnectionContext,
      execCtx.getPSDbConnectionContext,
      execCtx.getUDSDbConnectionContext
    )
    
    val quantRSM = quantRsmProvider.getQuantResultSummary(quantRsmId, true).get
    // FIXME: retrieve from quantRSM 
    val qcIds = Array(1,2)
    
    // Create somme mappings
    val mqPepById = Map() ++ quantRSM.masterQuantPeptides.map( mqPep => mqPep.id -> mqPep )    
    val protSetByPepInst = Map()++ quantRSM.resultSummary.proteinSets.flatMap( protSet => protSet.peptideSet.getPeptideInstances.map( pi => pi.id -> protSet ) )
    val protMatchById = quantRSM.resultSummary.resultSet.get.proteinMatchById
      
    // Stringify protein set data
    val protSetCellsById = new HashMap[Int,ArrayBuffer[Any]]
    for( mqProtSet <- quantRSM.masterQuantProteinSets ) {
      val protMatch = protMatchById( mqProtSet.proteinSet.proteinMatchIds(0) )
      val protSetCells = new ArrayBuffer[Any]
      protSetCells += protMatch.accession
      protSetCells += protMatch.description
      
      protSetCellsById += mqProtSet.id -> protSetCells
    }
    
    // TODO: stringify peptide instance data
    
    // Create a file writer and print the header
    val fileWriter = new PrintWriter(new FileOutputStream(outputFile))
    fileWriter.println(mkRowHeader(qcIds.length))
    fileWriter.flush()
    
    // Iterate over master quant peptides to export them
    quantRSM.masterQuantPeptides.foreach { mqPep =>
      val row = new ArrayBuffer[Any]
      
      // Append protein set and peptide data if they are defined
      if( mqPep.peptideInstance.isDefined) {
        
        for( pepInst <- mqPep.peptideInstance ) {
          val proSetOpt = protSetByPepInst.get(pepInst.id)
          if( proSetOpt.isDefined ) {
            row ++= protSetCellsById(proSetOpt.get.id)
          } else {
            row ++= Array.fill(proSetHeaders.length)("")
          }

          row ++= Array(pepInst.peptide.sequence)
        }
      } else {
        row ++= Array.fill(proSetHeaders.length + pepHeaders.length)("")
      }

      // Append master quant peptide data
      row ++= Array(mqPep.id,mqPep.selectionLevel)
      
      // Append quant peptide data for each condition
      val qPepCellsByQcId = new HashMap[Int,ArrayBuffer[Any]]
      for( (qcId, qPep) <- mqPep.quantPeptideMap ) {
        val qPepCells = new ArrayBuffer[Any]
        qPepCells += qPep.rawAbundance
        
        qPepCellsByQcId += qcId -> qPepCells
      }
      
      for(qcId <- qcIds) {
        val qPepCellsOpt = qPepCellsByQcId.get(qcId)
        if( qPepCellsOpt.isDefined ) row ++= qPepCellsOpt.get
        else row ++= Array.fill(qPepHeaders.length)("")
      }
      
      fileWriter.println(row.mkString("\t"))
      fileWriter.flush()
      
/*val rawAbundance: Float,
                         var abundance: Float,
                         val elutionTime: Float,
                         val peptideMatchesCount: Int,
                         
                         val quantChannelId: Int,
                         val peptideId: Int,
                         val peptideInstanceId: Int,*/
      
          /*
          var id: Int, // important: master quant component id
                               val peptideInstance: Option[PeptideInstance], // without label in the context of isotopic labeling
                               var quantPeptideMap: Map[Int,QuantPeptide], // QuantPeptide by quant channel id
                               var masterQuantPeptideIons: Array[MasterQuantPeptideIon],
                               
                               var selectionLevel: Int,
                               var resultSummaryId: Int,*/
        


    }

    fileWriter.close()
    
    true
  }
  
  def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] ++ proSetHeaders ++ pepHeaders ++ mqPepHeaders
    for( i <- 1 to quantChannelCount ) rowHeaders ++= ( qPepHeaders )
    rowHeaders.mkString("\t")
  }

}