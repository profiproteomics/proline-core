package fr.proline.core.om.model.msi

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap
import scala.reflect.BeanProperty

import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.weiglewilczek.slf4s.Logging
import org.apache.commons.lang3.StringUtils.isNotEmpty
import fr.proline.util.misc.InMemoryIdGen

object Peptide extends InMemoryIdGen with Logging {
  
  import scala.collection._
  
  /** Returns a list of LocatedPTM objects for the provided sequence, PTM definition and optional position constraints.
   *  The results contains a list of putative PTMs that may be present or not on the peptide sequence.
   *  To get a list of truly located PTMs one has to provide a list of position constraints.
   */
  def getPutativeLocatedPtms ( sequence: String, ptmDefinition: PtmDefinition,
                               positionConstraints: Option[Array[Boolean]] ): Unit = {
         
    // Define some vars
    val residues = sequence.toCharArray() //sequence.split("") map { _.charAt(0) }
    val nbResidues = residues.length
    val searchedResidue = ptmDefinition.residue
    val precursorDelta = ptmDefinition.precursorDelta
    val tmpLocatedPtms = new ArrayBuffer[LocatedPtm]()
    
    // N-term locations are: Any N-term or Protein N-term
    if( ptmDefinition.location matches """.+N-term""" ) {
      if( searchedResidue == '\0' || searchedResidue == residues(0) ) {
        tmpLocatedPtms += buildLocatedPtm( ptmDefinition, 0, precursorDelta, isNTerm = true )
      }
    }
    // C-term locations are: Any C-term, Protein C-term
    else if( ptmDefinition.location matches """.+C-term""" ) {
      if( searchedResidue == '\0' || searchedResidue == residues.last ) {
        tmpLocatedPtms += buildLocatedPtm( ptmDefinition, -1, precursorDelta, isCTerm = true )
      }
    }
    // No location constraint (location=Anywhere)
    else {
      var seqPos = 1
      for( val residue <- residues ) {
        if( searchedResidue == residue || residue == 'X' )  {
          tmpLocatedPtms += buildLocatedPtm( ptmDefinition, seqPos, precursorDelta )
        }
        seqPos += 1
      }
    }
    
    var locatedPtms: Array[LocatedPtm] = null
    
    // Check if position constraints are provided
    if( positionConstraints != None ) {
      val filteredLocatedPtms = new ArrayBuffer[LocatedPtm]
      
      for( val tmpLocatedPtm <- tmpLocatedPtms ) {
        
        val seqPos = tmpLocatedPtm.seqPosition
        val posConstraint = seqPos match {
          case -1 => positionConstraints.get.last
          case _ => positionConstraints.get(seqPos)
        }
        
        if( posConstraint == true ) filteredLocatedPtms += tmpLocatedPtm
      }
          
      locatedPtms = filteredLocatedPtms.toArray
    }
    else { locatedPtms = tmpLocatedPtms.toArray }
    
    locatedPtms
    
  }
  
  
  private def buildLocatedPtm( ptmDefinition: PtmDefinition, seqPosition: Int, precursorDelta: PtmEvidence,
                               isNTerm: Boolean = false, isCTerm: Boolean = false ): LocatedPtm = {
    new LocatedPtm( definition = ptmDefinition,
                    seqPosition = seqPosition,
                    monoMass = precursorDelta.monoMass,
                    averageMass = precursorDelta.averageMass,
                    composition = precursorDelta.composition,
                    isNTerm = isNTerm,
                    isCTerm = isCTerm
                  )
  }
  
