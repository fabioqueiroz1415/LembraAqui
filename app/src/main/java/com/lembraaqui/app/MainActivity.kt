package com.lembraaqui.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lembraaqui.app.ui.LembraAquiApp
import com.lembraaqui.app.ui.theme.LembraAquiTheme

class MainActivity : ComponentActivity() {
    private var openReminderId by mutableStateOf<String?>(null)
    private var openPlaceId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPlaceId = intent.getStringExtra("open_place_id")
        openReminderId = intent.getStringExtra("open_reminder_id")
        setContent {
            LembraAquiTheme {
                LembraAquiApp(
                    openPlaceId = openPlaceId,
                    openReminderId = openReminderId,
                    onOpenPlaceConsumed = {
                        openPlaceId = null
                        openReminderId = null
                        intent.removeExtra("open_place_id")
                        intent.removeExtra("open_reminder_id")
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPlaceId = intent.getStringExtra("open_place_id")
        openReminderId = intent.getStringExtra("open_reminder_id")
    }
}
