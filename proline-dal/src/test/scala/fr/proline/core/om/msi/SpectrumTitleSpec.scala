package fr.proline.core.om.msi

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.GivenWhenThen
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import fr.proline.core.om.model.msi.SpectrumTitleParsingRule
import fr.proline.core.om.model.msi.SpectrumTitleFields
import fr.proline.core.orm.uds.PeaklistSoftware.SoftwareRelease
import fr.proline.core.orm.uds.SpectrumTitleParsingRule.ParsingRule

@RunWith(classOf[JUnitRunner])
class SpectrumTitleSpec extends AnyFunSpec with GivenWhenThen with Matchers {
  
  val specTitleRuleBySoftName = ParsingRule.values().map { parsingRule =>
    parsingRule.getPeaklistSoftware -> SpectrumTitleParsingRule(
      rawFileIdentifierRegex = Option(parsingRule.getRawFileIdentifierRegex),
      firstCycleRegex = Option(parsingRule.getFirstCycleRegex),
      lastCycleRegex = Option(parsingRule.getLastCycleRegex),
      firstScanRegex = Option(parsingRule.getFirstScanRegex),
      lastScanRegex  = Option(parsingRule.getLastScanRegex),
      firstTimeRegex = Option(parsingRule.getFirstTimeRegex),
      lastTimeRegex  = Option(parsingRule.getLastTimeRegex)
    )
  }.toMap
  
  case class SpectrumTitleSpecif(
    softRelease: SoftwareRelease,
    expectedFields: String,
    titleString: String,
    fieldMap: Map[SpectrumTitleFields.Value,String]
  )
  
  val titleSpecifs = List(
    SpectrumTitleSpecif(
      SoftwareRelease.EXTRACT_MSN,
      "the raw file name and one scan number",
      "101811RML4SILAC6DaHCD.3864.3864.3.dta", 
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "101811RML4SILAC6DaHCD",
        SpectrumTitleFields.FIRST_SCAN -> "3864",
        SpectrumTitleFields.LAST_SCAN -> "3864"
      )
    ),
    SpectrumTitleSpecif(
      SoftwareRelease.DATA_ANALYSIS_4_0,
      "one retention time",
      "Cmpd 1063, +MSn(705.8878), 24.12 min", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "24.12",
        SpectrumTitleFields.LAST_TIME -> "24.12"
      )
    ),
    SpectrumTitleSpecif(
      SoftwareRelease.DATA_ANALYSIS_4_1,
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
      SoftwareRelease.MASCOT_DLL,
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
      SoftwareRelease.MASCOT_DISTILLER,
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
      SoftwareRelease.MASCOT_DISTILLER,
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
      SoftwareRelease.PROLINE,
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
      SoftwareRelease.PROTEIN_PILOT,
      "the raw file name and one scan number",
      """Locus:1.1.1.2129.15 File:"20140527UPS25fmol_IDA.wiff""""",
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "20140527UPS25fmol_IDA",
        SpectrumTitleFields.FIRST_CYCLE -> "2129",
        SpectrumTitleFields.LAST_CYCLE -> "2129"
      )
    ),
    SpectrumTitleSpecif(
      SoftwareRelease.PROTEOME_DISCOVER,
      "one scan number",
      "Spectrum1524 scans:2000,",
      Map(
        SpectrumTitleFields.FIRST_SCAN -> "2000",
        SpectrumTitleFields.LAST_SCAN -> "2000"
      )
    ),
    SpectrumTitleSpecif(
      SoftwareRelease.PROTEO_WIZARD_3_0,
      "the raw file name and one scan number",
      """QEAJS131018_18.7211.7211.2 File:"QEAJS131018_18.raw", NativeID:"controllerType=0 controllerNumber=1 scan=7211"""",
      Map(
        SpectrumTitleFields.RAW_FILE_IDENTIFIER -> "QEAJS131018_18",
        SpectrumTitleFields.FIRST_SCAN -> "7211",
        SpectrumTitleFields.LAST_SCAN -> "7211"
      )
    ),
    SpectrumTitleSpecif(
      SoftwareRelease.SPECTRUM_MILL,
      "one retention time",
      "Cmpd 1063, +MSn(705.8878), 24.12 min", 
      Map(
        SpectrumTitleFields.FIRST_TIME -> "24.12",
        SpectrumTitleFields.LAST_TIME -> "24.12"
      )
    )
  )
  
  // Iterate over each spectrum title specification
  for( titleSpecif <- titleSpecifs ) {
    
    val softRelease = titleSpecif.softRelease
    
    describe(softRelease +" spectrum title" ) {
      
      it("should contain "+ titleSpecif.expectedFields) {
        
        Given("the parsing rule")
        val parsingRule = specTitleRuleBySoftName(softRelease)
        
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