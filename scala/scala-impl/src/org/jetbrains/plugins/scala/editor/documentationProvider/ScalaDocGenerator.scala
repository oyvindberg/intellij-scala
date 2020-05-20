package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.javadoc.PsiDocComment
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationUtils.EmptyDoc
import org.jetbrains.plugins.scala.editor.documentationProvider.extensions.PsiMethodExt
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiClassExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer.AccessQualifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.{HtmlPsiUtils, PresentationUtil}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.project.ProjectContext

object ScalaDocGenerator {

  def generateDoc(elementWithDoc: PsiElement, originalElement: PsiElement): String = {
    val e = elementWithDoc.getNavigationElement

    implicit val projectContext: ProjectContext = e.projectContext
    implicit def typeRenderer: TypeRenderer = _.urlText(originalElement)

    val builder = new HtmlBuilderWrapper
    import builder._

    def appendDef(mainPart: => Unit): Unit = {
//      append(DocumentationMarkup.DEFINITION_START)
//      mainPart
//      append(DocumentationMarkup.DEFINITION_END)
      withTag("div", Seq(("class", "definition"))) {
        mainPart
      }
    }

    def appendDefWithContent(docOwner: PsiDocCommentOwner)(mainPart: => Unit): Unit = {
      appendDef(mainPart)
      append(parseDocComment(docOwner))
    }

    def appendMainSection(element: PsiElement, epilogue: => Unit = {}, needsTpe: Boolean = false): Unit = {
      pre {
        element match {
          case an: ScAnnotationsHolder =>
            val annotationsRendered = new AnnotationsRenderer(typeRenderer, "\n", TextEscaper.Html).renderAnnotations(an)
            append(annotationsRendered)
          case _ =>
        }

//        val start = length

        element match {
          case m: ScModifierListOwner =>
            val renderer = new ModifiersRenderer(new AccessModifierRenderer(AccessQualifierRenderer.WithHtmlPsiLink))
            append(renderer.render(m))
          case _ =>
        }

        append(ScalaDocumentationUtils.getKeyword(element))

        b {
          append(element match {
            case named: ScNamedElement => escapeHtml(named.name)
            case _ => "_"
          })
        }

        element match {
          case tpeParamOwner: ScTypeParametersOwner =>
            append(parseTypeParameters(tpeParamOwner))
          case _ =>
        }


        element match {
          case params: ScParameterOwner =>
            val renderer = definitionParamsRenderer(typeRenderer)
            // TODO: since SCL-13777 spaces are effectively not used! cause we remove all new lines and spaces after rendering
            //  review SCL-13777, maybe we should improve formatting of large classes
            //val spaces = length - start - 7
            val paramsRendered = renderer.renderClauses(params).replaceAll("\n\\s*", "")
            append(paramsRendered)
          case _ =>
        }

        append(element match {
          case typed: ScTypedDefinition => typeAnnotationRenderer.render(typed)
          case _ if needsTpe            => ": Nothing"
          case _                        => ""
        })

        epilogue
      }
    }

    def appendTypeDef(typedef: ScTypeDefinition): Unit =
      appendDefWithContent(typedef) {
        typedef.qualifiedName.lastIndexOf(".") match {
          case -1 =>
          case a =>
            withTag("font", Seq(("size", "-1"))) {
              b {
                append(typedef.qualifiedName.substring(0, a))
              }
            }
        }

        appendMainSection(typedef, {
          appendNl()
          append(parseExtendsBlock(typedef.extendsBlock))
        })
      }

    def appendFunction(fun: ScFunction): Unit =
      appendDefWithContent(fun) {
        append(parseClassUrl(fun))
        appendMainSection(fun)
      }

    def appendValOrVar(decl: ScValueOrVariable): Unit =
      appendDefWithContent(decl) {
        decl match {
          case decl: ScMember => append(parseClassUrl(decl))
          case _ =>
        }
        appendMainSection(decl, needsTpe = true)
      }

    def appendTypeAlias(tpe: ScTypeAlias): Unit =
      appendDefWithContent(tpe) {
        append(parseClassUrl(tpe))
        appendMainSection(tpe, {
          tpe match {
            case definition: ScTypeAliasDefinition =>
              val tp = definition.aliasedTypeElement.flatMap(_.`type`().toOption).getOrElse(psi.types.api.Any)
              append(s" = ${typeRenderer(tp)}")
            case _ =>
          }
        })
      }

    def appendBindingPattern(pattern: ScBindingPattern): Unit =
      pre {
        append("Pattern: ")
        b {
          append(escapeHtml(pattern.name))
        }
        append(typeAnnotationRenderer.render(pattern))
        val context = pattern.getContext
        if (context != null) {
          context.getContext match {
            case co: PsiDocCommentOwner =>
              append(parseDocComment(co))
            case _ =>
          }
        }
      }

    withHtmlMarkup {
      e match {
        case typeDef: ScTypeDefinition => appendTypeDef(typeDef)
        case fun: ScFunction           => appendFunction(fun)
        case decl: ScValueOrVariable   => appendValOrVar(decl)
        case tpe: ScTypeAlias          => appendTypeAlias(tpe)
        case pattern: ScBindingPattern => appendBindingPattern(pattern)
        case param: ScParameter        => appendMainSection(param) // TODO: it should contain description of the parameter from the scaladoc
        case _                         =>
      }
    }

    val result = builder.result()
    result
  }

