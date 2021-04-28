/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.KotlinUElementWithComments
import java.util.*

class KotlinUFile(
    override val psi: KtFile,
    override val languagePlugin: UastLanguagePlugin = kotlinUastPlugin
) : UFile, KotlinUElementWithComments {
    override val packageName: String
        get() = psi.packageFqName.asString()

    override val annotations: List<UAnnotation>
        get() = psi.annotationEntries.map { KotlinUAnnotation(it, this) }

    override val javaPsi: PsiClassOwner? by lz {
        val lightClasses = this@KotlinUFile.classes.map { it.javaPsi }.toTypedArray()
        val lightFile = lightClasses.asSequence()
            .mapNotNull { it.containingFile as? PsiClassOwner }
            .firstOrNull()

        if (lightFile == null) null
        else
            object : PsiClassOwner by lightFile {
                override fun getClasses() = lightClasses
                override fun setPackageName(packageName: String?) = error("Incorrect operation for non-physical Java PSI")
            }
    }

    override val sourcePsi: KtFile = psi

    override val allCommentsInFile by lz {
        val comments = ArrayList<UComment>(0)
        psi.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitComment(comment: PsiComment) {
                comments += UComment(comment, this@KotlinUFile)
            }
        })
        comments
    }
    
    override val imports by lz { psi.importDirectives.map { KotlinUImportStatement(it, this) } }

    override val classes by lz {
        val facadeOrScriptClass = if (psi.isScript()) psi.script?.toLightClass() else psi.findFacadeClass()
        val classes = psi.declarations.mapNotNull { (it as? KtClassOrObject)?.toLightClass()?.toUClass() }

        (facadeOrScriptClass?.toUClass()?.let { listOf(it) } ?: emptyList()) + classes
    }

    private fun PsiClass.toUClass() = languagePlugin.convertOpt<UClass>(this, this@KotlinUFile)
}