  /** Returns the given list of located PTMs as a string.
   *  Example of PTM string for peptide MENHIR with oxidation (M) and SILAC label (R): 1[O]7[C(-9) 13C(9)] 
   */
  def makePtmString( locatedPtms: List[LocatedPtm] ): String = {
    
    // Return null if no located PTM
    if( locatedPtms.length == 0 ) {
      return ""
      //throw new IllegalArgumentException("can't compute a PTM string using an empty list of located PTMs")
    }
    
    // Sort located PTMs
    val sortedLocatedPtms = locatedPtms.sort { (a,b) => a.seqPosition <= b.seqPosition }
    
    // Define data structure which will contain located PTM strings mapped by sequence position
    val locatedPtmStringBySeqPos = new mutable.HashMap[Int,ArrayBuffer[String]]()
    
    // Iterate over located PTMs
    var lastSeqPos = 1 // will be used to compute a sequence position range
    for( locatedPtm <- sortedLocatedPtms ) {
      
      var seqPos = -2
      if( locatedPtm.isNTerm ) { seqPos = 0 }
      else if( locatedPtm.isCTerm  ) { seqPos = -1 }
      else {
        seqPos = locatedPtm.seqPosition
        lastSeqPos = seqPos
      }
      
      // Define some vars
      val ptmComp = locatedPtm.composition
      val atomModBySymbol = this.computePtmStructure( ptmComp ).atomModBySymbol        
      val atomModStrings = new ArrayBuffer[String]
      
      // Sort atom symbols by ascendant order
      val sortedAtomSymbols = atomModBySymbol.keys.toList.sort { (a,b) => a < b }
      
      // Iterate over atom symbols
      for( val atomSymbol <- sortedAtomSymbols ) {
        
        val atomMod = atomModBySymbol(atomSymbol)
        
        // Sort atom symbols by ascendant order
        val sortedAtomIsotopes = atomMod.keys.toList.sort { (a,b) => a < b }
        
        // Iterate over atom isotopes
        for( val atomIsotope <- sortedAtomIsotopes ) {
          
          val isotopePrefix = if( atomIsotope == 0 ) "" else atomIsotope.toString
          val atomModIsotopeComposition = atomMod(atomIsotope)
          val nbAtoms = atomModIsotopeComposition.quantity
          var atomModString = isotopePrefix + atomSymbol
          
          // Stringify substracted atoms
          if( atomModIsotopeComposition.sign == "-" ) {
            
            atomModString += "(-"+nbAtoms+")"      
            
          // Stringify added atoms
          } else if( atomModIsotopeComposition.sign == "+" ) {
            
            if( nbAtoms > 1 ) atomModString += "("+nbAtoms+")"
            
          } else { throw new Exception("invalid sign of isotope composition") }
          
          atomModStrings += atomModString
        }
      }
      
      if( atomModStrings.length == 0 ) {
        throw new Exception( "a problem has occured during the ptm string construction" )
      }
      
      if( !locatedPtmStringBySeqPos.contains(seqPos) ) {
        locatedPtmStringBySeqPos += seqPos -> new ArrayBuffer[String]()
      }
      
      locatedPtmStringBySeqPos(seqPos) += atomModStrings.mkString(" ")
    }
    
    // Create a list of all possible PTM sequence positions
    val putativeSeqPositions = List(0) ++ (1 to lastSeqPos) ++ List(-1)
    
    // Sort PTMs and merge them into a unique string
    var ptmString = ""
    for( val seqPos <- putativeSeqPositions ) {
      val locatedPtmStrings = locatedPtmStringBySeqPos.get(seqPos)
      if( locatedPtmStrings != None ) {
        ptmString += locatedPtmStrings.get.toList
                                      .sort { (a,b) => a < b }
                                      .map { ptmStr => seqPos + "[" + ptmStr + "]" }
                                      .mkString("")
      }
    }
    
    ptmString
  }
  
  def makePtmString( locatedPtms: Array[LocatedPtm] ): String = {
    locatedPtms match {
      case null => ""
      case _ => Peptide.makePtmString( locatedPtms.toList )
    }
  }
  
  private case class PtmIsotopeComposition( sign: String, quantity: Int )
  private case class PtmStructure( atomModBySymbol: mutable.HashMap[String,mutable.HashMap[Int,PtmIsotopeComposition]] )
  
  private def computePtmStructure( composition: String ): PtmStructure = {
    
    import java.util.regex.Pattern
    
    // EX : SILAC label (R) => "C(-9) 13C(9)"
    val atomMods = composition.split(" ")
    val atomCompositionBySymbol = new mutable.HashMap[String,mutable.HashMap[Int,PtmIsotopeComposition]]()
    
    for( val atomMod <- atomMods ) {
      var( atomSymbol, nbAtoms, atomIsotope, sign ) = ("",0,0,"")
      
      val m = Pattern.compile("""^(\d*)(\w+)(\((-){0,1}(.+)\)){0,1}""").matcher(atomMod)
      if( m.matches ) {
        
        // 0 means most frequent isotope
        atomIsotope = if( isNotEmpty(m.group(1)) ) m.group(1).toInt else 0          
        atomSymbol = m.group(2)
        sign = if( isNotEmpty(m.group(4)) ) m.group(4) else "+"            
        nbAtoms = if( isNotEmpty(m.group(5)) ) m.group(5).toInt else 1
      }
      else { throw new Exception( "can't parse atom composition '"+atomMod+"'" ) }
      
      if( ! atomCompositionBySymbol.contains(atomSymbol) ) {
        atomCompositionBySymbol += atomSymbol -> new mutable.HashMap[Int,PtmIsotopeComposition]()
      }
      
      atomCompositionBySymbol(atomSymbol) += ( atomIsotope -> PtmIsotopeComposition( sign, nbAtoms ) )
      
      //ptmStructure(atomSymbol)(atomIsotope)(modifMode) = { nb_atoms = nbAtoms }
    }
    
    PtmStructure( atomCompositionBySymbol )
    
  }
  
