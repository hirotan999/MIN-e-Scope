package com.minescope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.minescope.app.ui.theme.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    
    // Search Results State
    var searchResults by remember { mutableStateOf<List<com.minescope.app.data.api.model.Tweet>>(emptyList()) }
    var analysisResult by remember { mutableStateOf<com.minescope.app.util.AnalysisResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var trendList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isTrendLoading by remember { mutableStateOf(true) }
    var trendSource by remember { mutableStateOf("Loading...") }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { com.minescope.app.data.SettingsDataStore(context) }

    val performSearch: (String) -> Unit = { query ->
        active = false 
        scope.launch {
            val apiKey = dataStore.getApiKey()
            if (apiKey.isNullOrEmpty()) {
                android.widget.Toast.makeText(context, "設定画面でAPIキーを入力してください", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            
            isSearching = true
            errorMessage = null
            searchResults = emptyList()
            analysisResult = null

            try {
                val response = com.minescope.app.data.api.RetrofitClient.instance.searchTweets(
                    authHeader = "Bearer $apiKey",
                    query = "$query -is:retweet"
                )
                if (response.isSuccessful) {
                    val tweets = response.body()?.data ?: emptyList()
                    searchResults = tweets
                    if (tweets.isEmpty()) {
                        errorMessage = "検索結果が見つかりませんでした"
                    } else {
                        // Analyze tweets
                        analysisResult = com.minescope.app.util.TweetAnalyzer.analyze(tweets)
                    }
                } else if (response.code() == 402 || response.code() == 403) {
                    errorMessage = "エラー: APIアクセス権限がありません(403)。プランやクレジット残高をご確認ください。"
                } else {
                    errorMessage = "エラー: ${response.code()} ${response.message()}"
                }
            } catch (e: Exception) {
                errorMessage = "通信エラー: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val apiKey = dataStore.getApiKey()
        
        // Define the Trends Fetcher Function for reuse
        suspend fun fetchYahooTrends() {
            withContext(Dispatchers.IO) {
                try {
                    // Use Yahoo Japan RSS as Google is dead
                    val rssUrl = "https://news.yahoo.co.jp/rss/topics/top-picks.xml"
                    val connection = java.net.URL(rssUrl).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    
                    if (connection.responseCode == 200) {
                        val rssContent = connection.inputStream.bufferedReader().use { it.readText() }
                        val titles = Regex("<title>(.*?)</title>")
                            .findAll(rssContent)
                            .map { it.groupValues[1] }
                            .filter { !it.contains("Yahoo!ニュース") }
                            .take(5)
                            .map { it to "Yahoo! Top" }
                            .toList()

                        if (titles.isNotEmpty()) {
                            trendList = titles
                            trendSource = "Yahoo! News (Live)"
                        } else {
                            throw Exception("No items parsed")
                        }
                    } else {
                        throw Exception("HTTP ${connection.responseCode}")
                    }
                } catch (rssEx: Exception) {
                    trendList = listOf(
                        "API制限" to "Check Plan",
                        "通信エラー" to "Retry Later",
                        "増税" to "Fallback",
                        "裏金" to "Fallback",
                        "少子化" to "Fallback"
                    )
                    trendSource = "Offline: ${rssEx.message}"
                }
            }
        }

        if (!apiKey.isNullOrEmpty()) {
            try {
                // Try X Search API
                val response = com.minescope.app.data.api.RetrofitClient.instance.searchTweets(
                    authHeader = "Bearer $apiKey",
                    query = "lang:ja -is:retweet min_retweets:100"
                )
                
                if (response.isSuccessful) {
                    val tweets = response.body()?.data
                    if (!tweets.isNullOrEmpty()) {
                        // Frequency Analysis
                        val stopWords = setOf("ある", "ない", "する", "いる", "の", "に", "を", "て", "で", "が", "と", "は", "ます", "です", "こと", "もの", "ため", "よう", "それ", "これ", "あれ", "さん", "ちゃん", "くん", "http", "https", "t.co", "amp", "今日", "明日", "昨日", "日本", "自分", "私", "僕", "俺", "みんな")
                        val allText = tweets.joinToString(" ") { it.text }
                        val tokens = allText.split(Regex("[\\s\\p{Punct}「」【】『』。、！？]+")) 
                            .map { it.trim() }
                            .filter { it.length >= 2 }
                            .filter { !stopWords.contains(it) }
                            .filter { !it.contains("http") }
                            .filter { !it.matches(Regex("^[0-9a-zA-Z]+$")) }

                        val keywordCounts = tokens.groupingBy { it }.eachCount()
                        val topKeywords = keywordCounts.entries
                            .sortedByDescending { it.value }
                            .take(5)
                            .map { it.key to "${it.value * 10}+ Reac" }

                        if (topKeywords.isNotEmpty()) {
                            trendList = topKeywords
                            trendSource = "X Search (Live)"
                        } else {
                            fetchYahooTrends() // X worked but no keywords -> Fallback
                        }
                    } else {
                        fetchYahooTrends() // X worked but no tweets -> Fallback
                    }
                } else {
                    fetchYahooTrends() // X API Error -> Fallback
                }
            } catch (e: Exception) {
                fetchYahooTrends() // X Network Error -> Fallback
            } finally {
                isTrendLoading = false
            }
        } else {
            // No API Key -> Immediately use Yahoo Trends
            fetchYahooTrends()
            isTrendLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Bar Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = performSearch,
                active = active,
                onActiveChange = { active = it },
                placeholder = { Text("X内ポストのキーワード検索") },
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                 // Search History Mock
                 Column(modifier = Modifier.padding(16.dp)) {
                     Text("最近の検索", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                     Spacer(modifier = Modifier.height(8.dp))
                     Text("裏金", modifier = Modifier.padding(vertical = 8.dp), color = Color.White)
                     Text("マイナンバー", modifier = Modifier.padding(vertical = 8.dp), color = Color.White)
                 }
            }
        }

        // Content Area (Scrollable)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSearching) {
                 item { 
                     Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                     }
                 }
            } else if (errorMessage != null) {
                 item { 
                     Text(text = errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp)) 
                 }
            } else if (searchResults.isNotEmpty()) {
                 // Analysis Results
                 item {
                     Text(
                        text = "Build: 2026-01-23 14:20 (v1.2.9)",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                 }

                 item {
                     Text(
                        text = "分析結果 (直近${searchResults.size}件)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                 }

                 analysisResult?.let { result ->
                     item {
                         Text(text = "感情分布", color = Color.Gray, fontSize = 14.sp)
                         EmotionChart(result.sentimentDistribution)
                     }
                     item {
                         Text(text = "関心ワード", color = Color.Gray, fontSize = 14.sp)
                         WordCloud(result.trendingKeywords)
                     }
                 }
                 
                 item {
                     Spacer(modifier = Modifier.height(16.dp))
                     Row(
                         modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Text(
                            text = "ポスト一覧",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        
                        var expanded by remember { mutableStateOf(false) }
                        var sortOption by remember { mutableStateOf(SortOption.NEWEST) }
                        
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(sortOption.label, color = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                SortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortOption = option
                                            expanded = false
                                            searchResults = sortTweets(searchResults, option)
                                        }
                                    )
                                }
                            }
                        }
                     }
                 }

                 items(searchResults.size) { index ->
                     val tweet = searchResults[index]
                     TweetCard(tweet)
                 }
            } else {
                // Default Dashboard Content (Mock)
                item {
                    Text(
                        text = "今日の注目テーマ TOP5",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Source: $trendSource",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(trendList.size) { index ->
                val themeTitle = trendList[index].first
                val themeCount = trendList[index].second
                ThemeCard(
                    rank = index + 1, 
                    title = themeTitle, 
                    count = themeCount,
                    onClick = { 
                        searchQuery = themeTitle
                        performSearch(themeTitle) 
                    }
                )
            }
            }
        }

        // AdMob Banner Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        // Production Ad Unit ID
                        adUnitId = "ca-app-pub-7882116070402777/1123919837"
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCard(rank: Int, title: String, count: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$count 投稿",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            // Trend Icon (Mock)
            Text(text = "↗", color = EmotionAnger, fontSize = 20.sp)
        }
    }
}

@Composable
fun EmotionChart(distribution: Map<com.minescope.app.util.Emotion, Float>) {
    val anger = distribution[com.minescope.app.util.Emotion.ANGER] ?: 0f
    val anxiety = distribution[com.minescope.app.util.Emotion.ANXIETY] ?: 0f
    val hope = distribution[com.minescope.app.util.Emotion.HOPE] ?: 0f
    val empathy = distribution[com.minescope.app.util.Emotion.EMPATHY] ?: 0f
    
    // Normalize to ensure total is 1.0 (avoid divide by zero)
    val total = anger + anxiety + hope + empathy
    val (nAnger, nAnxiety, nHope, nEmpathy) = if (total > 0) {
        listOf(anger/total, anxiety/total, hope/total, empathy/total)
    } else {
        listOf(0.25f, 0.25f, 0.25f, 0.25f) // Default equal distribution if empty
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color.DarkGray, RoundedCornerShape(12.dp))
                .padding(1.dp)
        ) {
            if (nAnger > 0) Box(modifier = Modifier.weight(nAnger).fillMaxHeight().background(EmotionAnger, RoundedCornerShape(topStart=12.dp, bottomStart=12.dp)))
            if (nAnxiety > 0) Box(modifier = Modifier.weight(nAnxiety).fillMaxHeight().background(EmotionAnxiety))
            if (nHope > 0) Box(modifier = Modifier.weight(nHope).fillMaxHeight().background(EmotionHope))
            if (nEmpathy > 0) Box(modifier = Modifier.weight(nEmpathy).fillMaxHeight().background(EmotionEmpathy, RoundedCornerShape(topEnd=12.dp, bottomEnd=12.dp)))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("怒り ${(nAnger * 100).toInt()}%", color = EmotionAnger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("不安 ${(nAnxiety * 100).toInt()}%", color = EmotionAnxiety, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("期待 ${(nHope * 100).toInt()}%", color = EmotionHope, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("共感 ${(nEmpathy * 100).toInt()}%", color = EmotionEmpathy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordCloud(keywords: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        keywords.forEach { keyword ->
            SuggestionChip(
                onClick = {}, 
                label = { Text(keyword) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = Color.White
                )
            )
        }
        if (keywords.isEmpty()) {
            Text("関連キーワードが検出されませんでした", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun TweetCard(tweet: com.minescope.app.data.api.model.Tweet) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tweet.text,
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tweet.created_at ?: "",
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            tweet.public_metrics?.let { metrics ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "♥ ${metrics.like_count}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

fun getThemeTitle(index: Int): String {
    return when (index) {
        0 -> "増税"
        1 -> "裏金"
        2 -> "少子化"
        3 -> "年金"
        4 -> "改憲"
        else -> "その他"
    }
}

fun getThemeCount(index: Int): String {
    return when (index) {
        0 -> "125.4K"
        1 -> "89.2K"
        2 -> "56.8K"
        3 -> "45.1K"
        4 -> "32.0K"
        else -> "10.0K"
    }
}

enum class SortOption(val label: String) {
    NEWEST("新しい順"),
    OLDEST("古い順"),
    LIKES("いいネ順")
}

fun sortTweets(tweets: List<com.minescope.app.data.api.model.Tweet>, option: SortOption): List<com.minescope.app.data.api.model.Tweet> {
    return when (option) {
        SortOption.NEWEST -> tweets.sortedByDescending { it.created_at }
        SortOption.OLDEST -> tweets.sortedBy { it.created_at }
        SortOption.LIKES -> tweets.sortedByDescending { it.public_metrics?.like_count ?: 0 }
    }
}
