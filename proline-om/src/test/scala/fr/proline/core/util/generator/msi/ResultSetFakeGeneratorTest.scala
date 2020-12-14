package fr.proline.core.util.generator.msi

import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.om.model.msi.{IonTypes, LocatedPtm, PtmDefinition, PtmEvidence, PtmNames, ResultSet}


@Test
class ResultSetFakeGeneratorTest extends JUnitSuite with StrictLogging {

  @Test
  def simpleResultSet() : Unit =  {
    val nbProts: Int = 2
    val nbPeps: Int = 10

    val rsb = new ResultSetFakeGenerator(nbPeps = nbPeps, nbProts = nbProts)
    val rs = rsb.toResultSet()

    assert(rs != null)
    assert(rsb.allPeps.size == nbPeps)
    assert(rsb.allProts.size == nbProts)
    assert(rsb.allPepMatches.size == nbPeps)

//    rsb.printForDebug  
  }

  @Test
  def resultSetFromProteinSeq(): Unit = {

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


    val pEvidence = new PtmEvidence(ionType = IonTypes.Precursor, composition = "H O(3) P", monoMass = 79.966331, averageMass = 79.9799, false)

    val pS = new PtmDefinition(id= 52, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='S', ptmId = 16L, unimodId = 21)
    val pT = new PtmDefinition(id= 51, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='T', ptmId = 16L, unimodId = 21)
    val pY = new PtmDefinition(id= 50, location = "Anywhere", names = PtmNames("Phospho", "Phosphorylation"), ptmEvidences = Array(pEvidence), residue ='Y', ptmId = 16L, unimodId = 21)

    val rsb = new ResultSetFakeGenerator(proteinSequence = pp150_seq)
    val proteinMatch = rsb.allProtMatches(0)
    rsb.addPeptide( pepSeq = "FHNPDLSSVLEEFEVR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVAR", proteinMatch = proteinMatch)
    rsb.addPeptide(pepSeq = "CVAVARR", proteinMatch = proteinMatch)

    rsb.addPeptide(pepSeq = "HAAFSLVSPQVTKASPGR", proteinMatch = proteinMatch) //todo: existe en deux versions (S5,S15) et (S8,S15)

    rsb.addPeptide(
      pepSeq = "HAAFSLVSPQVTK",
      ptms = Array(new LocatedPtm(definition = pS, seqPosition = 8, precursorDelta =  pEvidence)),
      probabilities = Array(0.98f),
      proteinMatch = proteinMatch
    )

    rsb.addPeptide(pepSeq = "GDLFSGDEDSDSSDGYPPNR",proteinMatch = proteinMatch) //todo: existe en 8 versions differentes

    val rs = rsb.toResultSet()
  }

  @Test
  def sharedPepResultSet() : Unit =  {
    val nbProts: Int = 2
    val nbPeps: Int = 4

    val rsb = new ResultSetFakeGenerator(
      nbPeps = nbPeps, nbProts = nbProts)
    rsb.addSharedPeptide(rsb.allProtMatches)
    val rs: ResultSet = rsb.toResultSet()

    assert(rs != null)
    assert(rsb.allPeps.size == nbPeps + 1)
    assert(rsb.allProts.size == nbProts)
    assert(rsb.allPepMatches.size == nbPeps + 1)

    //		rsb.printForDebug  
  }

  @Test
  def sharedPepFromNewProtResultSet() : Unit =    {
    val nbProts: Int = 2
    val nbPeps: Int = 4

    val rsb = new ResultSetFakeGenerator(nbPeps = nbPeps, nbProts = nbProts)
    rsb.createNewProteinMatchFromPeptides(rsb.allPeps)
    
    val rs: ResultSet = rsb.toResultSet()

    assert(rs != null)
    assert(rsb.allPeps.size == nbPeps)
    assert(rsb.allProts.size == nbProts +1)
    assert(rsb.allPepMatches.size == nbPeps)

    rsb.printForDebug  
  }

