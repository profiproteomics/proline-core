package fr.proline.core.util.generator.lcms

import org.junit.Test
import org.scalatest.junit.JUnitSuite

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.core.om.model.lcms.RawFile

object LcMsRunGeneratorsTest {
  
  final val MIN_MOZ = 300
  final val MAX_MOZ = 2000
  final val MAX_RUN_TIME = 120 * 60
  final val MAX_FT_TIME = MAX_RUN_TIME
  final val MAX_MS1_INTENSITY = 10e9.toInt
  final val MAX_MS2_INTENSITY = 1000
  
  val landmarks = Array( (1,10e7),(300,10e7),(400,10e9),(2000,10e9),(3000,10e8),(4000,10e7),(6000,10e8),(7200,10e6) )
  val relLandmarks = landmarks.map( lm => lm._1.toFloat -> (lm._2/10e9).toFloat )
  
  val lcmsRunGenerator = new LcMsRunFakeGenerator(
    rawFile = RawFile(name="generated.raw",extension="raw"),
    minMoz = MIN_MOZ,
    maxMoz = MAX_MOZ,
    maxTime = MAX_RUN_TIME,
    ms1MaxIntensity = MAX_MS1_INTENSITY,
    ms2MaxIntensity = MAX_MS2_INTENSITY,
    ms1RelativeLandmarks = relLandmarks
  )
  
}

@Test
class LcMsRunGeneratorsTest extends JUnitSuite with Logging {
  
  //@Test
  def testRawMapGenerator() {
    
    val rawMapGenerator = new RawMapFakeGenerator(
      nbFeatures = 10,
      minMoz = LcMsRunGeneratorsTest.MIN_MOZ,
      maxMoz = LcMsRunGeneratorsTest.MAX_MOZ,
      minTime = 0,
      maxTime = LcMsRunGeneratorsTest.MAX_FT_TIME
    )
    
    val rawMap = rawMapGenerator.generateRawMap( LcMsRunGeneratorsTest.lcmsRunGenerator.generateLcMsRun )
    assert( rawMap.features.length === 10 )

    /*rawMap.features.foreach { ft =>
      println( "id=%d moz=%.6f intensity=%.0f charge=%d time=%.1f duration=%.0f ms1Count=%d ms2Count=%d".format(
        ft.id, ft.moz, ft.intensity, ft.charge, ft.elutionTime, ft.duration, ft.ms1Count, ft.ms2Count
      ))
      
    }*/
    
    ()
  }
  
  @Test
  def testMapSetGenerator() {
    
    val nbMaps = 3
    val nbFeatures = 10
    
    val rawMapGenerator = new RawMapFakeGenerator(
      nbFeatures = nbFeatures,
      minMoz = LcMsRunGeneratorsTest.MIN_MOZ,
      maxMoz = LcMsRunGeneratorsTest.MAX_MOZ,
      minTime = 0,
      maxTime = LcMsRunGeneratorsTest.MAX_FT_TIME
    )
    
    val lcmsRun = LcMsRunGeneratorsTest.lcmsRunGenerator.generateLcMsRun()
    val rawMap = rawMapGenerator.generateRawMap( lcmsRun )
    
    val alnFakeGen = new MapAlignmentFakeGenerator( distortion = Distortions.SINE_DISTORTION(), amplitude = 1f )
    val mapSetFakeGen = new LcMsMapSetFakeGenerator( nbMaps = nbMaps, alnFakeGenerator = alnFakeGen )
    
    val mapSet = mapSetFakeGen.generateMapSet(lcmsRun, rawMap)
    assert( mapSet.childMaps.length === nbMaps )
  }


}