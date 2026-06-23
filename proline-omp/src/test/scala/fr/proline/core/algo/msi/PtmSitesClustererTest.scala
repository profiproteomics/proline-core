package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder
import fr.proline.core.om.model.msi.{IonTypes, LocatedPtm, PeptideMatch, PtmCluster, PtmDefinition, PtmEvidence, PtmNames, PtmSite2, ResultSummary}
import fr.proline.core.service.msi.RsmPtmSitesIdentifierV2
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator
import org.junit.Test

import scala.collection.mutable.ArrayBuffer

@Test
class PtmSitesClustererTest extends StrictLogging {

  val pEvidence: PtmEvidence = PtmEvidence(ionType = IonTypes.Precursor, composition = "H O(3) P", monoMass = 79.966331, averageMass = 79.9799, isRequired = false)

  val phosphoByAA: Map[String, PtmDefinition] = Map(
    "S" -> new PtmDefinition(id= 52, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue = 'S', ptmId = 16L, unimodId = 21),
    "T" -> new PtmDefinition(id= 51, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='T', ptmId = 16L, unimodId = 21),
    "Y" -> new PtmDefinition(id= 50, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='Y', ptmId = 16L, unimodId = 21)
  )

  private def _createFakeRedondantRsm(): ResultSummary = {
    val pp150_seq =
      "MCLSFDSNYCRNILKHAVEMSPARMSPARMSPARMSPARMSAHTFLGARSPSLEFDERNA" +
        "DDANLLSLGGGSAFSSVPKKHVPTQPLDGWSWIASPWKGHKPFRFEAHGSLAPAAEAHAA" +
        "RSAAVGYYDEEEKRRERQKRVDDEVVQREKQQLKAWEERQQNLQQRQQQPPPPARKPSAS" +
        "RRLFGSSADEDDDDDDDEKNIFTPIKKPGTSGKGAASGGGVSSIFSGLLSSGSQKPTSGP" +
        "LNIPQQQQRHAAFSLVSPQVTKASPGRVRRDSAWDVRPLTETRGDLFSGDEDSDSSDGYP" +
        "PNRQDPRFTDTLVCVAVARRGYKPPVTTAYKFEQPTLTFGAGVNVPAGAGAAILTPTPVN" ;


    val rsb = new ResultSetFakeGenerator(proteinSequence = pp150_seq)
    val proteinMatch = rsb.allProtMatches(0)

    rsb.addPeptide( pepSeq = "GGGSAFSSVPKKHVPT", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVAR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVARR", proteinMatch = proteinMatch)

    rsb.addPeptide(
      pepSeq = "MSPARMSPAR",
      ptms = _buildLocatedPtms(Array("S(7)")),
      probabilities = Array(0.98f),
      proteinMatch = proteinMatch
    )


    val rs =    rsb.toResultSet()

    val proteinSetInferer = new ParsimoniousProteinSetInferer(new BasicPepInstanceBuilder())
    val rsm = proteinSetInferer.computeResultSummary( resultSet = rs )
    rsm
  }


  private def _getClusters(ptmSites2 : Array[PtmSite2], rsm : ResultSummary, clusterizeMC : Boolean = true): Array[PtmCluster] ={
    val ptmIds = Array(50L, 51L, 52L)
    val sitesByProteinMatchIds = ptmSites2.filter{ s =>  ptmIds.contains(s.ptmDefinitionId) }.groupBy(_.proteinMatchId)

    def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
      val peptideMatches = rsm.peptideInstances.filter{ pi => peptideIds.contains(pi.peptide.id) }.flatMap(_.peptideMatches)
      peptideMatches.map( pm => pm.id -> pm).toMap
    }

    val clusterizer = new PtmSiteExactClusterer(rsm,rsm.resultSet.get.proteinMatches, clusterizePartiallyIsomorphicPep=clusterizeMC)
    sitesByProteinMatchIds.flatMap{ case(protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds, IdGenerator) }.toArray
  }

  @Test
  def identifyRedondantFromFakeRsV2(): Unit = {
    val rsm =  _createFakeRedondantRsm()
    val ptmSites2 = new PtmSitesIdentifierV2(rsm,rsm.resultSet.get.proteinMatches).identifyPtmSite2s()

    val clusters = _getClusters(ptmSites2.toArray, rsm)

    assert(clusters.length == 3)
    clusters.foreach{c=>{
      c.ptmSiteLocations.foreach{ sl => logger.info(s"Next location ${c.id}=> "+sl)}
    }}

    ptmSites2.foreach{ s =>{
      logger.info("Site "+s.id+" : seqPos "+s.seqPosition+"; ")
    } }
  }

  @Test
  def identifyRedondantFromFakeRs(): Unit = {

    val rsm = _createFakeRedondantRsm()
    val ptmSites = new PtmSitesIdentifier(rsm,rsm.resultSet.get.proteinMatches).identifyPtmSites()
    val ptmSites2 = RsmPtmSitesIdentifierV2.toPtmSites2(ptmSites)

    val clusters = _getClusters(ptmSites2, rsm)

    assert(clusters.length == 3)
    clusters.foreach{c=>{
      c.ptmSiteLocations.foreach{ sl => logger.info(s"Next location ${c.id}=> "+sl)}
    }}

    ptmSites2.foreach{ s =>{
      logger.info("Site "+s.id+" : seqPos "+s.seqPosition+"; ")
    } }

  }