  private def definitionParamsRenderer(implicit typeRenderer: TypeRenderer): ParametersRenderer = {
    val parameterRenderer = new ParameterRenderer(
      typeRenderer,
      ModifiersRenderer.WithHtmlPsiLink,
      typeAnnotationRenderer(typeRenderer),
      TextEscaper.Html,
      withMemberModifiers = false,
      withAnnotations = true
    )
    new ParametersRenderer(
      parameterRenderer,
      renderImplicitModifier = true,
      clausesSeparator = "",
    )
  }

  private def typeAnnotationRenderer(implicit typeRenderer: TypeRenderer): TypeAnnotationRenderer =
    new TypeAnnotationRenderer(typeRenderer, ParameterTypeDecorateOptions.DecorateAll)

  private def parseClassUrl(elem: ScMember): String = {
    val clazz = elem.containingClass
    if (clazz == null) EmptyDoc
    else HtmlPsiUtils.classFullLink(clazz)
  }

  private def parseTypeParameters(elems: ScTypeParametersOwner): String = {
    val typeParameters = elems.typeParameters
    // todo hyperlink identifiers in type bounds
    if (typeParameters.nonEmpty)
      escapeHtml(typeParameters.map(PresentationUtil.presentationStringForPsiElement(_)).mkString("[", ", ", "]"))
    else EmptyDoc
  }

  private def parseExtendsBlock(elem: ScExtendsBlock)
                               (implicit typeToString: TypeRenderer): String = {
    val buffer: StringBuilder = new StringBuilder()
    elem.templateParents match {
      case Some(x: ScTemplateParents) =>
        val seq = x.allTypeElements
        buffer.append(typeToString(seq.head.`type`().getOrAny) + "\n")
        for (i <- 1 until seq.length)
          buffer append " with " + typeToString(seq(i).`type`().getOrAny)
      case None =>
        if (elem.isUnderCaseClass) {
          buffer.append(HtmlPsiUtils.psiElementLink("scala.Product", "Product"))
        }
    }

    if (buffer.isEmpty) EmptyDoc
    else " extends " + buffer
  }

  // TODO: strange naming.. not "parse", it not only parses but also resolves base
  private def parseDocComment(potentialDocOwner: PsiDocCommentOwner): String =
    findActualComment(potentialDocOwner).fold(EmptyDoc) { case (docOwner, docComment, isInherited) =>
      parseDocComment(docOwner, docComment, isInherited)
    }

  private def findActualComment(docOwner: PsiDocCommentOwner): Option[(PsiDocCommentOwner, PsiDocComment, Boolean)] =
    docOwner.getDocComment match {
      case null =>
        superElementWithDocComment(docOwner) match {
          case Some((base, baseComment)) =>
            Some((base, baseComment, true))
          case _ =>
            None
        }
      case docComment =>
        Some((docOwner, docComment, false))
    }

  private def parseDocComment(
    docOwner: PsiDocCommentOwner,
    docComment: PsiDocComment,
    isInherited: Boolean
  ): String = {
    val commentParsed = docComment match {
      case scalaDoc: ScDocComment => generateScalaDocInfoContent(docOwner, scalaDoc)
      case _                      => generateJavaDocInfoContent(docOwner)
    }
    if (isInherited)
      wrapWithInheritedDescription(docOwner.containingClass)(commentParsed)
    else
      commentParsed
  }

  private def superElementWithDocComment(docOwner: PsiDocCommentOwner) =
    docOwner match {
      case method: PsiMethod => superMethodWithDocComment(method)
      case _                 => None
    }

  private def superMethodWithDocComment(method: PsiMethod): Option[(PsiMethod, PsiDocComment)] =
    method.superMethods.map(base => (base, base.getDocComment)).find(_._2 != null)

