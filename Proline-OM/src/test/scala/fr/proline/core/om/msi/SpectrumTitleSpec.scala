package fr.proline.core.om.msi

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.GivenWhenThen
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import fr.proline.core.om.model.msi.SpectrumTitleFields

@RunWith(classOf[JUnitRunner])
class SpectrumTitleSpec extends FunSpec with GivenWhenThen with ShouldMatchers {
  
  // TODO: update proline admin config files
  object SoftNames extends Enumeration {
    val EXTRACT_MSN = Value("extract_msn.exe")
    val DATA_ANALYSIS_4_0 = Value("Data Analysis 4.0") // added
    val DATA_ANALYSIS_4_1 = Value("Data Analysis 4.1") // added
    val MASCOT_DLL = Value("mascot.dll")
    val MASCOT_DISTILLER = Value("Mascot Distiller")
    val MAX_QUANT = Value("MaxQuant")
    val PROTEOME_DISCOVER = Value("Proteome Discoverer")
    val PROTEO_WIZARD_2_0 = Value("ProteoWizard 2.0")
    val PROTEO_WIZARD_2_1 = Value("ProteoWizard 2.1")
    val SPECTRUM_MILL = Value("Spectrum Mill") // renamed
  }
  
  case class SpectrumTitleSpecif(
    softName: SoftNames.Value,
    expectedFields: String,
    titleString: String,
    fieldMap: Map[SpectrumTitleFields.Value,String]
  )
  
