package fr.proline.core.om.builder

import fr.profi.util.bytes._
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object BioSequenceBuilder {
  
  import BioSequenceAlphabet._
  
  protected val BioSeqCols = MsiDbBioSequenceColumns
  
  def buildBioSequences(eachRecord: (IValueContainer => BioSequence) => Seq[BioSequence], setSequence: Boolean = true): Array[BioSequence] = {
    eachRecord( { r => buildBioSequence(r, setSequence) } ).toArray
  }
  
  def buildBioSequence(record: IValueContainer, setSequence: Boolean = true): BioSequence = {
    
    val r = record

    val id = r.getLong(BioSeqCols.ID)
    val alphabetAsStr = r.getString(BioSeqCols.ALPHABET)
    val alphabet = BioSequenceAlphabet.withName(alphabetAsStr.toUpperCase())
    val sequenceOpt = if(setSequence) Some(r.getString(BioSeqCols.SEQUENCE)) else None
    val length = r.getInt(BioSeqCols.LENGTH)
    val mass = r.getDouble(BioSeqCols.MASS)
    val pi = r.getFloatOrElse(BioSeqCols.PI,Float.NaN)
    val crc64 = r.getString(BioSeqCols.CRC64)
    val properties = r.getStringOption(BioSeqCols.SERIALIZED_PROPERTIES).flatMap { propsAsStr =>
      if( propsAsStr.isEmpty() ) None
      else Some(ProfiJson.deserialize[BioSequenceProperties](propsAsStr))
    }
  
    BioSequence(
      id = id,
      alphabet = alphabet,
      sequence = sequenceOpt,
      length = length,
      mass = mass,
      pi = pi,
      crc64 = crc64,
      properties = properties
    )

  }

}