  @Test
  def identifyFromFakeRsV2(): Unit = {

    val rsm = _createFakeRsm()

    val ptmSites2 = new PtmSitesIdentifierV2(rsm,rsm.resultSet.get.proteinMatches).identifyPtmSite2s().toArray

    val clusters = _getClusters(ptmSites2, rsm)

    assert(clusters.length == 8)
  }

  @Test
  def identifyFromFakeRs(): Unit = {

    val rsm = _createFakeRsm()

    val ptmSites = new PtmSitesIdentifier(rsm,rsm.resultSet.get.proteinMatches).identifyPtmSites()
    val ptmSites2 = RsmPtmSitesIdentifierV2.toPtmSites2(ptmSites)

    val clusters = _getClusters(ptmSites2, rsm)
//
//    val ptmIds = Array(50L, 51L, 52L)
//    val sitesByProteinMatchIds = ptmSites2.filter{ s =>  ptmIds.contains(s.ptmDefinitionId) }.groupBy(_.proteinMatchId)
//
//    def _getPeptideMatchesByPeptideIds(peptideIds: Array[Long]): Map[Long, PeptideMatch] = {
//      val peptideMatches = rsm.peptideInstances.filter{ pi => peptideIds.contains(pi.peptide.id) }.flatMap(_.peptideMatches)
//      peptideMatches.map( pm => (pm.id -> pm)).toMap
//    }
//
//    val clusterizer = new PtmSiteExactClusterer(rsm,rsm.resultSet.get.proteinMatches)
//    val clusters = sitesByProteinMatchIds.flatMap{ case(protMatchId, sites) => clusterizer.clusterize(protMatchId, sites, _getPeptideMatchesByPeptideIds, IdGenerator) }

    assert(clusters.length == 8)
  }

  @Test
  def identifyFromFakeRsV3(): Unit = {

    val rsm = _createFakeRsm()
    val pep5 = rsm.peptideInstances.filter(pi => pi.peptide.sequence.equals("LVSPQVTK")).head
    val pep0 = rsm.peptideInstances.filter(pi => pi.peptide.sequence.equals("HAAFSLVSPQVTKASPGR") && (pi.peptide.ptms.length == 3)).head
    val pep2 = rsm.peptideInstances.filter(pi => pi.peptide.sequence.equals("HAAFSLVSPQVTKASPGR") && (pi.peptide.ptms.length == 2) && (pi.peptide.ptms.toList.filter(lptm => lptm.seqPosition == 8).size > 0)).head
    assert(pep5 != null)
    assert(pep0 != null)
    assert(pep2 != null)

    val ptmSites = new PtmSitesIdentifier(rsm, rsm.resultSet.get.proteinMatches).identifyPtmSites()
    val ptmSites2 = RsmPtmSitesIdentifierV2.toPtmSites2(ptmSites)
    assert(ptmSites2.length == 7)

    val clusterizeMC = false
    val clusters = _getClusters(ptmSites2, rsm, clusterizeMC)
    assert(clusters.length == 8)
    // -- no more OK for new pep list
    var foundC0 = false
    var foundC2 = false
    var foundC3 = false
    var foundErr = false
    if(clusterizeMC) {
      for (c <- clusters) {
        if (c.peptideIds.contains(pep5.peptide.id)) {
          if (c.peptideIds.length == 2 && c.peptideIds.contains(pep0.peptide.id)) {
            foundC0 = true
          } else if (c.peptideIds.length == 4 && c.peptideIds.contains(pep2.peptide.id)) {
            foundC2 = true
          } else if (c.peptideIds.length == 3) {
            foundC3 = true
          } else
            foundErr = true
        }
      }
    } else {
      for (c <- clusters) {
        if (c.peptideIds.contains(pep5.peptide.id)) {
          if (c.peptideIds.length == 3 ) {
            foundC3 = true
          } else
            foundErr = true
        } else if (c.peptideIds.contains(pep0.peptide.id)) {
          if (c.peptideIds.length == 1) {
            foundC0 = true
          } else
            foundErr = true
        } else if (c.peptideIds.contains(pep2.peptide.id)) {
          if (c.peptideIds.length == 1) {
            foundC2 = true
          } else
            foundErr = true
        }
      }
    }
    assert(foundC0)
    assert(foundC2)
    assert(foundC3)
    assert(!foundErr)
  }

  private def _createFakeRsm() : ResultSummary = {
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

    rsb.addPeptide(pepSeq = "FHNPDLSSVLEEFEVR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVAR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVARR", proteinMatch = proteinMatch)

    //S5 -> Prot position 494
    //S8 -> Prot position 497
    //S15 -> Prot position 504

    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQVTKASPGR",
      ptms = _buildLocatedPtms(Array("S(5)","S(8)", "S(15)")),
      probabilities = Array(0.98f,0.79f,  0.88f),
      proteinMatch = proteinMatch
    )

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

    //Fake Pep (no cleavage site) or cluster test...
    rsb.addPeptide(
      pepSeq = "LVSPQVTK",
      ptms = _buildLocatedPtms(Array("S(3)")),
      probabilities = Array(0.98f),
      proteinMatch = proteinMatch
    )

    //Fake Pep (no cleavage site) or cluster test...
    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQV",
      ptms = _buildLocatedPtms(Array("S(8)")),
      probabilities = Array(0.87f),
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
    val rsm = proteinSetInferer.computeResultSummary(resultSet = rs)
     rsm
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
