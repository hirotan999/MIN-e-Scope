package com.minescope.app.data.api.model

data class TweetResponse(
    val data: List<Tweet>?,
    val meta: Meta?
)

data class Tweet(
    val id: String,
    val text: String,
    val created_at: String? = null,
    val public_metrics: PublicMetrics? = null
)

data class PublicMetrics(
    val retweet_count: Int,
    val reply_count: Int,
    val like_count: Int,
    val quote_count: Int
)

data class Meta(
    val result_count: Int,
    val next_token: String? = null
)
