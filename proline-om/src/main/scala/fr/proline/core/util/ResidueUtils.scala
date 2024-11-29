package fr.proline.core.util

/**
 * OM uses Scala char '\u0000' convention when residue is null (C-Term or N-Term).
 */
// TODO: DAL has a fr.proline.core.om.utils namespace, maybe we should change the package name to this NS
object ResidueUtils {

  def scalaCharToCharacter(residue: Char): java.lang.Character = {

    if (residue == '\u0000') {
      null
    } else {
      java.lang.Character.valueOf(residue)
    }

  }

  def characterToScalaChar(residue: java.lang.Character): Char = {

    if (residue == null) {
      '\u0000'
    } else {
      residue.charValue
    }

  }

}
