package fr.proline.core.algo.msq.profilizer

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.util.lang.EnhancedEnum
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq.MasterQuantPeptide
import fr.proline.core.om.model.msq.MasterQuantProteinSet

object MqPeptidesClusteringMethod extends EnhancedEnum {
  val PEPTIDE_SEQUENCE = Value // Cluster name = SEQUENCE
  val PEPTIDE_SET = Value // Cluster name = PROTEIN ACCESSION
  val PTM_PATTERN = Value // Cluster name = MODIFIED/UNMODIFIED LOCATED PTM IN PROTEIN SEQUENCE AND ACCESSION FOR OTHERS
  val QUANT_PROFILE = Value // Cluster name = RATIO STATES
}

case class MqPeptidesClustererConfig(
  ptmPatternPtmDefIds: Seq[Long] = Seq() // only for PTM_PATTERN method
)

case class MasterQuantPeptidesCluster(
  name: String,
  mqProteinSet: MasterQuantProteinSet,
  mqPeptides: Seq[MasterQuantPeptide]
)

object MasterQuantPeptidesClusterer {

  def apply(
    methodName: String,
    clustererConfig: Option[MqPeptidesClustererConfig],
    groupSetupNumber: Int
  ): IMqPeptidesClusterer = {

    import MqPeptidesClusteringMethod._
    
    val clusteringMethod = MqPeptidesClusteringMethod.withName(methodName)

    val clusters = clusteringMethod match {
      case PEPTIDE_SEQUENCE => new PeptideSequenceBasedClusterer()
      case PEPTIDE_SET      => new PeptideSetBasedClusterer()
      case PTM_PATTERN      => new PtmPatternBasedClusterer(clustererConfig.get)
      case QUANT_PROFILE    => new QuantProfileBasedClusterer(groupSetupNumber)
    }

    clusters
  }

  def computeMqPeptidesClusters(
    methodName: String,
    clustererConfig: Option[MqPeptidesClustererConfig],
    groupSetupNumber: Int,
    masterQuantProtSets: Seq[MasterQuantProteinSet]
  ): Array[MasterQuantPeptidesCluster] = {
    this.apply(methodName, clustererConfig, groupSetupNumber).computeMqPeptidesClusters(masterQuantProtSets)
  }
}

trait IMqPeptidesClusterer {

  def clustererConfig: MqPeptidesClustererConfig

  def computeMqPeptidesClusters(
    masterQuantProtSets: Seq[MasterQuantProteinSet]
  ): Array[MasterQuantPeptidesCluster] = {

    val clustersBuffer = new ArrayBuffer[MasterQuantPeptidesCluster](masterQuantProtSets.length)
    for (masterQuantProtSet <- masterQuantProtSets) {

      // Reset quant profiles for this masterQuantProtSet
      // TODO: is this needed ???
      /*for (masterQuantProtSetProps <- masterQuantProtSet.properties) {
        masterQuantProtSetProps.setMqProtSetProfilesByGroupSetupNumber(None)
      }*/
      
      // Retrieve selected master quant peptides in this protein set
      val selMqPepIdSet = masterQuantProtSet.properties.get.selectedMasterQuantPeptideIds.map(_.toSet).getOrElse(Set())
      
      // Keep only selected master quant peptides
      val selectedMqPeps = masterQuantProtSet.masterQuantPeptides.filter { mqPep =>
        val isSelectedInDataset = mqPep.selectionLevel >= 2
        val isSelectedInProtSet = selMqPepIdSet.contains(mqPep.id)
        
        ( isSelectedInDataset && isSelectedInProtSet )
      }

      // Compute clusters if we have at least one selected peptide
      if( selectedMqPeps.isEmpty == false ) {
        this.computeMqPeptidesClusters(masterQuantProtSet, selectedMqPeps, clustersBuffer)
      }
      
    }

    clustersBuffer.toArray
  }

