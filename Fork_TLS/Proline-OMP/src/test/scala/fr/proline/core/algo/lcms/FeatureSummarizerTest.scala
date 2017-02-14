package fr.proline.core.algo.lcms

import org.junit.Assert._
import org.junit.Test
import org.scalatest.Assertions._
import fr.profi.mzdb.model.PeakelDataMatrix
import fr.proline.core.om.model.lcms._
import fr.proline.core.algo.lcms.summarizing._

@Test
class FeatureSummarizerTest {
  
  val charge = 2
  val moz = 824.1
  val elutionTime = 300f
  val duration = 60f
  val isotopeRatios = Array(0.5f, 0.3f) // TODO: change these values
  
  val featureRelationsTemplate = FeatureRelations(
    ms2EventIds = Array(),
    firstScanInitialId = 0,
    lastScanInitialId = 0,
    apexScanInitialId = 0
  )
  val featureTemplate = Feature(
    id = Feature.generateNewId,
    moz = moz,
    intensity = 0f,
    charge = charge,
    elutionTime = elutionTime,
    duration = duration,
    qualityScore = 0,
    ms1Count = 15,
    ms2Count = 0,
    isOverlapping = false,
    relations = featureRelationsTemplate
  )
  
  val peakelDataMatrixTemplate = new PeakelDataMatrix(
    Array(298,299,300,301,302),
    Array(298f,299f,300f,301f,302f),
    Array.fill(5)(moz),
    Array(5f,10f,20f,15f,5f)
  )
  
  val peakelTemplate = Peakel(
    id = Peakel.generateNewId,
    moz = moz,
    elutionTime = elutionTime,
    area = 0f,
    duration = duration,
    isOverlapping = false,
    featuresCount = 1,
    dataMatrix = peakelDataMatrixTemplate
  )
  
  val features = (1 to 6).toArray.map( i =>
    featureTemplate.copy(
      id = Feature.generateNewId,
      relations = featureRelationsTemplate.copy()
    )
  )
  // Index features by group number
  val indexedFeatures = features.take(3).map((_,1)) ++ features.drop(3).map((_,2))
  
  // Link peakels to features
  val intensityCoeffs = Array(1.05f,0.98f,0.93f,2.10f,1.95f,1.87f)
  for( (feature,intensityCoeff) <- features.zip(intensityCoeffs) ) {
    val peakelPattern = buildFeaturePeakelPattern( feature, intensityCoeff )
    feature.relations.peakelItems = peakelPattern
    feature.intensity = peakelPattern.head.getPeakel().get.apexIntensity
  }
    
  private def buildFeaturePeakelPattern( feature: Feature, intensityFactor: Float ): Array[FeaturePeakelItem] = {
    
    val dataMatrices = Array(
      new PeakelDataMatrix(
        peakelDataMatrixTemplate.getSpectrumIds(),
        peakelDataMatrixTemplate.getElutionTimes(),
        peakelDataMatrixTemplate.getMzValues(),
        peakelDataMatrixTemplate.getIntensityValues().map( _ * intensityFactor )
      ),
      new PeakelDataMatrix(
        peakelDataMatrixTemplate.getSpectrumIds(),
        peakelDataMatrixTemplate.getElutionTimes(),
        peakelDataMatrixTemplate.getMzValues().map( _ + 1 / charge ),
        peakelDataMatrixTemplate.getIntensityValues().map( _ * isotopeRatios(0) * intensityFactor )
      ),
      new PeakelDataMatrix(
        peakelDataMatrixTemplate.getSpectrumIds(),
        peakelDataMatrixTemplate.getElutionTimes(),
        peakelDataMatrixTemplate.getMzValues().map( _ + 2 / charge ),
        peakelDataMatrixTemplate.getIntensityValues().map( _ * isotopeRatios(1) * intensityFactor )
      )
    )
    
    (0 to 2).toArray.map { isotopeIndex =>
      FeaturePeakelItem(
        featureReference = feature,
        isotopeIndex = isotopeIndex,
        isBasePeakel = isotopeIndex == 0,
        peakelReference = peakelTemplate.copy(
          dataMatrix = dataMatrices(isotopeIndex),
          area = dataMatrices(isotopeIndex).integratePeakel()._2
        )
      )
    }
  }
  
  @Test
  def testPeakelSummarizer {
    //val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MAX_ABUNDANCE_SUM)    
    //assertArrayEquals( Array(3.99E+08f,Float.NaN,Float.NaN,3.90E+08f), singleRow, 3.99E+08f * 0.01f)
    
    for(met <- PeakelSummarizingMethod.values) {
      //println(met)
      for( feature <- features; peakelItem <- feature.relations.peakelItems.headOption ) {
        //val peakelIntensity = PeakelSummarizer.computePeakelIntensity(peakelItem.getPeakel.get, met)
        //println(a)
      }
      
      //println("******")
    }
    
  }

  @Test
  def testPeakelFiltering {
    //val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MAX_ABUNDANCE_SUM)    
    //assertArrayEquals( Array(3.99E+08f,Float.NaN,Float.NaN,3.90E+08f), singleRow, 3.99E+08f * 0.01f)
    
    for( feature <- features; peakelItem <- feature.relations.peakelItems.headOption ) {
      
      val peakel = peakelItem.getPeakel.get
      PeakelSavitzkyGolayFilter.applyFilter(peakel.copy())
      PeakelPolynomialFittingFilter.applyFilter(peakel.copy())
      PeakelGaussianFittingFilter.applyFilter(peakel.copy())
    }
  }

  @Test
  def testFeatureSummarizer {
    //val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, Method.MAX_ABUNDANCE_SUM)    
    //assertArrayEquals( Array(3.99E+08f,Float.NaN,Float.NaN,3.90E+08f), singleRow, 3.99E+08f * 0.01f)
    
    for( ftSummarizingMethod <- FeatureSummarizingMethod.values ) {
      val ftSummarizer = new FeatureSummarizer(
        peakelPreProcessingMethod = PeakelPreProcessingMethod.NONE,
        peakelSummarizingMethod = PeakelSummarizingMethod.APEX_INTENSITY,
        featureSummarizingMethod = ftSummarizingMethod,
        maxPeakelsCount = Some(1)
      )
      
      val sampleNumbers = Array(1,1,1,2,2,2)
      val ftIntensities = ftSummarizer.computeFeaturesIntensities( features.zip(sampleNumbers) )
      //println( ftSummarizingMethod )
      //println( ftIntensities.toList )
    }

    

  }

}