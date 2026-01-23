package com.minescope.app.data.api.service

import com.minescope.app.data.api.model.TweetResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface XApiService {
    @GET("2/tweets/search/recent")
    suspend fun searchTweets(
        @Header("Authorization") authHeader: String,
        @Query("query") query: String,
        @Query("max_results") maxResults: Int = 100,
        @Query("tweet.fields") tweetFields: String = "created_at,public_metrics"
    ): Response<TweetResponse>
    @GET("1.1/trends/place.json")
    suspend fun getTrends(
        @Header("Authorization") authHeader: String,
        @Query("id") id: Int = 23424856 // Japan WOEID
    ): Response<List<com.minescope.app.data.api.model.TrendWrapper>>
}