  protected def computeMqPeptidesClusters(
    masterQuantProtSet: MasterQuantProteinSet,
    selectedMqPeps: Array[MasterQuantPeptide],
    clustersBuffer: ArrayBuffer[MasterQuantPeptidesCluster]
  ): Unit

  protected def getRepresentativeProteinMatch(protSet: ProteinSet): ProteinMatch = {

    val samesetProteinMatchesOpt = protSet.samesetProteinMatches
    require(
      samesetProteinMatchesOpt != null && samesetProteinMatchesOpt.isDefined,
      "the protein matches must be linked to the protein set"
    )

    val reprProtMatchOpt = protSet.getRepresentativeProteinMatch()
    val protMatch = Option(reprProtMatchOpt).flatten.getOrElse(samesetProteinMatchesOpt.get.head)

    protMatch
  }

}

class PeptideSequenceBasedClusterer() extends IMqPeptidesClusterer {

  val clustererConfig = MqPeptidesClustererConfig()
  
  def computeMqPeptidesClusters(
    masterQuantProtSet: MasterQuantProteinSet,
    selectedMqPeps: Array[MasterQuantPeptide],
    clustersBuffer: ArrayBuffer[MasterQuantPeptidesCluster]
  ): Unit = {

    // Group master quant peptides by sequence
    val mqPepsBySameSeq = selectedMqPeps.groupBy(_.peptideInstance.map(_.peptide.sequence).getOrElse(""))

    for ((pepSeq, mqPeps) <- mqPepsBySameSeq) {
      clustersBuffer += MasterQuantPeptidesCluster(
        name = pepSeq,
        mqProteinSet = masterQuantProtSet,
        mqPeptides = mqPeps
      )
    }

    ()
  }

}

/** Clustering based on identified peptide set for each protein set **/
class PeptideSetBasedClusterer() extends IMqPeptidesClusterer {

  val clustererConfig = MqPeptidesClustererConfig()
  
  def computeMqPeptidesClusters(
    masterQuantProtSet: MasterQuantProteinSet,
    selectedMqPeps: Array[MasterQuantPeptide],
    clustersBuffer: ArrayBuffer[MasterQuantPeptidesCluster]
  ): Unit = {

    val reprProtMatch = this.getRepresentativeProteinMatch(masterQuantProtSet.proteinSet)

    clustersBuffer += MasterQuantPeptidesCluster(
      name = reprProtMatch.accession,
      mqProteinSet = masterQuantProtSet,
      mqPeptides = selectedMqPeps
    )

    ()
  }

}

/** The goal of this clusterer is to group Master Quant Peptides having the same PTM pattern **/
class PtmPatternBasedClusterer(val clustererConfig: MqPeptidesClustererConfig) extends IMqPeptidesClusterer {
  
  private val ptmDefIdSet = clustererConfig.ptmPatternPtmDefIds.toSet

