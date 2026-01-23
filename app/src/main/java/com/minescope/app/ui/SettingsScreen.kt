package com.minescope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onBackCompete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dataStore = remember { com.minescope.app.data.SettingsDataStore(context) }
    var apiKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val savedKey = dataStore.getApiKey()
        if (!savedKey.isNullOrEmpty()) {
            apiKey = savedKey
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "X APIキー設定",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Bearer Token (S: AAAA...)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { 
                dataStore.saveApiKey(apiKey)
                onBackCompete() 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存して戻る")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "※ X Developer Portalで取得したキーを入力してください。\n※ 端末内に暗号化して保存されます。",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}
