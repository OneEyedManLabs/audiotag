package org.oneeyedmanlabs.audiotag

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.service.NFCTagRouter

/**
 * Receives NFC intents while the app is closed and hands them to
 * [NFCTagRouter], then finishes.
 *
 * The theme is translucent rather than Theme.NoDisplay. Android requires a
 * NoDisplay activity to call finish() before onResume() completes, and this
 * activity has to wait for a database lookup, so every background scan used
 * to end in an IllegalStateException. A translucent activity may live for
 * the lookup; while it is on screen the app is in the foreground, so it is
 * always allowed to start the next activity. The user sees nothing either
 * way.
 */
class NFCDispatchActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun route(intent: Intent) {
        Log.d("NFCDispatchActivity", "NFC intent: ${intent.action}")
        if (!NFCTagRouter.isNfcIntent(intent)) {
            finish()
            return
        }
        scope.launch {
            try {
                NFCTagRouter.open(this@NFCDispatchActivity, intent, newTask = true)
            } catch (e: Exception) {
                Log.e("NFCDispatchActivity", "Failed to route NFC tag", e)
            } finally {
                finish()
            }
        }
    }
}
