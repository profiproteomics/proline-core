package fr.proline.core.om.msi

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.GivenWhenThen
import org.scalatest.FunSpec
import org.scalatest.Matchers
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import fr.proline.core.om.model.msi.SpectrumTitleFields

@RunWith(classOf[JUnitRunner])
class SpectrumTitleSpec extends FunSpec with GivenWhenThen with Matchers {
  
  object SoftNames extends Enumeration {
    val EXTRACT_MSN = Value("extract_msn.exe")
    val DATA_ANALYSIS_4_0 = Value("Data Analysis 4.0")
    val DATA_ANALYSIS_4_1 = Value("Data Analysis 4.1")
    val MASCOT_DLL = Value("mascot.dll")
    val MASCOT_DISTILLER = Value("Mascot Distiller")
    val MAX_QUANT = Value("MaxQuant")
    val PROLINE = Value("Proline")
    val PROTEIN_PILOT = Value("Protein Pilot")
    val PROTEOME_DISCOVER = Value("Proteome Discoverer")
    val PROTEO_WIZARD_2_0 = Value("ProteoWizard 2.0")
    val PROTEO_WIZARD_2_1 = Value("ProteoWizard 2.1")
    val PROTEO_WIZARD_3_0 = Value("ProteoWizard 3.0")
    val SPECTRUM_MILL = Value("Spectrum Mill")
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
      "the raw file name and one scan number",
      "101811RML4SILAC6DaHCD.3864.3864.3.dta", 
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "101811RML4SILAC6DaHCD",
        SpectrumTitleFields.FIRST_SCAN -> "3864",
        SpectrumTitleFields.LAST_SCAN -> "3864"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.DATA_ANALYSIS_4_0,
      "one retention time",
      "Cmpd 1063, +MSn(705.8878), 24.12 min", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "24.12",
        SpectrumTitleFields.LAST_TIME -> "24.12"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.DATA_ANALYSIS_4_1,
      "one retention time and one scan number",
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
      "the raw file name, one cycle and one retention time",
      "File: QSAG051130001.wiff, Sample: bandeA1 (sample number 1), Elution: 92.3 min, Period: 1, Cycle(s): 2040 (Experiment 2)", 
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "QSAG051130001",
        SpectrumTitleFields.FIRST_CYCLE -> "2040",
        SpectrumTitleFields.LAST_CYCLE -> "2040",
        SpectrumTitleFields.FIRST_TIME -> "92.3",
        SpectrumTitleFields.LAST_TIME -> "92.3"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.MASCOT_DISTILLER,
      "the raw file name, one scan and one retention time",
      """"9272: Scan 13104 (rt=30.2521) [D:\MSData\All\Qex1_000949.raw]""", 
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> """Qex1_000949""",
        SpectrumTitleFields.FIRST_SCAN -> "13104",
        SpectrumTitleFields.LAST_SCAN -> "13104",
        SpectrumTitleFields.FIRST_TIME -> "30.2521",
        SpectrumTitleFields.LAST_TIME -> "30.2521"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.MASCOT_DISTILLER,
      "the raw file name, the scans and the retention times",
      """"134: Sum of 3 scans in range 723 (rt=19.5118) to 733 (rt=19.6302) [\\DSV_D01\CPManip\analyses\AMT_Process12\CAJU1269.RAW]""", 
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> """CAJU1269""",
        SpectrumTitleFields.FIRST_SCAN -> "723",
        SpectrumTitleFields.LAST_SCAN -> "733",
        SpectrumTitleFields.FIRST_TIME -> "19.5118",
        SpectrumTitleFields.LAST_TIME -> "19.6302"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.PROLINE,
      "the raw file name, the scans and the retention times",
      """"first_scan:12669;last_scan:12669;first_time:47.4957;last_time:47.4957;raw_file_identifier:QEMMA140604_106;raw_precursor_moz:582.7711;"""""",
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> """QEMMA140604_106""",
        SpectrumTitleFields.FIRST_SCAN -> "12669",
        SpectrumTitleFields.LAST_SCAN -> "12669",
        SpectrumTitleFields.FIRST_TIME -> "47.4957",
        SpectrumTitleFields.LAST_TIME -> "47.4957"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.PROTEIN_PILOT,
      "the raw file name and one scan number",
      """Locus:1.1.1.2129.15 File:"20140527UPS25fmol_IDA.wiff""""",
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "20140527UPS25fmol_IDA",
        SpectrumTitleFields.FIRST_CYCLE -> "2129",
        SpectrumTitleFields.LAST_CYCLE -> "2129"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.PROTEOME_DISCOVER,
      "one scan number",
      "Spectrum1524 scans:2000,",
      Map(
        SpectrumTitleFields.FIRST_SCAN -> "2000",
        SpectrumTitleFields.LAST_SCAN -> "2000"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.PROTEO_WIZARD_3_0,
      "the raw file name and one scan number",
      """QEAJS131018_18.7211.7211.2 File:"QEAJS131018_18.raw", NativeID:"controllerType=0 controllerNumber=1 scan=7211"""",
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "QEAJS131018_18",
        SpectrumTitleFields.FIRST_SCAN -> "7211",
        SpectrumTitleFields.LAST_SCAN -> "7211"
      )
    ),
    SpectrumTitleSpecif(
      SoftNames.SPECTRUM_MILL,
      "one retention time",
      "Cmpd 1063, +MSn(705.8878), 24.12 min", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "24.12",
        SpectrumTitleFields.LAST_TIME -> "24.12"
      )
    )
  )
  
  val specTitleRuleBySoftName = Map(
    SoftNames.EXTRACT_MSN -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Some("""(.+)\.\d+\.\d+\.\d+\.dta"""),
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
      rawFileIdentifierRegex = Some("""^File: (.+?)\.wiff,"""),
      firstCycleRegex        = Some("""Cycle\(s\): (\d+) \(Experiment \d+\)||Cycle\(s\): (\d+), \d+"""),
      lastCycleRegex         = Some("""Cycle\(s\): (\d+) \(Experiment \d+\)||Cycle\(s\): \d+, (\d+)"""),
      firstTimeRegex         = Some("""Elution: (.+?) to .+? min||.+Elution: (.+?) min"""), // modified
      lastTimeRegex          = Some("""Elution: .+? to (.+?) min||.+Elution: (.+?) min""") // modified
    ),
    /*SoftNames.MASCOT_DISTILLER -> SpectrumTitleParsingRule(
      rawFileNameRegex = Some("""\[(.+?)\]"""),
      firstCycleRegex  = Some("""(\d+?) \(rt="""),
      lastCycleRegex   = Some("""- \d+: Scan (\d+?) \(rt="""),
      firstTimeRegex   = Some("""\(rt=(\d+?\.\d+?)\)"""),
      lastTimeRegex    = Some("""-.+Scan \d+ \(rt=(\d+\.\d+)""")
    ),*/
    SoftNames.MASCOT_DISTILLER -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Some("""\[.+\\(.+?)\..+\]"""),
      firstScanRegex         = Some("""in range (\d+) \(rt=||Scan (\d+) \(rt="""),
      lastScanRegex          = Some("""\) to (\d+) \(rt=||Scan (\d+) \(rt="""),
      firstTimeRegex         = Some("""in range \d+ \(rt=(\d+.\d+)\)||\(rt=(\d+.\d+)\)"""),
      lastTimeRegex          = Some("""\) to \d+ \(rt=(\d+.\d+)\)||\(rt=(\d+.\d+)\)""")
    ),
    SoftNames.MAX_QUANT -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Some("""^RawFile: (.+?) FinneganScanNumber:"""),
      firstScanRegex         = Some("""FinneganScanNumber: (\d+)"""),
      lastScanRegex          = Some("""FinneganScanNumber: (\d+)""")
    ),
    SoftNames.PROTEIN_PILOT -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Some("""File:"(\w+)\.wiff""""),
      firstCycleRegex         = Some("""Locus:\d\.\d\.\d\.(\d+)\.\d+ File:"""),
      lastCycleRegex          = Some("""Locus:\d\.\d\.\d\.(\d+)\.\d+ File:""")
    ),
    SoftNames.PROLINE -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Some("""raw_file_identifier:(\w+?);"""),
      firstCycleRegex         = Some("""first_cycle:(\d+);"""),
      lastCycleRegex          = Some("""last_cycle:(\d+);"""),
      firstScanRegex         = Some("""first_scan:(\d+);"""),
      lastScanRegex          = Some("""last_scan:(\d+);"""),
      firstTimeRegex         = Some("""first_time:(\d+\.\d+);"""),
      lastTimeRegex          = Some("""last_time:(\d+\.\d+);""")
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
      rawFileIdentifierRegex = Some("""(.+)\.\d+\.\d+\.\d+$"""),
      firstScanRegex         = Some(""".+\.(\d+)\.\d+\.\d+$"""),
      lastScanRegex          = Some(""".+\.\d+\.(\d+)\.\d+$""")
    ),
    SoftNames.PROTEO_WIZARD_3_0 -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Some("""File:"(.+?)\..+","""),
      firstScanRegex   = Some("""scan=(\d+)"""),
      lastScanRegex    = Some("""scan=(\d+)""")
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