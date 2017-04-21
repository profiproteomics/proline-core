package fr.proline.core.algo.msi

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.control.Breaks.break
import scala.util.control.Breaks.breakable
import fr.proline.context.IExecutionContext
import fr.profi.util.regex.RegexUtils._
import com.typesafe.scalalogging.LazyLogging
import javax.persistence.EntityManager
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.PtmSite
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.SequenceMatch
import fr.proline.core.om.model.msi.IPtmSpecificity
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PtmSite

case class PeptideInstancePtm(peptideInstance: PeptideInstance, ptm: LocatedPtm)


/**
 * Determine PTMs site modifications 
 * 
 */
class PtmSitesIdentifier () extends LazyLogging {
   
  /**
   *   
   */
	def identifyPtmSites(rsm: ResultSummary, proteinMatches: Array[ProteinMatch]) : Iterable[PtmSite] = {
	  
	  val proteinMatchesById = proteinMatches.map { pm => pm.id -> pm }.toMap
	  val ptmSites = ArrayBuffer.empty[PtmSite]
	 
	  for (peptideSet <- rsm.peptideSets) {	    
	    for (proteinMatchId <- peptideSet.proteinMatchIds) {
	      
	      def isModificationProbabilityDefined(pm: PeptideMatch, ptm: LocatedPtm): Boolean = {
	        val result = (pm.properties.isDefined && 
	         pm.properties.get.ptmSiteProperties.isDefined &&
	         pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.isDefined &&
	         pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(ptm.toReadableString()))
	        result
	      }
	      
	      val sequenceMatchesByPeptideId: Map[Long, SequenceMatch] = proteinMatchesById(proteinMatchId).sequenceMatches.map { sm => (sm.getPeptideId() -> sm) }.toMap
	      val proteinMatchSites = scala.collection.mutable.Map[(PtmDefinition, Int), ArrayBuffer[PeptideInstancePtm]]() 
	      val peptideInstanceIdsBySeqPtm = scala.collection.mutable.Map[String, ArrayBuffer[Long]]()
	      val peptideInstancesById = peptideSet.getPeptideInstances().map( pi => (pi.id -> pi) ).toMap
	      
	      for (peptideInstance <- peptideSet.getPeptideInstances().filter(!_.peptide.ptms.isEmpty)) {
	        val key = _getKey(peptideInstance)
	        peptideInstanceIdsBySeqPtm.getOrElseUpdate(key , ArrayBuffer.empty[Long]) += peptideInstance.id
	        
	        val seqMatch = sequenceMatchesByPeptideId(peptideInstance.peptide.id)
	        for (ptm <- peptideInstance.peptide.ptms) {
	          if (isModificationProbabilityDefined(peptideInstance.peptideMatches.head, ptm)) {
	            proteinMatchSites.getOrElseUpdate((ptm.definition, ptm.seqPosition + seqMatch.start - 1), ArrayBuffer.empty[PeptideInstancePtm]) += PeptideInstancePtm(peptideInstance, ptm) 
	          } 
	        }
	      }
	      
	      val site = proteinMatchSites.map{case (k,peptideInstances) => 

	       def modificationProbability(pm: PeptideMatch, ptm: LocatedPtm): Float = {
	              pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(ptm.toReadableString())
	       }
	        
	        // Search for the best PeptideMatch 
	        val bestPMs = peptideInstances.map{t => 
	          (t.peptideInstance.peptideMatches.maxBy { pm => modificationProbability(pm, t.ptm) } -> t.ptm)
	        }
	        
	        val bestPeptideMatch = bestPMs.maxBy(t => modificationProbability(t._1, t._2))._1
	        
	        val isomericPeptideInstanceIds = peptideInstances.flatMap(piptm => peptideInstanceIdsBySeqPtm(_getKey(piptm.peptideInstance))).distinct
	        isomericPeptideInstanceIds --= peptideInstances.map(_.peptideInstance.id)
	        val isomericPeptideInstances = isomericPeptideInstanceIds.map(id => peptideInstancesById(id))

//	        val peptideMatchesSeq = peptideInstances.map(_.peptideInstance.peptide.sequence).toArray
//	        val isomericPeptideMatchesSeq = isomericPeptideInstances.map(_.peptide.sequence).toArray
//	        println(proteinMatchesById(proteinMatchId).accession + ", "+k._2+", "+k._1.toReadableString()+", matches = "+peptideMatchesSeq.mkString(",")+"("+peptideInstances.map(_.peptideInstance.id).toArray.mkString(",")+")"+", isomeric matches = "+isomericPeptideMatchesSeq.mkString(",")+"("+isomericPeptideInstances.map(_.id).toArray.mkString(",")+")") 
	        
	        val peptideIdsBySeqPosition = peptideInstances.groupBy(_.ptm.seqPosition).mapValues( _.map(_.peptideInstance.peptide.id).toArray)
	        
	        PtmSite(
	          proteinMatchId = proteinMatchId, 
	          definitionId= k._1.id, 
	          seqPosition = k._2, 
	          bestPeptideMatchId = bestPeptideMatch.id,
	          peptideIdsByPtmPosition = peptideIdsBySeqPosition,
	          peptideInstanceIds = peptideInstances.map(_.peptideInstance.id).toArray, 
	          isomericPeptideInstanceIds = isomericPeptideInstances.map(_.id).toArray)
	      }
	      ptmSites ++= site
	    }
	  }
	  logger.info(ptmSites.size + " Ptm sites identified")
    ptmSites
	}
	
	private def _getKey(peptideInstance: PeptideInstance): String = {
	  peptideInstance.peptide.sequence+peptideInstance.peptide.ptms.map(_.definition.toReadableString()).sorted.mkString
	}
}

