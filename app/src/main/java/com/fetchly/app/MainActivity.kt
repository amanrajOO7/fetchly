package com.fetchly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fetchly.app.ui.FetchlyScreen
import com.fetchly.app.ui.LinkGrabberScreen
import com.fetchly.app.ui.theme.FetchlyTheme

private enum class AppScreen { DOWNLOADS, LINK_GRABBER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FetchlyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by rememberSaveable { mutableStateOf(AppScreen.DOWNLOADS) }
                    BackHandler(enabled = screen != AppScreen.DOWNLOADS) {
                        screen = AppScreen.DOWNLOADS
                    }
                    when (screen) {
                        AppScreen.DOWNLOADS -> FetchlyScreen(
                            onOpenLinkGrabber = { screen = AppScreen.LINK_GRABBER },
                        )
                        AppScreen.LINK_GRABBER -> LinkGrabberScreen(
                            onBack = { screen = AppScreen.DOWNLOADS },
                        )
                    }
                }
            }
        }
    }
}
