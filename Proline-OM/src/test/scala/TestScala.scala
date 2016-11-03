

import fr.proline.core.om.model.msq.MasterQuantComponent
import fr.proline.core.om.model.msq.MasterQuantPeptide
import fr.proline.core.om.model.msq.MasterQuantPeptideIon
import fr.proline.core.om.model.msq.QuantComponent
import fr.proline.core.om.model.msq.QuantPeptide
import fr.proline.core.om.model.msq.QuantPeptideIon
import scala.collection.mutable.LongMap
import scala.reflect.runtime.universe._

object TestScala extends App {

  val qpep = QuantPeptide(
    rawAbundance = 1000000.0f,
    abundance = 9999999.0f,
    elutionTime = 56.2f,
    peptideMatchesCount = 2,
    selectionLevel = 2,
    quantChannelId = 8)

 val qpepion = QuantPeptideIon(
    rawAbundance = 1000000.0f,
    abundance = 9999999.0f,
    moz = 5555.5f,
    elutionTime = 56.2f,
    duration = 5.2f,
    correctedElutionTime = 55.5f,
    scanNumber = 99,
    peptideMatchesCount = 2,
    ms2MatchingFrequency = Some(1.0f),
    bestPeptideMatchScore = Some(1.0f),
    predictedElutionTime = Some(1.0f),
    quantChannelId = 8,
    selectionLevel = 2)

  val mqPepIon = MasterQuantPeptideIon(
    id = 99,
  unlabeledMoz = 999.3f,
  charge = 2,
  elutionTime = 56.2f,
  peptideMatchesCount = 2,
  calculatedMoz =  None,

  selectionLevel = 2,
  masterQuantPeptideId = 5, 
  resultSummaryId= 1,
  quantPeptideIonMap = LongMap()+={8L -> qpepion} 
  )
    
  val mqPep = MasterQuantPeptide(
    id = 12L,
    peptideInstance = None,
    quantPeptideMap = LongMap()+={8L -> qpep} ,
    masterQuantPeptideIons = Array(mqPepIon),
   selectionLevel = 2,
  resultSummaryId = 1
  )

    

   val mq: MasterQuantComponent[QuantComponent] = mqPep.asInstanceOf[MasterQuantComponent[QuantComponent]]
   
   if (mq.isInstanceOf[MasterQuantPeptide]) { 
            val mqPep = mq.asInstanceOf[MasterQuantPeptide] 
            println("Master Quant Peptide : "+mqPep)
          } else { 
            val mqPepIon = mq.asInstanceOf[MasterQuantPeptideIon]
            println("Master Quant Peptide Ion : "+mqPepIon)
          }
  
  
  
pmatch(mq)
pmatch(mqPep)


  def pmatch[T:TypeTag](o: T) = typeTag[T].tpe match {
    case t if t =:= typeOf[MasterQuantComponent[QuantPeptide]] => println(s"Peptide: $t")
    case t if t =:= typeOf[MasterQuantComponent[QuantPeptideIon]] => println(s"Ion: $t")
    case t => println(s"Nothing: $t")
  }
}