package fr.proline.core.algo.msi

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.misc.InMemoryIdGen
import fr.proline.core.om.model.SelectionLevel
import fr.proline.core.om.model.msi._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class PeptideInstancePtmTuple(peptideInstance: PeptideInstance, ptm: LocatedPtm)

object PtmSitesIdentifier {

  def allModificationsProbability(pm: PeptideMatch): Float = {
    val proba = if (pm.properties.isDefined &&
                    pm.properties.get.ptmSiteProperties.isDefined &&
                    pm.properties.get.ptmSiteProperties.get.mascotDeltaScore.isDefined) {
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

  def singleModificationProbability(pm: PeptideMatch, ptm: LocatedPtm): Float = {
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

  protected val proteinMatchesById = proteinMatches.map { pm => pm.id -> pm }.toMap

  /**
    * Identifies all ptm sites from validated peptides of the resultSummary
    */
  def identifyPtmSites(): Iterable[PtmSite] = {

    val validatedProteinMatchesById = scala.collection.immutable.HashSet(resultSummary.getValidatedResultSet().get.proteinMatches.map(_.id): _*)
    val ptmSites = ArrayBuffer.empty[PtmSite]

    for (peptideSet <- resultSummary.peptideSets) {
      for (proteinMatchId <- peptideSet.proteinMatchIds) {

        // Apply PTM site identification only on validated protein sets
        if (validatedProteinMatchesById.contains(proteinMatchId)) {

          val sequenceMatchesByPeptideId = proteinMatchesById(proteinMatchId).sequenceMatches.groupBy { _.getPeptideId()  }
          val proteinMatchSites = scala.collection.mutable.Map[(Long, Int), ArrayBuffer[PeptideInstancePtmTuple]]()
          val peptideInstanceIdsBySeqPtm = scala.collection.mutable.Map[String, ArrayBuffer[Long]]()
          val peptideInstancesById = peptideSet.getPeptideInstances().map(pi => (pi.id -> pi)).toMap

          for (peptideInstance <- peptideSet.getPeptideInstances().filter(!_.peptide.ptms.isEmpty)) {
            val aaSeqAndPTMsKey = _getAASequenceAndPTMsAsString(peptideInstance)
            peptideInstanceIdsBySeqPtm.getOrElseUpdate(aaSeqAndPTMsKey, ArrayBuffer.empty[Long]) += peptideInstance.id

            for (seqMatch <- sequenceMatchesByPeptideId(peptideInstance.peptide.id)) {
              for (ptm <- peptideInstance.peptide.ptms) {
                if (PtmSitesIdentifier.isModificationProbabilityDefined(peptideInstance.peptideMatches.head, ptm)) {
                  proteinMatchSites.getOrElseUpdate((ptm.definition.id, ptm.seqPosition + seqMatch.start - 1), ArrayBuffer.empty[PeptideInstancePtmTuple]) += PeptideInstancePtmTuple(peptideInstance, ptm)
                }
              }
            }
          }

          val site = proteinMatchSites.map {
            case (ptmDefinitionPositionTuple, peptideInstances) =>

              // -- Search for the best PeptideMatch
              //  Should order by score before getting max value. maxBy don't respect "first for equal order" !
              val bestPMs = peptideInstances.map(peptideInstancePtmTuple => {
                var bestProba: Float = 0.00f
                var bestPM: PeptideMatch = null
                val sortedPepMatches: Array[PeptideMatch] = peptideInstancePtmTuple.peptideInstance.peptideMatches.sortBy(_.score).reverse
                sortedPepMatches.foreach { pepM =>
                  val proba = PtmSitesIdentifier.singleModificationProbability(pepM, peptideInstancePtmTuple.ptm)
                  if (proba > bestProba) {
                    bestPM = pepM
                    bestProba = proba
                  }
                }
                (bestPM -> peptideInstancePtmTuple.ptm)
              })

              var bestPeptideMatch: PeptideMatch = null
              var bestProba: Float = 0.00f
              val sortedBestPMs = bestPMs.sortBy(_._1.score).reverse
              sortedBestPMs.foreach(f => {
                val proba = PtmSitesIdentifier.singleModificationProbability(f._1, f._2)
                if (proba > bestProba) {
                  bestPeptideMatch = f._1
                  bestProba = proba
                }
              })

              val isomericPeptideInstanceIds = peptideInstances.flatMap(piptm => peptideInstanceIdsBySeqPtm(_getAASequenceAndPTMsAsString(piptm.peptideInstance))).distinct
              isomericPeptideInstanceIds --= peptideInstances.map(_.peptideInstance.id)
              val isomericPeptideInstances = isomericPeptideInstanceIds.map(id => peptideInstancesById(id))
              val peptideIdsBySeqPosition = peptideInstances.groupBy(_.ptm.seqPosition).mapValues(_.map(_.peptideInstance.peptide.id).toArray)

              PtmSite(
                proteinMatchId = proteinMatchId,
                ptmDefinitionId = ptmDefinitionPositionTuple._1,
                seqPosition = ptmDefinitionPositionTuple._2,
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
  protected def _getAASequenceAndPTMsAsString(peptideInstance: PeptideInstance): String = {
    peptideInstance.peptide.sequence + peptideInstance.peptide.ptms.map(_.definition.toReadableString()).sorted.mkString
  }

}

//New version of PtmSitesIdentifier which creates PtmSite2 object
class  PtmSitesIdentifierV2(resultSummary: ResultSummary, proteinMatches: Array[ProteinMatch])
    extends PtmSitesIdentifier(resultSummary, proteinMatches) {

  def identifyPtmSite2s(): Iterable[PtmSite2] = {
    val validatedProteinMatchesById = scala.collection.immutable.HashSet(resultSummary.getValidatedResultSet().get.proteinMatches.map(_.id): _*)
    val ptmSites = ArrayBuffer.empty[PtmSite2]

    for (peptideSet <- resultSummary.peptideSets) {
      for (proteinMatchId <- peptideSet.proteinMatchIds) {

        // Apply PTM site identification only on validated protein sets
        if (validatedProteinMatchesById.contains(proteinMatchId)) {

          val sequenceMatchesByPeptideId = proteinMatchesById(proteinMatchId).sequenceMatches.groupBy { _.getPeptideId()  }
          val proteinMatchSites = scala.collection.mutable.Map[(Long, Int), ArrayBuffer[PeptideInstancePtmTuple]]()
          val peptideInstanceIdsBySeqPtm = scala.collection.mutable.Map[String, ArrayBuffer[Long]]()
          val peptideInstancesById = peptideSet.getPeptideInstances().map(pi => pi.id -> pi).toMap

          for (peptideInstance <- peptideSet.getPeptideInstances().filter(!_.peptide.ptms.isEmpty)) {
            val aaSeqAndPTMsKey = _getAASequenceAndPTMsAsString(peptideInstance)
            peptideInstanceIdsBySeqPtm.getOrElseUpdate(aaSeqAndPTMsKey, ArrayBuffer.empty[Long]) += peptideInstance.id

            for (seqMatch <- sequenceMatchesByPeptideId(peptideInstance.peptide.id)) {
              for (ptm <- peptideInstance.peptide.ptms) {
                if (PtmSitesIdentifier.isModificationProbabilityDefined(peptideInstance.peptideMatches.head, ptm)) {
                  proteinMatchSites.getOrElseUpdate((ptm.definition.id, ptm.seqPosition + seqMatch.start - 1), ArrayBuffer.empty[PeptideInstancePtmTuple]) += PeptideInstancePtmTuple(peptideInstance, ptm)
                }
              }
            }
          }

          var siteIndex = ptmSites.size
          val site = proteinMatchSites.map {
            case (ptmDefinitionPositionTuple, peptideInstPtmTuple) =>

              var peptideIdsBySeqPosition = peptideInstPtmTuple.groupBy(_.ptm.seqPosition).mapValues(_.map(_.peptideInstance.peptide.id).toArray)

              // -- Search for the best PeptideMatch & get SeqPostion correction N/CTerm modif
              var seqCorrection  = 0
              val isNTerm = peptideInstPtmTuple.head.ptm.isNTerm
              val isCTerm = peptideInstPtmTuple.head.ptm.isCTerm

              if( isNTerm) {
                logger.info("NTerm Ptm site identified, pepIds size: {}",peptideIdsBySeqPosition.size)
                seqCorrection = +1 //locate modif on first pepAA
                if(peptideIdsBySeqPosition.size != 1)
                  throw new RuntimeException("Error identifying PTM Sites : NTerm in more than one position !! ")

                val pepIds = peptideInstPtmTuple.map(_.peptideInstance.peptide.id).toArray
                peptideIdsBySeqPosition =  Map.empty[Int, Array[Long]]
                peptideIdsBySeqPosition += 1->pepIds
              }

              if( isCTerm) {
                seqCorrection = peptideInstPtmTuple.head.peptideInstance.peptide.sequence.length+1 //locate modif on last pepAA
              }

              //  Should order by score before getting max value. maxBy don't respect "first for equal order" !
              val bestPMs = peptideInstPtmTuple.map(peptideInstancePtmTuple => {
                var bestProba: Float = 0.00f
                var bestPM: PeptideMatch = null
                val sortedPepMatches: Array[PeptideMatch] = peptideInstancePtmTuple.peptideInstance.peptideMatches.sortBy(_.score).reverse
                sortedPepMatches.foreach { pepM =>
                  val proba = PtmSitesIdentifier.singleModificationProbability(pepM, peptideInstancePtmTuple.ptm)
                  if (proba > bestProba) {
                    bestPM = pepM
                    bestProba = proba
                  }
                }
                (bestPM -> peptideInstancePtmTuple.ptm)
              })

              var bestPeptideMatch: PeptideMatch = null
              var bestProba: Float = 0.00f
              val sortedBestPMs = bestPMs.sortBy(_._1.score).reverse
              sortedBestPMs.foreach(f => {
                val proba = PtmSitesIdentifier.singleModificationProbability(f._1, f._2)
                if (proba > bestProba) {
                  bestPeptideMatch = f._1
                  bestProba = proba
                }
              })

              val isomericPeptideInstanceIds = peptideInstPtmTuple.flatMap(piptm => peptideInstanceIdsBySeqPtm(_getAASequenceAndPTMsAsString(piptm.peptideInstance))).distinct
              isomericPeptideInstanceIds --= peptideInstPtmTuple.map(_.peptideInstance.id)
              val isomericPeptideInstances = isomericPeptideInstanceIds.map(id => peptideInstancesById(id))

              val sitePosition = ptmDefinitionPositionTuple._2 + seqCorrection

               val nextProtSite = PtmSite2(
                id = siteIndex,
                proteinMatchId = proteinMatchId,
                ptmDefinitionId = ptmDefinitionPositionTuple._1,
                seqPosition = sitePosition,
                bestPeptideMatchId = bestPeptideMatch.id,
                localizationConfidence = bestProba,
                peptideIdsByPtmPosition = peptideIdsBySeqPosition,
                isomericPeptideIds = isomericPeptideInstances.map(_.id).toArray,
                isNTerminal = isNTerm,
                isCTerminal = isCTerm )

              siteIndex = siteIndex+1
              nextProtSite
          }
          ptmSites ++= site
        }
      }
    }
    logger.info(ptmSites.size + " Ptm sites v2 were identified")
    ptmSites

  }

  def aggregatePtmSite2s(childrenSites: Array[Iterable[PtmSite2]], sitesProteinMatches: Array[ProteinMatch], peptideMatchProvider: (Array[Long]) => Map[Long, PeptideMatch]): Iterable[PtmSite2] = {
    val proteinAccessionByProteinMatchId = sitesProteinMatches.map { pm => pm.id -> pm.accession }.toMap
    val proteinMatchesByAccession = proteinMatches.map { pm => pm.accession -> pm }.toMap
    val ptmSites = ArrayBuffer.empty[PtmSite2]

    val ptmSitesMap = scala.collection.mutable.Map[(String, Long, Int), ArrayBuffer[PtmSite2]]()

    for (sites <- childrenSites) {
      sites.foreach { site =>
        ptmSitesMap.getOrElseUpdate((proteinAccessionByProteinMatchId(site.proteinMatchId), site.ptmDefinitionId, site.seqPosition), ArrayBuffer.empty[PtmSite2]) += site
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
        val newIsomericPeptideIds = site.map(_.isomericPeptideIds).flatten.distinct

        val newSite2 = PtmSite2(
          id = ptmSites.size+1,
          proteinMatchId = proteinMatchesByAccession(key._1).id,
          ptmDefinitionId =  key._2,
          seqPosition = key._3,
          bestPeptideMatchId = newBestPeptideMatchId,
          localizationConfidence = newBestPTMProbability,
          peptideIdsByPtmPosition = newPeptideMap,
          isomericPeptideIds = newIsomericPeptideIds.toArray,
          isNTerminal = site.head.isNTerminal,
          isCTerminal = site.head.isCTerminal )

        ptmSites += newSite2
    }
    logger.info(ptmSites.size + " Ptm sites identified")
    ptmSites
  }

}

object PtmStatus extends Enumeration {
  val Isomorphic, PartiallyIsomorphic, Compatible, Conflicting = Value
}

trait IPtmSiteClusterer {

  def clusterize(proteinMatchId: Long, sites: Array[PtmSite2], peptideMatchProvider: (Array[Long]) => Map[Long, PeptideMatch], idGen: InMemoryIdGen): Array[PtmCluster]

}

object ClusteringMethodParam extends Enumeration {
  type Param = Value
  val EXACT_POSITION_MATCHING = Value("EXACT_POSITION_MATCHING")
  val TOLERANT_POSITION_MATCHING = Value("TOLERANT_POSITION_MATCHING")
  val ISOMORPHIC_MATCHING = Value("ISOMORPHIC_MATCHING")
}

object PtmSiteClusterer extends LazyLogging {
  def apply(clusteringMethodName: String, resultSummary: ResultSummary, proteinMatches: Array[ProteinMatch]): IPtmSiteClusterer = {
    this.apply(ClusteringMethodParam.withName(clusteringMethodName), resultSummary, proteinMatches)
  }

  def apply(methodParam: ClusteringMethodParam.Value, resultSummary: ResultSummary, proteinMatches: Array[ProteinMatch]): IPtmSiteClusterer = {
    methodParam match {
      case ClusteringMethodParam.EXACT_POSITION_MATCHING => new PtmSiteExactClusterer(resultSummary, proteinMatches)
      case ClusteringMethodParam.TOLERANT_POSITION_MATCHING => new PtmSiteExactClusterer(resultSummary, proteinMatches)
      case ClusteringMethodParam.ISOMORPHIC_MATCHING => new PtmSiteExactClusterer(resultSummary, proteinMatches, false)
    }
  }

  def groupSitesBySequenceMatch(sites: Array[PtmSite2], sequenceMatchesByPeptideId: Map[Long, Array[SequenceMatch]]) : Map[SequenceMatch, Array[PtmSite2]] = {
    val sitesBySequenceMatch = new mutable.HashMap[SequenceMatch, ArrayBuffer[PtmSite2]]()
    sites.map { ptmSite => ptmSite -> ptmSite.peptideIdsByPtmPosition.values.toArray.flatten.distinct }.foreach {
      case (site, ids) =>
        ids.foreach { peptideId =>
          // partition sites by sequence matches
          val sequenceMatch = sequenceMatchesByPeptideId(peptideId).filter(sm => (site.seqPosition >= sm.start) && (site.seqPosition <= sm.end))
          if (!sequenceMatch.isEmpty) {
            //##VDS #22252 : why only head. In case same peptide has 2 matches on site, only one is taken !
            if(sequenceMatch.length > 1)
              logger.warn(s" !!!! MORE Than one sequence match for PTM site ${site} for peptide id ${peptideId} !!!! ")
            sequenceMatch.foreach(seqM => {
              sitesBySequenceMatch.getOrElseUpdate(seqM, new ArrayBuffer[PtmSite2]) += site
            })
          } else {
            logger.error(s"The PTM site ${site} has no sequence match for peptide id ${peptideId}")
          }
        }
    }
    sitesBySequenceMatch.toMap.mapValues(_.toArray)
  }

}

class PtmSiteExactClusterer(
    val resultSummary: ResultSummary,
    val proteinMatches: Array[ProteinMatch],
    val clusterizePartiallyIsomorphicPep: Boolean = true
) extends IPtmSiteClusterer with LazyLogging {

  private val proteinMatchesById = proteinMatches.map { pm => pm.id -> pm }.toMap
  private var peptideById = resultSummary.peptideInstances.map { pi => pi.peptide.id -> pi.peptide }.toMap

  def clusterize(proteinMatchId: Long, sites: Array[PtmSite2], peptideMatchProvider: (Array[Long]) => Map[Long, PeptideMatch], idGen: InMemoryIdGen): Array[PtmCluster] = {

    var clusters = new ArrayBuffer[PtmCluster]()

    val sequenceMatchesByPeptideId = proteinMatchesById(proteinMatchId).sequenceMatches.groupBy { _.getPeptideId()  }

    val sitesBySequenceMatch = PtmSiteClusterer.groupSitesBySequenceMatch(sites, sequenceMatchesByPeptideId)

    // sequenceMatches ordered by number of matching ptmSites
    val orderedSequenceMatches = sitesBySequenceMatch.toSeq.sortBy(_._2.size).reverse.map { _._1 }
    var clusterizedSequenceMatches = new mutable.HashMap[SequenceMatch, Boolean]()

    val createdClusterIds = new ArrayBuffer[Long]()
    val clustersByLowerSitePosition = new mutable.HashMap[Int, ArrayBuffer[PtmCluster]]()

    for (sequenceMatch <- orderedSequenceMatches) {
      //do not clusterize if the current sequenceMatch is already clusterized
      if (!clusterizedSequenceMatches.contains(sequenceMatch)) {

        val clusterizedPeptides = new mutable.HashMap[PtmStatus.Value, ArrayBuffer[Peptide]]()
        clusterizedPeptides.getOrElseUpdate(PtmStatus.Isomorphic, new ArrayBuffer[Peptide]) += peptideById(sequenceMatch.getPeptideId());

        // retrieve all peptide id matching at least one of the sequenceMatch's sites
        var matchingSequenceMatches = sitesBySequenceMatch(sequenceMatch).flatMap(_.peptideIdsByPtmPosition.flatMap(_._2)).distinct.flatMap(sequenceMatchesByPeptideId(_)).toArray
        // keep only sequence matches with bounds (start,stop) intersecting the reference sequenceMatch (this could not be the case for peptide repeated in the protein sequence
        matchingSequenceMatches = matchingSequenceMatches.filter(sm => (sm.start <= sequenceMatch.end) && (sm.end >= sequenceMatch.start ))
        // keep only sequenceMatches that are not already clusterized. IS THIS NECESSARY ??
        matchingSequenceMatches = matchingSequenceMatches.filter(!clusterizedSequenceMatches.contains(_))
        // evaluate the status of each sequence match
        for (candidateSequenceMatch <- matchingSequenceMatches) {
          val status = comparePeptidesPtms(sequenceMatch, candidateSequenceMatch, sitesBySequenceMatch)
          status match {
            case PtmStatus.Isomorphic => {
              clusterizedPeptides(status) += peptideById(candidateSequenceMatch.getPeptideId())
              clusterizedSequenceMatches += (candidateSequenceMatch -> true)
            }
            case PtmStatus.Compatible | PtmStatus.PartiallyIsomorphic => {
              if (clusterizePartiallyIsomorphicPep) {
                clusterizedPeptides.getOrElseUpdate(status, new ArrayBuffer[Peptide]) += peptideById(candidateSequenceMatch.getPeptideId())
              }
            }
            case _ => {}
          }

        }

        // determine bestPeptideMatch
        val isomorphicPeptideIds = clusterizedPeptides(PtmStatus.Isomorphic).map(_.id).toArray
        val isomorphicPeptideMatches = peptideMatchProvider(isomorphicPeptideIds)

        val bestPeptideMatch = isomorphicPeptideMatches.map(_._2).toArray.sortBy(_.score).reverse.maxBy{ pm => PtmSitesIdentifier.allModificationsProbability(pm)}
        val sitePosList = sitesBySequenceMatch(sequenceMatch).map(_.seqPosition).toArray.sorted
        val nextCluster = new PtmCluster(
          id = idGen.generateNewId(),
          ptmSiteLocations = sitesBySequenceMatch(sequenceMatch).map(_.id).toArray,
          bestPeptideMatchId = bestPeptideMatch.id,
          localizationConfidence = PtmSitesIdentifier.allModificationsProbability(bestPeptideMatch),
          peptideIds = clusterizedPeptides.flatten(_._2).map(_.id).toArray.distinct,
          isomericPeptideIds = Array.empty[Long],
          selectionLevel = SelectionLevel.SELECTED_AUTO,
          selectionConfidence = null,
          selectionInformation = if(clusterizePartiallyIsomorphicPep) "Exact Position Matching" else "Isomorphic Matching"
        )

        createdClusterIds += nextCluster.id //save id used for cluster of current proteinMatch
        //Associate current cluster to lower site position
        val ptmClusters = clustersByLowerSitePosition.getOrElse(sitePosList.head, new ArrayBuffer[PtmCluster]())
        ptmClusters += nextCluster
        clustersByLowerSitePosition.put(sitePosList.head,ptmClusters)
        //clusters += nextCluster
      }
    }

    //Reorder cluster to increment Ids depending on Site position.
    val sitePositions = clustersByLowerSitePosition.keySet.toArray.sorted
    var currentIndexInIds = 0
    sitePositions.foreach( pos => {
      val clustersForPosition = clustersByLowerSitePosition(pos)
      clustersForPosition.foreach( c => {
        clusters += c.copy(id = createdClusterIds(currentIndexInIds))
        currentIndexInIds = currentIndexInIds+1
      })
    })

    clusters.toArray
  }

  def comparePeptidesPtms(reference: SequenceMatch, candidate: SequenceMatch, sitesBySequenceMatch: Map[SequenceMatch, Array[PtmSite2]]): PtmStatus.Value = {
    val refSites = sitesBySequenceMatch(reference)
    val candidateSites = sitesBySequenceMatch(candidate)
    val diffRC = refSites.filter(!candidateSites.contains(_))
    val diffCR = candidateSites.filter(!refSites.contains(_))

    // same identified sites in each sequence matches
    if ((diffCR.size == 0) && (diffRC.size == 0)) {
      return PtmStatus.Isomorphic
    } else if (diffCR.size == 0) {
      // the reference sequence match get more sites than the candidate sequence match
      val sitesInCandidateBounds = diffRC.filter(s => (s.seqPosition >= candidate.start) && (s.seqPosition <= candidate.end))
      if (sitesInCandidateBounds.length == 0) {
        // but this sites are located outside of the candidate seqMatch bounds: the candidate is partially isomorphic
        return PtmStatus.PartiallyIsomorphic
      } else {
        // there is some additional sites located inside the candidate sequenceMatch bounds, consider them as conflicting
        return PtmStatus.Conflicting
      }
    } else {
      // the candidate sequence match gets more sites than the reference. Since we start with the sequence with the highest
      // number of sites, consider them as conflicting
      return PtmStatus.Conflicting
    }
  }
}
