package fr.proline.core.om.util

import javax.persistence.EntityManager

import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.core.om.model.msi._
import fr.proline.core.orm.msi.repository.{ ProteinSetRepositorty => MsiProSetRepo }
import fr.proline.core.orm.util.DataStoreConnectorFactory
import fr.proline.core.util.ResidueUtils._

/**
 * Provides method to convert Peptides and PTM objects from ORM to OM.
 * If specified in constructor, created objects will be stored in map( referenced by their ID) to be retrieve later if necessary.
 *
 * @author VD225637
 *
 */
class PeptidesOMConverterUtil(useCachedObject: Boolean = true) {

  val peptideInstancesCache = new HashMap[Long, fr.proline.core.om.model.msi.PeptideInstance]
  val peptideMatchesCache = new HashMap[Long, fr.proline.core.om.model.msi.PeptideMatch]
  val peptidesCache = new HashMap[Long, fr.proline.core.om.model.msi.Peptide]
  val locatedPTMsCache = new HashMap[Long, LocatedPtm]
  val ptmNamesCache = new HashMap[String, PtmNames]
  val ptmDefinitionsCache = new HashMap[Long, PtmDefinition]
  val peptideSetsCache = new HashMap[Long, fr.proline.core.om.model.msi.PeptideSet]

  type MsiPeptideMatch = fr.proline.core.orm.msi.PeptideMatch
  type MsiPeptideInstance = fr.proline.core.orm.msi.PeptideInstance
  type MsiPeptideSet = fr.proline.core.orm.msi.PeptideSet
  type MsiSeqDatabase = fr.proline.core.orm.msi.SeqDatabase
  type MsiPeptide = fr.proline.core.orm.msi.Peptide
  type MsiPeptidePtm = fr.proline.core.orm.msi.PeptidePtm
  type MsiPtmSpecificity = fr.proline.core.orm.msi.PtmSpecificity
  
  val msiPrecursorType = fr.proline.core.orm.msi.PtmEvidence.Type.Precursor

  //implicit def javaIntToScalaInt(javaInt: java.lang.Integer) = javaInt.intValue

  def convertPeptideMatchORM2OM(msiPepMatch: MsiPeptideMatch): PeptideMatch = {

    //Verify if object is in cache 
    if (useCachedObject && peptideMatchesCache.contains(msiPepMatch.getId())) {
      peptideMatchesCache(msiPepMatch.getId())
    }

    null
  }

