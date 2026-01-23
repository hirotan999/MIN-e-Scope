package com.minescope.app.util

import com.minescope.app.data.api.model.Tweet

enum class Emotion {
    ANGER,    // 怒り
    ANXIETY,  // 不安
    HOPE,     // 期待
    EMPATHY,  // 共感
    NEUTRAL   // 中立
}

data class AnalysisResult(
    val sentimentDistribution: Map<Emotion, Float>,
    val trendingKeywords: List<String>
)

object TweetAnalyzer {

    // 簡易感情辞書 (拡張可能)
    private val angerKeywords = setOf("許せない", "最悪", "ふざけるな", "怒", "反対", "辞めろ", "嘘つき", "クソ", "死ね", "馬鹿", "異常", "ひどい")
    private val anxietyKeywords = setOf("不安", "心配", "怖い", "恐ろしい", "大丈夫", "危険", "リスク", "崩壊", "パニック", "困る", "迷う")
    private val hopeKeywords = setOf("期待", "希望", "楽しみ", "応援", "頑張れ", "未来", "解決", "前進", "良くなる", "信じる", "賛成")
    private val empathyKeywords = setOf("わかる", "同意", "その通り", "確かに", "同じ", "共感", "泣ける", "感動", "ありがとう", "感謝", "お大事に")

    // 無視するキーワード（ストップワード）
    private val stopWords = setOf("ある", "ない", "する", "いる", "の", "に", "を", "て", "で", "が", "と", "は", "ます", "です", "こと", "もの", "ため", "よう", "それ", "これ", "あれ", "さん", "ちゃん", "くん", "http", "https", "t.co", "amp")

    fun analyze(tweets: List<Tweet>): AnalysisResult {
        if (tweets.isEmpty()) {
            return AnalysisResult(
                mapOf(Emotion.ANGER to 0f, Emotion.ANXIETY to 0f, Emotion.HOPE to 0f, Emotion.EMPATHY to 0f),
                emptyList()
            )
        }

        // 1. 感情分析
        val emotionCounts = mutableMapOf(
            Emotion.ANGER to 0,
            Emotion.ANXIETY to 0,
            Emotion.HOPE to 0,
            Emotion.EMPATHY to 0,
            Emotion.NEUTRAL to 0
        )

        tweets.forEach { tweet ->
            val text = tweet.text
            var maxCount = 0
            var detectedEmotion = Emotion.NEUTRAL

            val angerCount = countMatches(text, angerKeywords)
            if (angerCount > maxCount) { maxCount = angerCount; detectedEmotion = Emotion.ANGER }

            val anxietyCount = countMatches(text, anxietyKeywords)
            if (anxietyCount > maxCount) { maxCount = anxietyCount; detectedEmotion = Emotion.ANXIETY }

            val hopeCount = countMatches(text, hopeKeywords)
            if (hopeCount > maxCount) { maxCount = hopeCount; detectedEmotion = Emotion.HOPE }

            val empathyCount = countMatches(text, empathyKeywords)
            if (empathyCount > maxCount) { maxCount = empathyCount; detectedEmotion = Emotion.EMPATHY }

            emotionCounts[detectedEmotion] = emotionCounts.getOrDefault(detectedEmotion, 0) + 1
        }

        // NEUTRALを除外して割合を計算（あるいはNEUTRALも含めるか方針次第だが、UIに合わせて主要4感情の分布とする）
        val totalValid = (emotionCounts.values.sum() - emotionCounts.getOrDefault(Emotion.NEUTRAL, 0)).toFloat()
        val distribution = if (totalValid > 0) {
            mapOf(
                Emotion.ANGER to emotionCounts.getOrDefault(Emotion.ANGER, 0) / totalValid,
                Emotion.ANXIETY to emotionCounts.getOrDefault(Emotion.ANXIETY, 0) / totalValid,
                Emotion.HOPE to emotionCounts.getOrDefault(Emotion.HOPE, 0) / totalValid,
                Emotion.EMPATHY to emotionCounts.getOrDefault(Emotion.EMPATHY, 0) / totalValid
            )
        } else {
            // 全て中立だった場合のデフォルト配分 (便宜上均等、またはUI側で処理)
            mapOf(Emotion.ANGER to 0f, Emotion.ANXIETY to 0f, Emotion.HOPE to 0f, Emotion.EMPATHY to 0f)
        }

        // 2. キーワード抽出 (簡易的な2文字以上の単語抽出)
        val allText = tweets.joinToString(" ") { it.text }
        // 簡易トークナイズ: 空白、改行、記号で分割し、2文字以上の連続する漢字・カタカナ・ひらがなを抽出
        // 厳密な形態素解析ではないが、トレンド把握にはある程度有効
        val tokens = allText.split(Regex("[\\s\\p{Punct}「」【】『』。、！？]+")) 
            .map { it.trim() }
            .filter { it.length >= 2 } // 1文字は除外
            .filter { !stopWords.contains(it) } // ストップワード除外
            .filter { !it.contains("http") } // 改めてURL除外

        val keywordCounts = tokens.groupingBy { it }.eachCount()
        val topKeywords = keywordCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }

        return AnalysisResult(distribution, topKeywords)
    }

    private fun countMatches(text: String, keywords: Set<String>): Int {
        var count = 0
        keywords.forEach { keyword ->
            if (text.contains(keyword)) {
                count++
            }
        }
        return count
    }
}
