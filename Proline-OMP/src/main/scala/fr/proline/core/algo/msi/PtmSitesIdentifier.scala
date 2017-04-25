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
import fr.proline.core.om.model.msi.PtmLocation

case class PeptideInstancePtm(peptideInstance: PeptideInstance, ptm: LocatedPtm)


/**
 * Determine PTMs site modifications 
 * 
 */
class PtmSitesIdentifier () extends LazyLogging {
   
// VDS Workaround test for issue #16643   
//  private def  toOtherReadableString(ptm: LocatedPtm) = {
//    val ptmDef = ptm.definition
//    val shortName = ptmDef.names.shortName
//    
//    val ptmConstraint = if (ptm.isNTerm || ptm.isCTerm){ 
//        val loc = PtmLocation.withName(ptmDef.location)
//        var otherLoc : String = ""
//        loc match {
//          case PtmLocation.ANY_C_TERM => otherLoc = PtmLocation.PROT_C_TERM.toString()
//          case PtmLocation.PROT_C_TERM => otherLoc = PtmLocation.ANY_C_TERM.toString()
//          case PtmLocation.ANY_N_TERM => otherLoc = PtmLocation.PROT_N_TERM.toString()
//          case PtmLocation.PROT_N_TERM => otherLoc = PtmLocation.ANY_N_TERM.toString()
//        }
//        otherLoc
//           
//      } else "" + ptmDef.residue + ptm.seqPosition
//    
//    s"${shortName} (${ptmConstraint})"
//  }
  
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
	         // VDS Workaround test for issue #16643
//          var result = false; 	        
//	        if(pm.properties.isDefined && pm.properties.get.ptmSiteProperties.isDefined && pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.isDefined){
//	          if(pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(ptm.toReadableString()))
//	            result = true;
//	          else {
//	            result = pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(toOtherReadableString(ptm))
//	          }
//	        }
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
//	           VDS Workaround test for issue #16643
//	           val f = if(pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(ptm.toReadableString())) {
//	             pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(ptm.toReadableString())
//	           } else {
//	             pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(toOtherReadableString(ptm))
//	           }
//           
//             f
	           pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(ptm.toReadableString())	           
	         }

            // -- Search for the best PeptideMatch         
            //  Should order by score before getting max value. maxBy don't respect "first for equal order" ! 
            val bestPMs = peptideInstances.map(t =>
              {
                var bestProba: Float = 0.00f;
                var bestPM: PeptideMatch = null;
                val sortedPepMatches: Array[PeptideMatch] = t.peptideInstance.peptideMatches.sortBy(_.score).reverse
                sortedPepMatches.foreach { pepM =>
                  val proba = modificationProbability(pepM, t.ptm);
                  if (proba > bestProba) {
                    bestPM = pepM
                    bestProba = proba
                  }
                }
                (bestPM -> t.ptm)
              });

            var bestPeptideMatch: PeptideMatch = null
            var bestProba: Float = 0.00f;
            val sortedBestPMs = bestPMs.sortBy(_._1.score).reverse
            sortedBestPMs.foreach(f => {
              val proba = modificationProbability(f._1, f._2);
              if (proba > bestProba) {
                bestPeptideMatch = f._1
                bestProba = proba
              }
            })
	        
	        
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

