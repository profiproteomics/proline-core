package fr.proline.core.algo.msq.profilizer

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap
import org.junit.Assert._
import org.junit.Test
import org.scalatest.Assertions._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._
import scala.collection.breakOut

@Test
class MasterQuantPeptidesClustererTest {
  
  val phosphoSerinePtm = PtmDefinition(
    id = PtmDefinition.generateNewId(),
    location = PtmLocation.ANYWHERE.toString,
    names = PtmNames("Phosphorylation","Phosphorylation"),
    ptmEvidences = Array[PtmEvidence](
      PtmEvidence(
        ionType = IonTypes.Precursor,
        composition = "H3PO4",
        monoMass = 80.0,
        averageMass = 80.0
      )   
    ),
  
    residue = 'S'
  )
  
  val peptides = Array(
    new Peptide(
      sequence = "EGVVGAVEK",
      ptms = Array()
    ),
    new Peptide(
      sequence = "ENVVQSVTSVAEK",
      ptms = Array()
    ),
    new Peptide(
      sequence = "ENVVQSVTSVAEK",
      ptms = Array(
        new LocatedPtm(
          definition = phosphoSerinePtm,
          seqPosition = 6,
          precursorDelta = phosphoSerinePtm.precursorDelta
        )
      )
    ),
    new Peptide(
      sequence = "TKENVVQSVTSVAEK",
      ptms = Array(
        new LocatedPtm(
          definition = phosphoSerinePtm,
          seqPosition = 8,
          precursorDelta = phosphoSerinePtm.precursorDelta
        )
      )
    )
  )
  
  val ir = ComputedRatio (
    numerator = 1f,
    denominator = 1f,
    state = 0,
    tTestPValue = None,
    zTestPValue = None,
    zScore = None
  )
  val vr = ir.copy(  numerator = 2f, state = 1 )
  val computedRatios = List(ir,ir,vr,vr)
  val computedRatioByPeptide = peptides.zip(computedRatios).toMap

  val proteinMatch = ProteinMatch(
    id = ProteinMatch.generateNewId(),
    accession = "PPIA_HUMAN",
    description = "Peptidyl-prolyl cis-trans isomerase A (Chain 1-165, N terminal His tag)- Homo sapiens (Human)",
    sequenceMatches = Array(
      SequenceMatch(
        start = 1,
        end = peptides(0).sequence.length,
        residueBefore = '\0',
        residueAfter = 'T',
        peptide = Some(peptides(0))
      ),
      SequenceMatch(
        start = peptides(0).sequence.length + 3,
        end = 2 + peptides(0).sequence.length + peptides(1).sequence.length,
        residueBefore = 'K',
        residueAfter = 'A',
        peptide = Some(peptides(1))
      ),
      SequenceMatch(
        start = peptides(0).sequence.length + 3,
        end = 2 + peptides(0).sequence.length + peptides(2).sequence.length,
        residueBefore = 'K',
        residueAfter = 'A',
        peptide = Some(peptides(2))
      ),
      SequenceMatch(
        start = peptides(0).sequence.length + 1,
        end = peptides(0).sequence.length + peptides(3).sequence.length,
        residueBefore = 'K',
        residueAfter = 'A',
        peptide = Some(peptides(3))
      )
    )
  )

  val mqProtSetId = ProteinSet.generateNewId()
  val mqPep = MasterQuantPeptide(
    id = 0,
    peptideInstance = Option.empty[PeptideInstance],
    quantPeptideMap = LongMap.empty[QuantPeptide],
    masterQuantPeptideIons = Array.empty[MasterQuantPeptideIon],

    selectionLevel = 2,
    resultSummaryId = -1L
  )
  
  def buildMqPep( peptide: Peptide ) = {
    mqPep.copy(
      id = MasterQuantPeptide.generateNewId(),
      peptideInstance = Some(
        PeptideInstance(
          id = PeptideInstance.generateNewId(),
          peptide = peptide,
          peptideMatchIds = Array(0)
        )
      ),
      properties = Some(
        MasterQuantPeptideProperties(
          mqProtSetIds = Some(Array(mqProtSetId)),
          mqPepProfileByGroupSetupNumber = HashMap[Int, MasterQuantPeptideProfile](
            1 -> MasterQuantPeptideProfile(List(Some(computedRatioByPeptide(peptide))))
          )
        )
      )
    )
  }
  
