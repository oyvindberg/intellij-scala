package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.collection.Seq

trait Element extends StructureViewTreeElement with ColoredItemPresentation {
  def element: PsiElement

  def inherited: Boolean

  def isAlwaysLeaf: Boolean

  def isAlwaysShowsPlus: Boolean
}

object Element {

  def apply(element: PsiElement, inherited: Boolean = false): Seq[Element] = element match {
    case packaging: ScPackaging => packaging.typeDefinitions.map(new TypeDefinition(_))
    // TODO Type definition can be inherited
    case definition: ScTypeDefinition => Seq(new TypeDefinition(definition))
    case parameter: ScClassParameter  => Seq(new ValOrVarParameter(parameter, inherited))
    case function: ScFunction         => Seq(new Function(function, inherited))
    case variable: ScVariable         => variable.declaredElements.map(new Variable(_, inherited))
    case value: ScValue               => value.declaredElements.map(new Value(_, inherited))
    case alias: ScTypeAlias           => Seq(new TypeAlias(alias, inherited))
    case block: ScBlockExpr           => Seq(new Block(block))
    case _                            => Seq.empty
  }

  def apply(fileProvider: () => ScalaFile): Element = new File(fileProvider)
}