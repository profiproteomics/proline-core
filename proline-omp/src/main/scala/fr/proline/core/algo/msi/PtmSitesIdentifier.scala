package fr.proline.core.algo.msi

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class PeptideInstancePtm(peptideInstance: PeptideInstance, ptm: LocatedPtm)

object PtmSitesIdentifier {

  def getModificationsProbability(pm: PeptideMatch): Float = {
    val proba = if (pm.properties.isDefined &&
                    pm.properties.get.ptmSiteProperties.isDefined && pm.properties.get.ptmSiteProperties.get.mascotDeltaScore.isDefined) {
      pm.properties.get.ptmSiteProperties.get.mascotDeltaScore.get
    } else {
      0.0f
    }
    proba
  }

  def isModificationProbabilityDefined(pm: PeptideMatch, ptm: LocatedPtm): Boolean = {
    // VDS : Correct Code
    //	        val result = (pm.properties.isDefined &&
    //	         pm.properties.get.ptmSiteProperties.isDefined &&
    //	         pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.isDefined &&
    //	         pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(ptm.toReadableString()))
    // VDS Workaround test for issue #16643
    var result = false
    if (pm.properties.isDefined && pm.properties.get.ptmSiteProperties.isDefined && pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.isDefined) {
      if (pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(ptm.toReadableString()))
        result = true
      else {
        result = pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(toOtherReadableString(ptm))
      }
    }
    result
  }

  def modificationProbability(pm: PeptideMatch, ptm: LocatedPtm): Float = {
    //	VDS Workaround test for issue #16643
    val f = if (pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get.contains(ptm.toReadableString())) {
      pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(ptm.toReadableString())
    } else {
      pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(PtmSitesIdentifier.toOtherReadableString(ptm))
    }
    f
    //VDS : Correct Code
    //	           pm.properties.get.ptmSiteProperties.get.getMascotProbabilityBySite.get(ptm.toReadableString())
  }

  /*
 * VDS Workaround test for issue #16643
 */
  private def toOtherReadableString(ptm: LocatedPtm) = {
    val ptmDef = ptm.definition
    val shortName = ptmDef.names.shortName

    val ptmConstraint = if (ptm.isNTerm || ptm.isCTerm) {
      val loc = PtmLocation.withName(ptmDef.location)
      var otherLoc: String = ""
      loc match {
        case PtmLocation.ANY_C_TERM => otherLoc = PtmLocation.PROT_C_TERM.toString()
        case PtmLocation.PROT_C_TERM => otherLoc = PtmLocation.ANY_C_TERM.toString()
        case PtmLocation.ANY_N_TERM => otherLoc = PtmLocation.PROT_N_TERM.toString()
        case PtmLocation.PROT_N_TERM => otherLoc = PtmLocation.ANY_N_TERM.toString()
      }
      otherLoc

    } else "" + ptmDef.residue + ptm.seqPosition

    s"${shortName} (${ptmConstraint})"
  }

}

/**
  * Determine PTMs site modifications
  *
  */
