package com.workhub.app.device

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.workhub.app.R
import com.workhub.app.ui.DeviceFindAlarm
import com.workhub.app.ui.WorkHubTheme

/**
 * Màn hình chiếm toàn bộ thiết bị khi admin bật Lost Mode.
 * Không Back; hiện rõ ràng đây là khóa quản trị WorkHub (không giấu).
 */
class LostModeActivity : ComponentActivity() {
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DeviceCommandRouter.ACTION_STOP_LOST_MODE) {
                active = false
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        active = true
        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(DeviceCommandRouter.ACTION_STOP_LOST_MODE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguard.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LocalDeviceAdmin.lockNow(this)
        DeviceFindAlarm.start(this)
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        setContent {
            WorkHubTheme {
                LostModeScreen(message = message)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        DeviceFindAlarm.stop(this)
        active = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MESSAGE = "lost_message"
        @Volatile var active: Boolean = false
    }
}

@Composable
private fun LostModeScreen(message: String) {
    BackHandler(enabled = true) { /* chặn Back */ }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7F1D1D)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Text(
                stringResource(R.string.lost_mode_title),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                message.ifBlank { stringResource(R.string.lost_mode_default_message) },
                color = Color(0xFFFECACA),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.lost_mode_footer),
                color = Color(0xFFFCA5A5),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
