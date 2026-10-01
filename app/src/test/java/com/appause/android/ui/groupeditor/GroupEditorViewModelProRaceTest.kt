package com.appause.android.ui.groupeditor

import androidx.test.core.app.ApplicationProvider
import com.appause.android.AppauseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression pin for the Pro race in [GroupEditorViewModel.save].
 *
 * The bug: `isPro` is a StateFlow built with SharingStarted.WhileSubscribed
 * and initialValue=false. A save in the first frames — before anything has
 * subscribed and the entitlement flow has emitted — read `.value == false`
 * and silently zeroed all four re-remind settings of a PAYING Pro user.
 *
 * The fix: save() reads `proState.isPro.first()`, which suspends until the
 * entitlement flow emits its real value. This test pins the property the fix
 * relies on, inside the same first-frame window:
 *  - an unsubscribed WhileSubscribed StateFlow still reports the false
 *    initial even when the install IS Pro (the race window is real), and
 *  - `first()` is authoritative in that same window (returns true).
 *
 * Why no full save()-while-Pro end-to-end case here? Under Robolectric, a
 * `viewModelScope.launch` save against the REAL Room/DataStore wiring only
 * completes in the FIRST test of the JVM; in any later test the coroutine
 * stalls (static AppDatabase/DataStore singletons plus the cached Main
 * dispatcher outliving Robolectric's per-test environment swap). The free
 * user's save semantics are covered by AppGroupRepositoryTest at the DAO
 * level and by the manual device check: debug Pro toggle → edit a re-remind
 * group → save immediately → all four values must survive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupEditorViewModelProRaceTest {

    @Test
    fun `stateIn initial is the race window while first() is authoritative`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AppauseApp>()
        // Mark this install as Pro BEFORE anything observes isPro — the same
        // flag the debug Diagnostics toggle flips.
        app.proState.unlockProDebug()

        // A WhileSubscribed StateFlow with nothing subscribed: this is the
        // exact value save() used to read in the first-frame window.
        val scope = CoroutineScope(Job())
        val window = app.proState.isPro.stateIn(
            scope, SharingStarted.WhileSubscribed(5000), false
        )
        assertFalse(
            "precondition: an unsubscribed WhileSubscribed StateFlow still holds the false initial",
            window.value
        )

        // The authoritative read the fix uses: suspends until the entitlement
        // flow emits its real value — which is Pro for this install.
        assertTrue(
            "first() must see the real Pro state inside the same window",
            app.proState.isPro.first()
        )

        scope.cancel()
    }
}