class PtmSitesIdentifier(
    val resultSummary: ResultSummary,
    val proteinMatches: Array[ProteinMatch]) extends LazyLogging {

  private val proteinMatchesById = proteinMatches.map { pm => pm.id -> pm }.toMap

  /**
    * Identifies all ptm sites from validated peptides of the resultSummary
    */
  def identifyPtmSites(): Iterable[PtmSite] = {

    val validatedProteinMatchesById = scala.collection.immutable.HashSet(resultSummary.getValidatedResultSet().get.proteinMatches.map(_.id): _*)
    val ptmSites = ArrayBuffer.empty[PtmSite]

    for (peptideSet <- resultSummary.peptideSets) {
      for (proteinMatchId <- peptideSet.proteinMatchIds) {
        // test if that proteinMatch is member of a validated protein sets
        if (validatedProteinMatchesById.contains(proteinMatchId)) {

          val sequenceMatchesByPeptideId = proteinMatchesById(proteinMatchId).sequenceMatches.groupBy { _.getPeptideId()  }
          val proteinMatchSites = scala.collection.mutable.Map[(Long, Int), ArrayBuffer[PeptideInstancePtm]]()
          val peptideInstanceIdsBySeqPtm = scala.collection.mutable.Map[String, ArrayBuffer[Long]]()
          val peptideInstancesById = peptideSet.getPeptideInstances().map(pi => (pi.id -> pi)).toMap

          for (peptideInstance <- peptideSet.getPeptideInstances().filter(!_.peptide.ptms.isEmpty)) {
            val key = _getKey(peptideInstance)
            peptideInstanceIdsBySeqPtm.getOrElseUpdate(key, ArrayBuffer.empty[Long]) += peptideInstance.id

            for (seqMatch <- sequenceMatchesByPeptideId(peptideInstance.peptide.id)) {
              for (ptm <- peptideInstance.peptide.ptms) {
                if (PtmSitesIdentifier.isModificationProbabilityDefined(peptideInstance.peptideMatches.head, ptm)) {
                  proteinMatchSites.getOrElseUpdate((ptm.definition.id, ptm.seqPosition + seqMatch.start - 1), ArrayBuffer.empty[PeptideInstancePtm]) += PeptideInstancePtm(peptideInstance, ptm)
                }
              }
            }
          }

          val site = proteinMatchSites.map {
            case (k, peptideInstances) =>

              // -- Search for the best PeptideMatch         
              //  Should order by score before getting max value. maxBy don't respect "first for equal order" ! 
              val bestPMs = peptideInstances.map(t => {
                var bestProba: Float = 0.00f
                var bestPM: PeptideMatch = null
                val sortedPepMatches: Array[PeptideMatch] = t.peptideInstance.peptideMatches.sortBy(_.score).reverse
                sortedPepMatches.foreach { pepM =>
                  val proba = PtmSitesIdentifier.modificationProbability(pepM, t.ptm)
                  if (proba > bestProba) {
                    bestPM = pepM
                    bestProba = proba
                  }
                }
                (bestPM -> t.ptm)
              })

              var bestPeptideMatch: PeptideMatch = null
              var bestProba: Float = 0.00f
              val sortedBestPMs = bestPMs.sortBy(_._1.score).reverse
              sortedBestPMs.foreach(f => {
                val proba = PtmSitesIdentifier.modificationProbability(f._1, f._2)
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

              val peptideIdsBySeqPosition = peptideInstances.groupBy(_.ptm.seqPosition).mapValues(_.map(_.peptideInstance.peptide.id).toArray)

              PtmSite(
                proteinMatchId = proteinMatchId,
                ptmDefinitionId = k._1,
                seqPosition = k._2,
                bestPeptideMatchId = bestPeptideMatch.id,
                localizationConfidence = bestProba,
                peptideIdsByPtmPosition = peptideIdsBySeqPosition,
                peptideInstanceIds = peptideInstances.map(_.peptideInstance.id).toArray,
                isomericPeptideInstanceIds = isomericPeptideInstances.map(_.id).toArray)
          }
          ptmSites ++= site
        }
      }
    }
    logger.info(ptmSites.size + " Ptm sites were identified")
    ptmSites
  }

  def aggregatePtmSites(childrenSites: Array[Iterable[PtmSite]], sitesProteinMatches: Array[ProteinMatch], peptideMatchProvider: (Array[Long]) => Map[Long, PeptideMatch]): Iterable[PtmSite] = {
    val proteinAccessionByProteinMatchId = sitesProteinMatches.map { pm => pm.id -> pm.accession }.toMap
    val proteinMatchesByAccession = proteinMatches.map { pm => pm.accession -> pm }.toMap
    val ptmSites = ArrayBuffer.empty[PtmSite]

    val ptmSitesMap = scala.collection.mutable.Map[(String, Long, Int), ArrayBuffer[PtmSite]]()

    for (sites <- childrenSites) {
      sites.foreach { site =>
        ptmSitesMap.getOrElseUpdate((proteinAccessionByProteinMatchId(site.proteinMatchId), site.ptmDefinitionId, site.seqPosition), ArrayBuffer.empty[PtmSite]) += site
      }
    }

    ptmSitesMap.foreach {
      case (key, site) =>
        val peptideMap = site.map(_.peptideIdsByPtmPosition).flatten
        val newPeptideMap = peptideMap.groupBy(_._1).map { case (k, v) => k -> v.map(_._2).flatten.distinct.toArray }
        val bestProbabilities = site.map(_.bestPeptideMatchId) zip site.map(_.localizationConfidence)
        val newBestPTMProbability = bestProbabilities.maxBy(_._2)._2
        val bestPeptideMatchIds = bestProbabilities.filter(_._2 >= newBestPTMProbability)
        val newBestPeptideMatchId = {
          if (bestPeptideMatchIds.size > 1) {
            // if there is more than one peptideMatch with the same max probability, choose the one with the highest identification score
            val pmScoresById = peptideMatchProvider(bestPeptideMatchIds.map(_._1).toArray)
            bestPeptideMatchIds.map(x => (x._1, pmScoresById(x._1))).maxBy(_._2.score)._1
          } else {
            bestPeptideMatchIds.head._1
          }
        }
        val newPeptideInstanceIds = site.map(_.peptideInstanceIds).flatten.distinct
        val newIsomericPeptideInstanceIds = site.map(_.isomericPeptideInstanceIds).flatten.distinct

        val newSite = PtmSite(proteinMatchId = proteinMatchesByAccession(key._1).id,
          ptmDefinitionId = key._2,
          seqPosition = key._3,
          bestPeptideMatchId = newBestPeptideMatchId,
          localizationConfidence = newBestPTMProbability,
          peptideIdsByPtmPosition = newPeptideMap,
          peptideInstanceIds = newPeptideInstanceIds.toArray,
          isomericPeptideInstanceIds = newIsomericPeptideInstanceIds.toArray)
        ptmSites += newSite
    }
    logger.info(ptmSites.size + " Ptm sites identified")
    ptmSites
  }

  /*
   * get a key for the given PeptideInstance based on sequence and ptms definition sorted by name. This means that to peptide instances with
   * same sequence and a same modification located at a different position get the same key.
   */
  private def _getKey(peptideInstance: PeptideInstance): String = {
    peptideInstance.peptide.sequence + peptideInstance.peptide.ptms.map(_.definition.toReadableString()).sorted.mkString
  }

}

object PtmStatus extends Enumeration {
  val Isomorphic, PartiallyIsomorphic, Compatible, Conflicting = Value
}

class PtmSiteClusterer(
    val resultSummary: ResultSummary,
    val proteinMatches: Array[ProteinMatch]
) extends LazyLogging {

  private val proteinMatchesById = proteinMatches.map { pm => pm.id -> pm }.toMap
  private var peptideById = resultSummary.peptideInstances.map { pi => pi.peptide.id -> pi.peptide }.toMap

  def clusterize(proteinMatchId: Long, sites: Array[PtmSite2], peptideMatchProvider: (Array[Long]) => Map[Long, PeptideMatch]): Array[PtmCluster] = {

    var sitesBySequenceMatch = new mutable.HashMap[SequenceMatch, ArrayBuffer[PtmSite2]]()
    var clusters = new ArrayBuffer[PtmCluster]()

    val sequenceMatchesByPeptideId = proteinMatchesById(proteinMatchId).sequenceMatches.groupBy { _.getPeptideId()  }

    // group sites by peptideIds
    sites.map { s => s -> s.peptideIdsByPtmPosition.values.toArray.flatten.distinct }.foreach {
      case (site, ids) =>
        ids.foreach { id =>
          // partition sites by sequence matches
          val sequenceMatch = sequenceMatchesByPeptideId(id).filter(sm => (site.seqPosition >= (sm.start - 1) && (site.seqPosition <= sm.end)))
          if (!sequenceMatch.isEmpty) {
            sitesBySequenceMatch.getOrElseUpdate(sequenceMatch.head, new ArrayBuffer[PtmSite2]) += site
          } else {
            logger.error(s"The PTM site ${site} has no sequence match for peptide id ${id} = ${peptideById(id)}")
          }
        }
    }

    var orderedSequenceMatches = sitesBySequenceMatch.toSeq.sortBy(_._2.size).reverse.map { _._1 }
    var clusterizedSequenceMatches = new mutable.HashMap[SequenceMatch, Boolean]()

    for (referenceSequenceMatch <- orderedSequenceMatches) {
      if (!clusterizedSequenceMatches.contains(referenceSequenceMatch)) {
        val clusterizedPeptides = new mutable.HashMap[PtmStatus.Value, ArrayBuffer[Peptide]]()
        clusterizedPeptides.getOrElseUpdate(PtmStatus.Isomorphic, new ArrayBuffer[Peptide]) += peptideById(referenceSequenceMatch.getPeptideId());

        var matchingSequenceMatches = sitesBySequenceMatch(referenceSequenceMatch).flatMap(_.peptideIdsByPtmPosition.flatMap(_._2)).distinct.flatMap(sequenceMatchesByPeptideId(_)).toArray
        // restrict to sequenceMatch for which their is at least one site from the reference matching on it
        matchingSequenceMatches = matchingSequenceMatches.filter(sm => (sm.start <= referenceSequenceMatch.end) && (sm.end >= referenceSequenceMatch.start )).filter(!clusterizedSequenceMatches.contains(_))
        for (candidateSequenceMatch <- matchingSequenceMatches) {
          val status = comparePeptidesPtms(referenceSequenceMatch, candidateSequenceMatch, sitesBySequenceMatch, proteinMatchId)
          status match {
            case PtmStatus.Isomorphic => {
              clusterizedPeptides(status) += peptideById(candidateSequenceMatch.getPeptideId())
              clusterizedSequenceMatches += (candidateSequenceMatch -> true)
            }
            case PtmStatus.Compatible | PtmStatus.PartiallyIsomorphic => {
              clusterizedPeptides.getOrElseUpdate(status, new ArrayBuffer[Peptide]) += peptideById(candidateSequenceMatch.getPeptideId())
            }
            case _ => {}
          }

        }

        // determine bestPeptideMatch
        val peptideIds = clusterizedPeptides(PtmStatus.Isomorphic).map(_.id).toArray
        val peptideMatches = peptideMatchProvider(peptideIds)

        val bestPeptideMatch = peptideMatches.values.maxBy{ pm => PtmSitesIdentifier.getModificationsProbability(pm)}

        clusters += new PtmCluster(
          ptmSiteLocations = sitesBySequenceMatch(referenceSequenceMatch).map(_.id).toArray,
          bestPeptideMatchId = bestPeptideMatch.id,
          localizationConfidence = PtmSitesIdentifier.getModificationsProbability(bestPeptideMatch),
          peptideIds = clusterizedPeptides.flatten(_._2).map(_.id).toArray.distinct,
          isomericPeptideIds = Array.empty[Long])
      }
    }

    clusters.toArray
  }

  def comparePeptidesPtms(reference: SequenceMatch, candidate: SequenceMatch, sitesBySequenceMatch: mutable.HashMap[SequenceMatch, ArrayBuffer[PtmSite2]], proteinMatchId: Long): PtmStatus.Value = {
    val refSites = sitesBySequenceMatch(reference)
    val candidateSites = sitesBySequenceMatch(candidate)
    val diffRC = refSites.filter(!candidateSites.contains(_))
    val diffCR = candidateSites.filter(!refSites.contains(_))
    if ((diffCR.size == 0) && (diffRC.size == 0)) {
      return PtmStatus.Isomorphic
    } else if (diffCR.size == 0) {

      val sitesInCandidateBounds = diffRC.filter(s => s.seqPosition >= (candidate.start-1) && (s.seqPosition <= candidate.end))
      if (sitesInCandidateBounds.length == 0)
        return PtmStatus.PartiallyIsomorphic
      else
        return PtmStatus.Conflicting
    } else {
      return PtmStatus.Conflicting
    }
  }
}