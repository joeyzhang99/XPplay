package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSettings

/**
 * The car-hotspot controls on the connection page: the WRITE_SETTINGS toggle for this head unit and
 * the ADB grant flow behind the "open after the car starts" switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE,
    shadows = [CarHotspotSwitchTest.Grant::class, CarHotspotAdbGrantTest.WritePermission::class])
class CarHotspotSwitchTest {
    private lateinit var activity: DiPlayActivity
    private lateinit var controls: LinearLayout

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot",
            "xcertplay_airplay", "diplay")) {
            app.getSharedPreferences(name, 0).edit().clear().commit()
        }
        CarPlayBackgroundSession.clear()
        CarHotspotAdbGrantTest.WritePermission.allowed = false
        ShadowSettings.setCanDrawOverlays(false)
        Grant.entered = CountDownLatch(1)
        Grant.release = CountDownLatch(1)
        Grant.worker = null
        Grant.requested.clear()
        Grant.fail = false
        Grant.allowed = CarHotspotSetup.Permission.entries.toSet()
        Grant.access = LocalAdb.Access.READY
        // The switch's worker thread resolves these classes off the test thread; a lazy first
        // initialization inside a busy sandbox can stall the grant call there.
        Class.forName(CarHotspotSetup::class.java.name, true, javaClass.classLoader)
        Class.forName(LocalAdb::class.java.name, true, javaClass.classLoader)
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveAutoStartOnBoot(activity, false)
        controls = LinearLayout(activity)
        renderConnectionSettings(controls)
        activity.setContentView(controls)
    }

    @After fun tearDown() {
        Grant.release.countDown()
        Grant.worker?.join(10_000)
        shadowOf(Looper.getMainLooper()).idle()
        CarPlayBackgroundSession.clear()
    }

    private fun renderConnectionSettings(target: LinearLayout) {
        // The hotspot card and the boot-launch switch both live on the connection page.
        DiPlayActivity::class.java.getDeclaredMethod("connectionSettings", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, target)
    }

    // ---------- the WRITE_SETTINGS hotspot control ----------

    @Test fun theHotspotTogglePersistsWithoutAdb() {
        assertFalse(CarHotspotSettings.enabled(activity))
        hotspotSwitch().isChecked = true
        assertTrue(CarHotspotSettings.enabled(activity))
        hotspotSwitch().isChecked = false
        assertFalse(CarHotspotSettings.enabled(activity))
    }

    @Test fun theHotspotControlIsHiddenWithoutTheManualHotspotMode() {
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.WIFI_P2P)
        val fresh = LinearLayout(activity)
        renderConnectionSettings(fresh)
        assertNull(switches(fresh).firstOrNull {
            it.contentDescription == activity.getString(R.string.auto_car_hotspot_title)
        })
    }

    // ---------- the ADB grant flow behind the boot-launch switch ----------

    @Test fun enablingBootLaunchWaitsForThePermissionGrant() {
        CarHotspotSettings.setEnabled(activity, true)
        bootSwitch().isChecked = true
        awaitGrant()
        assertEquals(listOf(CarHotspotSetup.Permission.BOOT_LAUNCH), Grant.requested)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
        completeGrant()
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertTrue(bootSwitch().isChecked)
    }

    @Test fun anAdbFailureKeepsBootLaunchOff() {
        CarHotspotSettings.setEnabled(activity, true)
        Grant.fail = true
        bootSwitch().isChecked = true
        awaitGrant()
        completeGrant()
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertFalse(bootSwitch().isChecked)
    }

    @Test fun anOutstandingGrantBlocksASecondAuthorizationFlow() {
        CarHotspotSettings.setEnabled(activity, true)
        bootSwitch().isChecked = true
        awaitGrant()
        assertFalse(bootSwitch().isEnabled)
        assertEquals(listOf(CarHotspotSetup.Permission.BOOT_LAUNCH), Grant.requested)
        completeGrant()
        assertTrue(bootSwitch().isEnabled)
    }

    // Generous compared with the product's own pace: it only has to fail when the grant is
    // never entered at all, not to measure how quickly the worker thread gets there.
    private fun awaitGrant() {
        val ok = Grant.entered.await(10, TimeUnit.SECONDS)
        assertTrue(ok)
    }

    private fun completeGrant() {
        Grant.release.countDown()
        Grant.worker!!.join(10_000)
        assertFalse(Grant.worker!!.isAlive)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun hotspotSwitch(): Switch = switchFor(R.string.auto_car_hotspot_title)

    private fun switchFor(title: Int): Switch = switches(controls).single {
        it.contentDescription == activity.getString(title)
    }

    // Both the full settings list and the connection page render the section that owns this switch.
    private fun bootSwitch(): Switch = switches(controls).first {
        it.contentDescription == activity.getString(R.string.open_after_the_car_starts)
    }

    private fun switches(view: View): List<Switch> = buildList {
        if (view is Switch) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(switches(view.getChildAt(i)))
    }

    @Implements(CarHotspotSetup::class, isInAndroidSdk = false)
    internal class Grant {
        @Implementation fun check(context: Context, adb: LocalAdb): LocalAdb.Access = LocalAdb.Access.UNREACHABLE

        @Implementation fun grant(context: Context, permissions: List<CarHotspotSetup.Permission>, adb: LocalAdb): LocalAdb.Access {
            worker = Thread.currentThread()
            requested.addAll(permissions)
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            check(!fail) { "ADB connection failed" }
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (permission !in allowed) break
                    when (permission) {
                        CarHotspotSetup.Permission.HOTSPOT -> CarHotspotAdbGrantTest.WritePermission.allowed = true
                        CarHotspotSetup.Permission.BOOT_LAUNCH -> ShadowSettings.setCanDrawOverlays(true)
                    }
                }
            }
            return access
        }

        companion object {
            lateinit var entered: CountDownLatch
            lateinit var release: CountDownLatch
            lateinit var allowed: Set<CarHotspotSetup.Permission>
            lateinit var access: LocalAdb.Access
            val requested = CopyOnWriteArrayList<CarHotspotSetup.Permission>()
            @Volatile var worker: Thread? = null
            @Volatile var fail = false
        }
    }
}
