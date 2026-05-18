package com.denmarkarms.scraper.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denmarkarms.scraper.domain.PlanningApplication
import com.denmarkarms.scraper.ui.viewmodel.PlanningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningTimelineScreen(vm: PlanningViewModel = viewModel()) {
    val applications by vm.applications.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planning Applications") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (applications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocationCity,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No planning applications found yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add addresses in Config and run a check",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(applications) { app ->
                    PlanningApplicationCard(app, vm.formatTimestamp(app.firstSeen))
                }
            }
        }
    }
}

@Composable
private fun PlanningApplicationCard(app: PlanningApplication, firstSeen: String) {
    val context = LocalContext.current
    val url = "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=summary&keyVal=${app.keyVal}"
    val refType = planningRefType(app.reference)
    val subtitle = buildString {
        if (refType.isNotBlank()) append(refType)
        if (app.receivedDate.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append("Validated: ${app.receivedDate}")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                // Primary: reference number + status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        app.reference.ifBlank { "Unknown ref" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusChip(app.status)
                }
                // Secondary: type name · validated date
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // Detail: description text
                if (app.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (app.address.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            app.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "First tracked: $firstSeen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun planningRefType(reference: String): String {
    val suffix = reference.substringAfterLast("/").uppercase().trim()
    return when (suffix) {
        "FUL" -> "Full Planning Application"
        "LBC" -> "Listed Building Consent"
        "LBD" -> "Listed Building Demolition"
        "LDC", "LDCP" -> "Lawful Development Certificate (Proposed)"
        "LDCE" -> "Lawful Development Certificate (Existing)"
        "HH" -> "Householder Application"
        "ADV" -> "Advertisement Consent"
        "NMA" -> "Non-Material Amendment"
        "PA" -> "Prior Approval"
        "PAC" -> "Prior Approval Certificate"
        "CON" -> "Conservation Area Consent"
        "DEM" -> "Demolition in Conservation Area"
        "TCA" -> "Tree in Conservation Area"
        "TPO" -> "Tree Preservation Order"
        "VAR", "S73" -> "Variation of Condition"
        "OUT" -> "Outline Planning Application"
        "RES" -> "Reserved Matters"
        "PIP" -> "Permission in Principle"
        "TDC" -> "Technical Details Consent"
        "ENV" -> "Environmental Statement"
        "HAZ" -> "Hazardous Substances Consent"
        "OBJ" -> "Objection"
        else -> ""
    }
}

@Composable
private fun StatusChip(status: String) {
    val containerColor = when {
        status.contains("pending", ignoreCase = true) -> MaterialTheme.colorScheme.secondaryContainer
        status.contains("granted", ignoreCase = true) || status.contains("approved", ignoreCase = true) ->
            MaterialTheme.colorScheme.tertiaryContainer
        status.contains("refused", ignoreCase = true) || status.contains("refused", ignoreCase = true) ->
            MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    if (status.isNotBlank()) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor
        ) {
            Text(
                status,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
