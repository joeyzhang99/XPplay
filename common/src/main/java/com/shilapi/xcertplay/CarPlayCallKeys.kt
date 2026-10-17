package com.shilapi.xcertplay

import android.annotation.SuppressLint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.shilapi.xcertplay.orchestration.CarPlayController

/**
 * The steering wheel's call keys during a CarPlay call: the call key answers, the hang-up and
 * multifunction keys end or decline the call. The call key comes as a key event, to the wheel key
 * service or to the CarPlay screen; the hang-up keys only as the system's hang-up broadcast.
 * Outside a CarPlay session every key is left to the car.
 */
internal object CarPlayCallKeys {
    private const val TAG = "XPplay-CallKeys"
    // The car's window manager opens its phone app on the call key's release; CarPlay comes back after it.
    private const val RETURN_DELAY_MILLIS = 700L

    private const val KEYCODE_DIAL_ANSWER = 313
    private const val KEYCODE_HANG_UP = 314
    private const val KEYCODE_MULTIPLEXING = 309
    private const val ACTION_HANG_UP = "com.byd.btcall.action.CLOSE_BLUETOOTHSETTING"
    private const val EXTRA_KEYCODE = "keycode"

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var installed = false

    /** Listens for the system's hang-up broadcast for the life of the process; idempotent. */
    // UnspecifiedRegisterReceiverFlag: the API 33+ branch passes RECEIVER_EXPORTED; the pre-33
    // overload predates the flag by design.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val controller = currentController()
                    if (!controller.hasSession()) return
                    val keyCode = intent.getIntExtra(EXTRA_KEYCODE, -1)
                    if (!isHangUpKey(keyCode)) return
                    val sent = controller?.endCall() == true
                    Log.i(TAG, "hang-up key $keyCode: end CarPlay call sent=$sent")
                }
            }
            // Sent by the system, so it has to be exported.
            runCatching {
                val filter = IntentFilter(ACTION_HANG_UP)
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    app.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null,
                        Context.RECEIVER_EXPORTED)
                } else {
                    app.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null)
                }
            }.onFailure { Log.w(TAG, "hang-up broadcast unavailable", it) }
            installed = true
        }
    }

    /** Returns true when the key belongs to a CarPlay call and must not reach the car. */
    fun onKey(context: Context, keyCode: Int, down: Boolean, controller: CarPlayController? = currentController()): Boolean {
        if (!controller.hasSession()) return false
        when (keyCode) {
            KEYCODE_DIAL_ANSWER, KeyEvent.KEYCODE_CALL -> {
                if (!down) {
                    val sent = controller?.answerCall() == true
                    Log.i(TAG, "call key $keyCode: answer CarPlay call sent=$sent")
                    if (keyCode == KEYCODE_DIAL_ANSWER) returnToCarPlay(context.applicationContext)
                }
                return true
            }
            KEYCODE_HANG_UP, KEYCODE_MULTIPLEXING, KeyEvent.KEYCODE_ENDCALL -> {
                if (!down) {
                    val sent = controller?.endCall() == true
                    Log.i(TAG, "key $keyCode: end CarPlay call sent=$sent")
                }
                return true
            }
            else -> return false
        }
    }

    private fun returnToCarPlay(app: Context) {
        handler.postDelayed({
            if (!currentController().hasSession()) return@postDelayed
            runCatching {
                app.startActivity(
                    Intent(app, CarPlayHostActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            }.onFailure { Log.w(TAG, "could not bring CarPlay back after the call key", it) }
        }, RETURN_DELAY_MILLIS)
    }

    private fun currentController(): CarPlayController? = CarPlayBackgroundSession.snapshot()?.controller

    private fun isHangUpKey(keyCode: Int): Boolean =
        keyCode == KEYCODE_HANG_UP || keyCode == KEYCODE_MULTIPLEXING

    private fun CarPlayController?.hasSession(): Boolean = this?.activeAirPlaySessionToken() != null
}
