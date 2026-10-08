package com.negi.mmsswahilitts

import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Android platform TTS bridge for the bundled, offline MMS Swahili VITS model. */
class MmsSwahiliTtsService : TextToSpeechService() {
    private val requestEpoch = AtomicLong(0)
    private val synthesisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mms-swh-synthesis").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TTS service created")
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        if (lang.equals(SWAHILI.language, ignoreCase = true) || lang.equals(SWAHILI_ISO3, ignoreCase = true)) {
            if (country.isNullOrBlank()) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }.also { Log.i(TAG, "Language availability $lang-$country-$variant = $it") }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetLanguage(): Array<String> = arrayOf(SWAHILI_ISO3, "", "").also {
        Log.i(TAG, "Reported default language $SWAHILI_ISO3")
    }

    override fun onGetVoices(): MutableList<Voice> = mutableListOf(VOICE)

    override fun onLoadVoice(voiceName: String?): Int =
        if (voiceName == VOICE.name) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onIsValidVoiceName(voiceName: String?): Int = onLoadVoice(voiceName)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? =
        if (onIsLanguageAvailable(lang, country, variant) >= TextToSpeech.LANG_AVAILABLE) VOICE.name else null

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val localeStatus = onIsLanguageAvailable(request.language, request.country, request.variant)
        if (localeStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback.error(TextToSpeech.LANG_NOT_SUPPORTED)
            return
        }
        val epoch = requestEpoch.incrementAndGet()
        synthesisExecutor.execute {
            val startedAt = System.nanoTime()
            try {
                val synthesizer = MmsVitsSynthesizer.get(applicationContext)
                val chunks = synthesizer.chunks(request.charSequenceText)
                if (chunks.isEmpty()) {
                    callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
                    return@execute
                }
                if (isStopped(epoch)) return@execute
                if (callback.start(MmsVitsSynthesizer.SAMPLE_RATE_HZ, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
                    return@execute
                }
                var emittedAudio = false
                for (chunk in chunks) {
                    if (isStopped(epoch)) return@execute
                    val inferenceStartedAt = System.nanoTime()
                    val pcm = synthesizer.synthesize(chunk)
                    val inferenceMillis = (System.nanoTime() - inferenceStartedAt) / 1_000_000.0
                    val audioMillis = pcm.size * 1_000.0 / MmsVitsSynthesizer.SAMPLE_RATE_HZ
                    Log.i(TAG, "Inference ${"%.1f".format(inferenceMillis)} ms for ${"%.1f".format(audioMillis)} ms audio; RTF=${"%.3f".format(inferenceMillis / audioMillis)}")
                    val writeResult = writePcm(callback, pcm, epoch)
                    if (!emittedAudio && writeResult.firstAudioNanos != null) {
                        emittedAudio = true
                        Log.i(TAG, "First audio latency ${(writeResult.firstAudioNanos - startedAt) / 1_000_000} ms")
                    }
                    if (!writeResult.completed) return@execute
                }
                if (!isStopped(epoch)) {
                    callback.done()
                    Log.i(TAG, "Synthesis complete in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
                }
            } catch (_: Throwable) {
                if (!isStopped(epoch)) callback.error(TextToSpeech.ERROR_SYNTHESIS)
            }
        }
    }

    override fun onStop() {
        requestEpoch.incrementAndGet()
        Log.i(TAG, "Synthesis stop requested")
    }

    override fun onDestroy() {
        requestEpoch.incrementAndGet()
        synthesisExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun writePcm(callback: SynthesisCallback, pcm: ShortArray, epoch: Long): WriteResult {
        val maxBytes = callback.maxBufferSize.coerceAtLeast(2).and(0.inv() shl 1)
        val bytes = ByteArray(maxBytes)
        var sampleOffset = 0
        var firstAudioNanos: Long? = null
        while (sampleOffset < pcm.size) {
            if (isStopped(epoch)) return WriteResult(completed = false, firstAudioNanos = firstAudioNanos)
            val samples = minOf((bytes.size / 2), pcm.size - sampleOffset)
            var destination = 0
            repeat(samples) { index ->
                val value = pcm[sampleOffset + index].toInt()
                bytes[destination++] = (value and 0xff).toByte()
                bytes[destination++] = ((value ushr 8) and 0xff).toByte()
            }
            val sentAt = System.nanoTime()
            if (callback.audioAvailable(bytes, 0, destination) != TextToSpeech.SUCCESS) {
                return WriteResult(completed = false, firstAudioNanos = firstAudioNanos)
            }
            if (firstAudioNanos == null) firstAudioNanos = sentAt
            sampleOffset += samples
        }
        return WriteResult(completed = true, firstAudioNanos = firstAudioNanos)
    }

    private fun isStopped(epoch: Long): Boolean = requestEpoch.get() != epoch

    private data class WriteResult(val completed: Boolean, val firstAudioNanos: Long?)

    companion object {
        private val SWAHILI = Locale("sw")
        private const val SWAHILI_ISO3 = "swa"
        private val VOICE = Voice(
            "mms-swh-vits",
            SWAHILI,
            Voice.QUALITY_NORMAL,
            Voice.LATENCY_NORMAL,
            false,
            emptySet(),
        )
        private const val TAG = "MmsSwhTts"
    }
}
