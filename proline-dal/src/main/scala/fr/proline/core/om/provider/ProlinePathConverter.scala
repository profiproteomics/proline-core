package fr.proline.core.om.provider

trait IProlinePathConverter {
  
  def prolinePathToAbsolutePath( prolineResourcePath: String, dirType: ProlineManagedDirectoryType.Value ): String

}

class RegexBasedProlinePathConverter( regex: String, replacement: String ) extends IProlinePathConverter {
  
  def prolinePathToAbsolutePath( prolineResourcePath: String, dirType: ProlineManagedDirectoryType.Value ): String = {
    prolineResourcePath.replaceAll(regex, replacement)
  }

}

object ProlineManagedDirectoryType extends Enumeration {
  val MZDB_FILES = Value("MZDB_FILES")
  val RAW_FILES = Value("RAW_FILES")
  val RESULT_FILES = Value("RESULT_FILES")
}