  val masterQuantPeptides: Array[MasterQuantPeptide] = peptides.map( buildMqPep(_) )

  val mqProtSetProp = MasterQuantProteinSetProperties()
  val mqSelectLevelByPepId : HashMap[Long, Int]= HashMap[Long, Int]()
  mqSelectLevelByPepId ++= masterQuantPeptides.map(mqp => (mqp.id -> 2))
  mqProtSetProp.setSelectionLevelByMqPeptideId(Some(mqSelectLevelByPepId))

  val masterQuantProtSet = MasterQuantProteinSet(
    proteinSet = ProteinSet(
      id = mqProtSetId,
      samesetProteinMatchIds = Array(proteinMatch.id),
      samesetProteinMatches = Some(Array(proteinMatch)),
      peptideSet = PeptideSet(
        id = PeptideSet.generateNewId(),
        items = Array.empty[PeptideSetItem],
        isSubset = false,
        sequencesCount = 1,
        peptideMatchesCount = 1,
        proteinMatchIds = Array(proteinMatch.id)
      ),
      hasPeptideSubset = false,
      isDecoy = false
    ),
    quantProteinSetMap = LongMap.empty[QuantProteinSet], // QuantProteinSet by quant channel id
    masterQuantPeptides = masterQuantPeptides,
    
    selectionLevel = 2,
    properties = Some(mqProtSetProp)
  )
  
  @Test
  def testPeptideSequenceClustering {
    val clusterer = new PeptideSequenceBasedClusterer()
    val clusters = clusterer.computeMqPeptidesClusters(Array(masterQuantProtSet))
    
    assert( clusters.length === 3, " => expecting 3 different clusters" )
    
    /*clusters.foreach { cluster =>
      println(cluster.name, cluster.mqPeptides.map { mqPep =>
        val p = mqPep.peptideInstance.get.peptide
        p.sequence + " " + p.readablePtmString
      }.toList)
    }*/
  }
  
  @Test
  def testPeptideSetClustering {
    val clusterer = new PeptideSetBasedClusterer()    
    val clusters = clusterer.computeMqPeptidesClusters(Array(masterQuantProtSet))
    
    assert( clusters.length === 1, " => expecting a single cluster" )
  }
  
  @Test
  def testPtmPatternClustering {
    val clusterer = new PtmPatternBasedClusterer(MqPeptidesClustererConfig(ptmPatternPtmDefIds = Seq(phosphoSerinePtm.id) ))
    val clusters = clusterer.computeMqPeptidesClusters(Array(masterQuantProtSet))
    
    assert( clusters.length === 3, " => expecting 3 different clusters" )
    assert( clusters.find(_.name == "PPIA_HUMAN").isDefined === true, " => 'PPIA_HUMAN' cluster is missing" )
    assert( clusters.find(_.name == "MODIFIED Phosphorylation@17").isDefined === true, " => 'MODIFIED Phosphorylation@17' cluster is missing" )
    assert( clusters.find(_.name == "UNMODIFIED Phosphorylation@17").isDefined === true, " => 'UNMODIFIED Phosphorylation@17' cluster is missing" )
    
    /*clusters.foreach { cluster =>
      println(cluster.name, cluster.mqPeptides.map { mqPep =>
        val p = mqPep.peptideInstance.get.peptide
        p.sequence + " " + p.readablePtmString
      }.toList)
    }*/
  }
  
  @Test
  def testQuantProfileClustering {
    val clusterer = new QuantProfileBasedClusterer(groupSetupNumber = 1)
    val clusters = clusterer.computeMqPeptidesClusters(Array(masterQuantProtSet))

    assert(clusters.length === 2, " => expecting two different clusters")

    clusters.foreach { cluster =>
      println(cluster.name, cluster.mqPeptides.map { mqPep =>
        val p = mqPep.peptideInstance.get.peptide
        p.sequence + " " + p.readablePtmString
      }.toList)
    }
  }
  
}