  /**
   * Create a OM PeptideInstance corresponding to the specified ORM PeptideInstance.
   *
   * @param pepInstORM  ORM PeptideInstance to create the OM PeptodeInstance for
   * @param loadPepMatches specify if associated OM PeptideMatch should be created or not.
   * @param msiEM EntityManager to the MSIdb the data are issued from
   * @return an OM PeptideInstance corresponding to specified ORM PeptideInstance.
   */
  def convertPeptideInstanceORM2OM(
    msiPepInst: MsiPeptideInstance,
    loadPepMatches: Boolean,
    msiEM: EntityManager
  ): PeptideInstance = {

    //Verify if object is in cache
    if (useCachedObject && peptideInstancesCache.contains(msiPepInst.getId())) {
      return peptideInstancesCache(msiPepInst.getId())
    }

    //Objects to access data in repositories    
    //val prolineRepo = ProlineRepository.getProlineRepositoryInstance()
    
    // FIXME this code is NOT Thread / Context safe
    
    val dataStoreConnectorFactory = DataStoreConnectorFactory.getInstance

    //Found PeptideInstance Children mapped by their id
    val pepInstChildById = new HashMap[Long, PeptideInstance]()

    //---- Peptide Matches Arrays 
    val msiPepMatches = msiPepInst.getPeptideInstancePeptideMatchMaps.map { _.getPeptideMatch } //ORM PeptideMatches

    var pepMatches: Array[PeptideMatch] = null //OM PeptideMatches. Get only if asked for 
    if (loadPepMatches) pepMatches = new Array[PeptideMatch](msiPepMatches.size)

    val pepMatchIds = new Array[Long](msiPepMatches.size) //OM PeptideMatches ids

    //**** Create PeptideMatches array and PeptideInstance Children    
    var index = 0
    for (nextMsiPM <- msiPepMatches) {

      //Go through PeptideMatch children and get associated PeptideInstance 
      val msiPepMatchChildIT = nextMsiPM.getChildren().iterator()
      while (msiPepMatchChildIT.hasNext()) {

        val nextMsiPMChild = msiPepMatchChildIT.next()
        val msiPepInstChild = MsiProSetRepo.findPeptideInstanceForPepMatch(msiEM, nextMsiPMChild.getId)

        if (!pepInstChildById.contains(msiPepInstChild.getId)) {
          //Convert child ORM Peptide Instance to OM Peptide Instance
          pepInstChildById += msiPepInstChild.getId -> convertPeptideInstanceORM2OM(msiPepInstChild, loadPepMatches, msiEM)
        }
      }

      // Fill Peptide Matches Arrays
      pepMatchIds(index) = nextMsiPM.getId()

      if (loadPepMatches) pepMatches(index) = convertPeptideMatchORM2OM(nextMsiPM)

      index += 1
    }

    //Create Peptide Instance Child Arrays
    //val pepInstChildren = new Array[PeptideInstance](pepInstChildById.size)
    val pepInstChildren = pepInstChildById.values.toArray

    //Get Peptide, Unmodified Peptide && PeptideInstance 
    val msiPeptide = msiPepInst.getPeptide()
    val msiUnmodifiedPep = msiEM.find(classOf[MsiPeptide], msiPepInst.getUnmodifiedPeptideId())
    val unmodifiedPep = if (msiUnmodifiedPep == null) None else Some(convertPeptideORM2OM(msiUnmodifiedPep))

    val msiUnmodifiedPepInst = MsiProSetRepo.findPeptideInstanceForPeptide(msiEM, msiPepInst.getUnmodifiedPeptideId())
    val unmodifiedPepInst = if (msiUnmodifiedPepInst == null) None
    else Some(convertPeptideInstanceORM2OM(msiUnmodifiedPepInst, loadPepMatches, msiEM))

    //Create OM PeptideInstance 
    val convertedPepInst = new PeptideInstance(
      id = msiPepInst.getId(),
      peptide = convertPeptideORM2OM(msiPeptide),
      //peptideMatchIds = pepMatchIds,
      peptideMatches = pepMatches,
      children = pepInstChildren,
      //unmodifiedPeptideId = if( unmodifiedPep.isEmpty ) 0 else unmodifiedPep.id,
      unmodifiedPeptide = unmodifiedPep,
      proteinMatchesCount = msiPepInst.getProteinMatchCount(),
      proteinSetsCount = msiPepInst.getProteinSetCount(),
      validatedProteinSetsCount=msiPepInst.getValidatedProteinSetCount(),  
      totalLeavesMatchCount=msiPepInst.getTotalLeavesMatchCount(),
      selectionLevel = msiPepInst.getSelectionLevel(),
      elutionTime = msiPepInst.getElutionTime().floatValue(),
      bestPeptideMatchId = msiPepInst.getBestPeptideMatchId(),
      masterQuantComponentId = Option( msiPepInst.getMasterQuantComponentId() ).map(_.longValue).getOrElse(0L),
      resultSummaryId = msiPepInst.getResultSummary().getId()
    )
    if (useCachedObject) peptideInstancesCache.put(msiPepInst.getId(), convertedPepInst)

    //*** Create PeptideSets for current PeptideInstance    
    val pepInstanceById = new HashMap[Long, PeptideInstance]()

    val msiPepSetItemIT = msiPepInst.getPeptideSetPeptideInstanceItems().iterator()
    val pepSetById = new HashMap[Long, PeptideSet]()

    while (msiPepSetItemIT.hasNext()) {
      val msiPepSetItem = msiPepSetItemIT.next()

      if (!pepSetById.contains(msiPepSetItem.getPeptideSet().getId)) {
        val pepSet = convertPepSetORM2OM(msiPepSetItem.getPeptideSet(), loadPepMatches, msiEM)
        pepSetById += (msiPepSetItem.getPeptideSet().getId -> pepSet)
      } else {
        //VDS: Should not happen : PeptideInstance only once in each PeptideSet ! 
        throw new Exception("PeptideInstance should be unique inside a PeptideSet !")
      }
    }

    // Update peptideSets attribute
    convertedPepInst.peptideSets = pepSetById.values.toArray

    convertedPepInst
  }

