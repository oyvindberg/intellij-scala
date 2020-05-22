package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.util.assertions.StringAssertions._

class ScalaDocumentationProviderQuickInfoTest extends ScalaDocumentationProviderTestBase {

  override protected def generateDoc(editor: Editor, file: PsiFile): String =
    generateQuickInfo(editor, file)

  protected def generateQuickInfo(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    documentationProvider.getQuickNavigateInfo(referredElement, elementAtCaret)
  }

  override protected def normalizeHtml(html: String): String =
    html.trim.replaceAll("[\r]", "")

  def moduleName: String = getModule.getName

  def testSimpleClass(): Unit =
    doGenerateDocTest(
      s"""class ${|}MyClass""",
      s"""[$moduleName] default
         |class <a href="psi_element://MyClass"><code>MyClass</code></a>""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>""".stripMargin
    )

  def testSimpleTrait(): Unit =
    doGenerateDocTest(
      s"""trait ${|}MyTrait""",
      s"""[$moduleName] default
         |trait <a href="psi_element://MyTrait"><code>MyTrait</code></a>""".stripMargin
    )

  def testSimpleObject(): Unit =
    doGenerateDocTest(
      s"""object ${|}MyObject""",
      s"""[$moduleName] default
         |object <a href="psi_element://MyObject"><code>MyObject</code></a>""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>"""
    )

  def testClassWithModifiers(): Unit =
    doGenerateDocTest(
      s"""abstract sealed class ${|}MyClass""",
      s"""[$moduleName] default
         |abstract sealed class <a href="psi_element://MyClass"><code>MyClass</code></a>""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>"""
    )

  def testClassWithModifiers_1(): Unit =
    doGenerateDocTest(
      s"""final class ${|}MyClass""",
      s"""[$moduleName] default
         |final class <a href="psi_element://MyClass"><code>MyClass</code></a>""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>"""
    )

  def testClassWithGenericParameter(): Unit =
    doGenerateDocTest(
      s"""class ${|}Class[T]""",
      s"[$moduleName] default\n" +
        "class <a href=\"psi_element://Class\"><code>Class</code></a>[T]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWithGenericParameter_WithBounds(): Unit =
    doGenerateDocTest(
      s"""trait Trait[A]
         |class ${|}Class[T <: Trait[_ >: Object]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "class <a href=\"psi_element://Class\"><code>Class</code></a>[T &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[_ &gt;:" +
        " <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>]]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWIthGenericParameter_WithRecursiveBounds(): Unit =
    doGenerateDocTest(
      s"""trait Trait[T]
         |class ${|}Class2[T <: Trait[T]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "class <a href=\"psi_element://Class2\"><code>Class2</code></a>[T &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[T]]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWIthGenericParameter_WithRecursiveBounds_1(): Unit =
    doGenerateDocTest(
      s"""trait Trait[T]
         |class ${|}Class4[T <: Trait[_ >: Trait[T]]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "class <a href=\"psi_element://Class4\"><code>Class4</code></a>[T &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[_ &gt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[T]]]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWithSuperWithGenerics(): Unit =
    doGenerateDocTest(
      s"""trait Trait[A]
         |abstract class ${|}Class extends Comparable[_ <: Trait[_ >: String]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "abstract class <a href=\"psi_element://Class\"><code>Class</code></a>" +
        " extends <a href=\"psi_element://java.lang.Comparable\"><code>Comparable</code></a>[_ &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[_ &gt;:" +
        " <a href=\"psi_element://scala.Predef.String\"><code>String</code></a>]]"
    )

  def testClassExtendsListShouldNotContainWithObject(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait1
        |trait BaseTrait2
        |""".stripMargin
    )
    val classesWithoutObject = Seq(
      s"class ${|}MyClass2 extends BaseClass",
      s"class ${|}MyClass4 extends BaseTrait1",
      s"class ${|}MyClass3 extends BaseClass with BaseTrait1",
      s"class ${|}MyTrait1",
      s"class ${|}MyTrait2 extends BaseTrait1",
      s"class ${|}MyTrait3 extends BaseTrait1 with BaseTrait2"
    )
    // testing exact quick info value would be very noisy, it's enough to test just presence of ` with Object` which can be escaped!
    val withObjectRegex      = "(\\s|\\n)with .*Object".r
    classesWithoutObject.foreach { content =>
      val quickInfo = generateDoc(content)
      assertStringNotMatches(quickInfo, withObjectRegex)
    }
  }

  def testClassExtendsListShouldContainObjectIfThereAreNoBaseClasses(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait1
        |trait BaseTrait2
        |""".stripMargin
    )

    val classesWithObject = Seq(
      s"class ${|}MyClass1"
    )

    val extendsObjectRegex = "(\\s|\\n)extends .*Object".r
    classesWithObject.foreach { content =>
      val quickInfo = generateDoc(content)
      assertStringMatches(quickInfo, extendsObjectRegex)
    }
  }

  def testValueDefinition(): Unit =
    doGenerateDocTest(
      s"""class Wrapper {
         |  val (field1, ${|}field2) = (42, "hello")
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |val field2: <a href="psi_element://java.lang.String"><code>String</code></a> = (42, "hello")""".stripMargin
    )

  def testValueDeclaration(): Unit =
    doGenerateDocTest(
      s"""abstract class Wrapper {
         |  val ${|}field2: String
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |val field2: <a href="psi_element://scala.Predef.String"><code>String</code></a>""".stripMargin
    )

  def testVariableDefinition(): Unit =
    doGenerateDocTest(
      s"""class Wrapper {
         |  var (field1, ${|}field2) = (42, "hello")
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |var field2: <a href="psi_element://java.lang.String"><code>String</code></a> = (42, "hello")""".stripMargin
    )

  def testVariableDeclaration(): Unit =
    doGenerateDocTest(
      s"""abstract class Wrapper {
         |  var ${|}field2: String
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |var field2: <a href="psi_element://scala.Predef.String"><code>String</code></a>""".stripMargin
    )

  def testValueWithModifiers(): Unit =
    doGenerateDocTest(
      s"""class Wrapper {
         |  protected final lazy val ${|}field2 = "hello"
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |protected final lazy val field2: <a href="psi_element://java.lang.String"><code>String</code></a> = "hello"""".stripMargin
    )
}