  @Test
  def withSimpleMissCleavage() : Unit =   {
    val nbProts: Int = 2
    val nbPeps: Int = 20

    val pepWMissCleavagesNb: Int = 5
    val missCleavage: Int = 2

    val rsb = new ResultSetFakeGenerator(nbPeps = nbPeps, nbProts = nbProts)
      .addNewPeptidesWithMissedCleavage(nbPeps = pepWMissCleavagesNb, nbMissedCleavages = missCleavage)
    val rs: ResultSet = rsb.toResultSet()

    //		rsb.printForDebug

    assert(rs != null)
    assert(rsb.allPeps.size == nbPeps + pepWMissCleavagesNb)
    assert(rsb.allProts.size == nbProts)
  }

  @Test
  def withMultiMissCleavage() : Unit =  {
    val nbProts: Int = 2
    val nbPeps: Int = 20

    val pepWMissCleavages2Nb: Int = 5
    val pepWMissCleavages3Nb: Int = 2
    val missCleavage2: Int = 2
    val missCleavage3: Int = 3

    val rsb = new ResultSetFakeGenerator(nbPeps = nbPeps, nbProts = nbProts)
      .addNewPeptidesWithMissedCleavage(nbPeps = pepWMissCleavages2Nb, nbMissedCleavages = missCleavage2)
      .addNewPeptidesWithMissedCleavage(nbPeps = pepWMissCleavages3Nb, nbMissedCleavages = missCleavage3)
    val rs: ResultSet = rsb.toResultSet()

    //		rsb.printForDebug

    assert(rs != null)
    assert(rsb.allPeps.size == nbPeps + pepWMissCleavages2Nb + pepWMissCleavages3Nb)
    assert(rsb.allProts.size == nbProts)
  }

  @Test
  def withDuplicatedPeptides(): Unit =   {
    val nbProts: Int = 4
    val nbPeps: Int = 20
    val duplic1Nb: Int = 5
    val duplic2Nb: Int = 10

    val rsb = new ResultSetFakeGenerator(nbPeps = nbPeps, nbProts = nbProts)
      .addDuplicatedPeptideMatches(duplic1Nb)
      .addDuplicatedPeptideMatches(duplic2Nb)

    val rs: ResultSet = rsb.toResultSet
    assert(rs.peptideMatches.size == nbPeps + duplic1Nb + duplic2Nb)

    //  	  rsb.printForDebug  	  
  }

  @Test
  def withAll() : Unit = {
    val nbProts: Int = 4
    val nbPeps: Int = 20

    //MissCleavages
    val missCleavage2: Int = 2
    val missCleavage3: Int = 3
    val pepWMissCleavages2Nb: Int = 5
    val pepWMissCleavages3Nb: Int = 2

    //Duplicated PeptideMatch
    val duplicNb: Int = 5

    val rsb = new ResultSetFakeGenerator(nbPeps = nbPeps, nbProts = nbProts)
      .addNewPeptidesWithMissedCleavage(nbPeps = pepWMissCleavages2Nb, nbMissedCleavages = missCleavage2)
      .addNewPeptidesWithMissedCleavage(nbPeps = pepWMissCleavages3Nb, nbMissedCleavages = missCleavage3)
      .addDuplicatedPeptideMatches(duplicNb)

    val rs: ResultSet = rsb.toResultSet

    //  	  rsb.printForDebug  	

    assert(rsb.allPepMatches.size == nbPeps + pepWMissCleavages2Nb + pepWMissCleavages3Nb + duplicNb)
    assert(rsb.allPeps.size == nbPeps + pepWMissCleavages2Nb + pepWMissCleavages3Nb)

  }

  //  	@Test
  //  	def bigData() = {
  //  	  val nbProts:Int = 1000
  //	  val nbPeps:Int = 5000		
  //	  val deltanbPeps:Int = 3
  //	  val duplic1Nb:Int = 5
  //	  val duplic2Nb:Int = 10
  //	  
  //  	  val rsb = new ResultSetFakeBuilder(nbPeps=nbPeps, nbProts=nbProts, deltanbPeps=deltanbPeps)
  //  	   		.addDuplicatedPeptides(duplic1Nb)
  //  	   		.addDuplicatedPeptides(duplic2Nb)
  //  	   		
  //  	  val rs:ResultSet = rsb.toResultSet
  //  	  assert(rs.peptideMatches.size == nbPeps+duplic1Nb+duplic2Nb)
  //  	  
  //  	  //rsb.printForDebug  	  
  //  	}

}

