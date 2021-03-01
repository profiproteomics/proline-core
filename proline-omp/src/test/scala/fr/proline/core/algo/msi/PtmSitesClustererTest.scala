package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder
import fr.proline.core.om.model.msi.{IonTypes, LocatedPtm, PeptideMatch, PtmDefinition, PtmEvidence, PtmNames}
import fr.proline.core.service.msi.RsmPtmSitesIdentifierV2
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator
import org.junit.Test

import scala.collection.mutable.ArrayBuffer

@Test
class PtmSitesClustererTest extends StrictLogging {

  val pEvidence = new PtmEvidence(ionType = IonTypes.Precursor, composition = "H O(3) P", monoMass = 79.966331, averageMass = 79.9799, false)

  val phosphoByAA = Map(
    "S" -> new PtmDefinition(id= 52, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue = 'S', ptmId = 16L, unimodId = 21),
    "T" -> new PtmDefinition(id= 51, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='T', ptmId = 16L, unimodId = 21),
    "Y" -> new PtmDefinition(id= 50, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='Y', ptmId = 16L, unimodId = 21)
  )


  @Test
  def identifyFromFakeRs(): Unit = {

    val pp150_seq = "MSLQFIGLQRRDVVALVNFLRHLTQKPDVDLEAHPKILKKCGEKRLHRRTVLFNELMLWL" +
      "GYYRELRFHNPDLSSVLEEFEVRCVAVARRGYTYPFGDRGKARDHLAVLDRTEFDTDVRH" +
      "DAEIVERALVSAVILAKMSVRETLVTAIGQTEPIAFVHLKDTEVQRIEENLEGVRRNMFC" +
      "VKPLDLNLDRHANTALVNAVNKLVYTGRLIMNVRRSWEELERKCLARIQERCKLLVKELR" +
      "MCLSFDSNYCRNILKHAVENGDSADTLLELLIEDFDIYVDSFPQSAHTFLGARSPSLEFD" +
      "DDANLLSLGGGSAFSSVPKKHVPTQPLDGWSWIASPWKGHKPFRFEAHGSLAPAAEAHAA" +
      "RSAAVGYYDEEEKRRERQKRVDDEVVQREKQQLKAWEERQQNLQQRQQQPPPPARKPSAS" +
      "RRLFGSSADEDDDDDDDEKNIFTPIKKPGTSGKGAASGGGVSSIFSGLLSSGSQKPTSGP" +
      "LNIPQQQQRHAAFSLVSPQVTKASPGRVRRDSAWDVRPLTETRGDLFSGDEDSDSSDGYP" +
      "PNRQDPRFTDTLVDITDTETSAKPPVTTAYKFEQPTLTFGAGVNVPAGAGAAILTPTPVN" +
      "PSTAPAPAPTPTFAGTQTPVNGNSPWAPTAPLPGDMNPANWPRERAWALKNPHLAYNPFR" +
      "MPTTSTASQNTVSTTPRRPSTPRAAVTQTASRDAADEVWALRDQTAESPVEDSEEEDDDS" +
      "SDTGSVVSLGHTTPSSDYNNDVISPPSQTPEQSTPSRIRKAKLSSPMTTTSTSQKPVLGK" +
      "RVATPHASARAQTVTSTPVQGRLEKQVSGTPSTVPATLLQPQPASSKTTSSRNVTSGAGT" +
      "SSASSARQPSASASVLSPTEDDVVSPATSPLSMLSSASPSPAKSAPPSPVKGRGSRVGVP" +
      "SLKPTLGGKAVVGRPPSVPVSGSAPGRLSGSSRAASTTPTYPAVTTVYPPSSTAKSSVSN" +
      "APPVASPSILKPGASAALQSRRSTGTAAVGSPVKSTTGMKTVAFDLSSPQKSGTGPQPGS" +
      "AGMGGAKTPSDAVQNILQKIEKIKNTEE"




    val rsb = new ResultSetFakeGenerator(proteinSequence = pp150_seq)
    val proteinMatch = rsb.allProtMatches(0)

    rsb.addPeptide( pepSeq = "FHNPDLSSVLEEFEVR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVAR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVARR", proteinMatch = proteinMatch)

    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQVTKASPGR",
      ptms = _buildLocatedPtms(Array("S(5)", "S(15)")),
      probabilities = Array(0.98f, 0.88f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQVTKASPGR",
      ptms = _buildLocatedPtms(Array("S(8)", "S(15)")),
      probabilities = Array(0.98f, 0.88f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQVTKASPGR",
      ptms = _buildLocatedPtms(Array("S(15)")),
      probabilities = Array(0.88f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQVTK",
      ptms = _buildLocatedPtms(Array("S(8)")),
      probabilities = Array(0.98f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(
      pepSeq = "AKLSSPMTTTSTSQKPVLGK",
      ptms = _buildLocatedPtms(Array("S(4)")),
      probabilities = Array(0.98f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(
      pepSeq = "LSSPMTTTSTSQKPVLGK",
      ptms = _buildLocatedPtms(Array("S(2)")),
      probabilities = Array(0.98f),
      proteinMatch = proteinMatch
    )


    rsb.addPeptide(
      pepSeq = "GDLFSGDEDSD",
      ptms = _buildLocatedPtms(Array("S(5)", "S(10)")),
      probabilities = Array(0.98f, 0.88f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(
      pepSeq = "EDSDSSDGYPPNR",
      ptms = _buildLocatedPtms(Array("S(3)", "S(6)")),
      probabilities = Array(0.98f, 0.88f),
      proteinMatch = proteinMatch
    )


//    rsb.addPeptide(pepSeq = "GDLFSGDEDSDSSDGYPPNR",proteinMatch = proteinMatch) //todo: existe en 8 versions differentes

    val rs = rsb.toResultSet()

    val proteinSetInferer = new ParsimoniousProteinSetInferer(new BasicPepInstanceBuilder())
    val rsm = proteinSetInferer.computeResultSummary( resultSet = rs )

    val ptmSites = new PtmSitesIdentifier(rsm,rs.proteinMatches).identifyPtmSites()
    val ptmSites2 = RsmPtmSitesIdentifierV2.toPtmSites2(ptmSites)

    val ptmIds = Array(50L, 51L, 52L)
    val sitesByProteinMatchIds = ptmSites2.filter{ s =>  ptmIds.contains(s.ptmDefinitionId) }.groupBy(_.proteinMatchId)

    def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
      val peptideMatches = rsm.peptideInstances.filter{ pi => peptideIds.contains(pi.peptide.id) }.flatMap(_.peptideMatches)
      peptideMatches.map( pm => (pm.id -> pm)).toMap
    }

    val clusterizer = new PtmSiteExactClusterer(rsm,rs.proteinMatches)
    val clusters = sitesByProteinMatchIds.flatMap{ case(protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds, IdGenerator) }

    assert(clusters.size == 7)
  }

  def _buildLocatedPtms(ptmsAsString: Array[String]): Array[LocatedPtm] = {
    val ptms = ArrayBuffer[LocatedPtm]()
    val regex = "(.)\\((\\d+)\\)".r
    ptmsAsString.foreach{ s =>
      val regex(aminoAcid, position) = s
      val ptmDef = phosphoByAA(aminoAcid)
      ptms += new LocatedPtm(definition = ptmDef, seqPosition = position.toInt, precursorDelta =  pEvidence)
    }

    ptms.toArray
  }

}
