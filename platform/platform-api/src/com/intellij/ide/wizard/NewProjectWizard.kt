// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import javax.swing.JLabel

interface NewProjectWizard<T> {
  val language: String
  var settingsFactory: () -> T

  fun enabled(): Boolean = true
  fun settingsList(settings: T): List<LabelAndComponent> = emptyList()
  fun setupProject(project: Project?, settings: T) { }

  companion object {
    var EP_WIZARD = ExtensionPointName<NewProjectWizard<*>>("com.intellij.newProjectWizard")
    const val PLACE = "Project Wizard"
  }
}

data class LabelAndComponent(val label: JLabel? = null, val component: JComponent)