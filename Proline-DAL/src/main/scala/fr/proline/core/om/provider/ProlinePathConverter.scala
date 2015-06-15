package fr.proline.core.om.provider

trait IProlinePathConverter {
  
  def prolinePathToAbsolutePath( prolineResourcePath: String ): String

}

class RegexBasedProlinePathConverter( regex: String, replacement: String ) extends IProlinePathConverter {
  
  def prolinePathToAbsolutePath( prolineResourcePath: String ): String = {
    prolineResourcePath.replaceAll(regex, replacement)
  }

}