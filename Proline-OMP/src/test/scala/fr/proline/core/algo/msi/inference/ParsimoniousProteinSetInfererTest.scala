package fr.proline.core.algo.msi.inference

import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.utils.generator.ResultSetFakeBuilder

@Test
class ParsimoniousProteinSetInfererTest extends JUnitSuite with Logging {
	
  val proNb:Int = 2
  val pepNb:Int = 10
  	
	@Test
	def simpleCheckWithGenData() = {
	  var rs:ResultSet = new ResultSetFakeBuilder(pepNb=pepNb, proNb=proNb).rs
	  var ppsi = new ParsimoniousProteinSetInferer()
	  var rsu = ppsi.computeResultSummary(resultSet=rs) 
	  assert(rsu != null)
	  assert(rsu.peptideSets.length==2)
	  assert(rsu.proteinSets.length==proNb)	  
	}

}

