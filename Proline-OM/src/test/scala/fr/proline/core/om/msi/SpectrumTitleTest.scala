package fr.proline.core.om.msi

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.GivenWhenThen
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import fr.proline.core.om.model.msi.SpectrumTitleFields

@RunWith(classOf[JUnitRunner])
class SpectrumTitleTest extends FunSpec with GivenWhenThen with ShouldMatchers {
  
  object SoftNames extends Enumeration {
    val EXTRACT_MSN = Value("extract_msn.exe")
    val MASCOT_DLL = Value("mascot.dll")
    val MASCOT_DISTILLER = Value("Mascot Distiller")
    val MAX_QUANT = Value("MaxQuant")
    val PROTEOME_DISCOVER = Value("Proteome Discoverer")
    val PROTEO_WIZARD_2_0 = Value("ProteoWizard 2.0")
    val PROTEO_WIZARD_2_1 = Value("ProteoWizard 2.1")
    val SPECTRUM_MILL_OR_BRUKER = Value("Spectrum Mill/Bruker")
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
      SoftNames.MASCOT_DLL,
      "the raw file name, the cycles and the times",
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
    )
  )
  
  val specTitleRuleBySoftName = Map(
    SoftNames.EXTRACT_MSN -> SpectrumTitleParsingRule(
      id = 1,
      rawFileNameRegex = Some("""(.+)\.\d+\.\d+\.\d+\.dta"""),
      firstScanRegex = Some(""".+\.(\d+)\.\d+\.\d+\.dta"""),
      lastScanRegex = Some(""".+\.\d+\.(\d+)\.\d+\.dta""")
    ),
    SoftNames.MASCOT_DLL -> SpectrumTitleParsingRule(
      id = 2,
      rawFileNameRegex = Some("""^File: (.+?),"""),
      firstCycleRegex = Some("""Cycle\(s\): (\d+) \(Experiment \d+\)||Cycle\(s\): (\d+), \d+"""),
      lastCycleRegex = Some("""Cycle\(s\): (\d+) \(Experiment \d+\)||Cycle\(s\): \d+, (\d+)"""),
      firstTimeRegex = Some("""Elution: (.+?) to .+? min||.+Elution: (.+?) min"""), // modified
      lastTimeRegex = Some("""Elution: .+? to (.+?) min||.+Elution: (.+?) min""") // modified
    ),
    SoftNames.MASCOT_DISTILLER -> SpectrumTitleParsingRule(
      id = 3,
      rawFileNameRegex = Some("""\[(.+?)\]"""),
      firstCycleRegex = Some("""(\d+?) \(rt="""),
      lastCycleRegex = Some("""- \d+: Scan (\d+?) \(rt="""),
      firstTimeRegex = Some("""\(rt=(\d+?\.\d+?)\)"""),
      lastTimeRegex = Some("""-.+Scan \d+ \(rt=(\d+\.\d+)""")
    ),
    SoftNames.MAX_QUANT -> SpectrumTitleParsingRule(
      id = 4,
      rawFileNameRegex = Some("""^RawFile: (.+?) FinneganScanNumber:"""),
      firstScanRegex = Some("""FinneganScanNumber: (\d+)"""),
      lastScanRegex = Some("""FinneganScanNumber: (\d+)""")
    ),
    SoftNames.PROTEOME_DISCOVER -> SpectrumTitleParsingRule(
      id = 5,
      firstScanRegex = Some("""Spectrum\d+\s+scans:(\d+?),"""),
      lastScanRegex = Some("""Spectrum\d+\s+scans:(\d+?),""")
    ),
    SoftNames.PROTEO_WIZARD_2_0 -> SpectrumTitleParsingRule(
      id = 6,
      firstScanRegex = Some("""scan=(\d+)"""),
      lastScanRegex = Some("""scan=(\d+)""")
    ),
    SoftNames.PROTEO_WIZARD_2_1 -> SpectrumTitleParsingRule(
      id = 7,
      rawFileNameRegex = Some("""(.+)\.\d+\.\d+\.\d+$"""),
      firstScanRegex = Some(""".+\.(\d+)\.\d+\.\d+$"""),
      lastScanRegex = Some(""".+\.\d+\.(\d+)\.\d+$""")
    ),
    SoftNames.SPECTRUM_MILL_OR_BRUKER -> SpectrumTitleParsingRule(
      id = 8,
      firstTimeRegex = Some("""Cmpd.+MSn.+, (\d+\.\d+) min"""),
      lastTimeRegex = Some("""Cmpd.+MSn.+, (\d+\.\d+) min""")
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