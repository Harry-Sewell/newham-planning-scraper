package com.denmarkarms.scraper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denmarkarms.scraper.data.db.entity.*
import com.denmarkarms.scraper.ui.viewmodel.DataManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagerScreen(
    onBack: () -> Unit,
    vm: DataManagerViewModel = viewModel()
) {
    val tabs = listOf("Applications", "People", "Change Log")
    var selectedTab by remember { mutableStateOf(0) }

    val applications by vm.applications.collectAsState()
    val documents by vm.documents.collectAsState()
    val persons by vm.persons.collectAsState()
    val appointments by vm.appointments.collectAsState()
    val changeLog by vm.changeLog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ApplicationsTab(applications, documents, vm)
                1 -> PeopleTab(persons, appointments, vm)
                2 -> ChangeLogTab(changeLog, vm)
            }
        }
    }
}

@Composable
private fun ApplicationsTab(
    applications: List<PlanningApplicationEntity>,
    documents: List<PlanningDocumentEntity>,
    vm: DataManagerViewModel
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear all applications?",
            text = "This will delete all ${applications.size} planning applications and their documents. They will be re-discovered on the next check.",
            onConfirm = { vm.clearAllApplications(); showClearConfirm = false },
            onDismiss = { showClearConfirm = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionToolbar(
                count = applications.size,
                label = "planning application",
                onClearAll = { showClearConfirm = true }
            )
        }
        if (applications.isEmpty()) {
            item { EmptyHint("No planning applications stored") }
        }
        items(applications, key = { it.id }) { app ->
            val appDocs = documents.filter { it.applicationKeyVal == app.keyVal }
            DismissibleCard(onDelete = { vm.deleteApplication(app) }) {
                Column {
                    Text(app.reference.ifBlank { app.keyVal },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(app.description.take(80) + if (app.description.length > 80) "…" else "",
                        style = MaterialTheme.typography.bodySmall)
                    Text(app.status, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("First seen: ${vm.formatTimestamp(app.firstSeen)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)

                    if (appDocs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Documents (${appDocs.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold)
                        appDocs.forEach { doc ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("• ${doc.name}",
                                        style = MaterialTheme.typography.labelSmall)
                                    if (doc.date.isNotBlank()) {
                                        Text(doc.date,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                IconButton(
                                    onClick = { vm.deleteDocument(doc) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete document",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(
    persons: List<PersonEntity>,
    appointments: List<AppointmentEntity>,
    vm: DataManagerViewModel
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear all people?",
            text = "This will delete all ${persons.size} people and their appointments. They will be re-discovered on the next check.",
            onConfirm = { vm.clearAllPersons(); showClearConfirm = false },
            onDismiss = { showClearConfirm = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionToolbar(
                count = persons.size,
                label = "person",
                onClearAll = { showClearConfirm = true }
            )
        }
        if (persons.isEmpty()) {
            item { EmptyHint("No people stored") }
        }
        items(persons, key = { it.id }) { person ->
            val personAppointments = appointments.filter { it.personId == person.id }
            DismissibleCard(onDelete = { vm.deletePerson(person) }) {
                Column {
                    Text(person.displayName, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Monitoring: ${person.monitoredName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("Officer ID: ${person.officerId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("First seen: ${vm.formatTimestamp(person.firstSeen)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)

                    if (personAppointments.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Appointments (${personAppointments.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold)
                        personAppointments.forEach { apt ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("• ${apt.companyName} (${apt.role})",
                                        style = MaterialTheme.typography.labelSmall)
                                    Text("from ${apt.appointedOn}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(
                                    onClick = { vm.deleteAppointment(apt) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete appointment",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeLogTab(
    entries: List<ChangeLogEntity>,
    vm: DataManagerViewModel
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear change log?",
            text = "This will delete all ${entries.size} log entries. Discovered data is not affected.",
            onConfirm = { vm.clearChangeLog(); showClearConfirm = false },
            onDismiss = { showClearConfirm = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionToolbar(
                count = entries.size,
                label = "log entry",
                onClearAll = { showClearConfirm = true }
            )
        }
        if (entries.isEmpty()) {
            item { EmptyHint("Change log is empty") }
        }
        items(entries, key = { it.id }) { entry ->
            DismissibleCard(onDelete = { vm.deleteLogEntry(entry) }) {
                Column {
                    Text(entry.type, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    Text(vm.formatTimestamp(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun SectionToolbar(count: Int, label: String, onClearAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$count ${label}${if (count != 1) "s" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        if (count > 0) {
            TextButton(onClick = onClearAll) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear all")
            }
        }
    }
}

@Composable
private fun DismissibleCard(onDelete: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) { content() }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
