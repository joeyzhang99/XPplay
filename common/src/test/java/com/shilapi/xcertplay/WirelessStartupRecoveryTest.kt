package com.shilapi.xcertplay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.WirelessStartupException
import com.shilapi.xcertplay.network.WirelessStartupFailure
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.transport.BluetoothLinkCheck
import com.shilapi.xcertplay.transport.BluetoothRadioCheck
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WirelessStartupRecoveryTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var retry: Button
    private val failure = CarPlayStatus.Failed("timeout", startupFailure = WirelessStartupFailure.FIRST_TCP_TIMEOUT)

    @Before fun setup() {
        CarPlayBackgroundSession.clear()
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        ReflectionHelpers.setField(CarPlayBackgroundSession, "owner", activity)
        retry = Button(activity).apply { visibility = View.GONE }
        ReflectionHelpers.setField(activity, "startupRetryButton", retry)
    }

    @After fun cleanup() {
        ReflectionHelpers.getField<Handler>(activity, "mainHandler").removeCallbacksAndMessages(null)
        CarPlayBackgroundSession.clear()
    }

    private fun report(generation: Int = 0, status: CarPlayStatus = failure) {
        ReflectionHelpers.callInstanceMethod<(CarPlayStatus) -> Unit>(activity, "createStatusReporter",
            from(Int::class.javaPrimitiveType, generation))(status)
    }
    private fun budget() = ReflectionHelpers.getField<WirelessStartupRetryBudget>(activity, "startupRetryBudget")

    @Test fun duplicateFailureAndCleanupErrorScheduleOnlyOneRetry() {
        report()
        report()
        report(status = CarPlayStatus.Failed("Bluetooth socket closed"))
        assertEquals(1, budget().retries)
        assertEquals(1, ReflectionHelpers.getField<Int>(activity, "reconnectAttempts"))
    }

    @Test fun exhaustedBudgetStopsAutomaticRecoveryAndExposesRetry() {
        repeat(6) { generation ->
            ReflectionHelpers.setField(activity, "restartGeneration", generation)
            ReflectionHelpers.setField(activity, "reconnectScheduled", false)
            report(generation)
        }
        assertEquals(5, budget().retries)
        assertTrue(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertEquals(View.VISIBLE, retry.visibility)
        report(5, CarPlayStatus.Failed("Bluetooth socket closed"))
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
    }

    @Test fun unrecoverableConfigurationDoesNotAutomaticallyRetry() {
        report(status = CarPlayStatus.Failed("Permission missing", startupFailure = WirelessStartupFailure.HOTSPOT_CONFIGURATION))
        assertEquals(0, budget().retries)
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertEquals(View.VISIBLE, retry.visibility)
    }

    // No further attempt can make a head unit's Bluetooth reach the phone.
    @Test fun bluetoothThatReachesNoPhoneDoesNotAutomaticallyRetry() {
        report(status = CarPlayStatus.Failed("Android Bluetooth on this head unit reports connections that are not real",
            startupFailure = WirelessStartupFailure.BLUETOOTH_UNUSABLE))
        assertEquals(0, budget().retries)
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertTrue(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertEquals(View.VISIBLE, retry.visibility)
    }

    // Issue 709: every connect fails inside Android. The iPhone is not what is missing, and no retry helps.
    @Test fun bluetoothThatCannotOpenConnectionsSaysSoAndDoesNotRetry() {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        val failure = BluetoothLinkCheck.connectFailure(NullPointerException("Attempt to invoke interface method " +
            "'android.os.ParcelFileDescriptor android.bluetooth.IBluetoothSocketManager.connectSocket(" +
            "android.bluetooth.BluetoothDevice, int, android.os.ParcelUuid, int, int)' on a null object reference"),
            "phone-address") as WirelessStartupException
        report(status = CarPlayStatus.Failed(failure.message!!, startupFailure = failure.reason))
        assertEquals(activity.getString(R.string.bluetooth_cannot_open_connections), stage.text.toString())
        assertEquals(0, budget().retries)
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertTrue(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertEquals(View.VISIBLE, retry.visibility)
    }

    // A head unit whose Android has no Bluetooth adapter: nothing to turn on and nothing to wait for.
    @Test fun aHeadUnitWithoutABluetoothAdapterSaysSoAndDoesNotRetry() {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        val failure = BluetoothRadioCheck.failure(BluetoothRadioCheck.NO_ADAPTER) as WirelessStartupException
        report(status = CarPlayStatus.Failed(failure.message!!, startupFailure = failure.reason))
        assertEquals(activity.getString(R.string.bluetooth_cannot_open_connections), stage.text.toString())
        assertEquals(0, budget().retries)
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertTrue(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertEquals(View.VISIBLE, retry.visibility)
    }

    // The driver can switch Bluetooth on, so the screen asks for it and the attempts go on.
    @Test fun bluetoothThatIsOffAsksForItAndKeepsRetrying() {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        report(status = CarPlayStatus.Failed(BluetoothRadioCheck.RADIO_OFF))
        assertEquals(activity.getString(R.string.turn_on_bluetooth_in_the_head_unit_s_settings_to_connect), stage.text.toString())
        assertTrue(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertFalse(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertEquals(View.GONE, retry.visibility)
    }

    private val radioOff = CarPlayStatus.Failed(
        BluetoothRadioCheck.RADIO_OFF,
        startupFailure = WirelessStartupFailure.BLUETOOTH_OFF,
    )

    // A 2026 CarPlay report made 300 attempts in 2.5 hours because this path had no retry budget.
    @Test fun bluetoothTurnedOffUsesTheRetryBudgetAndThenStops() =
        assertNamedUntilRetriesStop(radioOff, R.string.turn_on_bluetooth_in_the_head_unit_s_settings_to_connect)

    @Test fun bluetoothOffRemembersThatTheRadioIsWhatToWaitFor() {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        report(status = radioOff)
        assertTrue(ReflectionHelpers.getField(activity, "bluetoothOffPending"))
        // A different failure clears it: the Bluetooth-on broadcast must not restart an unrelated stop.
        ReflectionHelpers.setField(activity, "reconnectScheduled", false)
        ReflectionHelpers.setField(activity, "restartGeneration", 1)
        report(1, CarPlayStatus.Failed("timeout", startupFailure = WirelessStartupFailure.FIRST_TCP_TIMEOUT))
        assertFalse(ReflectionHelpers.getField(activity, "bluetoothOffPending"))
    }

    @Test fun turningBluetoothOnResumesAStoppedAttempt() {
        val sizeClass = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")
        val size = sizeClass
            .getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(1080, 1564)
        ReflectionHelpers.setField(activity, "wirelessEnabled", true)
        ReflectionHelpers.setField(activity, "activeDisplaySize", size)
        ReflectionHelpers.setField(activity, "bluetoothOffPending", true)
        ReflectionHelpers.setField(activity, "startupRetryStopped", true)
        ReflectionHelpers.setField(activity, "restartGeneration", 0)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "resumeWirelessAfterBluetoothTurnedOn")
        assertEquals(1, ReflectionHelpers.getField<Int>(activity, "restartGeneration"))
        assertFalse(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertFalse(ReflectionHelpers.getField(activity, "bluetoothOffPending"))
        assertEquals(0, budget().retries)
        assertEquals(View.GONE, retry.visibility)
    }

    @Test fun bluetoothOnIsIgnoredUnlessTheRadioWasTheReason() {
        ReflectionHelpers.setField(activity, "wirelessEnabled", true)
        ReflectionHelpers.setField(activity, "restartGeneration", 0)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "resumeWirelessAfterBluetoothTurnedOn")
        assertEquals(0, ReflectionHelpers.getField<Int>(activity, "restartGeneration"))
    }

    // The head unit has no VPN consent dialog, so a USB-plug wired attempt on a wireless setup can
    // never start. It must return to the saved wireless connection instead of staying on the error.
    @Test fun aUsbWiredTrialReturnsToTheSavedWirelessConnectionWhenTheVpnDialogIsMissing() {
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        ReflectionHelpers.setField(activity, "usbAttachWiredTrial", true)
        ReflectionHelpers.setField(activity, "wirelessEnabled", false)
        ReflectionHelpers.setField(activity, "vpnAdbConsentTried", true)
        ReflectionHelpers.setField(activity, "menuOpen", true) // keep the start out of this test
        ReflectionHelpers.setField(activity, "stageStatusView", TextView(activity))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onVpnConsentUnavailable",
            from(String::class.java, "launch"),
            ReflectionHelpers.ClassParameter.from(Intent::class.java, null),
            from(RuntimeException::class.java, ActivityNotFoundException("no dialog")))
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "wirelessEnabled"))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "usbAttachWiredTrial"))
        assertTrue(AirPlayPersistence.loadWirelessEnabled(activity))
    }

    @Test fun aDeniedVpnConsentAlsoReturnsAUsbWiredTrialToWireless() {
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        ReflectionHelpers.setField(activity, "usbAttachWiredTrial", true)
        ReflectionHelpers.setField(activity, "wirelessEnabled", false)
        ReflectionHelpers.setField(activity, "awaitingVpnConsent", true)
        ReflectionHelpers.setField(activity, "menuOpen", true)
        ReflectionHelpers.setField(activity, "stageStatusView", TextView(activity))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onVpnConsentResult",
            from(Int::class.javaPrimitiveType, Activity.RESULT_CANCELED))
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "wirelessEnabled"))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "usbAttachWiredTrial"))
        assertTrue(AirPlayPersistence.loadWirelessEnabled(activity))
    }

    // A driver who saved wired as the transport keeps it: only a wireless preference falls back.
    @Test fun aUsbWiredTrialLeavesAWiredPreferenceAlone() {
        AirPlayPersistence.saveWirelessEnabled(activity, false)
        ReflectionHelpers.setField(activity, "usbAttachWiredTrial", true)
        ReflectionHelpers.setField(activity, "wirelessEnabled", false)
        ReflectionHelpers.setField(activity, "vpnAdbConsentTried", true)
        ReflectionHelpers.setField(activity, "menuOpen", true)
        ReflectionHelpers.setField(activity, "stageStatusView", TextView(activity))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onVpnConsentUnavailable",
            from(String::class.java, "launch"),
            ReflectionHelpers.ClassParameter.from(Intent::class.java, null),
            from(RuntimeException::class.java, ActivityNotFoundException("no dialog")))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "wirelessEnabled"))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "usbAttachWiredTrial"))
        assertFalse(AirPlayPersistence.loadWirelessEnabled(activity))
    }

    /** Fails every attempt with [status] until the retries stop, checking the screen names [cause] meanwhile. */
    private fun assertNamedUntilRetriesStop(status: CarPlayStatus.Failed, cause: Int) {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        repeat(6) { generation ->
            ReflectionHelpers.setField(activity, "restartGeneration", generation)
            ReflectionHelpers.setField(activity, "reconnectScheduled", false)
            report(generation, status)
            if (generation < 5) assertEquals(activity.getString(cause), stage.text.toString())
        }
        assertTrue(ReflectionHelpers.getField(activity, "startupRetryStopped"))
        assertEquals(View.VISIBLE, retry.visibility)
        assertEquals(activity.getString(cause) + "\n" + activity.getString(R.string.wireless_startup_retries_exhausted),
            stage.text.toString())
    }

    private val hotspotNotReady = CarPlayStatus.Failed("Could not establish MANUAL hotspot: Hotspot network is not ready",
        startupFailure = WirelessStartupFailure.HOTSPOT_NOT_READY)

    // #809: the screen said "Getting CarPlay ready…" while the car hotspot never came up, and after the retries stopped.
    @Test fun aHotspotThatIsNotReadyIsNamedAndTheStopIsSaid() =
        assertNamedUntilRetriesStop(hotspotNotReady, R.string.hotspot_network_not_ready)

    @Test @Config(qualifiers = "zh-rCN") fun aHotspotThatIsNotReadyIsNamedInChinese() {
        assertNotEquals("Hotspot network is not ready", activity.getString(R.string.hotspot_network_not_ready))
        assertNamedUntilRetriesStop(hotspotNotReady, R.string.hotspot_network_not_ready)
    }

    @Test fun anIphoneThatDoesNotConnectInTimeIsNamedAndTheStopIsSaid() =
        assertNamedUntilRetriesStop(failure, R.string.first_tcp_timeout)

    // Nothing is retried for a car hotspot DiPlay cannot use, so the screen does not say "Retrying…".
    @Test fun aHotspotDiPlayCannotUseSaysTheRetriesStopped() {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        listOf("The car hotspot is off. Turn it on in the car settings and connect again.",
            "Hotspot control permission is missing. Open the car hotspot settings and connect again.",
            "Manual hotspot is not running on 5 GHz").forEachIndexed { generation, cause ->
            ReflectionHelpers.setField(activity, "restartGeneration", generation)
            ReflectionHelpers.setField(activity, "startupRetryStopped", false)
            report(generation, CarPlayStatus.Failed(cause, startupFailure = WirelessStartupFailure.HOTSPOT_CONFIGURATION))
            assertEquals(cause, activity.getString(R.string.hotspot_network_not_ready) + "\n" +
                activity.getString(R.string.wireless_startup_retries_exhausted), stage.text.toString())
            assertEquals(0, budget().retries)
            assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        }
    }

    // The stopped attempt can still report its own failure again, or its cleanup's.
    @Test fun aLaterFailureOfTheStoppedAttemptKeepsTheLineSayingTheRetriesStopped() {
        val stage = TextView(activity)
        ReflectionHelpers.setField(activity, "stageStatusView", stage)
        val offline = CarPlayStatus.Failed("The car hotspot is off. Turn it on in the car settings and connect again.",
            startupFailure = WirelessStartupFailure.HOTSPOT_CONFIGURATION)
        report(status = offline)
        val stopped = activity.getString(R.string.hotspot_network_not_ready) + "\n" +
            activity.getString(R.string.wireless_startup_retries_exhausted)
        assertEquals(stopped, stage.text.toString())
        report(status = offline)
        report(status = CarPlayStatus.Failed("Bluetooth socket closed"))
        assertEquals(stopped, stage.text.toString())
        assertFalse(ReflectionHelpers.getField(activity, "reconnectScheduled"))
        assertEquals(View.VISIBLE, retry.visibility)
    }

    @Test fun staleGenerationUserDisconnectMenuAndDifferentOwnerCannotSchedule() {
        report(1)
        assertEquals(0, budget().retries)
        ReflectionHelpers.setField(activity, "menuOpen", true)
        report()
        assertEquals(0, budget().retries)
        ReflectionHelpers.setField(activity, "menuOpen", false)
        ReflectionHelpers.getField<AtomicBoolean>(activity, "shuttingDown").set(true)
        report()
        assertEquals(0, budget().retries)
        ReflectionHelpers.getField<AtomicBoolean>(activity, "shuttingDown").set(false)
        ReflectionHelpers.setField(CarPlayBackgroundSession, "owner", Any())
        report()
        assertEquals(0, budget().retries)
    }
}
