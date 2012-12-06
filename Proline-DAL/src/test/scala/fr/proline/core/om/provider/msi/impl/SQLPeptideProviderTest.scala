package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import org.hamcrest.CoreMatchers
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.PtmNames
import fr.proline.repository.util.JPAUtils
import fr.proline.repository.utils.DatabaseUtils
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.core.dal.PsDbSQLHelper
import fr.proline.repository.Database

@Test
class SQLPeptideProviderTest extends DatabaseTestCase {
  
  override def getDatabase() = Database.PS

  var sqlPepProvider:  SQLPeptideProvider = null
  private val SEQ_TO_FOUND : String = "LTGMAFR"
    
  
  override def getSQLScriptLocation() : String  ={
	  return DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION;
  }
  
  @Before 
  @throws(classOf[Exception])
  def setUp()  = {
	  initDatabase()
	  //initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName())
	  loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml")
	  
	  sqlPepProvider = new SQLPeptideProvider(new PsDbSQLHelper(this.getConnector()))
  }
  
  @After 
  @throws(classOf[Exception]) 
  override def tearDown() ={
		super.tearDown();
  }
  
  @Test
  def getSinglePeptide()= {
	  val pep : Option[Peptide] = sqlPepProvider.getPeptide(4);
  	  assertThat(pep, CoreMatchers.notNullValue());
  	  assertNotSame(pep, None);		
  	  assertThat(pep.get.calculatedMass, CoreMatchers.equalTo(810.405807));
  }
  
  @Test
  def getMultiplePeptides() = {
	 val ids = new ArrayBuffer[Int]
  	 ids += 0 
  	 ids += 1
  	 ids += 4
  	 
	 val peps : Array[Option[Peptide]] = sqlPepProvider.getPeptidesAsOptions(ids)
	 assertThat(peps, CoreMatchers.notNullValue())
	 assertThat(peps.length, CoreMatchers.equalTo(3))
	 assertThat(peps.apply(2).get.id, CoreMatchers.equalTo(4))
	 assertThat(peps(2).get.calculatedMass, CoreMatchers.equalTo(810.405807))
  }

  @Test
  def getPeptideWithNTermPTM() = {
 	 
	 val pep : Option[Peptide] = sqlPepProvider.getPeptide(6)
	 assertThat(pep, CoreMatchers.notNullValue())
	 assertNotSame(pep, None);		

	 assertThat(pep.get.id, CoreMatchers.equalTo(6))
	 assertThat(pep.get.ptms.length, CoreMatchers.equalTo(1))
	 assertThat(pep.get.ptms(0).definition.names.shortName, CoreMatchers.equalTo("Acetyl")) 
	 assertTrue(pep.get.ptms(0).isNTerm)	 
  }
  
  @Test
  def getPeptideOnSeqAndNoPtms() =  {
	 val ptms = new Array[LocatedPtm](0)
	 val pep : Option[Peptide] = sqlPepProvider.getPeptide(SEQ_TO_FOUND, ptms);
	 assertThat(pep, CoreMatchers.notNullValue());
	 assertNotSame(pep, None);	
	 assertTrue(pep.get.ptms == null ||  pep.get.ptms.length==0  );
  }
	
  @Test
  def getPeptideOnSeqAndPtms() = {		
	  var ptmsBuilder = Array.newBuilder[LocatedPtm]
		
	  val ptmEvi : PtmEvidence = new PtmEvidence(IonTypes.Precursor,"",  Double.MaxValue, Double.MaxValue, false)
	  val ptmEvidences = Array[PtmEvidence](ptmEvi)
	  
	  val ptmDef = new PtmDefinition(0, "ANYWHERE", new PtmNames("Oxidation", "Oxidation or Hydroxylation"), ptmEvidences, 'M', null, 0);
	  ptmsBuilder += new LocatedPtm(ptmDef, 3, Double.MaxValue, Double.MaxValue, "O", false, false)
	  
	  /*val provTest = new fr.proline.core.om.provider.msi.impl.ORMPTMProvider( this.em )
	  val ptmDefs = provTest.getPtmDefinitions(List(1,2,30))
	  ptmDefs.foreach { ptm => println(ptm.get.names.shortName ) }*/
		
	  val pep : Option[Peptide] = sqlPepProvider.getPeptide(SEQ_TO_FOUND, ptmsBuilder.result());
	  assertThat(pep, CoreMatchers.notNullValue());
	  assertNotSame(pep, None$.MODULE$);	
	  assertThat(pep.get.ptms.length, CoreMatchers.equalTo(1));
	  assertThat(pep.get.ptms(0).seqPosition, CoreMatchers.equalTo(3));
	}
  
}