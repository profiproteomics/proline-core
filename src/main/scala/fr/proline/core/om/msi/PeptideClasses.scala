package fr.proline.core.om.msi

package PeptideClasses {
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.ListBuffer
  import scala.collection.mutable.HashMap
  import org.apache.commons.lang3.StringUtils
  import fr.proline.core.om.helper.MiscUtils.InMemoryIdGen
  import fr.proline.core.om.msi.MsQueryClasses.Ms2Query
  import fr.proline.core.om.msi.MsQueryClasses.MsQuery
  import fr.proline.core.om.msi.PtmClasses._
  import fr.proline.core.om.msi.ResultSetClasses.ResultSet
  import fr.proline.core.om.msi.ResultSetClasses.ResultSummary
  import fr.proline.core.om.msi.ProteinClasses.ProteinMatch
  import fr.proline.core.om.msi.ProteinClasses.ProteinSet
  
  object Peptide extends InMemoryIdGen {
    
    import scala.collection._
    
    /** Returns a list of LocatedPTM objects for the provided sequence, PTM definition and optional position constraints.
     *  The results contains a list of putative PTMs that may be present or not on the peptide sequence.
     *  To get a list of truly located PTMs one has to provide a list of position constraints.
     */
    def getPutativeLocatedPtms ( sequence: String, ptmDefinition: PtmDefinition,
                                 positionConstraints: Option[Array[Boolean]] ): Unit = {
           
      // Define some vars
      val residues = sequence.split("") map { _.charAt(0) }
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
        throw new IllegalArgumentException("can't compute a PTM string using an empty list of located PTMs")
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
    
    private case class PtmIsotopeComposition( sign: String, quantity: Int )
    private case class PtmStructure( atomModBySymbol: mutable.HashMap[String,mutable.HashMap[Int,PtmIsotopeComposition]] )
    
    private def computePtmStructure( composition: String ): PtmStructure = {
      
      import java.util.regex.Pattern
      import org.apache.commons.lang3.StringUtils.isNotEmpty
      
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

  }
  
  class Peptide ( // Required fields
                  var id: Int,
                  val sequence: String,
                  val ptmString: String,
                  val ptms: Array[LocatedPtm],
                  val calculatedMass: Double,
                  
                  // Mutable optional fields
                  var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                  ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( sequence ) && calculatedMass > 0 )
    
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
    lazy val uniqueKey : String = sequence + "%" + ptmString
    
  }
  
  object PeptideMatch extends InMemoryIdGen {
    
  }
  class PeptideMatch ( // Required fields
                       var id: Int, 
                       val rank: Int,
                       val score: Float,
                       val scoreType: String,
                       val deltaMoz: Double,
                       val isDecoy: Boolean,
                       val peptide: Peptide,
                       
                       // Immutable optional fields
                       val missedCleavage: Int = 0,
                       val fragmentMatchesCount: Int = 0,
                       
                       val msQuery: MsQuery = null, // TODO: require ?
                       
                       // Mutable optional fields
                       var isValidated: Boolean = false, // only defined in the model
                       var resultSetId: Int = 0,    
                       
                       private var childrenIds: Array[Int] = null,
                       var children: Option[Array[PeptideMatch]] = null,
                       
                       private var bestChildId: Int = 0,
                       var bestChild : Option[PeptideMatch] = null,                                         
                       
                       var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                       ) {
    
    // Requirements
    require( rank > 0 )
    //require( scoreType == "mascot" )
    require( peptide != null )
    
    // Related objects ID getters    
   
    def getChildrenIds : Array[Int] = { if(children != null && children != None) children.get.map(_.id) else childrenIds  }
    
    def getBestChildId : Int = { if(bestChild != null && bestChild != None ) bestChild.get.id else bestChildId }     
    
    /** Returns a MS2 query object. */
    def getMs2Query: Ms2Query = { if(msQuery != null) msQuery.asInstanceOf[Ms2Query] else null }
    
  }
 
  class PeptideInstance ( // Required fields
                          var id: Int,
                          val peptide: Peptide,

                          // Immutable optional fields
                          private val peptideMatchIds: Array[Int] = null, //One of these 2 values should be specified
                          val peptideMatches: Array[PeptideMatch] = null,
                          
                          val children: Array[PeptideInstance] = null,                         
                          
                          private val unmodifiedPeptideId: Int = 0,
                          val unmodifiedPeptide: Option[Peptide] = null,
                          
                          private val unmodifiedPepInstanceId: Int = 0,
                          val unmodifiedPepInstance: Option[PeptideInstance] = null,
                          
                          // Mutable optional fields
                          var proteinMatchesCount: Int = 0,
                          var proteinSetsCount: Int = 0,
                          var selectionLevel: Int = -1,
                          
                          var peptideSets: Array[PeptideSet] = null,
                          private var resultSummaryId: Int = 0,                          
                          
                          var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                          
                          ) {
    
    // Requirements
    require( peptide != null )
    require( (peptideMatchIds != null || peptideMatches !=null) ) 
    
    // Related objects ID getters
    def getPeptideMatchIds : Array[Int] = { if(peptideMatches != null) peptideMatches.map(_.id)  else peptideMatchIds  }
    
    def getUnmodifiedPeptideId : Int = { if(unmodifiedPeptide != null && unmodifiedPeptide != None) unmodifiedPeptide.get.id else unmodifiedPeptideId }
    
    def getUnmodifiedPeptideInstanceId : Int = { if(unmodifiedPepInstance != null && unmodifiedPepInstance != None) unmodifiedPepInstance.get.id else unmodifiedPepInstanceId }
    
    
    /** Returns a true if the sequence is specific to a protein set. */
    def isProteinSetSpecific: Boolean = { proteinSetsCount == 1 }
    
    /** Returns a true if the sequence is specific to a protein match. */
    def isProteinMatchSpecific: Boolean = { proteinMatchesCount == 1 }
    
    /** Returns a true if the sequence is specific to a protein match. */
    def getPeptideMatchesCount: Int = {  peptideMatchIds.length }
  
  }
  
  class PeptideSetItem (
                     // Required fields
                     var id: Int,
                     var isBestPeptideSet: Boolean,
                     var selectionLevel: Int,
                     var peptideSet: PeptideSet,                                         
                     var peptideInstance: PeptideInstance,
                     
                     // Mutable optional fields
                     private var resultSummaryId: Int = 0,
                     
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
    
     require( peptideSet != null  && peptideInstance!= null)
  
  }
  
  class PeptideSet ( // Required fields
                     var id: Int,
                     val items: Array[PeptideSetItem],
                     val peptideMatchesCount: Int,
                     val isSubset: Boolean,
                     
                     // Immutable optional fields                                                              
                     private val resultSummaryId: Int = 0,
                     
                     // Mutable optional fields
                     val proteinSet: ProteinSet = null,
                     
                     private var strictSubsetIds: Array[Int] = null,
                     var strictSubsets: Option[Array[PeptideSet]] = null,
                     
                     private var subsumableSubsetIds: Array[Int] = null,
                     var subsumableSubsets: Option[Array[PeptideSet]] = null,
                     
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
    
    // Requirements
    require( items != null )
    require( peptideMatchesCount >= items.length )
    
    // Related objects ID getters    
    def getStrictSubsetIds : Array[Int] = { if(strictSubsets != null && strictSubsets != None) strictSubsets.get.map(_.id)  else strictSubsetIds  }
      
    def getSubsumableSubsetIds : Array[Int] = { if(subsumableSubsets != null && subsumableSubsets != None) subsumableSubsets.get.map(_.id)  else subsumableSubsetIds  }
    
    def getPeptideMatchIds: Array[Int] = {
        
      val peptideMatchIds = new ArrayBuffer[Int]()  
      for (pepSet <- items) {
        for (pepMatchId <- pepSet.peptideInstance.getPeptideMatchIds) {
        	if(!peptideMatchIds.contains(pepMatchId))
        		peptideMatchIds += pepMatchId
        }
      }
      
      peptideMatchIds.toArray
  
    }
    
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

}