  /**
   *  Create OM PeptideSet from ORM PeptideSet and associated objects :
   *  - PeptideSetItem and PeptideInstance (with PeptideMatch or not depending on getPepMatchForNewPepInst value)
   *
   * @param pepSetORM
   * @return
   */
  def convertPepSetORM2OM(msiPepSet: MsiPeptideSet, loadPepMatches: Boolean, msiEM: EntityManager): PeptideSet = {

    //Verify if exist in cache
    if (useCachedObject && peptideSetsCache.contains(msiPepSet.getId))
      return peptideSetsCache(msiPepSet.getId)

    val msiProtMatches = msiPepSet.getProteinMatches()
    val protMatchesIds = new Array[Long](msiProtMatches.size)
    val msiProtMatchesIter = msiProtMatches.iterator()

    var index = 0
    while (msiProtMatchesIter.hasNext()) {
      protMatchesIds(index) = msiProtMatchesIter.next().getId()
      index += 1
    }

    val msiPepSetItems = msiPepSet.getPeptideSetPeptideInstanceItems()
    val msiPepSetItemIT = msiPepSetItems.iterator()
    val pepSetItems = new Array[PeptideSetItem](msiPepSetItems.size)

    index = 0
    while (msiPepSetItemIT.hasNext()) {
      val msiPepSetItem = msiPepSetItemIT.next()

      val pepSetItem = new PeptideSetItem(
        selectionLevel = msiPepSetItem.getSelectionLevel(),
        peptideInstance = convertPeptideInstanceORM2OM(msiPepSetItem.getPeptideInstance(), loadPepMatches, msiEM),
        peptideSetId = msiPepSet.getId(),
        isBestPeptideSet = Some(msiPepSetItem.getIsBestPeptideSet()),
        resultSummaryId = msiPepSet.getResultSummaryId())
      pepSetItems(index) = pepSetItem
      index += 1
    }
    
    val pepSet = new PeptideSet(
      id = msiPepSet.getId(),
      items = pepSetItems,
      isSubset = msiPepSet.getIsSubset(),
      score = msiPepSet.getScore(),
      scoreType = msiPepSet.getScoring().getName(),
      sequencesCount = msiPepSet.getSequenceCount(),
      peptideMatchesCount = msiPepSet.getPeptideMatchCount(),
      proteinMatchIds = protMatchesIds,
      proteinSetId = msiPepSet.getProteinSet().getId(),
      resultSummaryId = msiPepSet.getResultSummaryId()
    )

    if (useCachedObject) peptideSetsCache += pepSet.id -> pepSet

    return pepSet
  }

  def convertPeptideORM2OM(msiPeptide: MsiPeptide): Peptide = {

    // Check if object is in cache 
    if (useCachedObject && peptidesCache.contains(msiPeptide.getId)) {
      return peptidesCache(msiPeptide.getId)
    }

    // **** Create OM LocatedPtm for specified Peptide
    val msiPtms = msiPeptide.getPtms()
    val locatedPtms = if (msiPtms == null) {
      new Array[LocatedPtm](0)
    } else {
      val ptmArray = new Array[LocatedPtm](msiPtms.size())
      val msiPepPtmIt = msiPtms.iterator()

      var index = 0
      while (msiPepPtmIt.hasNext()) {
        ptmArray(index) = convertPeptidePtmORM2OM(msiPepPtmIt.next())
        index += 1
      }

      ptmArray
    }
    
    if (locatedPtms.isEmpty && msiPeptide.getPtmString() != null && msiPeptide.getPtmString().isEmpty() == false) {
      println("missing ptms: "+msiPeptide.getId + " "+msiPeptide.getSequence + " " +msiPeptide.getPtmString())
    }

    // **** Create OM Peptide
    val peptide = new Peptide(
      id = msiPeptide.getId,
      sequence = msiPeptide.getSequence(),
      ptmString = msiPeptide.getPtmString(),
      ptms = locatedPtms,
      calculatedMass = msiPeptide.getCalculatedMass(),
      properties = null
    )
    
    if (useCachedObject) peptidesCache.put(peptide.id, peptide)

    peptide
  }

  /**
   *  Convert from fr.proline.core.orm.msi.PeptidePtm (ORM) to fr.proline.core.om.model.msi.LocatedPtm (OM).
   *
   * LocatedPtm, PtmDefinition, PtmEvidence and PtmNames will be created from specified
   * PeptidePtm and associated PtmSpecificity, PtmEvidence and Ptm
   *
   *
   * @param msiPeptidePtm : fr.proline.core.orm.msi.PeptidePtm to convert
   * @return created LocatedPtm (with associated objects)
   */
  def convertPeptidePtmORM2OM(msiPeptidePtm: MsiPeptidePtm): LocatedPtm = {

    import fr.profi.util.regex.RegexUtils._

    // Check if object is in cache 
    if (useCachedObject && locatedPTMsCache.contains(msiPeptidePtm.getId)) {
      return locatedPTMsCache(msiPeptidePtm.getId)
    }

    var precursorEvidence: PtmEvidence = null
    val msiPtmEvidencesIt = msiPeptidePtm.getSpecificity().getPtm().getEvidences().iterator()

    while (msiPtmEvidencesIt.hasNext() && precursorEvidence == null) {
      val msiPtmEvidence = msiPtmEvidencesIt.next()
      if (msiPtmEvidence.getType() == msiPrecursorType) {
        precursorEvidence = new PtmEvidence(
          IonTypes.Precursor,
          msiPtmEvidence.getComposition(),
          msiPtmEvidence.getMonoMass(),
          msiPtmEvidence.getAverageMass(),
          msiPtmEvidence.getIsRequired())
      }
    }

    // Create OM PtmDefinition from ORM PtmSpecificity
    val ptmDefinition = convertPtmSpecificityORM2OM(msiPeptidePtm.getSpecificity())

    //Create OM LocatedPtm from ORM PeptidePtm
    val locatedPtm = new LocatedPtm(
      definition = ptmDefinition,
      seqPosition = msiPeptidePtm.getSeqPosition(),
      monoMass = msiPeptidePtm.getMonoMass(),
      averageMass = msiPeptidePtm.getAverageMass(),
      composition = precursorEvidence.composition,
      isNTerm = if (ptmDefinition.location =~ """.+N-term$""") true else false,
      isCTerm = if (ptmDefinition.location =~ """.+C-term$""") true else false)
    if (useCachedObject) locatedPTMsCache.put(msiPeptidePtm.getId(), locatedPtm)

    locatedPtm
  }

