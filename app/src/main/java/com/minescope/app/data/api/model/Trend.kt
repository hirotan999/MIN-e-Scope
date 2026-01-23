package com.minescope.app.data.api.model

data class TrendResponse(
    val data: List<TrendWrapper>?
)

data class TrendWrapper(
    val trends: List<Trend>?
)

data class Trend(
    val name: String,
    val url: String?,
    val tweet_volume: Int?
)
