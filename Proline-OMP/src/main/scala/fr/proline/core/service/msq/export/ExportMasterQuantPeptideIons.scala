package fr.proline.core.service.msq.export

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.api.service.IService
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.context.IExecutionContext

class ExportMasterQuantPeptideIons(
  override val execCtx: IExecutionContext,
  override val masterQuantChannelId: Long,
  override val outputFile: File,
  override val expDesign: ExperimentalDesign
) extends ExportMasterQuantPeptides(execCtx,masterQuantChannelId,outputFile,expDesign) {
  
  protected val mqPepIonHeaders = "charge master_elution_time master_feature_id".split(" ")
  protected val qPepIonHeaders = "moz elution_time correct_elution_time duration raw_abundance ms2_count ms2_matching_freq feature_id".split(" ")
  
  // Create some mappings
  protected val mqPepById = Map() ++ quantRSM.masterQuantPeptides.map( mqPep => mqPep.id -> mqPep ) 

  override protected def writeRows( fileWriter: PrintWriter ) {
    
    // TODO: stringify peptide instance data
    
    // Iterate over master quant peptides to export them
    quantRSM.masterQuantPeptideIons.foreach { mqPepIon =>
      val row = new ArrayBuffer[Any]
      
      val mqPep = mqPepById(mqPepIon.masterQuantPeptideId)
      
      // Append protein set and peptide data if they are defined
      if( mqPep.peptideInstance.isDefined) {
        val pepInstId = mqPep.peptideInstance.get.id
        appendProtSetAndPepInstCells(row,protSetByPepInst.get(pepInstId),mqPep.peptideInstance)
      } else {
        appendProtSetAndPepInstCells(row,None,None)
      }
      
      row ++= mqPepCellsById(mqPep.id)
      
      // Append ratios
      val mqPepProfileByGroupSetupNumOpt = mqPep.properties.get.getMqPepProfileByGroupSetupNumber
      if( mqPepProfileByGroupSetupNumOpt.isDefined ) {
        val mqPepProfile = mqPepProfileByGroupSetupNumOpt.get(groupSetupNumber.toString)
        val ratios = mqPepProfile.getRatios.map(_.map(_.getState.toString).getOrElse("") )      
        row ++= ratios
      } else {
        row ++= Array.fill(this.ratioDefs.length)("")
      }
      
      // Append master quant peptide ion data
      row ++= Array(mqPepIon.charge,mqPepIon.elutionTime,mqPepIon.lcmsFeatureId.getOrElse(""))
      
      // Append quant peptide data for each condition
      val qPepIonCellsByQcId = new HashMap[Long,Seq[Any]]
      for( (qcId, qPepIon) <- mqPepIon.quantPeptideIonMap ) {
        val qPepIonCells = List(
          qPepIon.moz,
          qPepIon.elutionTime,
          qPepIon.correctedElutionTime,
          qPepIon.duration,
          qPepIon.rawAbundance,
          qPepIon.peptideMatchesCount,
          qPepIon.ms2MatchingFrequency.getOrElse(0),
          qPepIon.lcmsFeatureId
        )
        
        qPepIonCellsByQcId += qcId -> qPepIonCells
      }
      
      for(qcId <- qcIds) {
        val qPepIonCellsOpt = qPepIonCellsByQcId.get(qcId)
        if( qPepIonCellsOpt.isDefined ) row ++= qPepIonCellsOpt.get
        else row ++= Array.fill(qPepIonHeaders.length)("")
      }
      
      fileWriter.println(row.mkString("\t"))
      fileWriter.flush()

    }

  }
  
  override protected def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] + super.mkRowHeader(quantChannelCount) ++ mqPepIonHeaders
    for( i <- 1 to quantChannelCount ) rowHeaders ++= ( qPepIonHeaders.map(_+"_"+i) )
    rowHeaders.mkString("\t")
  }

}