  def generateScalaDocInfoContent(
    docCommentOwner: PsiDocCommentOwner,
    docComment: ScDocComment
  ): String = {
    val javaElement = prepareFakeJavaElementWithComment(docCommentOwner, docComment)
    javaElement.map(generateJavaDocInfoContent).getOrElse("")
  }

  def generateRenderedScalaDocContent(
    docCommentOwner: PsiDocCommentOwner,
    docComment: ScDocComment
  ): String = {
    val javaElement = prepareFakeJavaElementWithComment(docCommentOwner, docComment)
    javaElement.map(generateRenderedJavaDocInfo).getOrElse("")
  }

  private def prepareFakeJavaElementWithComment(docCommentOwner: PsiDocCommentOwner, docComment: ScDocComment) = {
    val withReplacedWikiTags = ScaladocWikiProcessor.replaceWikiWithTags(docComment)
    createFakeJavaElement(docCommentOwner, withReplacedWikiTags)
  }

  private def createFakeJavaElement(
    elem: PsiDocCommentOwner,
    docText: String
  ): Option[PsiDocCommentOwner] = {
    def getParams(fun: ScParameterOwner): String =
      fun.parameters.map(param => "int     " + escapeHtml(param.name)).mkString("(", ",\n", ")")

    def getTypeParams(tParams: Seq[ScTypeParam]): String =
      if (tParams.isEmpty) ""
      else tParams.map(param => escapeHtml(param.name)).mkString("<", " , ", ">")

    val javaText = elem match {
      case clazz: ScClass =>
        s"""
           |class A {
           |$docText
           |public ${getTypeParams(clazz.typeParameters)}void f${getParams(clazz)}{
           |}""".stripMargin
      case typeAlias: ScTypeAlias =>
        s"""$docText
           | class A${getTypeParams(typeAlias.typeParameters)} {}""".stripMargin
      case _: ScTypeDefinition =>
        s"""$docText
           |class A {
           |}""".stripMargin
      case f: ScFunction =>
        s"""class A {
           |$docText
           |public ${getTypeParams(f.typeParameters)}int f${getParams(f)} {}
           |}""".stripMargin
      case m: PsiMethod =>
        s"""class A {
           |${m.getText}
           |}""".stripMargin
      case _ =>
        s"""$docText
           |class A""".stripMargin
    }

    val javaDummyFile = createDummyJavaFile(javaText, elem.getProject)

    val clazz = javaDummyFile.getClasses.head
    // not using getAllMethods to avoid accessing indexes (and thus work in dump mode)
    elem match {
      case _: ScFunction | _: ScClass | _: PsiMethod => clazz.children.findByType[PsiMethod]
      case _                                         => Some(clazz)
    }
  }

  private def createDummyJavaFile(text: String, project: Project): PsiJavaFile =
    PsiFileFactory.getInstance(project).createFileFromText("dummy", StdFileTypes.JAVA, text).asInstanceOf[PsiJavaFile]

  private def generateJavaDocInfoContent(element: PsiElement): String = {
    val javadoc = generateJavaDocInfo(element)
    val javadocContent = extractJavaDocContent(javadoc)
    javadocContent
  }

  private def generateJavaDocInfo(element: PsiElement): String = {
    val builder = new java.lang.StringBuilder()
    val generator = new JavaDocInfoGenerator(element.getProject, element)
    generator.generateDocInfoCore(builder, false)
    builder.toString
  }

  private def generateRenderedJavaDocInfo(element: PsiElement): String = {
    val generator = new JavaDocInfoGenerator(element.getProject, element)
    generator.generateRenderedDocInfo
  }

  // TODO: this is far from perfect to rely on text... =(
  //  dive deep into Javadoc generation and implement in a more safe and structural way
  private def extractJavaDocContent(javadoc: String): String = {
    val contentStartIdx = javadoc.indexOf(DocumentationMarkup.CONTENT_START) match {
      case -1 => javadoc.indexOf(DocumentationMarkup.SECTIONS_START)
      case idx => idx
    }
    if (contentStartIdx > 0) javadoc.substring(contentStartIdx)
    else javadoc
  }

  private def wrapWithInheritedDescription(clazz: PsiClass)(text: String): String = {
    val prefix =
      s"""${DocumentationMarkup.CONTENT_START}
         |<b>Description copied from class: </b>
         |${HtmlPsiUtils.psiElementLink(clazz.qualifiedName, clazz.name)}
         |${DocumentationMarkup.CONTENT_END}""".stripMargin
    prefix + text
  }
}
