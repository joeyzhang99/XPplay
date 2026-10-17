package com.shilapi.xcertplay.transport

/** Stable identity of one USB device (vendor/product pair). */
data class UsbDeviceId(val vendorId: Int, val productId: Int)
