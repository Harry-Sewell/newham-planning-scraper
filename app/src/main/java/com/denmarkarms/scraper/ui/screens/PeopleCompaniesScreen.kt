package com.denmarkarms.scraper.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.denmarkarms.scraper.domain.Appointment
import com.denmarkarms.scraper.domain.Person
import com.denmarkarms.scraper.domain.SicCodes
import com.denmarkarms.scraper.ui.viewmodel.PeopleViewModel

private fun shareJson(context: android.content.Context, json: String, filename: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Export JSON"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleCompaniesScreen(vm: PeopleViewModel = viewModel()) {
    val persons by vm.persons.collectAsState()
    val appointments by vm.appointments.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People & Companies") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (persons.isNotEmpty()) {
                        IconButton(onClick = {
                            shareJson(context, vm.buildExportJson(persons, appointments), "people_companies.json")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export JSON",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (persons.isEmpty() && appointments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        if (vm.hasApiKey) Icons.Default.People else Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    if (!vm.hasApiKey) {
                        Text(
                            "Companies House API key required",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "A free API key is needed to search Companies House. Register at:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "developer.company-information.service.gov.uk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://developer.company-information.service.gov.uk/"))
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Then add the key in the Config tab under Companies House API Key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            "No people or companies tracked yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add person names in Config and run a check",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (persons.isNotEmpty()) {
                    item {
                        Text(
                            "Tracked Individuals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(persons.sortedByDescending { person ->
                        appointments
                            .filter { it.personId == person.id }
                            .maxOfOrNull { it.appointedOn } ?: "0000-00-00"
                    }) { person ->
                        val personAppointments = appointments
                            .filter { it.personId == person.id }
                            .sortedWith(compareBy<Appointment> { it.resignedOn.isNotBlank() }.thenByDescending { it.appointedOn })
                        PersonCard(person, personAppointments, vm.formatTimestamp(person.firstSeen))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: Person, appointments: List<Appointment>, firstSeen: String) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val profileUrl = "https://find-and-update.company-information.service.gov.uk/officers/${person.officerId}/appointments"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            person.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "Open in Companies House",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Monitoring: ${person.monitoredName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "First seen: $firstSeen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${appointments.size} appts")
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            if (expanded && appointments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                appointments.forEach { apt ->
                    AppointmentRow(apt)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun AppointmentRow(apt: Appointment) {
    val context = LocalContext.current
    val companyUrl = "https://find-and-update.company-information.service.gov.uk/company/${apt.companyNumber}"
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(companyUrl)))
        },
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Business,
            contentDescription = null,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(apt.companyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null,
                    modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.outline)
            }
            Text(
                "${apt.role} · from ${apt.appointedOn}" + if (apt.resignedOn.isNotBlank()) " to ${apt.resignedOn}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (apt.natureOfBusiness.isNotBlank()) {
                apt.natureOfBusiness.split(",").forEach { code ->
                    Text(
                        SicCodes.describe(code.trim()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (apt.companyStatus.isNotBlank()) {
                Text(
                    apt.companyStatus.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (apt.companyStatus == "active") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
