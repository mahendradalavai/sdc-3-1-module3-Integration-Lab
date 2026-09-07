package com.example.data.vector

data class TextChunk(
    val chunkIndex: Int,
    val text: String,
    val charCount: Int,
    val estimatedTokens: Int,
    val startChar: Int,
    val endChar: Int
)

object TextChunker {

    fun cleanText(text: String): String {
        return text.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun estimateTokens(text: String): Int {
        return maxOf(1, text.length / 4)
    }

    fun chunkText(
        text: String,
        chunkSize: Int = 400,
        chunkOverlap: Int = 80
    ): List<TextChunk> {
        val cleaned = cleanText(text)
        if (cleaned.isBlank()) return emptyList()

        if (cleaned.length <= chunkSize) {
            return listOf(
                TextChunk(
                    chunkIndex = 0,
                    text = cleaned,
                    charCount = cleaned.length,
                    estimatedTokens = estimateTokens(cleaned),
                    startChar = 0,
                    endChar = cleaned.length
                )
            )
        }

        val paragraphs = cleaned.split("\n\n")
        val rawChunks = mutableListOf<TextChunk>()
        var currentChunk = ""
        var currentStart = 0

        for (para in paragraphs) {
            val p = para.trim()
            if (p.isEmpty()) continue

            if (currentChunk.isNotEmpty() && currentChunk.length + p.length + 2 > chunkSize) {
                val chunkStr = currentChunk.trim()
                rawChunks.add(
                    TextChunk(
                        chunkIndex = rawChunks.size,
                        text = chunkStr,
                        charCount = chunkStr.length,
                        estimatedTokens = estimateTokens(chunkStr),
                        startChar = currentStart,
                        endChar = currentStart + chunkStr.length
                    )
                )

                val overlapPrefix = if (currentChunk.length >= chunkOverlap) {
                    currentChunk.takeLast(chunkOverlap)
                } else {
                    currentChunk
                }
                currentStart += currentChunk.length - overlapPrefix.length
                currentChunk = "$overlapPrefix\n\n$p"
            } else {
                currentChunk = if (currentChunk.isEmpty()) p else "$currentChunk\n\n$p"
            }
        }

        if (currentChunk.isNotBlank()) {
            val chunkStr = currentChunk.trim()
            rawChunks.add(
                TextChunk(
                    chunkIndex = rawChunks.size,
                    text = chunkStr,
                    charCount = chunkStr.length,
                    estimatedTokens = estimateTokens(chunkStr),
                    startChar = currentStart,
                    endChar = currentStart + chunkStr.length
                )
            )
        }

        return rawChunks
    }
}
