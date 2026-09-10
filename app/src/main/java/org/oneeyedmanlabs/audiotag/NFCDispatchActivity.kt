package org.oneeyedmanlabs.audiotag

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.service.NFCTagRouter

/**
 * Invisible activity that receives NFC intents while the app is closed and
 * hands them to [NFCTagRouter].
 *
 * It uses Theme.NoDisplay, and Android requires such an activity to call
 * finish() before onResume() completes; otherwise it throws
 * IllegalStateException and the app crashes on every background scan. So the
 * lookup runs on a coroutine tied to the application, not this activity, and
 * finish() is called synchronously here.
 */
class NFCDispatchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeAndFinish(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeAndFinish(intent)
    }

    private fun routeAndFinish(intent: Intent) {
        Log.d("NFCDispatchActivity", "NFC intent: ${intent.action}")
        if (NFCTagRouter.isNfcIntent(intent)) {
            val appContext = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NFCTagRouter.open(appContext, intent, newTask = true)
                } catch (e: Exception) {
                    Log.e("NFCDispatchActivity", "Failed to route NFC tag", e)
                }
            }
        }
        finish()
    }
}
