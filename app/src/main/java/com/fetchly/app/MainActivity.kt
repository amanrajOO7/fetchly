package com.fetchly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fetchly.app.ui.FetchlyScreen
import com.fetchly.app.ui.theme.FetchlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FetchlyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FetchlyScreen()
                }
            }
        }
    }
}
