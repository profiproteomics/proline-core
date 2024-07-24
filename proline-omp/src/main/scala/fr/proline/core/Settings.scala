package fr.proline.core

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}

object Settings {

  private val config = ConfigFactory.load()

  object SmartPeakelFinderConfig {
    val minPeaksCount = _smartPeakelFinderConfig.getInt("minPeaksCount")
    val miniMaxiDistanceThresh = _smartPeakelFinderConfig.getInt("miniMaxiDistanceThresh")
    val maxIntensityRelThresh = _smartPeakelFinderConfig.getDouble("maxIntensityRelThresh").toFloat
    val useOscillationFactor = _smartPeakelFinderConfig.getBoolean("useOscillationFactor")
    val maxOscillationFactor = _smartPeakelFinderConfig.getInt("maxOscillationFactor")
    val usePartialSGSmoother = _smartPeakelFinderConfig.getBoolean("usePartialSGSmoother")
    val useBaselineRemover = _smartPeakelFinderConfig.getBoolean("useBaselineRemover")
    val useSmoothing = _smartPeakelFinderConfig.getBoolean("useSmoothing")
  }

  object FeatureDetectorConfig {
    val minNbOverlappingIPs = _featureDetectorConfig.getInt("minNbOverlappingIPs")
    val intensityPercentile = _featureDetectorConfig.getDouble("intensityPercentile").toFloat
    val maxConsecutiveGaps = _featureDetectorConfig.getInt("maxConsecutiveGaps")
  }

  val featureIntensity = config.getString("MapSetDetector.featureIntensity")
  val filterAssignedPeakels = config.getBoolean("MapSetDetector.PeakelsDetector.filterAssignedPeakels")
  val isomericPeptidesSharePeakels = config.getBoolean("MapSetDetector.PeakelsDetector.isomericPeptidesSharePeakels")
  val meanPredictedRetentionTime = config.getBoolean("MapSetDetector.PeakelsDetector.meanPredictedRetentionTime")
  val writeMzdbFeaturesMatches = config.getBoolean("MapSetDetector.PeakelsDetector.writeMzdbFeaturesMatches")

  object LoessSmoother {
    val defaultBandwidth = config.getConfig("LoessSmoother").getDouble("defaultBandwidth")
  }


  private val _smartPeakelFinderConfig = config.getConfig("MapSetDetector.PeakelsDetector.SmartPeakelFinderConfig")
  private val _featureDetectorConfig = config.getConfig("MapSetDetector.PeakelsDetector.FeatureDetectorConfig")

  def renderConfigAsString(): String = {
    config.root().render()
  }

  def renderCurratedConfigAsString(): String = {

    val nCfg = config.withoutPath("path").withoutPath("idea").withoutPath("os").withoutPath("user")
    .withoutPath("sun")
    .withoutPath("java")
    .withoutPath("jdk")
    .withoutPath("file")
    .withoutPath("line")
    .withoutPath("native")

    //System.out.println(" RenderConfigAsString2 currated config :  "+nCfg.root().entrySet().size())
    nCfg.root().render(ConfigRenderOptions.concise().setComments(true).setFormatted(true))

//    System.out.println(" \n\n RenderConfigAsString2 "+config.root().entrySet().size())
//    System.out.println(config.root().render(ConfigRenderOptions.concise().setComments(true).setFormatted(true)))

  }

}
