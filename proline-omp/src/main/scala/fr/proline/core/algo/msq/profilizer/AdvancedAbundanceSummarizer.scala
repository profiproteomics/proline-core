package fr.proline.core.algo.msq.profilizer

import scala.collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.stat.StatUtils
import fr.profi.util.math.filteredMean
import fr.profi.util.math.filteredMedian
import fr.profi.util.primitives.isZeroOrNaN
import fr.profi.util.random.generatePositiveGaussianValues
import fr.proline.core.algo.msq.config.profilizer.MqPeptideAbundanceSummarizingMethod
import fr.proline.core.om.model.msq.ExperimentalDesignSetup

object AdvancedAbundanceSummarizer extends LazyLogging {

  def summarizeAbundanceMatrix(abundanceMatrix: Array[Array[Float]], method: MqPeptideAbundanceSummarizingMethod.Value, expDesignSetup: ExperimentalDesignSetup): Array[Float] = {

    val matrixLength = abundanceMatrix.length
    require(matrixLength > 0, AbundanceSummarizer.EMPTY_MATRIX_MESSAGE)

    if (matrixLength == 1) return abundanceMatrix.head

    method match {
      case MqPeptideAbundanceSummarizingMethod.MEDIAN_BIOLOGICAL_PROFILE => summarizeUsingMBP(abundanceMatrix, expDesignSetup)
      case _ => throw AbundanceSummarizer.createNoImplemError(method)
    }

  }
  
  
  def summarizeUsingMBP(abundanceMatrix: Array[Array[Float]], expDesignSetup : ExperimentalDesignSetup ): Array[Float] =  {
    // Group quant channels by biological samples 
    val bioSamplesGroupedMatrix = abundanceMatrix.map { abundanceRow =>

      val sampleAbundancesBySampleNum = abundanceRow.zip(expDesignSetup.qcSampleNumbers).groupBy(_._2)
      val sampleNumbers = expDesignSetup.qcSampleNumbers.distinct

      sampleNumbers.map { sampleNumber =>
        val sampleAbundancesTuples = sampleAbundancesBySampleNum(sampleNumber)
        sampleAbundancesTuples.map(_._1)
      }
    }

    //println("bioSamplesGroupedMatrix.length",bioSamplesGroupedMatrix.length)

    // Compute median abundances for each biological sample in each row of the matrix
    val samplesMedianAbMatrix = bioSamplesGroupedMatrix.withFilter { sampleAbundances =>
      // Internal filter: keep only rows having at least one biological sample with more than one defined value
      sampleAbundances.count(_.count(!isZeroOrNaN(_)) >= 2) >= 1
    } map { abundanceRow =>
      abundanceRow.map { sampleAbundances =>
        filteredMedian(sampleAbundances)
      }
    }

    // Check matrix is not empty after filtering
    if (samplesMedianAbMatrix.isEmpty) {
      logger.warn("Can't compute biological median profile for abundance summarizing (not enough defined values), fallback to MEDIAN_PROFILE")
      return AbundanceSummarizer.summarizeAbundanceMatrix(
        abundanceMatrix,
        MqPeptideAbundanceSummarizingMethod.MEDIAN_PROFILE
      )
    }

    // Remove values in columns having a low number of defined values
    val filteredSamplesMedianAbMatrix = samplesMedianAbMatrix.transpose.map { column =>
      val nValues = column.length
      val nDefValues = column.count(!isZeroOrNaN(_))
      val nonDefValues = column.length - nDefValues
      if (nDefValues < nonDefValues) Array.fill(nValues)(Float.NaN)
      else column
    } transpose

    // Compute median profile of the samplesMedianAbMatrix
    // We will thus obtain a reference abundance value for each biological sample
    // Note: if there no intensity for a given sample, we will not generate values for this sample (missing values)
    val samplesMedianProfile = AbundanceSummarizer.summarizeAbundanceMatrix(
      filteredSamplesMedianAbMatrix,
      MqPeptideAbundanceSummarizingMethod.MEDIAN_PROFILE
    )
    //println("samplesMedianProfile", samplesMedianProfile.mkString("\t"))

    // Transpose bioSamplesGroupedMatrix to have samples at the first level of the 3D matrix
    val samplesAbMatrices = bioSamplesGroupedMatrix.transpose

    // Create a matrix which will contain the sorted indices of abundances for each biological sample
    // Note: indices are sorted in descending abundance order
    val sampleSortedIndicesMatrix = new ArrayBuffer[Array[Int]](expDesignSetup.sampleCount)

    // Iterate over biological sample matrices to summarize them independently
    val samplesCvs = samplesAbMatrices.map { sampleAbMatrix =>

      // Compute TOP3 mean values
      val top3MeanSampleAb = AbundanceSummarizer.summarizeAbundanceMatrix(
        sampleAbMatrix,
        MqPeptideAbundanceSummarizingMethod.MEAN_OF_TOP3
      )

      // Compute ranks of TOP3 mean values
      //val sampleAbSortedIndices = new Array[Int](top3MeanSampleAb.length)
      val sampleAbSortedIndices = for ((ab, idxBeforeSort) <- top3MeanSampleAb.zipWithIndex.sortBy(-_._1)) yield {
        idxBeforeSort
      }
      //println(sampleAbSortedIndices.mkString("\t"))
      sampleSortedIndicesMatrix += sampleAbSortedIndices

      /*println("before norm")
        for( r <- sampleAbMatrix ) {
          println(r.mkString("\t"))
        }*/

      // Center the sample matrix to obtain comparable values
      //val normalizedSampleAbMatrix = AbundanceNormalizer.normalizeAbundances(sampleAbMatrix.transpose).transpose
      val centeredSampleAbMatrix = sampleAbMatrix.map { sampleAbundances =>
        // Skip samples having only a single defined value
        if (sampleAbundances.count(!isZeroOrNaN(_)) == 1) Array.fill(sampleAbundances.length)(Float.NaN)
        else {
          val sampleMean = filteredMean(sampleAbundances)
          sampleAbundances.map(_ / sampleMean)
        }
      }

      /*println("after norm")
        for( r <- centeredSampleAbMatrix ) {
          println(r.toList)
        }*/

      // Flatten abundances observed in the different rows
      val flattenedDefSampleAbundances = centeredSampleAbMatrix.flatten.filter(isZeroOrNaN(_) == false)

      // Check we have enough abundances (at least 3)
      if (flattenedDefSampleAbundances.length > 2) {
        //println("flattenedDefSampleAbundances",flattenedDefSampleAbundances.mkString("\t"))
        val medianSampleAb = filteredMedian(flattenedDefSampleAbundances)
        //println("medianSampleAb",medianSampleAb)
        val stdDev = Math.sqrt(StatUtils.variance(flattenedDefSampleAbundances.map(_.toDouble)))
        //println("stdDev",stdDev)
        stdDev / medianSampleAb
      } else {
        Double.NaN
      }
    }

    val defSampleCvs = samplesCvs.filter(isZeroOrNaN(_) == false)
    val sampleCvMean = if (defSampleCvs.isEmpty) Double.NaN else defSampleCvs.sum / defSampleCvs.length
    if (isZeroOrNaN(sampleCvMean)) {
      logger.warn("Can't compute CVs for abundance summarizing, fall back to MEDIAN_PROFILE")
      return AbundanceSummarizer.summarizeAbundanceMatrix(
        abundanceMatrix,
        MqPeptideAbundanceSummarizingMethod.MEDIAN_PROFILE
      )
    }

    // Generate abundance values using the computed gaussian model parameters (median and standard deviation values)
    val qcGeneratedValues = new ArrayBuffer[Float](expDesignSetup.qcCount)
    for ((sampleMedianAb, idx) <- samplesMedianProfile.zipWithIndex) {
      val sampleAbSortedIndices = sampleSortedIndicesMatrix(idx)
      val sampleCv = samplesCvs(idx)
      val sampleQcCount = expDesignSetup.samplesQcCount(idx)

      val sampleCvOrMeanCv = if (!isZeroOrNaN(sampleCv)) sampleCv else sampleCvMean
      assert(isZeroOrNaN(sampleCvOrMeanCv) == false)

      val sampleGeneratedValues = if (isZeroOrNaN(sampleMedianAb)) {
        Array.fill(sampleQcCount)(Float.NaN)
      } else {
        val sampleStdDev = sampleCvOrMeanCv * sampleMedianAb
        val generatedValues = generatePositiveGaussianValues(sampleMedianAb.toDouble, sampleStdDev, 0.05f, sampleQcCount).map(_.toFloat)

        if (generatedValues == null) {
          throw new Exception("Can't generate gaussian values")
          /*println("sampleMedianAb",sampleMedianAb)
            println("sampleStdDev",sampleStdDev)*/
        }

        //println("generatedValues",generatedValues.toList)

        // Sort generated abundances according to previously computed sorted indices
        generatedValues.sortBy(-_).zip(sampleAbSortedIndices).sortBy(_._2).map(_._1)
      }

      // TODO: how to be sure we are accumulating values in the right QC order ?
      qcGeneratedValues ++= sampleGeneratedValues
    }

    /*if( i >= 20 ){
        1 / 0
      }*/

    qcGeneratedValues.toArray
      
      /*
      // PREVIOUS IMPLEM AT GROUP LEVEL
      
      // Normalize the profiles
      val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrix.transpose).transpose
      
      // Do we need a scaling after normalization ???
      
      // Split matrix in biological replicates matrix
      val groupsMatrix = normalizedMatrix.map { normalizedRow =>
        val groupAbundancesByGroupNum = normalizedRow.zip(qcGroupNumbers).groupBy(_._2)
        
        groupSetup.biologicalGroups.map { bgGroup =>
          val groupAbundancesTuples = groupAbundancesByGroupNum(bgGroup.number)
          val groupAbundances = groupAbundancesTuples.map(_._1)
          this._medianAbundance(groupAbundances)
        }
      }
      
      val summarizedGroupsAbundances = AbundanceSummarizer.summarizeAbundanceMatrix(
        groupsMatrix,
        MEDIAN_PROFILE
      )
      
      // TODO: summarize also the normalizedMatrix
      */
      

  }
}