package fr.proline.core.om.model

object SelectionLevel {
  val DESELECTED_MANUAL = 0
  val DESELECTED_AUTO = 1
  val SELECTED_AUTO = 2
  val SELECTED_MANUAL = 3
  
  @inline def isSetAutomatically(selectionLevel: Int): Boolean = {
    selectionLevel == DESELECTED_AUTO || selectionLevel == SELECTED_AUTO
  }
  @inline def isSetManually(selectionLevel: Int): Boolean = {
    selectionLevel == DESELECTED_MANUAL || selectionLevel == SELECTED_MANUAL
  }
  @inline def isSelected(selectionLevel: Int): Boolean = selectionLevel >= 2
  @inline def isDeselected(selectionLevel: Int): Boolean = selectionLevel <= 1
}

// TODO: use this value class in Proline-OM classes and check if serialization works
class SelectionLevel(val value: Int) extends AnyVal {
  def isSetAutomatically(): Boolean = SelectionLevel.isSetAutomatically(value)
  def isSetManually(): Boolean = SelectionLevel.isSetManually(value)
  def isSelected(): Boolean = SelectionLevel.isSelected(value)
  def isDeselected(): Boolean = SelectionLevel.isDeselected(value)
}