  def computeMqPeptidesClusters(
    masterQuantProtSet: MasterQuantProteinSet,
    selectedMqPeps: Array[MasterQuantPeptide],
    clustersBuffer: ArrayBuffer[MasterQuantPeptidesCluster]
  ): Unit = {

    val protSet = masterQuantProtSet.proteinSet
    val reprProtMatch = this.getRepresentativeProteinMatch(protSet)
    val seqMatchesByPepId = reprProtMatch.sequenceMatches.groupBy( sm => sm.getPeptideId )
    
    // Group master quant peptides by PTM pattern
    val mqPepsByPtmPattern = selectedMqPeps.groupBy { mqPep =>
      
      val peptide = mqPep.peptideInstance.get.peptide
      val seqMatches = seqMatchesByPepId(peptide.id)
      
      this._buildPtmPattern(peptide, seqMatches.map(_.start) )
    }
    
    // Search for master quant peptides which do not have a PTM
    val otherMqPeps = mqPepsByPtmPattern.find(_._1.isEmpty).map(_._2).getOrElse(Array())
    val otherMqPepBySeq = otherMqPeps.view.map { otherMqPep =>
      otherMqPep.peptideInstance.get.peptide.sequence -> otherMqPep
    } toMap
    
    val unmodifiedMqPeps = new ArrayBuffer[MasterQuantPeptide](otherMqPeps.length)
    
    // Add master quant peptides associtedto a PTM pattern to the clusters buffer
    for ((ptmPattern, mqPeps) <- mqPepsByPtmPattern.filter(_._1.isEmpty == false) ) {
      
      clustersBuffer += MasterQuantPeptidesCluster(
        name = "MODIFIED " + ptmPattern,
        mqProteinSet = masterQuantProtSet,
        mqPeptides = mqPeps
      )
      
      // Add peptide with same sequence but no PTM to a cluster corresponding to the UNMODIFIED form of the pattern
      for( unmodifiedMqPep <- otherMqPepBySeq.get(mqPeps.head.peptideInstance.get.peptide.sequence) ) {
        unmodifiedMqPeps += unmodifiedMqPep
        
        clustersBuffer += MasterQuantPeptidesCluster(
          name = "UNMODIFIED " + ptmPattern,
          mqProteinSet = masterQuantProtSet,
          mqPeptides = Array(unmodifiedMqPep)
        )
      }
    }

    // Add all other peptides to a "PROTEIN" cluster
    val unmodifiedMqPepSet = unmodifiedMqPeps.toSet
    for( otherMqPep <- otherMqPeps; if unmodifiedMqPepSet.contains(otherMqPep) == false ) {
      clustersBuffer += MasterQuantPeptidesCluster(
        name = reprProtMatch.accession,
        mqProteinSet = masterQuantProtSet,
        mqPeptides = Array(otherMqPep)
      )
    }
    
    ()
  }
  
  private def _buildPtmPattern(peptide: Peptide, proteinStartLocations: Seq[Int] ): String = {
    
    val ptms = peptide.ptms
    
    val patterns = for (
      ptm <- ptms.sortBy(_.seqPosition);
      if ptmDefIdSet.contains(ptm.definition.id)
    ) yield {
      
      val shortName = ptm.definition.names.shortName
      
      val peptidePtmLocation = if (ptm.isNTerm) 1
      else if (ptm.isCTerm) peptide.sequence.length
      else ptm.seqPosition
      
      val proteinPtmLocations = proteinStartLocations.map( start => start + peptidePtmLocation - 1 )
     
      s"${shortName}@${proteinPtmLocations.mkString(",")}"
    }

    patterns.mkString("; ")
  }

}

/** Clustering based on similarities between profiles of quantified peptides **/
class QuantProfileBasedClusterer(groupSetupNumber: Int) extends IMqPeptidesClusterer {

  val clustererConfig = MqPeptidesClustererConfig()
  
  def computeMqPeptidesClusters(
    masterQuantProtSet: MasterQuantProteinSet,
    selectedMqPeps: Array[MasterQuantPeptide],
    clustersBuffer: ArrayBuffer[MasterQuantPeptidesCluster]
  ): Unit = {

    // Clusterize MasterQuantPeptides according to their profile
    val mqPepsByProfileAsStr = new HashMap[String, ArrayBuffer[MasterQuantPeptide]]()

    selectedMqPeps.foreach { mqPep =>

      val mqPepProfileOpt = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get.get(groupSetupNumber)

      for (mqPepProfile <- mqPepProfileOpt) {

        val profileSlopes = mqPepProfile.ratios.map(_.map(_.state).get)
        val profileAsStr = _stringifyProfile(profileSlopes)

        mqPepsByProfileAsStr.getOrElseUpdate(profileAsStr, new ArrayBuffer[MasterQuantPeptide]) += mqPep
      }
    }

    // Build master quant protein set profiles
    for ((profileAsStr, mqPeps) <- mqPepsByProfileAsStr) {
      clustersBuffer += MasterQuantPeptidesCluster(
        name = profileAsStr,
        mqProteinSet = masterQuantProtSet,
        mqPeptides = mqPeps
      )
    }

    ()
  }

  private def _stringifyProfile(slopes: Seq[Int]): String = { slopes.mkString(";") }

}
