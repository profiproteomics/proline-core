package fr.proline.core.om.msi

import org.junit.runner.RunWith
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.junit.JUnitRunner
import org.scalatest.GivenWhenThen
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.codahale.jerkson.Json
import fr.proline.core.om.model.msi._
import fr.profi.util.serialization.ProfiJson

@RunWith(classOf[JUnitRunner])
class SerializationSpec extends FunSpec with GivenWhenThen with ShouldMatchers {
  
  case class SerializationSpecif(
    description: String,
    jerksonDeserializer: Option[String => AnyRef],
    profiDeserializer: Option[String => AnyRef],
    objectData: AnyRef,
    jsonData: String
  )
  
  val ms2Query = Ms2Query(
    id = -1,
    initialId = 1,
    moz = 333.33,
    charge = 3,
    spectrumTitle = "scan id=2",
    spectrumId = 1,
    properties = Some(
      MsQueryProperties(
        targetDbSearch = Some( MsQueryDbSearchProperties(
          candidatePeptidesCount = 100,
          mascotIdentityThreshold = Some(25),
          mascotHomologyThreshold = Some(20)
        ))
      )
    )
  )
  
  val peptide = new Peptide(
    id = -1,
    sequence = "MENHIR",
    ptms = Array(
      LocatedPtm(
        definition = PtmDefinition(
          id = -1,
          location = PtmLocation.ANYWHERE.toString(),
          residue = 'M',
          names = PtmNames("Oxidation","Hydroxylation"),
          ptmEvidences = Array(
            PtmEvidence(
              ionType = IonTypes.Precursor,
              composition = "O",
              monoMass = 16.0,
              averageMass = 16.1
            )
          )
        ),
        seqPosition = 1,
        monoMass = 16.0,
        averageMass = 16.1,
        composition = "O"
      )
    )
  )
  
  val pepMatchProperties = PeptideMatchProperties(
    mascotProperties = Some(
      PeptideMatchMascotProperties(
        expectationValue = 0.001,
        readableVarMods = Some("Oxidation (M)")
      )
    )
  )
  
  val pepMatch = PeptideMatch(
    id = -1,
    rank = 1,
    score = 20,
    scoreType = "mascot:ions score",
    deltaMoz = 0.001f,
    isDecoy = false,
    peptide = peptide,
    msQuery = ms2Query,
    childrenIds = Array(-1L)
  )
  
  val protMatch = ProteinMatch(
    id = -1L,
    accession = "UNKNOWN",
    description = "unknown protein",
    score = 20,
    scoreType = "mascot:standard score",
    seqDatabaseIds = Array.empty[Long],
    geneName = "UNKNOWN",
    sequenceMatches = Array(
      SequenceMatch(
        start = 1,
        end = 6,
        residueBefore = '-',
        residueAfter = '-'
      )
    )
  )
  
