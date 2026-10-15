package io.github.ckirby04.nemesis

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Boots [TestNemesisApplication] instead of the real one (wired in app/build.gradle.kts). */
class NemesisTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestNemesisApplication::class.java.name, context)
}
