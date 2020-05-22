package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

object ScalaElementPresentation {

  //TODO refactor with name getters

  def getTypeDefinitionPresentableText(typeDefinition: ScTypeDefinition): String =
    if (typeDefinition.nameId != null) typeDefinition.nameId.getText else "unnamed"

  def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    if (typeAlias.nameId != null) typeAlias.nameId.getText else "type unnamed"

  def getPresentableText(elem: PsiElement): String = elem.getText

  def getValOrVarPresentableText(elem: ScNamedElement): String = {
    val typeText = elem match {
      case typed: Typeable => ": " + typed.`type`().getOrAny.presentableText(typed)
      case _ => ""
    }
    val keyword = ScalaPsiUtil.nameContext(elem) match {
      case _: ScVariable => ScalaKeyword.VAR
      case _: ScValue => ScalaKeyword.VAL
      case param: ScClassParameter if param.isVar => ScalaKeyword.VAR
      case param: ScClassParameter if param.isVal => ScalaKeyword.VAL
      case _ => ""
    }
    s"$keyword ${elem.name}$typeText"
  }
}
