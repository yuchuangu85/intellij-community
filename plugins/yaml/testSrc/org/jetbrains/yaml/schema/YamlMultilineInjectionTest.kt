// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parents
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.Predicate
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema
import junit.framework.TestCase
import org.jetbrains.yaml.psi.YAMLScalar

class YamlMultilineInjectionTest : BasePlatformTestCase() {

  private val myInjectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(myFixture)

  override fun setUp() {
    super.setUp()
    registerJsonSchema(myFixture, """
      {
        "properties": {
          "X": {
            "x-intellij-language-injection": "XML"
          },
          "myyaml": {
            "x-intellij-language-injection": "yaml"
          }
        }
      }
    """.trimIndent(), ".json", Predicate { true })

  }

  fun testSeparateLinesQuotesXmlInjection() {
    myFixture.configureByText("test.yaml", """
        X: "<html><caret>
             <body>boo</body>
           </html>"
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("<html> <body>boo</body> </html>")
  }


  fun testFoldedXmlInjection() {
    myFixture.configureByText("test.yaml", """
        X: >
          <html><caret>
               <body>boo</body>
          </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |     <body>boo</body>
      |</html>
      |""".trimMargin())
  }

  fun testBlockInjection() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html><caret>
               <body>boo</body>
          </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |     <body>boo</body>
      |</html>""".trimMargin())

  }

  fun testYamlToYamlInjection() {
    myFixture.configureByText("test.yaml", """
        myyaml: |
          ro<caret>ot:
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    assertInjectedAndLiteralValue("root:")
    myFixture.openFileInEditor(myInjectionFixture.topLevelFile.virtualFile)
    myFixture.editor.caretModel.run { moveToOffset(offset + 3) }
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |  """.trimMargin())
  }
  
  fun testYamlToYamlInjection2() {
    myFixture.configureByText("test.yaml", """
        myyaml: |
          root:<caret>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    assertEquals("literal text should be", "root:\n", literalTextAtTheCaret)
    myInjectionFixture.assertInjectedContent("root:\n")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |  
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n")
    myFixture.type("  ")
    PsiDocumentManager.getInstance(project).commitDocument(myFixture.getDocument(myFixture.file))
    myFixture.type("abc:")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |  abc:
      |  
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n  abc:\n")
    myFixture.type("   ")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |  abc:
      |     
      |     
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n  abc:\n     \n     ")
  }

  fun testBlockInjectionKeep() {
    myFixture.configureByText("test.yaml", """
        X: |+
          <html>
          <body>hello world</body>
          </html<caret>>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |""".trimMargin())
  }
  
  fun testBlockInjectionStrip() {
    myFixture.configureByText("test.yaml", """
        X: |-
          <html>
          <body>hello world</body>
          </html<caret>>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val fe = myInjectionFixture.openInFragmentEditor()
    TestCase.assertEquals("""
      |<html>
      |<body>hello world</body>
      |</html>""".trimMargin(), fe.file.text)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |-
          <html>
          <body>hello world</body>
          </html>
          
          
    """.trimIndent())
    fe.performEditorAction(IdeActions.ACTION_SELECT_ALL)
    fe.performEditorAction(IdeActions.ACTION_DELETE)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", "", fe.file.text)
    myFixture.checkResult("""
        |X: |-
        |  """.trimMargin())
    
  }
  
  
  fun testPutEnterInTheEnd() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html>
          <body>hello world</body>
          </html<caret>>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    TestCase.assertTrue("editor should survive adding enter to the end", quickEditHandler.isValid)
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          
          
    """.trimIndent())

    fe.type("footer")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |footer
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          footer
          
    """.trimIndent())
  }
  
  fun testPutEnterInTheEndWithBlankLine() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html>
          <body>hello world</body>
          </html<caret>>
          
        Y: 12
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    TestCase.assertTrue("editor should survive adding enter to the end", quickEditHandler.isValid)
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          
          
        Y: 12
    """.trimIndent())

    fe.type("footer")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |footer""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          
          footer
        Y: 12
    """.trimIndent())
  }
  
  fun testTypeHtmlFromEmpty() {
    myFixture.configureByText("test.yaml", """
        X: |
          <caret>
          
        Y: 12
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.type("<html>")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", "<html></html>\n", fe.file.text)
    myFixture.checkResult("""
        X: |
          <html></html>
        
        Y: 12
    """.trimIndent())
  }
  
  private fun assertInjectedAndLiteralValue(expectedText: String) {
    assertEquals("fragment editor should be", expectedText, myInjectionFixture.openInFragmentEditor().file.text)
    assertEquals("literal text should be", expectedText, literalTextAtTheCaret)
  }

  val literalTextAtTheCaret: String
    get() {
      val elementAt = myInjectionFixture.topLevelFile.findElementAt(myInjectionFixture.topLevelCaretPosition)
      return elementAt?.parents(true)?.mapNotNull { it.castSafelyTo<YAMLScalar>() }?.firstOrNull()?.textValue ?: 
      throw AssertionError("no literal element at the caret position, only $elementAt were found")
    }

}