  import org.biojava.bio.BioException
  import org.biojava.bio.proteomics._
  import org.biojava.bio.seq._
  import org.biojava.bio.symbol._
  
  def calcMass( sequence: String, peptidePtms: Array[LocatedPtm] ): Double = {
    
    // Compute peptide sequence mass
    var mass = this.calcMass( sequence )
    if( mass == 0.0 ) return 0.0
    
    // Add peptide PTMs masses
    peptidePtms.foreach { mass += _.monoMass }
    
    mass
  }
  
  def calcMass( sequence: String ): Double = {
    var mass : Double = 0
    
    // FIXME: find another way to deal with ambiguous residues
    import fr.proline.util.regex.RegexUtils._
    
    if( sequence ~~ "(?i)[BXZ]" ) mass = 0.0
    else {
	  val massCalcObject = new MassCalc(SymbolPropertyTable.MONO_MASS, false)
	  massCalcObject.setSymbolModification('U', 150.95363)
      mass = try {
//        new MassCalc(SymbolPropertyTable.MONO_MASS, false).getMass( ProteinTools.createProtein(sequence) )
		massCalcObject.getMass(ProteinTools.createProtein(sequence))
      } catch {
        case e: Exception => Double.NaN
      }
    }
    
    if( mass.isNaN() ) {
      throw new Exception("can't compute peptide mass for sequence="+sequence)
    }
    
    mass
    
  }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Peptide ( // Required fields
                var id: Long,
                val sequence: String,
                val ptmString: String,
                @transient val ptms: Array[LocatedPtm],
                val calculatedMass: Double,
                
                // Mutable optional fields
                var properties: Option[PeptideProperties] = None
                ) {
  
  // Define secondary constructors
  def this( id: Long, sequence: String, ptms: Array[LocatedPtm], calculatedMass: Double ) = {
      this( id, sequence, Peptide.makePtmString( ptms ), ptms, calculatedMass )
  }
  
  def this( sequence: String, ptms: Array[LocatedPtm], calculatedMass: Double ) = {
      this( Peptide.generateNewId(), sequence, Peptide.makePtmString( ptms ), ptms, calculatedMass )
  }
  
  def this( sequence: String, ptms: Array[LocatedPtm], id: Long = Peptide.generateNewId() ) = {
      this( id, sequence, ptms, Peptide.calcMass( sequence, ptms ) )
  }
  
  // Requirements
  require( isNotEmpty( sequence ) && calculatedMass >= 0 )
  
  /** Returns a string representing the peptide PTMs */
  lazy val readablePtmString : String = {
    
    var tmpReadablePtmString : String = null
    if( ptms != null ) {
      
      val ptmStringBuf = new ListBuffer[String]
      
      for( ptm <- ptms ) {
        
        val ptmDef = ptm.definition
        val shortName = ptmDef.names.shortName
        
        var ptmConstraint : String = ""
        if( ptm.isNTerm ) { ptmConstraint = "NTerm" }
        else if( ptm.isCTerm ) { ptmConstraint = "CTerm" }
        else { ptmConstraint = "" + ptmDef.residue + ptm.seqPosition }
        
        val ptmString = "%s (%s)".format(shortName, ptmConstraint)
        ptmStringBuf += ptmString
      }
      
      tmpReadablePtmString = ptmStringBuf.mkString("; ")

    }
    
    tmpReadablePtmString
    
  }
  
  /** Returns a string that can be used as a unique key for this peptide */
  @transient lazy val uniqueKey : String = { 
    if (ptmString != null) 
    	sequence + "%" + ptmString
    else
    	 sequence + "%" 
  }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideProperties

object PeptideMatch extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatch ( // Required fields
                     var id: Long, 
                     var rank: Int,
                     val score: Float,
                     val scoreType: String,
                     val deltaMoz: Float,
                     val isDecoy: Boolean,
                     @transient val peptide: Peptide,
                     
                     // Immutable optional fields
                     val missedCleavage: Int = 0,
                     val fragmentMatchesCount: Int = 0,
                     
                     @transient val msQuery: MsQuery = null, // TODO: require ?
                     
                     // Mutable optional fields
                     var isValidated: Boolean = true, // only defined in the model
                     var resultSetId: Long = 0,
                     
                     var childrenIds: Array[Long] = null,
                     @transient var children: Option[Array[PeptideMatch]] = null,
                     
                     protected var bestChildId: Long = 0,
                     @transient var bestChild : Option[PeptideMatch] = null,
                     
                     var properties: Option[PeptideMatchProperties] = None,
                     
                     @transient var validationProperties : Option[PeptideMatchResultSummaryProperties] = None
                     
                     ) {
  
  // Requirements
  require( rank > 0 )
  //require( scoreType == "mascot" )
  require( peptide != null )
  
  // Define lazy fields (mainly used for serialization purpose)
  lazy val msQueryId = this.msQuery.id
  lazy val peptideId = this.peptide.id
  
  // Related objects ID getters 
  def getChildrenIds : Array[Long] = { if(children != null && children != None) children.get.map(_.id) else childrenIds  }
  
  def getBestChildId : Long = { if(bestChild != null && bestChild != None ) bestChild.get.id else bestChildId }     
  
  /** Returns a MS2 query object. */
  def getMs2Query: Ms2Query = { if(msQuery != null) msQuery.asInstanceOf[Ms2Query] else null }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatchProperties (
  @BeanProperty var mascotProperties: Option[PeptideMatchMascotProperties] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatchMascotProperties (
  @BeanProperty var expectationValue: Double,
  @BeanProperty var readableVarMods: Option[String] = None,
  @BeanProperty var varModsPositions: Option[String] = None,
  @BeanProperty var ambiguityString: Option[String] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideMatchResultSummaryProperties (
  @BeanProperty var mascotScoreOffset: Option[Float] = None,
  @BeanProperty var mascotAdjustedExpectationValue: Option[Double] = None
)
 
object PeptideInstance extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideInstance ( // Required fields
                        var id: Long,
                        @transient val peptide: Peptide,

                        // Immutable optional fields
                        var peptideMatchIds: Array[Long] = null, //One of these 2 values should be specified                        
                        @transient val peptideMatches: Array[PeptideMatch] = null,
                        
                        val children: Array[PeptideInstance] = null,
                        
                        protected val unmodifiedPeptideId: Long = 0,
   
                        @transient val unmodifiedPeptide: Option[Peptide] = null,
                        
                        // Mutable optional fields
                        var proteinMatchesCount: Int = 0,
                        var proteinSetsCount: Int = 0,
                        var validatedProteinSetsCount: Int = 0,
                        var totalLeavesMatchCount: Int = 0,
                        var selectionLevel: Int = 2,
                        var elutionTime: Float = 0,
                        
                        @transient var peptideSets: Array[PeptideSet] = null,
                        var bestPeptideMatchId: Long = 0,
                        var resultSummaryId: Long = 0,
                        
                        var properties: Option[PeptideInstanceProperties] = None,
                        var peptideMatchPropertiesById: Map[Long, PeptideMatchResultSummaryProperties ] = null
                        
                        ) {
  
  // Requirements
  require( peptide != null )
  require( (peptideMatchIds != null || peptideMatches !=null) )
  
  lazy val peptideId = peptide.id
  lazy val peptideMatchesCount = getPeptideMatchIds.length
  
  // Related objects ID getters
  def getPeptideMatchIds : Array[Long] = { if(peptideMatches != null) peptideMatches.map(_.id)  else peptideMatchIds }
  
  def getUnmodifiedPeptideId : Long = { if(unmodifiedPeptide != null && unmodifiedPeptide != None) unmodifiedPeptide.get.id else unmodifiedPeptideId }
  
  def getPeptideMatchProperties( peptideMatchId: Long ): Option[PeptideMatchResultSummaryProperties] = {
    if( peptideMatchPropertiesById != null ) { peptideMatchPropertiesById.get(peptideMatchId) }
    else { None }
  }
  
  /** Returns true if the sequence is specific to a protein set. */
  def isProteinSetSpecific: Boolean = { proteinSetsCount == 1 }
  
  /** Returns true if the sequence is specific to a validated protein set. */
  def isValidProteinSetSpecific: Boolean = { validatedProteinSetsCount == 1 }
  
  /** Returns true if the sequence is specific to a protein match. */
  def isProteinMatchSpecific: Boolean = { proteinMatchesCount == 1 }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideInstanceProperties(
  //@BeanProperty var bestPeptideMatchId: Option[Int] = None
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideSetItem (
                   // Required fields                                  
                   var selectionLevel: Int,
                   @transient val peptideInstance: PeptideInstance,
                   
                   // Immutable optional fields
                   protected val peptideSetId: Long = 0,
                   @transient val peptideSet: Option[PeptideSet] = null,
                   
                   // Mutable optional fields
                   var isBestPeptideSet: Option[Boolean] = None,
                   var resultSummaryId: Long = 0,
                   
                   var properties: Option[PeptideSetItemProperties] = None
                   ) {
  
  lazy val peptideInstanceId = peptideInstance.id
  
  def getPeptideSetId : Long = { if(peptideSet != null && peptideSet != None) peptideSet.get.id else peptideSetId }
   
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideSetItemProperties

object PeptideSet extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideSet ( // Required fields
                   var id: Long,
                   var items: Array[PeptideSetItem],
                   val isSubset: Boolean,
                   val peptideMatchesCount: Int,
                   var proteinMatchIds: Array[Long],
                   
                   // Mutable optional fields
                   protected val proteinSetId: Long = 0,
                   @transient var proteinSet: Option[ProteinSet] = null,
                   
                   var resultSummaryId: Long = 0,
                   var score: Float = 0,
                   var scoreType: String = null,
                   
                   var strictSubsetIds: Array[Long] = null,
                   var strictSubsets: Option[Array[PeptideSet]] = null,
                   
                   var subsumableSubsetIds: Array[Long] = null,
                   var subsumableSubsets: Option[Array[PeptideSet]] = null,
                   
                   var properties: Option[PeptideSetProperties] = None
                   ) {
  
  // Requirements
  require( items != null )
  require( peptideMatchesCount >= items.length )
  
  // Related objects ID getters
  def getProteinSetId: Long = { if(proteinSet != null && proteinSet != None) proteinSet.get.id else proteinSetId }
  
  def getStrictSubsetIds: Array[Long] = { if(strictSubsets != null && strictSubsets != None) strictSubsets.get.map(_.id)  else strictSubsetIds  }
    
  def getSubsumableSubsetIds: Array[Long] = { if(subsumableSubsets != null && subsumableSubsets != None) subsumableSubsets.get.map(_.id)  else subsumableSubsetIds  }
  
  def getPeptideInstances: Array[PeptideInstance] = { items.map( _.peptideInstance ) }
  
  def getPeptideMatchIds: Array[Long] = {
      
    val peptideMatchIdSet = new ArrayBuffer[Long]()  
    for (pepSetItem <- items) {
      peptideMatchIdSet ++= pepSetItem.peptideInstance.getPeptideMatchIds
    }
    
    peptideMatchIdSet.toArray

  }
  
  def getPeptideIds: Array[Long] = this.items.map { _.peptideInstance.peptide.id }
  
  def hasStrictSubset: Boolean = { 
    if( (strictSubsetIds != null && strictSubsetIds.length > 0 ) || 
        (strictSubsets != null && strictSubsets != None) ) true else false
  }
  
  def hasSubsumableSubset: Boolean = { 
    if( (subsumableSubsetIds != null && subsumableSubsetIds.length > 0 ) || 
        (subsumableSubsets != null && subsumableSubsets != None) ) true else false
  }
  
  def hasSubset : Boolean = { if( hasStrictSubset || hasSubsumableSubset ) true else false }
  
//    def getItemByPepInstanceId: Map[Int, PeptideSetItem] = {
//      
//      val tmpItemByPepInstanceId = Map() ++ items.map { item => ( item.getPeptideInstanceId -> item ) }
//      
//      // Alternatives syntax :
//      // Two traversals
//      // val itemByPepInstanceId1 = items.map( item => (item.peptideInstanceId, item) ).toMap
//      
//      // Two traversals
//      //val itemByPepInstanceId2 = Map( items.map( {item => (item.peptideInstanceId, item)} ) : _* )
//      
//      // One traversal but mutable Map
//      //var itemByPepInstanceId3 = new collection.mutable.HashMap[Int, PeptideSetItem]
//      //items foreach { item => itemByPepInstanceId2 put( item.peptideInstanceId, item ) }
//      
//      // One traversal but more verbose
//      //val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,PeptideSetItem]
//      //for( item <- items ) { mapBuilder += ( item.peptideInstanceId -> item ) }
//      //itemByPepInstanceId4 = mapBuilder.result()
//      
//      if( tmpItemByPepInstanceId.size != items.length ) throw new Exception( "duplicated peptide instance id in the list of peptide set items" )
//
//      tmpItemByPepInstanceId
//  
//    }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PeptideSetProperties

