// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.ui.IconDeferrer
import com.intellij.util.containers.ObjectLongHashMap
import gnu.trove.THashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Icon
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class TimedIconCache {
  private val idToIcon = THashMap<String, Icon>()
  private val idToInvalid = THashMap<String, Boolean>()
  private val iconCheckTimes = ObjectLongHashMap<String>()
  private val iconCalcTime = ObjectLongHashMap<String>()
  private val idToSettings = THashMap<String, RunnerAndConfigurationSettings>()

  private val lock = ReentrantReadWriteLock()

  fun remove(id: String) {
    lock.write {
      idToIcon.remove(id)
      iconCheckTimes.remove(id)
      iconCalcTime.remove(id)
      idToSettings.remove(id)
    }
  }

  fun get(id: String, settings: RunnerAndConfigurationSettings, project: Project): Icon {
    return lock.read { idToIcon.get(id) } ?: lock.write {
      idToIcon.get(id)?.let {
        return it
      }

      lock.write { 
        idToSettings[id] = settings 
      }

      val icon = deferIcon(id, settings.configuration.icon, project.hashCode() xor settings.hashCode(), project)

      set(id, icon)
      icon
    }
  }

  private fun deferIcon(id: String, baseIcon: Icon?, hash: Int, project: Project): Icon {
    return IconDeferrer.getInstance().deferAutoUpdatable(baseIcon, hash) {
      if (project.isDisposed) {
        return@deferAutoUpdatable null
      }

      lock.write {
        iconCalcTime.remove(id)
      }

      val startTime = System.currentTimeMillis()
      val iconToValid = try {
        calcIcon(id, project)
      }
      catch (e: ProcessCanceledException) {
        return@deferAutoUpdatable null
      }

      lock.write {
        iconCalcTime.put(id, System.currentTimeMillis() - startTime)
        idToInvalid.set(id, iconToValid.second)
      }
      return@deferAutoUpdatable iconToValid.first
    }
  }

  fun isInvalid(id: String) : Boolean {
    idToInvalid.get(id)?.let {return it}
    return false
  }

  private fun calcIcon(id: String, project: Project): Pair<Icon, Boolean> {
    val settings = idToSettings[id]
    if (settings == null) return AllIcons.Actions.Help to false

    try {
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(project, Runnable {
        settings.checkSettings()
      })
      return ProgramRunnerUtil.getConfigurationIcon(settings, false) to false
    }
    catch (e: IndexNotReadyException) {
      return ProgramRunnerUtil.getConfigurationIcon(settings, false) to false
    }
    catch (ignored: RuntimeConfigurationException) {
      val invalid = !DumbService.isDumb(project)
      return ProgramRunnerUtil.getConfigurationIcon(settings, invalid) to invalid
    }
  }

  private fun set(id: String, icon: Icon) {
    idToIcon.put(id, icon)
    iconCheckTimes.put(id, System.currentTimeMillis())
  }

  fun clear() {
    lock.write {
      idToIcon.clear()
      iconCheckTimes.clear()
      iconCalcTime.clear()
      idToSettings.clear()
    }
  }

  fun checkValidity(id: String) {
    lock.read {
      val lastCheckTime = iconCheckTimes.get(id)
      var expired = lastCheckTime == -1L
      if (!expired) {
        var calcTime = iconCalcTime.get(id)
        if (calcTime == -1L || calcTime < 150) {
          calcTime = 150L
        }
        expired = (System.currentTimeMillis() - lastCheckTime) > (calcTime * 10)
      }

      if (expired) {
        lock.write {
          idToIcon.remove(id)
        }
      }
    }
  }
}