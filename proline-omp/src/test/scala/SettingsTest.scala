import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.Settings
import org.junit.Test

class SettingsTest extends StrictLogging {


  @Test
  def testConfig() = {

    assert(Settings.SmartPeakelFinderConfig.minPeaksCount == 3)
    assert(Settings.FeatureDetectorConfig.intensityPercentile == 0.9f)

    assert(Settings.featureIntensity == "basePeakel.apex")
  }


}
