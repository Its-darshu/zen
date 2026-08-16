package com.zenmode.app.feature.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zenmode.app.core.designsystem.ZenMinTouchTarget
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary

object PermissionsTestTags {
    const val ONBOARDING_SCREEN = "onboarding_screen"
    const val PERMISSIONS_SCREEN = "permissions_screen"
    const val STATUS = "permissions_status"
    const val ENABLE_BUTTON = "permissions_enable_button"
    const val SKIP_BUTTON = "permissions_skip_button"
    const val DISCLOSURE = "permissions_disclosure"
    const val ACCESSIBILITY_STATUS = "permissions_accessibility_status"
    const val EXACT_ALARM_STATUS = "permissions_exact_alarm_status"
    const val NOTIFICATION_STATUS = "permissions_notification_status"
}

/**
 * The accessibility disclosure (specification §29).
 *
 * States plainly what the service observes, what it does with it, what it does
 * not do, and that the user can switch it off at any time. Shown before the
 * user is ever sent to the settings screen — never after, and never dressed up
 * as something else.
 */
@Composable
fun AccessibilityDisclosure(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().testTag(PermissionsTestTags.DISCLOSURE)) {
        Paragraph(
            "Zen Mode needs Android's accessibility access to notice when you open an " +
                "app you have chosen to block, so it can bring you back to your Zen screen.",
        )

        ZenSectionHeader("What it observes")
        Paragraph(
            "Only which app is in the foreground, and only while a Zen session is running. " +
                "That is compared against the list of apps you selected.",
        )

        ZenSectionHeader("What it never does")
        Paragraph(
            "It does not read your messages, your screen contents, or anything you type. " +
                "It does not read your notifications and keeps no record of which apps you " +
                "open. Nothing is recorded, and nothing is sent anywhere — Zen Mode has no " +
                "server and no internet access at all.",
        )

        ZenSectionHeader("What it cannot do")
        Paragraph(
            "Android does not let any app lock down the whole device. Blocking works by " +
                "returning you to the Zen screen, and it will not cover every case — " +
                "Android's own settings and system screens stay reachable, and emergency " +
                "calls are never affected.",
        )

        ZenSectionHeader("You stay in control")
        Paragraph(
            "You can switch this off at any time in Android Settings → Accessibility → " +
                "Zen Mode. Sessions, history and statistics keep working without it; " +
                "only the app blocking stops.",
        )
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = ZenTextSecondary,
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * One platform capability: what it is, whether Android currently allows it, and
 * what actually happens if it does not.
 *
 * There is no scolding and no red badge — a capability being off is a fact
 * about the device, not a mistake the user made.
 */
@Composable
fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    grantedDescription: String,
    deniedDescription: String,
    actionText: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$title: ${if (granted) "on" else "off"}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ZenTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (granted) "ON" else "OFF",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) ZenTextPrimary else ZenTextSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (granted) grantedDescription else deniedDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = ZenTextSecondary,
        )
        if (actionText != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = actionText.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = ZenTextPrimary,
                modifier = Modifier
                    .heightIn(min = ZenMinTouchTarget)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
fun PermissionStatusText(enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(PermissionsTestTags.STATUS)) {
        Text(
            text = if (enabled) "Accessibility access is on" else "Accessibility access is off",
            style = MaterialTheme.typography.titleMedium,
            color = ZenTextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (enabled) {
                "Zen Mode can return you to the Zen screen when a blocked app opens."
            } else {
                "Zen Mode cannot block apps yet. Sessions still run and are recorded."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ZenTextSecondary,
        )
    }
}