  /**
   *  Convert from fr.proline.core.orm.msi.PeptideSpecificity(ORM) to fr.proline.core.om.model.msi.PtmDefinition (OM).
   *
   *
   * @param ptmSpecificityORM : fr.proline.core.orm.msi.PeptideSpecificity to convert
   * @return created PtmDefinition (with associated objects)
   */
  def convertPtmSpecificityORM2OM(msiPtmSpecificity: MsiPtmSpecificity): PtmDefinition = {

    import collection.JavaConversions.collectionAsScalaIterable

    //Verify PtmDefinition exist in cache
    if (useCachedObject && ptmDefinitionsCache.contains(msiPtmSpecificity.getId))
      return ptmDefinitionsCache(msiPtmSpecificity.getId)

    //*********** Create PtmNames from Ptm
    val msiPtm = msiPtmSpecificity.getPtm()
    val msiPtmShortName = msiPtm.getShortName()
    var ptmNames: PtmNames = null
    if (useCachedObject && ptmNamesCache.contains(msiPtmShortName))
      ptmNames = ptmNamesCache(msiPtmShortName)
    if (ptmNames == null) {
      ptmNames = new PtmNames(msiPtmShortName, msiPtm.getFullName())
      if (useCachedObject)
        ptmNamesCache.put(msiPtmShortName, ptmNames)
    }

    //*************** PtmEvidences ***************//    

    //Get PtmEvidences referencing PtmSpecificity of specified PeptidePtm. Creates corresponding OM objects
    val msiPtmEvidences = msiPtmSpecificity.getEvidences()
    var ptmEvidences = new ArrayBuffer[PtmEvidence](msiPtmEvidences.size())
    var precursorFound = false

    for (msiPtmEvid <- collectionAsScalaIterable(msiPtmEvidences)) {

      if (msiPtmEvid.getType() == msiPrecursorType)
        precursorFound = true

      ptmEvidences += new PtmEvidence(
        ionType = IonTypes.withName(msiPtmEvid.getType().name()),
        composition = msiPtmEvid.getComposition(),
        monoMass = msiPtmEvid.getMonoMass(),
        averageMass = msiPtmEvid.getAverageMass(),
        isRequired = msiPtmEvid.getIsRequired())
    }
    if (!precursorFound) {

      //"Precursor" PtmEvidence for this Ptm
      var precursorEvidence: PtmEvidence = null;
      val msiPtmEvidencesIt = msiPtmSpecificity.getPtm().getEvidences().iterator();
      while (msiPtmEvidencesIt.hasNext() && precursorEvidence == null) {
        val msiPtmEvidence = msiPtmEvidencesIt.next();

        if (msiPtmEvidence.getType() == msiPrecursorType) {
          precursorEvidence = new PtmEvidence(
            ionType = IonTypes.Precursor,
            msiPtmEvidence.getComposition(),
            msiPtmEvidence.getMonoMass(),
            msiPtmEvidence.getAverageMass(),
            msiPtmEvidence.getIsRequired())
        }
      }

      ptmEvidences += precursorEvidence
    }

    // Create OM PtmDefinition from ORM PtmSpecificity
    val ptmDef = new PtmDefinition(
      id = msiPtmSpecificity.getId(),
      location = msiPtmSpecificity.getLocation(),
      names = ptmNames,
      ptmEvidences = ptmEvidences.toArray,
      residue = characterToScalaChar(msiPtmSpecificity.getResidue),
      classification = msiPtmSpecificity.getClassification().getName().toString(),
      ptmId = msiPtmSpecificity.getPtm().getId()
    )
    
    if (useCachedObject)
      ptmDefinitionsCache.put(msiPtmSpecificity.getId(), ptmDef)

    ptmDef
  }

}
