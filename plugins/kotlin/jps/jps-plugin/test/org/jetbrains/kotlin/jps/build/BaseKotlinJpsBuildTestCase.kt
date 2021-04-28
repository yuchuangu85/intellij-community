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

package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.kotlin.compilerRunner.JpsKotlinCompilerRunner
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.AndroidStudioTestUtils

abstract class BaseKotlinJpsBuildTestCase : JpsBuildTestCase() {
    override fun setUp() {
        super.setUp()
        System.setProperty("kotlin.jps.tests", "true")
    }

    override fun shouldRunTest(): Boolean {
        return super.shouldRunTest() && !AndroidStudioTestUtils.skipIncompatibleTestAgainstAndroidStudio()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable {
                System.clearProperty("kotlin.jps.tests")
                myModel = null
                myBuildParams.clear()
            },
            ThrowableRunnable { JpsKotlinCompilerRunner.releaseCompileServiceSession() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun addJdk(name: String, path: String?): JpsSdk<JpsDummyElement> {
        val homePath = System.getProperty("java.home")
        val versionString = System.getProperty("java.version")
        val jdk = myModel.global.addSdk(name, homePath, versionString, JpsJavaSdkType.INSTANCE)
        jdk.addRoot(StandardFileSystems.JRT_PROTOCOL_PREFIX + homePath + URLUtil.JAR_SEPARATOR + "java.base", JpsOrderRootType.COMPILED)
        return jdk.properties
    }

    private val libraries = mutableMapOf<String, JpsLibrary>()

    protected fun requireLibrary(library: KotlinJpsLibrary) = libraries.getOrPut(library.id) {
        library.create(myProject)
    }
}
