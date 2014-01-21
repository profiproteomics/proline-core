package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import org.junit.Before
import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import org.scalatest.matchers.MustMatchers
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.lcms._
import fr.proline.core.util.generator.lcms._

class FeatureMapperTest extends JUnitSuite with MustMatchers with Logging {
  
  @Test
  def testComputePairwiseFtMapping() {
    
    val nbMaps = 2
    val nbFeatures = 100
    
    val rawMapGenerator = new RawMapFakeGenerator(
      nbFeatures = nbFeatures,
      minMoz = LcMsRunGeneratorsTest.MIN_MOZ,
      maxMoz = LcMsRunGeneratorsTest.MAX_MOZ,
      minTime = 0,
      maxTime = LcMsRunGeneratorsTest.MAX_FT_TIME
    )
    
    val lcmsRun = LcMsRunGeneratorsTest.lcmsRunGenerator.generateLcMsRun()
    val rawMap = rawMapGenerator.generateRawMap( lcmsRun )
    
    val alnFakeGen = new MapAlignmentFakeGenerator( distortion = Distortions.SINE_DISTORTION(), amplitude = 30f )
    val mapSetFakeGen = new LcMsMapSetFakeGenerator(
      nbMaps = nbMaps,
      alnFakeGenerator = alnFakeGen,
      mozErrorPPM = 2f,
      timeError = 5f
    )
    
    val mapSet = mapSetFakeGen.generateMapSet(lcmsRun, rawMap)
    assert( mapSet.childMaps.length === nbMaps )
    
    val childMaps = mapSet.childMaps
    val ftMappingParams = FeatureMappingParams(mozTol=10., mozTolUnit= "PPM", timeTol=20f )
    val ftMapping = FeatureMapper.computePairwiseFtMapping(
      childMaps(0).features, childMaps(1).features, ftMappingParams, false
    )
    
    // Check that all features are mapped
    assertEquals(ftMapping.size,nbFeatures)
    
    for ( ft<- childMaps(0).features ) {
      assertTrue( ftMapping(ft.id).length > 0 )
      
      /*if( ftMapping(ft.id).length > 1 ) {
        println( ft.elutionTime + " "+ftMapping(ft.id)(0).getCorrectedElutionTimeOrElutionTime )
        println( ft.elutionTime + " "+ftMapping(ft.id)(1).getCorrectedElutionTimeOrElutionTime )
      }*/
      
      //println( ftMapping(ft.id).length )
      //println( ft.moz + " "+ftMapping(ft.id)(0).moz )
      //println( ft.elutionTime + " "+ftMapping(ft.id)(0).getCorrectedElutionTimeOrElutionTime )
    }
    
    ()
  }
  
}