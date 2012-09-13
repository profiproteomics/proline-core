package fr.proline.core.om.utils

import org.junit.Assert._
import org.junit.Test

class PeptideIdentTest {

  @Test
  def testSame() {
    val ident1 = new PeptideIdent("toto", "tata")
    val ident2 = new PeptideIdent(ident1.sequence, ident1.ptmString)
    val ident3 = new PeptideIdent("titi", null)
    val ident4 = new PeptideIdent(ident3.sequence, "")

    assertEquals("Identity 1", ident1, ident1)
    assertEquals("Identity 3", ident3, ident3)
    assertEquals("Identity 4", ident4, ident4)

    assertEquals("Sequence + ptmString equality", ident1, ident2)
    assertEquals("Sequence + ptmString equality", ident2, ident1) // Revert
    assertEquals("Sequence + ptmString hashcode", ident1.hashCode, ident2.hashCode)

    assertEquals("Sequence + NULL / '' equality", ident3, ident4)
    assertEquals("Sequence + NULL / '' equality", ident4, ident3) // Revert
    assertEquals("Sequence + NULL / '' hashcode", ident3.hashCode, ident4.hashCode)
  }

  @Test
  def testNotEqual() {
    val ident1 = new PeptideIdent("toto", "A")
    val ident2 = new PeptideIdent("ToTo", ident1.ptmString)
    val ident3 = new PeptideIdent(ident1.sequence, "a")
    val ident4 = new PeptideIdent(ident1.sequence, "")
    val ident5 = new PeptideIdent(ident1.sequence, null)

    assertFalse("Sequence case", ident1.equals(ident2))
    assertFalse("PtmString case", ident1.equals(ident3))
    assertFalse("PtmString ''", ident1.equals(ident4))
    assertFalse("PtmString NULL", ident1.equals(ident5))
  }

}