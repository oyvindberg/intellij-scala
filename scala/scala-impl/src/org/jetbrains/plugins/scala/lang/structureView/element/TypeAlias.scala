package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.07.2008
 */
private class TypeAlias(alias: ScTypeAlias, inherited: Boolean) extends AbstractTreeElement(alias, inherited)  {
  override def location: Option[String] =
    Option(element.containingClass).map(_.name)

  override def getPresentableText: String =
    getTypeAliasPresentableText(element)

  private def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    if (typeAlias.nameId != null) typeAlias.nameId.getText else "type unnamed"

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}
