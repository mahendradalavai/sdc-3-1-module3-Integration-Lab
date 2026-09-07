package com.example.data.vector

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object VectorMath {

    fun dotProduct(v1: FloatArray, v2: FloatArray): Float {
        var sum = 0f
        val len = min(v1.size, v2.size)
        for (i in 0 until len) {
            sum += v1[i] * v2[i]
        }
        return sum
    }

    fun l2Norm(v: FloatArray): Float {
        var sumSquares = 0f
        for (x in v) {
            sumSquares += x * x
        }
        return sqrt(sumSquares)
    }

    fun normalize(v: FloatArray): FloatArray {
        val norm = l2Norm(v)
        if (norm < 1e-9f) return FloatArray(v.size)
        val result = FloatArray(v.size)
        for (i in v.indices) {
            result[i] = v[i] / norm
        }
        return result
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f
        val norm1 = l2Norm(v1)
        val norm2 = l2Norm(v2)
        if (norm1 < 1e-9f || norm2 < 1e-9f) return 0f

        val dot = dotProduct(v1, v2)
        val sim = dot / (norm1 * norm2)
        return max(-1f, min(1f, sim))
    }

    fun euclideanDistance(v1: FloatArray, v2: FloatArray): Float {
        var sum = 0f
        val len = min(v1.size, v2.size)
        for (i in 0 until len) {
            val diff = v1[i] - v2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}
