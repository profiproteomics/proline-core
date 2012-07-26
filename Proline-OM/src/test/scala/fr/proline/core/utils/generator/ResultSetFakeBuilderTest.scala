package fr.proline.core.utils.generator;

import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet

@Test
class ResultSetFakeBuilderTest extends JUnitSuite with Logging {
	
  
  
	@Test
	def simpleResultSet() = {
		val proNb:Int = 2
		val pepNb:Int = 10
  
		val rsb = new ResultSetFakeBuilder(
		    pepNb=pepNb, proNb=proNb)
		
		assert(rsb.rs != null)
		assert(rsb.allPeptides.size == pepNb)
		assert(rsb.allProts.size == proNb)
	}
  	
  	@Test
	def withDeltaNbPepResultSet() = {
		val protNb:Int = 3
		val pepNb:Int = 22
		val deltaPepNb:Int = 3
		val rsb = new ResultSetFakeBuilder(pepNb=pepNb, proNb=protNb, deltaPepNb=deltaPepNb)
		rsb.print
		assert(rsb.rs != null)
		assert(rsb.allPeptides.size == pepNb)
		assert(rsb.allProts.size == protNb)
	}
		
}

