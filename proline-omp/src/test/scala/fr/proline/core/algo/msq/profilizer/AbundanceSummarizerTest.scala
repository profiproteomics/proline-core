package fr.proline.core.algo.msq.profilizer

import org.junit.Assert._
import org.junit.Test
import org.scalatest.Assertions._
import AbundanceSummarizer._
import fr.proline.core.algo.msq.config.profilizer.AbundanceSummarizerMethod

import scala.collection.mutable.ArrayBuffer

@Test
class AbundanceSummarizerTest {

  val originalMatrix = Array(
    Array(3.99E+08f, Float.NaN, 3.97E+08f, 3.90E+08f),
    Array(3.03E+08f, Float.NaN, 3.25E+08f, 3.15E+08f),
    Array(1.45E+08f, Float.NaN, 1.45E+08f, 1.46E+08f),
    Array(5.05E+07f, Float.NaN, 5.12E+07f, 5.23E+07f)
  )

  val matrixWithMissingValues = originalMatrix.clone
  matrixWithMissingValues(0)(2) = Float.NaN // 3.97E+08f is removed
  matrixWithMissingValues(1)(3) = Float.NaN // 3.15E+08f is removed

  @Test
  def testSummarizeUsingBestScore {
    intercept[IllegalArgumentException] {
      summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.BEST_SCORE)
    }
  }

  @Test
  def testSummarizeUsingMaxAbundanceSum {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.MAX_ABUNDANCE_SUM)
    assertArrayEquals(Array(3.99E+08f, Float.NaN, Float.NaN, 3.90E+08f), singleRow, 3.99E+08f * 0.01f)
  }

  @Test
  def testSummarizeUsingMean {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.MEAN)
    assertArrayEquals(Array(2.24E+08f, Float.NaN, 1.74E+08f, 1.96E+08f), singleRow, 2.24E+08f * 0.01f)
  }

  @Test
  def summarizeUsingMeanOfTop3 {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.MEAN_OF_TOP3)
    assertArrayEquals(Array(2.82E+08f, Float.NaN, 2.35E+08f, 2.68E+08f), singleRow, 2.82E+08f * 0.01f)
  }

  @Test
  def summarizeUsingMedian {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.MEDIAN)
    assertArrayEquals(Array(2.24E+08f, Float.NaN, 1.45E+08f, 1.46E+08f), singleRow, 2.24E+08f * 0.01f)
  }

  @Test
  def summarizeUsingMedianProfile {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.MEDIAN_PROFILE)
    assertArrayEquals(Array(2.82E+08f, Float.NaN, 2.94E+08f, 2.99E+08f), singleRow, 2.82E+08f * 0.01f)
  }

  @Test
  def summarizeUsingSum {
    val singleRow = summarizeAbundanceMatrix(matrixWithMissingValues, AbundanceSummarizerMethod.SUM)
    assertArrayEquals(Array(8.98E+08f, Float.NaN, 5.21E+08f, 5.88E+08f), singleRow, 8.98E+08f * 0.01f)
  }

  @Test
  def summarizeUsingLFQ {

    val matrix = Array(
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 59117000.0f, 63155000.0f, 52742000.0f, 113450000.0f, 129170000.0f, 95058000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 1492200.0f, Float.NaN, Float.NaN, 8984800.0f, 6243200.0f, 8168600.0f, 12613000.0f, 15032000.0f, 11329000.0f, 37847000.0f, 39634000.0f, 29501000.0f, 73777000.0f, 67108000.0f, 70010000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 11110000.0f, 11659000.0f, 11266000.0f, 28584000.0f, 22807000.0f, 25171000.0f, 85518000.0f, 68357000.0f, 66276000.0f, 196670000.0f, 148080000.0f, 136400000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 1740000.0f, Float.NaN, Float.NaN, 3813300.0f, Float.NaN, Float.NaN, 37035000.0f, Float.NaN, Float.NaN, 73070000.0f, Float.NaN, Float.NaN, 212170000.0f, 1032000.0f, Float.NaN, 432130000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 6631400.0f, 8829000.0f, 8898000.0f, 19549000.0f, 17258000.0f, 13566000.0f, 35345000.0f, 34835000.0f, 30207000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 6376300.0f, Float.NaN, Float.NaN, Float.NaN, 17396000.0f, 5901300.0f, 16541000.0f, 29116000.0f, 24546000.0f, 21396000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 90470000.0f, 83643000.0f, 61984000.0f, 178790000.0f, 150940000.0f, 114410000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 4611600.0f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 2058000.0f, Float.NaN, Float.NaN, Float.NaN, 9747500.0f, Float.NaN, 6820500.0f, 11916000.0f, 10511000.0f, 9854700.0f, 35362000.0f, 25573000.0f, 23567000.0f, 69225000.0f, 51641000.0f, 44651000),
      Array(Float.NaN, 3227200.0f, Float.NaN, Float.NaN, 1848200.0f, 1737600.0f, Float.NaN, 1329000.0f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 3430700.0f, 2413200.0f, Float.NaN, Float.NaN, Float.NaN, 1646000.0f, 1714700.0f, 20743000.0f, 18944000.0f, 64604000.0f, 52889000.0f, 43705000.0f, 124440000.0f, 111090000.0f, 89579000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 28348000.0f, Float.NaN, Float.NaN, 62275000.0f, 66812000.0f, Float.NaN, 129520000.0f, 144530000.0f, 104310000),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 57135000.0f, 47513000.0f, 47813000.0f, 93092300.0f, 90913300.0f, 76993000.0f, 268421100.0f, 234361000.0f, 196204300.0f, 530790000.0f, 468827000.0f, 446146800)
    )

    val minima = Array(89960.0f, 262950, 123000, 55282, 122390, 133610, 100230, 134400, 143320, 39389, 122290, 99359, 86382, 80750, 136560, 76271, 88697, 57473, 57670, 92502, 126580, 68421, 69176, 55047, 112040, 83939, 120880, 89696, 146100, 71153)
    val singleRow = summarizeAbundanceMatrix(matrix, AbundanceSummarizerMethod.LFQ)
    //println(singleRow.mkString(","))

    val abundances = LFQSummarizer.summarize(matrix, minima)
    //println(abundances.mkString(","))
    //  val expectedResult = Array(Float.NaN,1602700.1f,Float.NaN,Float.NaN,778955.7f,2380288.0f,Float.NaN,863202.5f,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,2094279.0f,1942052.1f,785008.0f,1003444.2f,2.9862658E7f,1.5233927E7f,9.5460544E7f,1.77231632E8f,1.8071296E8f,1.7775504E8f,8.3922182E8f,7.5434694E8f,6.3843834E8f,1.51518618E9f,1.52327923E9f,1.42865254E9f)
    //  assertArrayEquals(expectedResult, abundances, 0.01f)
  }

  @Test
  def summarizeUsingLFQ2 {

    val matrix = Array(
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 412745.5f, 418297.03f, 397936.25f, 267286.91f, 822196.75f, 811863.5f, 840923.88f, 651688.94f, 2184739f, 1611606.25f, 1571033.25f, 1518471.75f, 3563049.5f, 3193230f, 3684954.25f, 2216229.5f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 236497.02f, Float.NaN, Float.NaN, 329206.25f, 293607.97f, 1030029.75f, 1028047.88f, 1213244.5f, 1073887.25f, 1983622.62f, 2182786.5f, 2387682.5f, 2211132.25f, 6208515.5f, 4755393.5f, 5683938f, 5040266f, 11022342f, 9515836f, 11945652f, 9562198f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 1548524.25f, Float.NaN, Float.NaN, Float.NaN, 6745815f, 6393756f, 6365023.5f, 4784954f, 14153034f, 12249425f, 12462140f, 10054140f, 39617692f, 32495698f, 30032938f, 23372204f, 78754544f, 63147152f, 65661628f, 28125032f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 703942f, 584729.75f, 518119.12f, 518022.44f, 1288962.25f, 1017748.62f, 941941.25f, 1157931.25f, 3743203.25f, 2459231.25f, 2374482f, 2314758.75f, 6805393.5f, 5381672f, 4785597f, 1497726.5f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 242280.02f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 516509.88f, 533490.88f, 301399.47f, 815104.44f, 788628.88f, 966794.69f, 637191.81f, 1282779f, 1616475.25f, 1593300f, 1246552.5f, 6680127f, 6655414f, 7630020.5f, 5929184f, 15449076f, 13949400f, 16172823f, 13141177f, 46251832f, 35843020f, 41529184f, 33930356f, 91566528f, 70841856f, 76794184f, 44582064f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 245200.09f, Float.NaN, Float.NaN, 258732.34f, 1311906f, 1526154.25f, 1661433.62f, 1562180.62f, 3052247.25f, 2911015f, 3230808.25f, 2577546f, 9052023f, 7764576f, 8386094.5f, 6944957f, 19350988f, 15397969f, 16504406f, 7227671f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 238310.77f, 1284367.5f, 1020665.12f, 1457888.12f, 1478807.62f, 3208390.75f, 2324430f, 2739512.75f, 3046164.5f, 8640694f, 6736510f, 7821349.5f, 6123884f, 18110040f, 13226038f, 14597406f, 4689200.5f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 456549.91f, 455015.28f, 469824.5f, 473964.84f, 815333.81f, 858791.44f, 980765.88f, 894903.44f, 2235874.75f, 1784861.88f, 2126732.75f, 1909743f, 5159366.5f, 3862294.25f, 4706061.5f, 2571026f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 825807.5f, 441338.34f, 468976.66f, 665864.38f, 1177581f, 986171.75f, 914757.31f, 435830f),
      Array(Float.NaN, 2742600f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 188038.98f, 251690.5f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 437122.62f, 1128999.12f, 828960.56f, 975544.56f, 866516.69f, 1916980f, 1689091.62f, 2060422.88f, 1365029.12f),
      Array(Float.NaN, Float.NaN, 1343429f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 318448.97f, 218218.44f, 176003.11f, Float.NaN, 433857.97f, 329101.34f, 313056.56f, 210593.77f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 978573.44f, 673802.88f, 1113235.75f, Float.NaN, Float.NaN, 1221926f, 1979701.62f, 1847098.75f, 2337024.5f, 2182466.5f, 8935212f, 8591497f, 9763901f, 8652752f, 20206258f, 19491470f, 21666672f, 17041332f, 58606716f, 46454752f, 53021148f, 46968444f, 121191712f, 90626040f, 111787480f, 60408960f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 366516.38f, 356304.53f, 450885.09f, Float.NaN, 1475533f, 1524386.88f, 1414291.88f, 1346296.5f, 3102544.5f, 3350540f, 3196454f, 3666947.25f, 9275462f, 6950060f, 6757980.5f, 7386282f, 19069754f, 13705563f, 17286028f, 5249453.5f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 164917.88f, Float.NaN, Float.NaN, Float.NaN, 330393f, 269966.38f, 266414.19f, 148517.31f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 161187.78f, 234921.41f, 279606.56f, 227505.3f, Float.NaN, 938346.81f, 874414.88f, 1037347.12f, 1003890.62f, 2120790.5f, 1971568.5f, 1986659.88f, 2060037.75f, 5369880f, 4400543f, 5299044.5f, 4306490.5f, 11932881f, 9374421f, 10658357f, 9015528f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 352402.03f, 291899.72f, 426176.97f, 221350.3f, 925149f, 569693.75f, 572173.44f, 462591.06f, 1683015.25f, 1329789.12f, 1332905.12f, 967112.19f, 3275067.75f, 2282688f, 2461956f, 1543123f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 425125.31f, Float.NaN, Float.NaN, 943882.31f, 727573.5f, 755545f, 596771.5f, 2149389.75f, 1912256.25f, 1549714.12f, 1629437.25f, 4357858f, 3760862.75f, 3677236.5f, 1933279.25f),
      Array(Float.NaN, Float.NaN, Float.NaN, 104166.62f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 457567.59f, 329000.06f, 553542.69f, 621765.81f, 2172135.75f, 1974563.25f, 2558356.25f, 2148640.25f, 4188857.5f, 4032093.25f, 5572043.5f, 4598633f, 12196848f, 11684254f, 12342608f, 11305891f, 23885324f, 21637742f, 27037934f, 19168522f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 804809.75f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 443634.09f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 389271.41f, 612914.88f, 499435.62f, 824569.06f, Float.NaN, 912113.25f, 500513.53f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 66852.81f, 100695.62f, 97342.11f, 404626.34f, 355165.72f, 388852.97f, 374141.09f, 749602.69f, 730532.5f, 933078.25f, 921078.75f, 2291406.25f, 1858791.25f, 2068948.62f, 1974594.25f, 4060085.25f, 3916358.75f, 4960922.5f, 3687008f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 220953.56f, Float.NaN, 190979.17f, 219433.22f, 424754f, 338119.69f, 377369.41f, 435184.59f, 1254024.38f, 991400.06f, 848354.94f, 1011076.25f, 2444529.5f, 2045535.5f, 1826177.5f, 1497950.5f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 284589.69f, Float.NaN, 211231.73f, Float.NaN, Float.NaN, 369282.72f, 460202.38f, 1546577.62f, 1550205.75f, 2044952.88f, 1746801.62f, 3756856.75f, 3191910.75f, 3590192.5f, 3262603.25f, 9352253f, 7802708.5f, 8760880f, 7608008f, 17974998f, 14402801f, 16835652f, 9511200f),
      Array(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 295942.03f, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 1443086.62f, 1549341.88f, 2023095.62f, 1787515.62f, 2932503f, 3134476.25f, 3017354f, 3464977f, 8588721f, 6620410f, 7940824.5f, 7590247.5f, 18144848f, 13698668f, 16097925f, 10412950f))

    val minima = Array(65103.14f, 44433.3f, 41141.32f, 34050.16f, 41769.55f, 41895.92f, 45705.4f, 34983.64f, 46035.12f, 12798.28f, 59004.55f, 44714.9f, 45638.9f, 40969.52f, 50311.71f, 20606.43f, 44591.68f, 46987.13f, 57538.86f, 36557.04f, 50714.55f, 37531.32f, 42057.8f, 32675.03f, 49524.7f, 27701.68f, 31676.29f, 36779.82f, 47594.77f, 42314.23f, 41511.98f, 39256.61f, 52328.61f, 42462.69f, 33200.38f, 44446.32f, 92678.65f, 45918.39f, 61418.64f, 46060.56f)

    val singleRow = summarizeAbundanceMatrix(matrix, AbundanceSummarizerMethod.LFQ)
    println(singleRow.mkString(","))

    val abundances = LFQSummarizer.summarize(matrix, minima)
    println(abundances.mkString(","))

    //  val expectedResult = Array(Float.NaN,1602700.1f,Float.NaN,Float.NaN,778955.7f,2380288.0f,Float.NaN,863202.5f,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,2094279.0f,1942052.1f,785008.0f,1003444.2f,2.9862658E7f,1.5233927E7f,9.5460544E7f,1.77231632E8f,1.8071296E8f,1.7775504E8f,8.3922182E8f,7.5434694E8f,6.3843834E8f,1.51518618E9f,1.52327923E9f,1.42865254E9f)
    //  assertArrayEquals(expectedResult, abundances, 0.01f)
  }


  @Test
  def summarizeFromFile: Unit = {
    val bufferedSource = io.Source.fromFile("C:\\Local\\bruley\\Dev\\WSIntelliJ_Proline\\Refactoring_2019\\Proline-Core\\proline-omp\\src\\test\\resources\\ions_for_mrf.tsv")

    val minima = Array(22190.92f, 10670.78f, 6057.45f, 8857.2f, 10721.79f, 15014.91f, 15594.41f, 12219.42f)

    val lines = bufferedSource.getLines
    var accession = ""
    lines.next()
    val buffer = new ArrayBuffer[Array[Float]]()
    for (line <- lines) {
      var values = new Array[Float](8)
      val cols = line.split("\t")

      if (!accession.isEmpty && !cols(0).equals(accession)) {
        val matrix = buffer.toArray
        val abundances = LFQSummarizer.summarize(matrix, minima)
        println(accession + ", " + abundances.mkString(","))
        buffer.clear()
        accession = cols(0)
      } else {
        accession = cols(0)
      }

      for (k <- 1 until 9) {
        values(k - 1) = {
          if ((k >= cols.length) || cols(k) == null || cols(k).isEmpty) {
            Float.NaN;
          } else {
            cols(k).replace(',', '.').toFloat
          }
        }
      }
      buffer += values
    }

    bufferedSource.close
  }
}