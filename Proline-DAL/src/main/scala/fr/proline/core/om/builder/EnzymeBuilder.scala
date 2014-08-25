package fr.proline.core.om.builder

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object EnzymeBuilder {
  
  protected val enzCols = UdsDbEnzymeColumns
  protected val enzCleavageCols = UdsDbEnzymeCleavageColumns
  
  def buildEnzymes(
    eachEnzymeRecord: (IValueContainer => Enzyme) => Seq[Enzyme],
    eachEnzymeCleavageRecordSelector: Long => ( (IValueContainer => EnzymeCleavage) => Seq[EnzymeCleavage] )
  ): Array[Enzyme] = {
    
    eachEnzymeRecord { r =>
      buildEnzyme(r,eachEnzymeCleavageRecordSelector)
    } toArray
  }
  
  def buildEnzymes(
    eachEnzymeRecord: (IValueContainer => Enzyme) => Seq[Enzyme],
    enzymeCleavagesByEnzymeId: Map[Long,Array[EnzymeCleavage]]
  ): Array[Enzyme] = {
    
    eachEnzymeRecord { r =>
      buildEnzyme(r, enzymeCleavagesByEnzymeId(r.getLong(enzCols.ID)) )
    } toArray
  }
  
  def buildEnzyme(
    enzymeRecord: IValueContainer,
    eachEnzymeCleavageRecordSelector: Long => ( (IValueContainer => EnzymeCleavage) => Seq[EnzymeCleavage] )
  ): Enzyme = {
    this.buildEnzyme(
      enzymeRecord,
      buildEnzymeCleavages( eachEnzymeCleavageRecordSelector( enzymeRecord.getLong(enzCols.ID) ) )
    )
  }
    
  def buildEnzyme(
    enzymeRecord: IValueContainer,
    enzymeCleavages: Array[EnzymeCleavage]
  ): Enzyme = {
    
    val r = enzymeRecord
    
    new Enzyme(
      id = r.getLong(enzCols.ID),
      name = r.getString(enzCols.NAME),
      enzymeCleavages = enzymeCleavages,
      cleavageRegexp = r.getStringOption(enzCols.CLEAVAGE_REGEXP),
      isIndependant = r.getBooleanOrElse(enzCols.IS_INDEPENDANT, false),
      isSemiSpecific = r.getBooleanOrElse(enzCols.IS_SEMI_SPECIFIC, false),
      properties = r.getStringOption(enzCols.SERIALIZED_PROPERTIES).map( ProfiJson.deserialize[EnzymeProperties](_))
    )
  
  }

  def buildEnzymeCleavages( eachRecord: (IValueContainer => EnzymeCleavage) => Seq[EnzymeCleavage] ): Array[EnzymeCleavage] = {    
    eachRecord( buildEnzymeCleavage ).toArray
  }
  
  def buildEnzymeCleavage( record: IValueContainer ): EnzymeCleavage = {
    
    val r = record

    new EnzymeCleavage(
      id = r.getLong(enzCleavageCols.ID),
      site = r.getString(enzCleavageCols.SITE),
      residues = r.getString(enzCleavageCols.RESIDUES),
      restrictiveResidues = r.getStringOption(enzCleavageCols.RESTRICTIVE_RESIDUES)
    )
  }

}