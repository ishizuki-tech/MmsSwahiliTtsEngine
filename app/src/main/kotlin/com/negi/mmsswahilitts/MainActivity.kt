package com.negi.mmsswahilitts

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

/** Minimal local status screen; it never downloads model data or sends text off-device. */
class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var testButton: Button
    private lateinit var longTestButton: Button
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MMS Swahili TTS"
        setContentView(createContent())
        refreshStatus()
    }

    override fun onDestroy() {
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun createContent(): View {
        val padding = (24 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply {
                text = "Offline Android Text-to-Speech engine\nModel: facebook/mms-tts-swh (VITS, 16 kHz)"
                textSize = 20f
            })
            progress = ProgressBar(context).also { addView(it) }
            status = TextView(context).apply { textSize = 16f }.also { addView(it) }
            testButton = Button(context).apply {
                text = "Speak Swahili sample"
                isEnabled = false
                setOnClickListener { speak(SAMPLE_TEXT) }
            }.also { addView(it) }
            longTestButton = Button(context).apply {
                text = "Speak long test"
                isEnabled = false
                setOnClickListener { speak(LONG_TEST_TEXT) }
            }.also { addView(it) }
            addView(Button(context).apply {
                text = "Stop speech"
                setOnClickListener { tts?.stop() }
            })
        }
    }

    private fun refreshStatus() {
        status.text = "Initializing bundled model…"
        executor.execute {
            val synthesizer = MmsVitsSynthesizer.get(applicationContext)
            runCatching { synthesizer.initialize() }
            val modelStatus = synthesizer.status()
            runOnUiThread {
                progress.visibility = View.GONE
                status.text = when (modelStatus.state) {
                    MmsVitsSynthesizer.State.READY -> "Ready offline. Initialization: ${modelStatus.initializationMillis} ms"
                    else -> "Initialization failed: ${modelStatus.error ?: "unknown error"}"
                }
                testButton.isEnabled = modelStatus.state == MmsVitsSynthesizer.State.READY
                longTestButton.isEnabled = modelStatus.state == MmsVitsSynthesizer.State.READY
            }
        }
    }

    private fun speak(text: String) {
        tts?.shutdown()
        tts = TextToSpeech(this, { result ->
            Log.i(TAG, "Diagnostic TextToSpeech init result=$result")
            if (result == TextToSpeech.SUCCESS) {
                tts?.language = Locale("sw")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "diagnostic")
            }
        }, packageName)
    }

    private companion object {
        const val TAG = "MmsSwhTts"
        const val SAMPLE_TEXT = "Habari, hii ni sauti ya Kiswahili inayotolewa bila mtandao."
        const val LONG_TEST_TEXT = "Karibu kwenye jaribio la sauti la Kiswahili. Injini hii inatengeneza sauti ndani ya simu bila kutumia mtandao. Maandishi marefu yanagawanywa katika sehemu ndogo ili programu ibaki thabiti na iweze kusimamisha sauti inapohitajika. Tafadhali sikiliza sentensi hizi na uthibitishe kwamba sauti inaendelea kwa utulivu bila kukwama. Huu ni ujumbe wa majaribio kwa utangulizi mrefu wa dodoso."
    }
}