  val titleSpecifs = List(
    SpectrumTitleSpecif(
      SoftNames.EXTRACT_MSN,
      "the raw file name and the scans",
      "101811RML4SILAC6DaHCD.3864.3864.3.dta", 
      Map(
        SpectrumTitleFields.RAW_FILE_NAME -> "101811RML4SILAC6DaHCD",
        SpectrumTitleFields.FIRST_SCAN -> "3864",
        SpectrumTitleFields.LAST_SCAN -> "3864"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.DATA_ANALYSIS_4_0,
      "the retention times",
      "Cmpd 1063, +MSn(705.8878), 24.12 min", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "24.12",
        SpectrumTitleFields.LAST_TIME -> "24.12"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.DATA_ANALYSIS_4_1,
      "the retention times and the scans",
      "Cmpd 19, +MS2(417.0491), 7.7-10.4eV, 4.1 min #576", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "4.1",
        SpectrumTitleFields.LAST_TIME -> "4.1",
        SpectrumTitleFields.FIRST_SCAN -> "576",
        SpectrumTitleFields.LAST_SCAN -> "576"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.MASCOT_DLL,
      "the raw file name, the cycles and the retention times",
      "File: QSAG051130001.wiff, Sample: bandeA1 (sample number 1), Elution: 92.3 min, Period: 1, Cycle(s): 2040 (Experiment 2)", 
      Map(
        SpectrumTitleFields.RAW_FILE_NAME -> "QSAG051130001.wiff",
        SpectrumTitleFields.FIRST_CYCLE -> "2040",
        SpectrumTitleFields.LAST_CYCLE -> "2040",
        SpectrumTitleFields.FIRST_TIME -> "92.3",
        SpectrumTitleFields.LAST_TIME -> "92.3"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.PROTEOME_DISCOVER,
      "the scans",
      "Spectrum1524 scans:2000,",
      Map(
        SpectrumTitleFields.FIRST_SCAN -> "2000",
        SpectrumTitleFields.LAST_SCAN -> "2000"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.SPECTRUM_MILL,
      "the retention times",
      "Cmpd 1063, +MSn(705.8878), 24.12 min", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "24.12",
        SpectrumTitleFields.LAST_TIME -> "24.12"
      )
    )
  )
  
  val specTitleRuleBySoftName = Map(
    SoftNames.EXTRACT_MSN -> SpectrumTitleParsingRule(
      rawFileNameRegex = Some("""(.+)\.\d+\.\d+\.\d+\.dta"""),
      firstScanRegex   = Some(""".+\.(\d+)\.\d+\.\d+\.dta"""),
      lastScanRegex    = Some(""".+\.\d+\.(\d+)\.\d+\.dta""")
    ),
    SoftNames.DATA_ANALYSIS_4_0 -> SpectrumTitleParsingRule(
      firstTimeRegex = Some("""Cmpd.+MSn.+, (\d+\.\d+) min"""),
      lastTimeRegex  = Some("""Cmpd.+MSn.+, (\d+\.\d+) min""")
    ),
    SoftNames.DATA_ANALYSIS_4_1 -> SpectrumTitleParsingRule(
      firstTimeRegex = Some("""Cmpd.+MS\d.+, (\d+\.\d+) min"""),
      lastTimeRegex  = Some("""Cmpd.+MS\d.+, (\d+\.\d+) min"""),
      firstScanRegex = Some("""Cmpd.+MS\d.+, \d+\.\d+ min #(\d+)"""),
      lastScanRegex  = Some("""Cmpd.+MS\d.+, \d+\.\d+ min #(\d+)""")
    ),
    SoftNames.MASCOT_DLL -> SpectrumTitleParsingRule(
      rawFileNameRegex = Some("""^File: (.+?),"""),
      firstCycleRegex  = Some("""Cycle\(s\): (\d+) \(Experiment \d+\)||Cycle\(s\): (\d+), \d+"""),
      lastCycleRegex   = Some("""Cycle\(s\): (\d+) \(Experiment \d+\)||Cycle\(s\): \d+, (\d+)"""),
      firstTimeRegex   = Some("""Elution: (.+?) to .+? min||.+Elution: (.+?) min"""), // modified
      lastTimeRegex    = Some("""Elution: .+? to (.+?) min||.+Elution: (.+?) min""") // modified
    ),
    SoftNames.MASCOT_DISTILLER -> SpectrumTitleParsingRule(
      rawFileNameRegex = Some("""\[(.+?)\]"""),
      firstCycleRegex  = Some("""(\d+?) \(rt="""),
      lastCycleRegex   = Some("""- \d+: Scan (\d+?) \(rt="""),
      firstTimeRegex   = Some("""\(rt=(\d+?\.\d+?)\)"""),
      lastTimeRegex    = Some("""-.+Scan \d+ \(rt=(\d+\.\d+)""")
    ),
    SoftNames.MAX_QUANT -> SpectrumTitleParsingRule(
      rawFileNameRegex = Some("""^RawFile: (.+?) FinneganScanNumber:"""),
      firstScanRegex   = Some("""FinneganScanNumber: (\d+)"""),
      lastScanRegex    = Some("""FinneganScanNumber: (\d+)""")
    ),
    SoftNames.PROTEOME_DISCOVER -> SpectrumTitleParsingRule(
      firstScanRegex = Some("""Spectrum\d+\s+scans:(\d+?),"""),
      lastScanRegex  = Some("""Spectrum\d+\s+scans:(\d+?),""")
    ),
    SoftNames.PROTEO_WIZARD_2_0 -> SpectrumTitleParsingRule(
      firstScanRegex = Some("""scan=(\d+)"""),
      lastScanRegex  = Some("""scan=(\d+)""")
    ),
    SoftNames.PROTEO_WIZARD_2_1 -> SpectrumTitleParsingRule(
      rawFileNameRegex = Some("""(.+)\.\d+\.\d+\.\d+$"""),
      firstScanRegex   = Some(""".+\.(\d+)\.\d+\.\d+$"""),
      lastScanRegex    = Some(""".+\.\d+\.(\d+)\.\d+$""")
    ),
    SoftNames.SPECTRUM_MILL -> SpectrumTitleParsingRule(
      firstTimeRegex = Some("""Cmpd.+MSn.+, (\d+\.\d+) min"""),
      lastTimeRegex  = Some("""Cmpd.+MSn.+, (\d+\.\d+) min""")
    )
  )
  
  // Iterate over each spectrum title specification
  for( titleSpecif <- titleSpecifs ) {
    
    val softName = titleSpecif.softName
    
    describe(softName +" spectrum title" ) {
      
      it("should contain "+ titleSpecif.expectedFields) {
        
        Given("the parsing rule")
        val parsingRule = specTitleRuleBySoftName(softName)
        
        When("parsing the title")
        val titleFields = parsingRule.parseTitle(titleSpecif.titleString)
        
        Then("title fields should be correctly extracted")
        
        for( (fieldName,fieldValue) <- titleSpecif.fieldMap ) {
          val extractFieldValueOpt = titleFields.get(fieldName)
          assert( extractFieldValueOpt.isDefined , fieldName + " should be defined" )
          extractFieldValueOpt.get should equal (fieldValue)
        }
         
      }
    }
  }
  
}