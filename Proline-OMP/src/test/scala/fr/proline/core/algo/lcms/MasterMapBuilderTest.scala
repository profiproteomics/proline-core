package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import org.junit.Before
import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.lcms._
import fr.proline.core.util.generator.lcms._
import filtering.Filter

class MasterMapBuilderTest extends JUnitSuite  with Logging {
  
  @Test
  def testBuildMasterMap() {
    
    val nbMaps = 3
    val nbFeatures = 1000
    
    val runMapGenerator = new RunMapFakeGenerator(
      nbFeatures = nbFeatures,
      minMoz = LcMsRunGeneratorsTest.MIN_MOZ,
      maxMoz = LcMsRunGeneratorsTest.MAX_MOZ,
      minTime = 0,
      maxTime = LcMsRunGeneratorsTest.MAX_FT_TIME
    )
    
    val lcmsRun = LcMsRunGeneratorsTest.lcmsRunGenerator.generateLcMsRun()
    val runMap = runMapGenerator.generateRunMap( lcmsRun )
    
    val alnFakeGen = new MapAlignmentFakeGenerator( distortion = Distortions.SINE_DISTORTION(), amplitude = 30f )
    val mapSetFakeGen = new LcMsMapSetFakeGenerator(
      nbMaps = nbMaps,
      alnFakeGenerator = alnFakeGen,
      mozErrorPPM = 2f,
      timeError = 5f
    )
    
    // Generate a new map set
    val mapSet = mapSetFakeGen.generateMapSet(lcmsRun, runMap)
    
    // Build the corresponding master map
    val ftMappingParams = FeatureMappingParams(mozTol=10., mozTolUnit= "PPM", timeTol=20f )    
    val masterFtFilter = new Filter( "INTENSITY", "GT", 0. )
    val masterMap = MasterMapBuilder.buildMasterMap(mapSet, masterFtFilter, ftMappingParams)
    
    
    assertEquals(nbFeatures, masterMap.features.length)
    println( masterMap.features.length )
    masterMap.features.foreach { mft =>
      //val allTimes = mft.children.map( _.getCorrectedElutionTimeOrElutionTime )
      //val allmoz = mft.children.map( _.moz )
      //println( mft.charge )
      //println( allmoz.mkString("\t") )
      //println( mft.children.length )
    }
    
  }
  
}