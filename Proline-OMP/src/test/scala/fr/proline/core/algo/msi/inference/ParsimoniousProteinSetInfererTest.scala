package fr.proline.core.algo.msi.inference

import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.utils.generator.ResultSetFakeBuilder

@Test
class ParsimoniousProteinSetInfererTest extends JUnitSuite with Logging {
	
  val nbProt:Int = 2
  val nbPep:Int = 10
  	
	@Test
	def simpleCheckWithGenData() = {
	  var rs:ResultSet = new ResultSetFakeBuilder(nbPep=nbPep, nbProt=nbProt).rs
	  var ppsi = new ParsimoniousProteinSetInferer()
	  var rsu = ppsi.computeResultSummary(resultSet=rs) 
	  assert(rsu != null)
	  assert(rsu.peptideSets.length==2)
	  assert(rsu.proteinSets.length==nbProt)	  
	}

}

