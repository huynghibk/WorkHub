package com.workhub.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workhub.app.R

data class AppShortcut(
    val id: String,
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
    val tint: Color? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSheet(
    showOrg: Boolean,
    onDismiss: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenOrg: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = buildList {
        add(
            AppShortcut(
                id = "notes",
                label = stringResource(R.string.tab_notes),
                icon = Icons.Filled.EditNote,
                tint = Color(0xFFF59E0B),
                onClick = {
                    onOpenNotes()
                    onDismiss()
                },
            ),
        )
        add(
            AppShortcut(
                id = "chat",
                label = stringResource(R.string.tab_chat),
                icon = Icons.Filled.Chat,
                tint = Color(0xFF14B8A6),
                onClick = {
                    onOpenChat()
                    onDismiss()
                },
            ),
        )
        if (showOrg) {
            add(
                AppShortcut(
                    id = "org",
                    label = stringResource(R.string.tab_org),
                    icon = Icons.Filled.AccountTree,
                    tint = Color(0xFF6366F1),
                    onClick = {
                        onOpenOrg()
                        onDismiss()
                    },
                ),
            )
        }
        add(
            AppShortcut(
                id = "calendar",
                label = stringResource(R.string.app_calendar),
                iconRes = R.drawable.ic_brand_calendar,
                onClick = onOpenCalendar,
            ),
        )
        add(
            AppShortcut(
                id = "maps",
                label = stringResource(R.string.app_maps),
                iconRes = R.drawable.ic_brand_maps,
                onClick = onOpenMaps,
            ),
        )
        add(
            AppShortcut(
                id = "search",
                label = stringResource(R.string.app_search),
                iconRes = R.drawable.ic_brand_google,
                onClick = onOpenSearch,
            ),
        )
        add(
            AppShortcut(
                id = "zalo",
                label = stringResource(R.string.app_zalo),
                iconRes = R.drawable.ic_brand_zalo,
                onClick = {
                    openZalo(context)
                    onDismiss()
                },
            ),
        )
        add(
            AppShortcut(
                id = "messenger",
                label = stringResource(R.string.app_messenger),
                iconRes = R.drawable.ic_brand_messenger,
                onClick = {
                    openMessenger(context)
                    onDismiss()
                },
            ),
        )
        add(
            AppShortcut(
                id = "wechat",
                label = stringResource(R.string.app_wechat),
                iconRes = R.drawable.ic_brand_wechat,
                onClick = {
                    openWeChat(context)
                    onDismiss()
                },
            ),
        )
        add(
            AppShortcut(
                id = "share_loc",
                label = stringResource(R.string.app_share_location),
                icon = Icons.Outlined.Place,
                tint = Color(0xFFEA4335),
                onClick = onOpenMaps,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.apps_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.apps_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    AppsGridItem(item)
                }
            }
        }
    }
}

@Composable
private fun AppsGridItem(item: AppShortcut) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item.iconRes != null -> {
                    Image(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label,
                        modifier = Modifier.size(32.dp),
                    )
                }
                item.icon != null -> {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = item.tint ?: Color(0xFF374151),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Color(0xFF374151),
        )
    }
}
