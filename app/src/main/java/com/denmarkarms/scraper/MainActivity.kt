package com.denmarkarms.scraper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.denmarkarms.scraper.ui.navigation.AppNavigation
import com.denmarkarms.scraper.ui.theme.DenmarkArmsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DenmarkArmsTheme {
                AppNavigation()
            }
        }
    }
}
