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
		val rs:ResultSet = rsb.toResultSet()
		
		assert(rs != null)
		assert(rsb.allPeptides.size == pepNb)
		assert(rsb.allProts.size == proNb)
	}
  	
  	@Test
	def withDeltaNbPepResultSet() = {
		val proNb:Int = 5
		val pepNb:Int = 22
		val deltaPepNb:Int = 3
		val rsb = new ResultSetFakeBuilder(pepNb=pepNb, proNb=proNb, deltaPepNb=deltaPepNb)
		val rs:ResultSet = rsb.toResultSet()
		
		assert(rs != null)
		assert(rsb.allPeptides.size == pepNb)
		assert(rsb.allProts.size == proNb)
	}
	
  	@Test
	def withSimpleMissCleavage() = {
		val proNb:Int = 5
		val pepNb:Int = 22		
		val deltaPepNb:Int = 3
		
		val pepWMissCleavagesNb:Int = 5		
		val missCleavage:Int = 2
		
		val rsb = new ResultSetFakeBuilder(pepNb=pepNb, proNb=proNb, deltaPepNb=deltaPepNb)
			.addNewPeptidesWithMissCleavage(pepNb=pepWMissCleavagesNb, missCleavageNb=missCleavage)			
		val rs:ResultSet = rsb.toResultSet()	
		
		assert(rs != null)
		assert(rsb.allPeptides.size == pepNb+pepWMissCleavagesNb)
		assert(rsb.allProts.size == proNb)
	}
  	
  	@Test
	def withMultiMissCleavage() = {
		val proNb:Int = 5
		val pepNb:Int = 22		
		val deltaPepNb:Int = 3
		
		val pepWMissCleavages2Nb:Int = 5
		val pepWMissCleavages3Nb:Int = 2
		val missCleavage2:Int = 2
		val missCleavage3:Int = 3
		
		val rsb = new ResultSetFakeBuilder(pepNb=pepNb, proNb=proNb, deltaPepNb=deltaPepNb)
			.addNewPeptidesWithMissCleavage(pepNb=pepWMissCleavages2Nb, missCleavageNb=missCleavage2)
			.addNewPeptidesWithMissCleavage(pepNb=pepWMissCleavages3Nb, missCleavageNb=missCleavage3)
		val rs:ResultSet = rsb.toResultSet()	
		
		assert(rs != null)
		assert(rsb.allPeptides.size == pepNb+pepWMissCleavages2Nb+pepWMissCleavages3Nb)
		assert(rsb.allProts.size == proNb)
	}
}

