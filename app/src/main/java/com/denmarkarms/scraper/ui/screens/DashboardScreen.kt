package com.denmarkarms.scraper.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denmarkarms.scraper.data.db.entity.ChangeLogEntity
import com.denmarkarms.scraper.domain.ChangeType
import com.denmarkarms.scraper.domain.DownloadStatus
import com.denmarkarms.scraper.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val changes by vm.recentChanges.collectAsState()
    val isChecking by vm.isChecking.collectAsState()
    val lastChecked by vm.lastChecked.collectAsState()
    val monitoredAddresses by vm.monitoredAddresses.collectAsState()
    val monitoredPersons by vm.monitoredPersons.collectAsState()
    val documentStatusMap by vm.documentStatusMap.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planning Scraper")
                        val subtitle = when {
                            isChecking -> "Checking…"
                            lastChecked != null -> "Updated ${vm.formatTimestamp(lastChecked!!)}"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (changes.isNotEmpty()) {
                        IconButton(onClick = { vm.dismissAll() }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear all",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { vm.runChecks() },
                        enabled = !isChecking
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Run checks now",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (isChecking) rotationAngle else 0f
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (changes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No changes detected yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))

                    // Planning addresses
                    if (monitoredAddresses.isEmpty()) {
                        MonitorSummaryRow(Icons.Default.LocationCity, "No addresses configured — add some in Config")
                    } else {
                        MonitorSummaryRow(
                            Icons.Default.LocationCity,
                            "Monitoring ${monitoredAddresses.size} address${if (monitoredAddresses.size != 1) "es" else ""}"
                        )
                        monitoredAddresses.forEach { addr ->
                            Text(
                                "  • ${addr.address}" + if (!addr.active) " (paused)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (addr.active) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // People
                    if (monitoredPersons.isEmpty()) {
                        MonitorSummaryRow(Icons.Default.Person, "No people configured — add names in Config")
                    } else {
                        MonitorSummaryRow(
                            Icons.Default.Person,
                            "Monitoring ${monitoredPersons.size} person${if (monitoredPersons.size != 1) "s" else ""}"
                        )
                        monitoredPersons.forEach { person ->
                            Text(
                                "  • ${person.name}" + if (!person.active) " (paused)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (person.active) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tap ↻ to run a check now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Recent Changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(changes, key = { it.id }) { entry ->
                    SwipeToDismissChangeCard(
                        entry = entry,
                        formattedTime = vm.formatTimestamp(entry.timestamp),
                        onDismiss = { vm.dismissEntry(entry) },
                        actionUrl = changeUrl(entry),
                        documentStatusMap = documentStatusMap
                    )
                }
            }
        }
    }
}

private fun changeUrl(entry: ChangeLogEntity): String? = when (entry.type) {
    ChangeType.NEW_APPLICATION, ChangeType.STATUS_CHANGE ->
        "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=summary&keyVal=${entry.entityId}"
    ChangeType.NEW_DOCUMENT ->
        "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=documents&keyVal=${entry.entityId}"
    ChangeType.NEW_PERSON ->
        "https://find-and-update.company-information.service.gov.uk/officers/${entry.entityId}/appointments"
    ChangeType.NEW_APPOINTMENT ->
        "https://find-and-update.company-information.service.gov.uk/company/${entry.entityId}"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissChangeCard(
    entry: ChangeLogEntity,
    formattedTime: String,
    onDismiss: () -> Unit,
    actionUrl: String? = null,
    documentStatusMap: Map<String, String> = emptyMap()
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier.fillMaxSize().background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        ChangeCard(entry, formattedTime, actionUrl, documentStatusMap)
    }
}

@Composable
private fun ChangeCard(entry: ChangeLogEntity, formattedTime: String, actionUrl: String? = null, documentStatusMap: Map<String, String> = emptyMap()) {
    val context = LocalContext.current
    val (icon, accentColor, label) = when (entry.type) {
        ChangeType.NEW_APPLICATION -> Triple(Icons.Default.AddCircle, MaterialTheme.colorScheme.primary, "New Application")
        ChangeType.NEW_DOCUMENT -> Triple(Icons.Default.Description, MaterialTheme.colorScheme.secondary, "New Document")
        ChangeType.STATUS_CHANGE -> Triple(Icons.Default.Update, MaterialTheme.colorScheme.tertiary, "Status Change")
        ChangeType.NEW_PERSON -> Triple(Icons.Default.PersonAdd, MaterialTheme.colorScheme.primary, "New Person")
        ChangeType.NEW_APPOINTMENT -> Triple(Icons.Default.Work, MaterialTheme.colorScheme.secondary, "New Appointment")
        else -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.outline, "Info")
    }
    val headline = cardHeadline(entry)

    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (actionUrl != null) Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)))
            } else Modifier
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (headline.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.description.take(120) + if (entry.description.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (entry.type == ChangeType.NEW_DOCUMENT) {
                    val docName = entry.description.substringAfter("): ").substringBeforeLast(" (").trim()
                    val status = documentStatusMap["${entry.entityId}:$docName"]
                    if (!status.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        DownloadStatusBadge(status)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorSummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DownloadStatusBadge(status: String) {
    val (icon, color, label) = when (status) {
        DownloadStatus.QUEUED -> Triple(Icons.Default.Schedule, MaterialTheme.colorScheme.outline, "Queued for download")
        DownloadStatus.IN_PROGRESS -> Triple(Icons.Default.Downloading, MaterialTheme.colorScheme.primary, "Downloading…")
        DownloadStatus.DOWNLOADED -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.tertiary, "Downloaded")
        DownloadStatus.FAILED -> Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, "Download failed")
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun cardHeadline(entry: ChangeLogEntity): String = when (entry.type) {
    ChangeType.NEW_APPLICATION ->
        entry.description.substringAfter("New planning application ").substringBefore(":").trim()
    ChangeType.STATUS_CHANGE ->
        entry.description.substringAfter("Status changed for ").substringBefore(":").trim()
    ChangeType.NEW_DOCUMENT ->
        entry.description.substringAfter("New document for ").substringBefore(" (").trim()
            .takeIf { it.contains("/") } ?: entry.entityId
    ChangeType.NEW_PERSON ->
        entry.description.substringAfter("New person found: ").substringBefore(" (").trim()
    ChangeType.NEW_APPOINTMENT ->
        entry.description.substringAfter("New appointment: ").substringBefore(" as ").trim()
    else -> ""
}
