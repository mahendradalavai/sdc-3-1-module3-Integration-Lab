package com.example.data.vector

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.sqrt

object DenseEmbeddingGenerator {

    const val DIMENSION = 64

    private fun md5BigInt(text: String): BigInteger {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return BigInteger(1, bytes)
    }

    private fun sha256BigInt(text: String): BigInteger {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return BigInteger(1, bytes)
    }

    private fun hashTokenToFeatures(token: String, dim: Int): FloatArray {
        val vec = FloatArray(dim)
        val tokenLower = token.lowercase().trim()
        if (tokenLower.isEmpty()) return vec

        val h1 = md5BigInt(tokenLower)
        val h2 = sha256BigInt(tokenLower)
        val dimBig = BigInteger.valueOf(dim.toLong())

        val idx1 = h1.mod(dimBig).toInt()
        val idx2 = h1.divide(dimBig).mod(dimBig).toInt()
        val idx3 = h2.mod(dimBig).toInt()

        val bit1 = h2.shiftRight(1).testBit(0)
        val bit2 = h2.shiftRight(2).testBit(0)
        val bit3 = h2.shiftRight(3).testBit(0)

        vec[idx1] += if (bit1) 1.0f else -1.0f
        vec[idx2] += if (bit2) 0.75f else -0.75f
        vec[idx3] += if (bit3) 0.5f else -0.5f

        // Character trigrams
        if (tokenLower.length >= 3) {
            for (i in 0..tokenLower.length - 3) {
                val tri = tokenLower.substring(i, i + 3)
                val triH = md5BigInt(tri)
                val tIdx = triH.mod(dimBig).toInt()
                val triBit = triH.testBit(0)
                vec[tIdx] += 0.3f * (if (triBit) 1.0f else -1.0f)
            }
        }

        return vec
    }

    fun embedText(text: String): FloatArray {
        if (text.isBlank()) return FloatArray(DIMENSION)

        val words = text.split(Regex("[\\s,.:;!?()\\[\\]\"'\\n\\r\\t]+"))
            .map { it.lowercase().trim() }
            .filter { it.length > 1 }

        if (words.isEmpty()) return FloatArray(DIMENSION)

        val aggregate = FloatArray(DIMENSION)

        // Term frequency map
        val tf = mutableMapOf<String, Float>()
        for (w in words) {
            tf[w] = (tf[w] ?: 0f) + 1f
        }

        for ((w, count) in tf) {
            val weight = 1.0f + ln(count.toDouble()).toFloat()
            val fVec = hashTokenToFeatures(w, DIMENSION)
            for (i in 0 until DIMENSION) {
                aggregate[i] += fVec[i] * weight
            }
        }

        // Contiguous bigrams
        for (i in 0 until words.size - 1) {
            val bigram = "${words[i]}_${words[i + 1]}"
            val bgVec = hashTokenToFeatures(bigram, DIMENSION)
            for (j in 0 until DIMENSION) {
                aggregate[j] += bgVec[j] * 0.8f
            }
        }

        return VectorMath.normalize(aggregate)
    }

    fun floatArrayToJson(vector: FloatArray): String {
        return vector.joinToString(prefix = "[", postfix = "]", separator = ",") { String.format("%.5f", it) }
    }

    fun jsonToFloatArray(json: String): FloatArray {
        val cleaned = json.removePrefix("[").removeSuffix("]").trim()
        if (cleaned.isEmpty()) return FloatArray(DIMENSION)
        val parts = cleaned.split(",")
        val res = FloatArray(parts.size)
        for (i in parts.indices) {
            res[i] = parts[i].trim().toFloatOrNull() ?: 0f
        }
        return res
    }
}
