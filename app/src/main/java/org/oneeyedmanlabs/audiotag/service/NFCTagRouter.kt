package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oneeyedmanlabs.audiotag.TagInfoActivity
import org.oneeyedmanlabs.audiotag.UnknownTagActivity

/**
 * The one place that decides what a scanned tag opens.
 *
 * Both the background path (NFCDispatchActivity) and the foreground path
 * (MainActivity's foreground dispatch) used to make this decision separately,
 * and the foreground one sent every tag to TagInfoActivity, which quietly
 * closed itself when the tag was new. Now a known tag opens TagInfoActivity
 * and a new tag opens UnknownTagActivity whichever way it was scanned.
 */
object NFCTagRouter {

    private const val TAG = "NFCTagRouter"

    fun isNfcIntent(intent: Intent): Boolean {
        return intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
    }

    /**
     * Look the tag up and open the right screen.
     *
     * Uses the application context for the lookup and, with [newTask], for the
     * launch, so it is safe to call from an activity that has already finished.
     * Returns false when the intent carried no usable tag.
     */
    suspend fun open(context: Context, intent: Intent, newTask: Boolean): Boolean {
        val application = context.applicationContext as AudioTaggerApplication
        val repository = application.repository
        val nfcService = NFCService()

        var tagId = nfcService.getTagIdFromIntent(intent)
        if (tagId == null) {
            Log.e(TAG, "No tag found in intent ${intent.action}")
            return false
        }

        var existingTag = repository.getTag(tagId)

        // A tag written by an older build carries its ID in NDEF, but the
        // database may only know the hardware ID (or the other way round).
        if (existingTag == null) {
            val hardwareId = nfcService.getHardwareTagIdFromIntent(intent)
            if (hardwareId != null && hardwareId != tagId) {
                existingTag = repository.getTag(hardwareId)
                tagId = hardwareId
            }
        }

        val target = if (existingTag != null) TagInfoActivity::class.java else UnknownTagActivity::class.java
        Log.d(TAG, "Tag $tagId ${if (existingTag != null) "known" else "unknown"}, opening ${target.simpleName}")

        val launch = Intent(application, target).apply {
            putExtra("tag_id", tagId)
            putExtra("from_nfc_scan", true)
            if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) {
            (if (newTask) application else context).startActivity(launch)
        }
        return true
    }
}
