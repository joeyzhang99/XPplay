package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalOnlyHotspotChannelPolicyTest {
    private fun resolve(
        band: String = "2.4 GHz",
        live: Int? = null,
        configured: Int = 0,
        requested: Int? = null,
        scanned: Int? = null,
        station: Int? = null,
    ) = LocalOnlyHotspotChannelPolicy.resolve(
        bandLabel = band,
        liveFrequencyMHz = live,
        configuredChannel = configured,
        requestedChannel = requested,
        scannedChannel = scanned,
        stationChannel = station,
    )

    @Test fun aMeasuredFrequencyWinsOverEveryHint() {
        assertEquals(13, resolve(live = 2472, configured = 3, requested = 36, scanned = 1, station = 6))
        assertEquals(6, resolve(live = 2437, configured = 11))
    }

    @Test fun theReservationChannelWinsWhenTheFrameworkReportsOne() {
        assertEquals(11, resolve(configured = 11, requested = 36, scanned = 1, station = 6))
    }

    @Test fun theRequestedChannelIsUsedOnTheCustomSoftApPath() {
        assertEquals(149, resolve(band = "5 GHz", requested = 149, scanned = 44))
    }

    @Test fun aScanResultFillsInAnAutomaticChannel() {
        assertEquals(6, resolve(scanned = 6, station = 1))
    }

    @Test fun theStationChannelIsOnlyAGuessForTwoPointFour() {
        assertEquals(13, resolve(station = 13))
        assertEquals(36, resolve(band = "5 GHz", station = 13))
    }

    @Test fun theBandDefaultsAreTheLastResort() {
        assertEquals(6, resolve())
        assertEquals(36, resolve(band = "5 GHz"))
    }

    @Test fun invalidHintsAreIgnored() {
        assertEquals(6, resolve(configured = -1, scanned = 0, station = 200))
    }
}
