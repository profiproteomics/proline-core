package fr.proline.core.utils.generator;

import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet

@Test
class ResultSetFakeBuilderTest extends JUnitSuite with Logging {
	
  val nbProt:Int = 2
  val nbPep:Int = 10
  
	@Test
	def simpleResultSet() = {	  				
		val rsb = new ResultSetFakeBuilder(nbPep=nbPep, nbProt=nbProt)
		assert(rsb.rs != null)
		assert(rsb.allPeptides.size == nbPep)
		assert(rsb.allProts.size == nbProt)
	}
		
}

