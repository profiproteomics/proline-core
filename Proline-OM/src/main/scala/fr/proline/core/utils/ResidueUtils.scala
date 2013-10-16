package fr.proline.core.utils

/**
 * OM uses Scala char '\0' convention when residue is null (C-Term or N-Term).
 */
object ResidueUtils {

  def scalaCharToCharacter(residue: Char): java.lang.Character = {

    if (residue == '\0') {
      null
    } else {
      java.lang.Character.valueOf(residue)
    }

  }

  def characterToScalaChar(residue: java.lang.Character): Char = {

    if (residue == null) {
      '\0'
    } else {
      residue.charValue
    }

  }

}
