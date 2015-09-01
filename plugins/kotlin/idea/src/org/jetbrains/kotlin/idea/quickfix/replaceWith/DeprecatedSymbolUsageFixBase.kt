/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.core.OptionalParametersHelper
import org.jetbrains.kotlin.idea.quickfix.JetIntentionAction
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.JetSimpleNameExpression
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.hasDefaultValue

//TODO: different replacements for property accessors

public abstract class DeprecatedSymbolUsageFixBase(
        element: JetSimpleNameExpression,
        val replaceWith: ReplaceWith
) : JetIntentionAction<JetSimpleNameExpression>(element) {

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!super.isAvailable(project, editor, file)) return false
        val strategy = UsageReplacementStrategy.build(element, replaceWith)
        return strategy != null && strategy.createReplacer(element) != null
    }

    final override fun invoke(project: Project, editor: Editor?, file: JetFile) {
        val strategy = UsageReplacementStrategy.build(element, replaceWith)!!
        invoke(strategy, project, editor)
    }

    protected abstract fun invoke(
            replacementStrategy: UsageReplacementStrategy,
            project: Project,
            editor: Editor?)

    companion object {
        public fun replaceWithPattern(descriptor: DeclarationDescriptor, project: Project): ReplaceWith? {
            val annotationClass = descriptor.builtIns.deprecatedAnnotation
            val annotation = descriptor.annotations.findAnnotation(DescriptorUtils.getFqNameSafe(annotationClass)) ?: return null
            val replaceWithValue = annotation.argumentValue(kotlin.deprecated::replaceWith.name) as? AnnotationDescriptor ?: return null
            val pattern = replaceWithValue.argumentValue(kotlin.ReplaceWith::expression.name) as? String ?: return null
            if (pattern.isEmpty()) return null
            val importValues = replaceWithValue.argumentValue(kotlin.ReplaceWith::imports.name) as? List<*> ?: return null
            if (importValues.any { it !is StringValue }) return null
            val imports = importValues.map { (it as StringValue).value }

            // should not be available for descriptors with optional parameters if we cannot fetch default values for them (currently for library with no sources)
            if (descriptor is CallableDescriptor &&
                descriptor.valueParameters.any { it.hasDefaultValue() && OptionalParametersHelper.defaultParameterValue(it, project) == null }) return null

            return ReplaceWith(pattern, *imports.toTypedArray())
        }
    }
}
