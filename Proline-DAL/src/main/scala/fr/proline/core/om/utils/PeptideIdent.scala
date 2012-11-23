package fr.proline.core.om.utils

import fr.proline.util.StringUtils

class PeptideIdent(seq: String, ptmStr: String) {
  require(seq != null)

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

}
