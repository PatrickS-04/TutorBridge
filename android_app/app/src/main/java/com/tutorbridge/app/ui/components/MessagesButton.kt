package com.tutorbridge.app.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutorbridge.app.R

/** What fits on the badge: 100 unread messages or more all read the same. */
internal fun unreadBadgeText(unread: Int): String = if (unread > 99) "99+" else "$unread"

/**
 * The home screens' way into Messages, showing how many messages are unopened. The badge
 * sits on the outside of the IconButton, which clips what it holds, so a two-digit count
 * is not cut off. The offset brings it back in over the icon's corner.
 */
@Composable
fun MessagesButton(unread: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            if (unread > 0) {
                Badge(modifier = Modifier.offset(x = (-12).dp, y = 12.dp)) { Text(unreadBadgeText(unread), fontSize = 12.sp) }
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.Chat,
                contentDescription = if (unread > 0) pluralStringResource(R.plurals.messages_unread, unread, unread)
                else stringResource(R.string.common_messages)
            )
        }
    }
}
