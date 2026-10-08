package com.negi.mmsswahilitts

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal class MmsVitsSynthesizer private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private val lock = Any()
    @Volatile private var state = State.UNINITIALIZED
    @Volatile private var errorMessage: String? = null
    private var session: OrtSession? = null
    private lateinit var tokenIds: Map<Char, Long>
    private lateinit var normalizer: SwahiliTextNormalizer

    enum class State { UNINITIALIZED, INITIALIZING, READY, FAILED, CLOSED }

    data class Status(val state: State, val error: String?, val initializationMillis: Long?)

    @Volatile private var initializationMillis: Long? = null

    fun status(): Status = Status(state, errorMessage, initializationMillis)

    fun initialize() {
        if (state == State.READY) return
        synchronized(lock) {
            if (state == State.READY) return
            check(state != State.CLOSED) { "Synthesizer is closed" }
            state = State.INITIALIZING
            val started = System.nanoTime()
            try {
                tokenIds = readTokens()
                normalizer = SwahiliTextNormalizer(tokenIds.keys)
                val modelFile = materializeModel()
                session = environment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
                initializationMillis = (System.nanoTime() - started) / 1_000_000
                errorMessage = null
                state = State.READY
                Log.i(TAG, "Model initialized in ${initializationMillis} ms")
            } catch (failure: Throwable) {
                errorMessage = failure.message ?: failure.javaClass.simpleName
                state = State.FAILED
                throw IllegalStateException("Unable to initialize MMS Swahili model", failure)
            }
        }
    }

    fun chunks(text: CharSequence): List<String> {
        initialize()
        return normalizer.chunks(text)
    }

    /** Runs one VITS request and returns a mono 16-bit PCM buffer at [SAMPLE_RATE_HZ]. */
    fun synthesize(normalizedText: String): ShortArray {
        initialize()
        val ids = intersperseBlanks(normalizedText)
        val localSession = checkNotNull(session)
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs["x"] = OnnxTensor.createTensor(environment, arrayOf(ids))
            inputs["x_length"] = OnnxTensor.createTensor(environment, longArrayOf(ids.size.toLong()))
            inputs["noise_scale"] = OnnxTensor.createTensor(environment, floatArrayOf(NOISE_SCALE))
            inputs["length_scale"] = OnnxTensor.createTensor(environment, floatArrayOf(LENGTH_SCALE))
            inputs["noise_scale_w"] = OnnxTensor.createTensor(environment, floatArrayOf(NOISE_SCALE_W))
            localSession.run(inputs).use { output ->
                val waveform = (output[0].value as Array<Array<FloatArray>>)[0][0]
                return ShortArray(waveform.size) { index ->
                    (waveform[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                }
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    override fun close() {
        synchronized(lock) {
            session?.close()
            session = null
            state = State.CLOSED
        }
    }

    private fun intersperseBlanks(text: String): LongArray {
        require(text.isNotBlank())
        return LongArray(text.length * 2 + 1).also { values ->
            text.forEachIndexed { index, character ->
                values[index * 2 + 1] = checkNotNull(tokenIds[character])
            }
        }
    }

    private fun readTokens(): Map<Char, Long> = appContext.assets.open(TOKENS_ASSET).bufferedReader().useLines { lines ->
        lines.associate { line ->
            val split = line.lastIndexOf(' ')
            require(split > 0) { "Invalid token row: $line" }
            val token = line.substring(0, split)
            require(token.length == 1) { "Only single-character MMS tokens are supported" }
            token[0] to line.substring(split + 1).toLong()
        }
    }

    private fun materializeModel(): File {
        val modelDirectory = File(appContext.filesDir, "models").also(File::mkdirs)
        val destination = File(modelDirectory, MODEL_ASSET)
        if (!destination.exists() || destination.length() != MODEL_BYTES || sha256(destination) != MODEL_SHA256) {
            appContext.assets.open(MODEL_ASSET).use { input ->
                FileOutputStream(destination).use(input::copyTo)
            }
            check(destination.length() == MODEL_BYTES) { "Model size mismatch after extraction" }
            check(sha256(destination) == MODEL_SHA256) { "Model checksum mismatch after extraction" }
        }
        return destination
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                update(buffer, 0, count)
            }
        }
        digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val MODEL_ASSET = "mms-swh-vits.onnx"
        private const val TOKENS_ASSET = "mms-swh-tokens.txt"
        private const val MODEL_BYTES = 114_017_796L
        private const val MODEL_SHA256 = "af4f2e2174960af06a7a7d07810ea7eb2d78fba827be7690abb230159717d250"
        private const val NOISE_SCALE = 0.667f
        private const val LENGTH_SCALE = 1.0f
        private const val NOISE_SCALE_W = 0.8f
        private const val TAG = "MmsSwhTts"

        @Volatile private var instance: MmsVitsSynthesizer? = null

        fun get(context: Context): MmsVitsSynthesizer = instance ?: synchronized(this) {
            instance ?: MmsVitsSynthesizer(context).also { instance = it }
        }
    }
}
