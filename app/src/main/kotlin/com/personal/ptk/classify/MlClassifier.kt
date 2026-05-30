package com.personal.ptk.classify

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MlClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var initialized = false
    private val maxLen = 128

    @Synchronized
    fun initialize(): Boolean {
        if (initialized) return interpreter != null
        initialized = true
        return try {
            val modelBuffer = loadModelFile("political_spam_model.tflite")
            interpreter = Interpreter(modelBuffer)
            vocab = loadVocab("vocab.json")
            true
        } catch (e: Exception) {
            Log.w(TAG, "ML model not available: ${e.message}")
            false
        }
    }

    fun classify(text: String): Float {
        if (!initialize()) return 0f

        val tokens = tokenize(text)
        val input = Array(1) { FloatArray(maxLen) { tokens[it].toFloat() } }
        val output = Array(1) { FloatArray(1) }

        return try {
            interpreter?.run(input, output)
            output[0][0]
        } catch (e: Exception) {
            Log.e(TAG, "ML inference failed", e)
            0f
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        initialized = false
    }

    private fun tokenize(text: String): IntArray {
        // Must match the Python tokenize() in step6_train.py exactly
        val normalized = text.lowercase()
            .replace(Regex("https?://\\S+"), " _URL_ ")
            .replace(Regex("\\b\\d{10,}\\b"), " _PHONE_ ")
            .replace(Regex("[^a-z_]"), " ")

        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }

        val indices = IntArray(maxLen) // 0 = padding
        for (i in words.indices.take(maxLen)) {
            indices[i] = vocab[words[i]] ?: 1 // 1 = OOV
        }
        return indices
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        )
    }

    private fun loadVocab(filename: String): Map<String, Int> {
        val json = context.assets.open(filename).bufferedReader().readText()
        val obj = JSONObject(json)
        val result = mutableMapOf<String, Int>()
        for (key in obj.keys()) {
            result[key] = obj.getInt(key)
        }
        return result
    }

    companion object {
        private const val TAG = "MlClassifier"
    }
}
