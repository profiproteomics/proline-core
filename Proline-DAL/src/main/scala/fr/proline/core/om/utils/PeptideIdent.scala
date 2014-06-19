package fr.proline.core.om.utils

import fr.profi.util.StringUtils

class PeptideIdent(seq: String, ptmStr: String) {

  require(!StringUtils.isEmpty(seq), "Invalid seq")

  val sequence: String = seq
  val ptmString: String = if (StringUtils.isEmpty(ptmStr)) null else ptmStr

  override def equals(other: Any): Boolean = {

    if (other.isInstanceOf[PeptideIdent]) {
      val otherIdent = other.asInstanceOf[PeptideIdent]

      sequence.equals(otherIdent.sequence) &&
        (((ptmString == null) && (otherIdent.ptmString == null)) ||
          ((ptmString != null) && ptmString.equals(otherIdent.ptmString)))
    } else {
      false
    }

  }

  override def hashCode = sequence.hashCode()

  override def toString = {
    val buff = new StringBuilder()
    buff.append("sequence: ").append(sequence)

    if (ptmString != null) {
      buff.append(", ptmString: ").append(ptmString)
    }

    buff.toString
  }

}
