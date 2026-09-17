package com.appause.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.appause.android.ui.appselect.AppSelectViewModel
import com.appause.android.ui.feedback.FeedbackViewModel
import com.appause.android.ui.groupeditor.GroupEditorViewModel
import com.appause.android.ui.home.HomeViewModel
import com.appause.android.ui.onboarding.OnboardingViewModel
import com.appause.android.ui.pro.ProViewModel
import com.appause.android.ui.recommended.RecommendedAppsViewModel
import com.appause.android.ui.settings.SettingsViewModel
import com.appause.android.ui.stats.StatsViewModel
import java.io.File
import java.util.Collections
import kotlin.reflect.KClass
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards a whole crash class that the rest of the test suite is structurally
 * unable to see.
 *
 * Why this exists: when a screen calls Compose's `viewModel()` without a
 * factory, `AndroidViewModelFactory` reflectively looks for a constructor that
 * takes exactly one `Application` argument. Kotlin default parameters do NOT
 * generate that constructor in bytecode — so as soon as someone adds a
 * default-valued test-seam parameter to a ViewModel's primary constructor,
 * every user tapping that screen gets `NoSuchMethodException` and the app
 * dies. That is exactly what happened to [StatsViewModel] from v0.5.39 to
 * v0.5.42: 216 unit tests stayed green the whole time because they build the
 * ViewModel through the injected constructor and never go through the default
 * factory's reflection.
 *
 * We assert the contract directly on the bytecode instead of instantiating the
 * ViewModels, so this test needs no Android runtime at all — no Robolectric,
 * no emulator — and stays fast and free of side effects.
 */
class ViewModelFactoryContractTest {

    /**
     * Every AndroidViewModel reachable from a Compose screen. Keep this in sync
     * when adding a ViewModel: [theCoveredListMatchesTheClasspath] fails if a
     * new one shows up that is not listed here.
     */
    private val viewModels: List<KClass<out AndroidViewModel>> = listOf(
        AppSelectViewModel::class,
        FeedbackViewModel::class,
        GroupEditorViewModel::class,
        HomeViewModel::class,
        OnboardingViewModel::class,
        ProViewModel::class,
        RecommendedAppsViewModel::class,
        SettingsViewModel::class,
        StatsViewModel::class,
    )

    @Test
    fun everyViewModelExposesTheSingleApplicationConstructor() {
        val broken = viewModels.filter { viewModel ->
            runCatching {
                viewModel.java.getConstructor(Application::class.java)
            }.isFailure
        }

        assertTrue(
            "These ViewModels have no (Application) constructor, so a screen using the " +
                "default viewModel() helper crashes with NoSuchMethodException on every " +
                "entry (this is how StatsViewModel broke in v0.5.39). Fix: annotate the " +
                "primary constructor with @JvmOverloads. Broken: ${broken.map { it.simpleName }}",
            broken.isEmpty(),
        )
    }

    /**
     * Catches the blind spot of a hand-written list: a brand-new ViewModel that
     * nobody remembered to add to [viewModels]. Skipped if the classpath is not
     * laid out as directories (e.g. packaged as a jar) so this never turns the
     * CI gate red for an unrelated reason.
     */
    @Test
    fun theCoveredListMatchesTheClasspath() {
        val notCovered = discoverViewModelClassNames() - viewModels.map { it.java.name }.toSet()

        assertTrue(
            "New ViewModel(s) found on the classpath but missing from the list in this " +
                "test: $notCovered. Add them so the factory contract is enforced for them too.",
            notCovered.isEmpty(),
        )
    }

    /**
     * Finds every compiled `...ViewModel` class under `com.appause.android`.
     * Returns an empty set when the classes are not on disk as directories, so
     * the caller can treat "can't tell" as "nothing to report".
     */
    private fun discoverViewModelClassNames(): Set<String> {
        val loader = ViewModelFactoryContractTest::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()
        val packageRoots = Collections.list(loader.getResources(BASE_PACKAGE_PATH))
        val found = mutableSetOf<String>()

        for (root in packageRoots) {
            if (root.protocol != "file") continue
            val directory = runCatching { File(root.toURI()) }.getOrNull() ?: continue
            if (!directory.isDirectory) continue

            directory.walkTopDown()
                .filter { it.isFile && it.name.endsWith("ViewModel.class") }
                .forEach { file ->
                    val nestedPath = file.relativeTo(directory).path.removeSuffix(".class")
                    val className = "${BASE_PACKAGE_PATH.replace('/', '.')}." +
                        nestedPath.replace(File.separatorChar, '.')
                    // initialize=false: we only need the type, never its static init.
                    val loaded = runCatching {
                        Class.forName(className, false, loader)
                    }.getOrNull() ?: return@forEach
                    if (AndroidViewModel::class.java.isAssignableFrom(loaded)) {
                        found += className
                    }
                }
        }
        return found
    }

    private companion object {
        const val BASE_PACKAGE_PATH = "com/appause/android"
    }
}
