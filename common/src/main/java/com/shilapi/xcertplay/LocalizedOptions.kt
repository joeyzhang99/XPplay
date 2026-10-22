package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.CarPlaySize
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.transport.EvChargingConnectors

internal fun CarPlaySize.localizedLabel(context: Context): String = context.getString(when (this) {
    CarPlaySize.LARGE -> R.string.option_size_large
    CarPlaySize.MEDIUM -> R.string.option_size_medium
    CarPlaySize.SMALL -> R.string.option_size_small
})

/** [custom] marks the row that opens the length slider; its [widthMillimeters] is where the slider starts. */
internal data class CarPlaySizeChoice(val label: String, val widthMillimeters: Int, val custom: Boolean = false)

/** The three presets with their lengths, then the Custom row: the saved length when it is no preset, else "Custom…". */
internal fun carPlaySizeChoices(context: Context, savedMillimeters: Int): List<CarPlaySizeChoice> {
    fun named(name: String, millimeters: Int, custom: Boolean = false) =
        CarPlaySizeChoice(context.getString(R.string.size_option_millimeters, name, millimeters), millimeters, custom)
    val presets = CarPlaySize.entries.map { named(it.localizedLabel(context), it.widthMillimeters) }
    val custom = if (CarPlaySize.exact(savedMillimeters) != null) {
        CarPlaySizeChoice(context.getString(R.string.option_size_custom) + "\u2026", savedMillimeters, custom = true)
    } else named(context.getString(R.string.option_size_custom), savedMillimeters, custom = true)
    return presets + custom
}

internal fun EvChargingConnectors.localizedLabel(context: Context): String = context.getString(when (this) {
    EvChargingConnectors.CCS2_TYPE2 -> R.string.connectors_ccs2_type2
    EvChargingConnectors.GB_T -> R.string.connectors_gb_t
    EvChargingConnectors.CCS1_J1772 -> R.string.connectors_ccs1_j1772
})

internal fun ManualHotspotValidation.Error.messageResource(): Int = when (this) {
    ManualHotspotValidation.Error.EMPTY_NAME -> R.string.hotspot_error_empty_name
    ManualHotspotValidation.Error.LONG_NAME -> R.string.hotspot_error_long_name
    ManualHotspotValidation.Error.INVALID_CHARACTER -> R.string.hotspot_error_invalid_character
    ManualHotspotValidation.Error.PASSWORD_LENGTH -> R.string.hotspot_error_password_length
}