  // Note : the used values are not representative of any real case
  val jsonSpecifs = List(
    
    SerializationSpecif(
      "A Ms2Query object with defined properties",
      jerksonDeserializer = Some( jsonData => Json.parse[Ms2Query](jsonData) ),
      profiDeserializer = Some( jsonData => ProfiJson.deserialize[Ms2Query](jsonData) ),
      ms2Query,
      """{"id":-1,"initial_id":1,"moz":333.33,"charge":3,"spectrum_title":"scan id=2","spectrum_id":1,"""+
      """"properties":{"target_db_search":{"candidate_peptides_count":100,"mascot_identity_threshold":25.0,"""+
      """"mascot_homology_threshold":20.0}},"ms_level":2}"""
    ),
    SerializationSpecif(
      "A Peptide object without properties",
      jerksonDeserializer = None,
      profiDeserializer = None,
      peptide,
      """{"id":-1,"sequence":"MENHIR","ptm_string":"1[O]","calculated_mass":814.3806546000001,"readable_ptm_string":"Oxidation (M1)"}"""
    ),
    SerializationSpecif(
      "A PeptideMatch object without properties",
      jerksonDeserializer = None,
      profiDeserializer = None,
      pepMatch,
      """{"id":-1,"rank":1,"score":20.0,"score_type":"mascot:ions score","delta_moz":0.001,"is_decoy":false,"missed_cleavage":0,"""+
      """"fragment_matches_count":0,"is_validated":true,"result_set_id":0,"children_ids":[-1],"best_child_id":0,"ms_query_id":-1,"peptide_id":-1}"""
    ),
    SerializationSpecif(
      "A PeptideMatchProperties object",
      jerksonDeserializer = Some( jsonData => Json.parse[PeptideMatchProperties](jsonData) ),
      profiDeserializer = Some( jsonData => ProfiJson.deserialize[PeptideMatchProperties](jsonData) ),
      pepMatchProperties,
      """{"mascot_properties":{"expectation_value":0.001,"readable_var_mods":"Oxidation (M)"}},"ms_query_id":-1,"peptide_id":-1}"""
    ),
    SerializationSpecif(
      "A ProteinMatch object without properties",
      jerksonDeserializer = None,
      profiDeserializer = None,
      protMatch,
      """{"accession":"UNKNOWN","description":"unknown protein","is_decoy":false,"is_last_bio_sequence":false,"id":-1,"""+
      """"taxon_id":0,"result_set_id":0,"protein_id":0,"seq_database_ids":[],"gene_name":"UNKNOWN","score":20.0,"""+
      """"score_type":"mascot:standard score","coverage":0.0,"peptide_matches_count":0,"sequence_matches":"""+
      """[{"start":1,"end":6,"residue_before":"-","residue_after":"-","is_decoy":false,"result_set_id":0,"""+
      """"peptide_id":0,"best_peptide_match_id":0}],"peptides_count":1}"""
    )
  )
  
  for( jsonSpecif <- jsonSpecifs ) {
    println( Json.generate(jsonSpecif.objectData) )
    println( ProfiJson.serialize(jsonSpecif.objectData) )
  }
  
  // Iterate over each sperialization specification
  for( jsonSpecif <- jsonSpecifs; if 1 == 1 ) {
    
    describe(jsonSpecif.description) {
      
      it("should be correctly serialized to JSON with Jerkson") {
        
        Given("the object data")
        val objectData = jsonSpecif.objectData
        
        When("serializing to JSON and parsing it as a Map[String,Any]")
        val jsonString = Json.generate(objectData)
        val jsonAsMap = Json.parse[Map[String,Any]](jsonString)
        
        Then("it should match the Map obtained from the expected JSON string")
        jsonAsMap should equal (Json.parse[Map[String,Any]](jsonSpecif.jsonData))         
      }
      
      if( jsonSpecif.jerksonDeserializer.isDefined ) {
        it("should be correctly deserialized from JSON with Jerkson") {
          
          Given("the JSON data")
          val jsonData = jsonSpecif.jsonData
          
          When("deserializing from JSON")
          val objectData = jsonSpecif.jerksonDeserializer.get(jsonData)
          
          Then("the obtained object should match the serialized one")
          objectData should equal (jsonSpecif.objectData)
        }
      }
      
      it("should be correctly serialized to JSON with the ProFI serializer") {
        
        Given("the object data")
        val objectData = jsonSpecif.objectData
        
        When("serializing to JSON and parsing it as a Map[String,Any]")
        val jsonString = ProfiJson.serialize(objectData)
        val jsonAsMap = ProfiJson.deserialize[Map[String,Any]](jsonString)
        
        Then("it should match the Map obtained from the expected JSON string")
        jsonAsMap should equal ( ProfiJson.deserialize[Map[String,Any]](jsonSpecif.jsonData) )         
      }
      
      if( jsonSpecif.profiDeserializer.isDefined ) {
        it("should be correctly deserialized from JSON with the ProFI deserializer") {
          
          Given("the JSON data")
          val jsonData = jsonSpecif.jsonData
          
          When("deserializing from JSON")
          val objectData = jsonSpecif.profiDeserializer.get(jsonData)
          
          Then("the obtained object should match the serialized one")
          objectData should equal (jsonSpecif.objectData)
        }
      }
    }
  }
  
}