package com.workhub.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.workhub.app.R
import com.workhub.app.data.LocalAppGraph
import kotlinx.coroutines.launch

/**
 * Logout action that warns first when Room still holds changes not uploaded yet,
 * because logging out wipes the local database.
 */
@Composable
fun rememberGuardedLogout(): () -> Unit {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    var unsynced by remember { mutableIntStateOf(0) }
    if (unsynced > 0) {
        AlertDialog(
            onDismissRequest = { unsynced = 0 },
            title = { Text(stringResource(R.string.logout_unsynced_title)) },
            text = { Text(stringResource(R.string.logout_unsynced_body, unsynced)) },
            confirmButton = {
                TextButton(onClick = {
                    unsynced = 0
                    scope.launch { graph.session.clear() }
                }) {
                    Text(stringResource(R.string.logout_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { unsynced = 0 }) { Text(stringResource(R.string.back)) }
            },
        )
    }
    return {
        scope.launch {
            val count = graph.offline.pendingCount()
            if (count > 0) unsynced = count else graph.session.clear()
        }
    }
}
