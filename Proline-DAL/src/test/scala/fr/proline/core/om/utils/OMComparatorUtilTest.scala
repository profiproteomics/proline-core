package fr.proline.core.om.utils

import org.junit.Before
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.repository.util.JPAUtils
import org.junit.After
import org.junit.Assert._
import fr.proline.repository.utils.DatabaseUtils
import fr.proline.core.orm.ps.PeptidePtm
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.LocatedPtm
import org.junit.Test
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.PtmNames
import fr.proline.repository.Database

class OMComparatorUtilTest extends DatabaseTestCase {
  
  override def getDatabase() = Database.PS
  
  @Before
  @throws(classOf[Exception])
  def initialize() = {
     initDatabase();
     //initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName());
     loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml");   
  }
  
  @After 
  @throws(classOf[Exception]) 
  override def tearDown() ={
		super.tearDown();
  }
  
   override def getSQLScriptLocation() : String  ={
	  return DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION;
  }
   
   	@Test
	def comparePepPtm() =  {
		val pepPtm : PeptidePtm = this.getEntityManager.find(classOf[PeptidePtm],1)
		
		val ptmEvi = new PtmEvidence(ionType = IonTypes.Precursor,
							composition= "Post-translational", 
							monoMass= 15.994915, 
							averageMass = 15.9994, 
							isRequired = true)
		
		var ptmEvidences = new Array[PtmEvidence](1)
		ptmEvidences.update(0, ptmEvi)
		
		val  ptmDef = new PtmDefinition(id = -100, 
		    				location = "ANYWHERE",
		    				names = new PtmNames(shortName= "Oxidation", fullName= "Oxidation or Hydroxylation"), 
		    				ptmEvidences = ptmEvidences, 
		    				residue= 'M', 
		    				classification = "Post-translational",ptmId= 0)
		
		val lPtm : LocatedPtm = new LocatedPtm(definition = ptmDef, 
									seqPosition = 3,
									monoMass= Double.MaxValue, 
									averageMass= Double.MaxValue,
									composition = "O",
									isNTerm= false,
									isCTerm= false)
		
		assertTrue(OMComparatorUtil.comparePeptidePtm(lPtm, pepPtm));
	
	}
}