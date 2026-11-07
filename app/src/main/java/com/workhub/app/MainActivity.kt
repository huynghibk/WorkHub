package com.workhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.workhub.app.data.AppGraph
import com.workhub.app.data.LocalAppGraph
import com.workhub.app.ui.WorkHubNav
import com.workhub.app.ui.WorkHubTheme

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        graph = AppGraph.get(applicationContext)
        graph.offerNotificationIntent(intent)
        setContent {
            CompositionLocalProvider(LocalAppGraph provides graph) {
                WorkHubTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        WorkHubNav()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::graph.isInitialized) {
            graph.offerNotificationIntent(intent)
        }
    }
}
