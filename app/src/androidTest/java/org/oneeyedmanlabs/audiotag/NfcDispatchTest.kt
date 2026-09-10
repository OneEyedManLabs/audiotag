package org.oneeyedmanlabs.audiotag

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import java.io.File

/**
 * Drives the NFC entry points with a mock Tag, the way the system would when
 * a real tag is scanned. No NFC hardware is needed. The emulator must allow
 * hidden APIs (adb shell settings put global hidden_api_policy 1) because
 * Tag.createMockTag is not public.
 *
 * Background scans go through NFCDispatchActivity, which must not crash and
 * must open the right screen. Foreground scans arrive at MainActivity via
 * onNewIntent and must do the same.
 */
@RunWith(AndroidJUnit4::class)
class NfcDispatchTest {

    private val app = ApplicationProvider.getApplicationContext<AudioTaggerApplication>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val unknownId = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    private val knownId = byteArrayOf(0x04, 0x77, 0x77.toByte(), 0x77.toByte(), 0x77.toByte(), 0x77.toByte(), 0x77.toByte())
    private val knownIdHex = "04777777777777"

    companion object {
        /**
         * The first activity launch in a freshly started test process can take
         * many seconds on the emulator before onCreate runs, which has nothing
         * to do with NFC. Warm the process up once so each test measures only
         * its own path.
         */
        @BeforeClass
        @JvmStatic
        fun warmUpProcess() {
            ActivityScenario.launch(MainActivity::class.java).close()
            Thread.sleep(500)
        }
    }

    @Before
    fun seedDatabase() = runBlocking {
        app.repository.deleteTagById(unknownIdHex())
        app.repository.deleteTagById(knownIdHex)
        app.repository.insertTag(
            TagEntity(tagId = knownIdHex, type = "tts", content = "Test tag", title = "Known test tag")
        )
    }

    @After
    fun closeEverything() {
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.CREATED)) {
                monitor.getActivitiesInStage(stage).toList().forEach { it.finishAndRemoveTask() }
            }
        }
        Thread.sleep(500)
    }

    @Test
    fun backgroundScan_unknownTag_opensUnknownTagActivity() {
        app.startActivity(dispatchIntent(unknownId))
        waitForResumed(UnknownTagActivity::class.java, "background-unknown")
    }

    @Test
    fun backgroundScan_knownTag_opensTagInfoActivity() {
        app.startActivity(dispatchIntent(knownId))
        waitForResumed(TagInfoActivity::class.java, "background-known")
    }

    @Test
    fun foregroundScan_onMainScreen_unknownTag_opensUnknownTagActivity() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForResumed(MainActivity::class.java, "main")
        // Foreground dispatch delivers the tag to the running MainActivity
        // through a single-top PendingIntent; this is the same delivery.
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .setAction(NfcAdapter.ACTION_TAG_DISCOVERED)
                .putExtra(NfcAdapter.EXTRA_TAG, mockTag(unknownId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        waitForResumed(UnknownTagActivity::class.java, "foreground-unknown")
        scenario.close()
    }

    @Test
    fun foregroundScan_onMainScreen_knownTag_opensTagInfoActivity() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForResumed(MainActivity::class.java, "main")
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .setAction(NfcAdapter.ACTION_TAG_DISCOVERED)
                .putExtra(NfcAdapter.EXTRA_TAG, mockTag(knownId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        waitForResumed(TagInfoActivity::class.java, "foreground-known")
        scenario.close()
    }

    private fun dispatchIntent(id: ByteArray): Intent =
        Intent(app, NFCDispatchActivity::class.java)
            .setAction(NfcAdapter.ACTION_TAG_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_TAG, mockTag(id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun unknownIdHex() = unknownId.joinToString("") { String.format("%02X", it) }

    private fun mockTag(id: ByteArray): Tag {
        val nfcA = 1 // TagTechnology.NFC_A
        val techList = intArrayOf(nfcA)
        val extras = arrayOfNulls<Bundle>(1)
        val cls = Tag::class.java
        return try {
            cls.getMethod("createMockTag", ByteArray::class.java, IntArray::class.java, Array<Bundle>::class.java)
                .invoke(null, id, techList, extras) as Tag
        } catch (e: NoSuchMethodException) {
            cls.getMethod("createMockTag", ByteArray::class.java, IntArray::class.java, Array<Bundle>::class.java, java.lang.Long.TYPE)
                .invoke(null, id, techList, extras, 0L) as Tag
        }
    }

    // Generous: a starved emulator has been seen to take a minute to show an
    // activity. A real device does this in well under a second.
    private fun waitForResumed(expected: Class<out Activity>, screenshotName: String, timeoutMs: Long = 120000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var seen: List<String> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            var found = false
            instrumentation.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                seen = resumed.map { it.javaClass.simpleName }
                found = resumed.any { expected.isInstance(it) }
            }
            if (found) {
                Thread.sleep(800) // let Compose draw before the screenshot
                screenshot(screenshotName)
                return
            }
            Thread.sleep(200)
        }
        throw AssertionError("${expected.simpleName} was not resumed within ${timeoutMs}ms; resumed: $seen")
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(app.filesDir, "test-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        assertTrue(File(dir, "$name.png").length() > 0)
    }
}
