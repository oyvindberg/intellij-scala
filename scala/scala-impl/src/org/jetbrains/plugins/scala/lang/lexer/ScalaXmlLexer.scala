package org.jetbrains.plugins.scala
package lang
package lexer

import com.intellij.lexer.{MergingLexerAdapter, _XmlLexer, __XmlLexer}
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.xml.XmlTokenType
import com.intellij.util.containers.ContainerUtil

final class ScalaXmlLexer extends MergingLexerAdapter(
  new _XmlLexer(new __XmlLexer(null), false),
  ScalaXmlLexer.TokensToMerge
) {
  override def getTokenType: IElementType = ScalaXmlLexer.ScalaXmlTokenType(super.getTokenType)
}

object ScalaXmlLexer {

  final class ScalaXmlTokenType private(debugName: String) extends ScalaTokenType(debugName)

  object ScalaXmlTokenType {

    private val typesByName = ContainerUtil.newHashMap[String, ScalaXmlTokenType]

    def apply(debugName: String): ScalaXmlTokenType = {
      val tokenType = new ScalaXmlTokenType(debugName)
      typesByName.put(debugName, tokenType)
      tokenType
    }

    // scala.Option usages should be avoided in lexer
    def apply(elementType: IElementType): IElementType = elementType match {
      case null => null
      case _ =>
        elementType.toString match {
          case null => elementType
          case name =>
            typesByName.get(name) match {
              case null => elementType
              case value => value
            }
        }
    }

    def unapply(elementType: IElementType): Boolean =
      typesByName.containsKey(elementType.toString)
  }

  private val TokensToMerge = TokenSet.create(
    ScalaXmlTokenTypes.XML_DATA_CHARACTERS,
    ScalaXmlTokenTypes.XML_TAG_CHARACTERS, // merging can be performed in locateToken() => we need to merge both types of tokens
    ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN,
    ScalaXmlTokenTypes.XML_COMMENT_CHARACTERS,
    XmlTokenType.XML_DATA_CHARACTERS,
    XmlTokenType.XML_TAG_CHARACTERS,
    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
    XmlTokenType.XML_PI_TARGET,
    XmlTokenType.XML_COMMENT_CHARACTERS
  )
}