package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.network.WirelessStartupException
import com.shilapi.xcertplay.network.WirelessStartupFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRadioCheckTest {
    @Test fun aRadioThatIsOnLetsTheAttemptGoOn() {
        assertNull(BluetoothRadioCheck.blocker(adapterPresent = true, enabled = true, pausedByDiPlay = false))
    }

    // An Allwinner T8 head unit came up with Bluetooth off. A fork build that still offered the
    // local-only hotspot made 94 attempts in under six hours; each started the hotspot and only
    // then failed on the radio.
    @Test fun aRadioThatIsOffStopsTheAttemptBeforeTheHotspot() {
        assertEquals(
            BluetoothRadioCheck.RADIO_OFF,
            BluetoothRadioCheck.blocker(adapterPresent = true, enabled = false, pausedByDiPlay = false),
        )
    }

    // The pause is undone before the handshake; stopping here would never let that happen.
    @Test fun aRadioThatDiPlayPausedDoesNotStopTheAttempt() {
        assertNull(BluetoothRadioCheck.blocker(adapterPresent = true, enabled = false, pausedByDiPlay = true))
    }

    @Test fun aHeadUnitWithoutAnAdapterStopsTheAttemptWhateverElseIsKnown() {
        for (paused in listOf(false, true)) {
            assertEquals(
                BluetoothRadioCheck.NO_ADAPTER,
                BluetoothRadioCheck.blocker(adapterPresent = false, enabled = false, pausedByDiPlay = paused),
            )
        }
    }

    // No retry gives a head unit a Bluetooth adapter; it used to be retried for ever as "Connecting to your iPhone".
    @Test fun aMissingAdapterStopsTheAttemptsWithItsCause() {
        val failure = BluetoothRadioCheck.failure(BluetoothRadioCheck.NO_ADAPTER)
        assertTrue(failure is WirelessStartupException)
        assertEquals(WirelessStartupFailure.BLUETOOTH_UNUSABLE, (failure as WirelessStartupException).reason)
        assertFalse(failure.reason.retryable)
        assertEquals(BluetoothRadioCheck.NO_ADAPTER, failure.message)
        assertTrue(BluetoothRadioCheck.NO_ADAPTER.startsWith("Bluetooth adapter is unavailable"))
        assertTrue(BluetoothRadioCheck.NO_ADAPTER.contains(BluetoothLinkCheck.NO_SOCKETS_MARK))
    }

    // The driver can switch the radio on, so the attempt is retried — but only through the host's
    // retry budget, which stops it instead of rebuilding CarPlay every 30 s for hours.
    @Test fun aRadioThatIsOffIsRetriedThroughTheStartupBudget() {
        val failure = BluetoothRadioCheck.failure(BluetoothRadioCheck.RADIO_OFF)
        assertTrue(failure is WirelessStartupException)
        assertEquals(WirelessStartupFailure.BLUETOOTH_OFF, (failure as WirelessStartupException).reason)
        assertTrue(failure.reason.retryable)
        assertEquals(BluetoothRadioCheck.RADIO_OFF, failure.message)
    }

    // A radio that is turning on is not a failure: the handshake starts once Android finishes.
    @Test fun adapterStatesDecideWhetherTheAttemptCanGoOn() {
        val states = mapOf(
            android.bluetooth.BluetoothAdapter.STATE_ON to null,
            android.bluetooth.BluetoothAdapter.STATE_TURNING_ON to null,
            android.bluetooth.BluetoothAdapter.STATE_OFF to BluetoothRadioCheck.RADIO_OFF,
            android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF to BluetoothRadioCheck.RADIO_OFF,
        )
        for ((state, expected) in states) {
            assertEquals("state=$state", expected,
                BluetoothRadioCheck.blockerForState(adapterPresent = true, state = state, pausedByDiPlay = false))
        }
        assertEquals(BluetoothRadioCheck.NO_ADAPTER,
            BluetoothRadioCheck.blockerForState(adapterPresent = false, state = null, pausedByDiPlay = false))
        assertEquals(BluetoothRadioCheck.NO_ADAPTER,
            BluetoothRadioCheck.blockerForState(adapterPresent = true, state = null, pausedByDiPlay = false))
        assertNull(BluetoothRadioCheck.blockerForState(adapterPresent = true,
            state = android.bluetooth.BluetoothAdapter.STATE_OFF, pausedByDiPlay = true))
    }

    // CarPlayHostActivity shows the request in the app's language by finding these words.
    @Test fun theRequestCarriesTheWordsTheHostScreenLooksFor() {
        assertTrue(BluetoothRadioCheck.RADIO_OFF.contains(BluetoothRadioCheck.RADIO_OFF_MARK))
    }
}
