package com.denmarkarms.scraper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denmarkarms.scraper.domain.MonitoredAddress
import com.denmarkarms.scraper.domain.MonitoredPerson
import com.denmarkarms.scraper.domain.PrefsKeys
import com.denmarkarms.scraper.domain.Recipient
import com.denmarkarms.scraper.domain.RecipientType
import com.denmarkarms.scraper.ui.viewmodel.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigateToDataManager: () -> Unit = {},
    vm: ConfigViewModel = viewModel()
) {
    val addresses by vm.monitoredAddresses.collectAsState()
    val persons by vm.monitoredPersons.collectAsState()
    val recipients by vm.recipients.collectAsState()
    val testStatus by vm.testStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(testStatus) {
        if (!testStatus.isNullOrEmpty() && testStatus != "Sending…") {
            snackbarHostState.showSnackbar(testStatus!!, duration = SnackbarDuration.Long)
            vm.clearTestStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AddressSection(addresses, vm) }
            item { PersonSection(persons, vm) }
            item { RecipientSection(recipients, vm) }
            item { NotificationSettingsSection(vm, testStatus) }
            item { ApiSettingsSection(vm) }
            item { DataManagementSection(onNavigateToDataManager) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun AddressSection(addresses: List<MonitoredAddress>, vm: ConfigViewModel) {
    var newAddress by remember { mutableStateOf("") }

    SectionHeader("Monitored Addresses")
    OutlinedTextField(
        value = newAddress,
        onValueChange = { newAddress = it },
        label = { Text("Address to monitor") },
        placeholder = { Text("e.g. Denmark Arms 381 Barking Road East Ham E6 1LA") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { vm.addAddress(newAddress); newAddress = "" }) {
                Icon(Icons.Default.Add, contentDescription = "Add address")
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    addresses.forEach { addr ->
        AddressChip(
            label = addr.address,
            active = addr.active,
            onToggle = { vm.toggleMonitoredAddress(addr) },
            onDelete = { vm.removeAddress(addr) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PersonSection(persons: List<MonitoredPerson>, vm: ConfigViewModel) {
    var newPerson by remember { mutableStateOf("") }

    SectionHeader("Monitored People (Companies House)")
    OutlinedTextField(
        value = newPerson,
        onValueChange = { newPerson = it },
        label = { Text("Full name to monitor") },
        placeholder = { Text("e.g. Maxwell Paul DAVITT") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { vm.addPerson(newPerson); newPerson = "" }) {
                Icon(Icons.Default.Add, contentDescription = "Add person")
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    persons.forEach { person ->
        AddressChip(
            label = person.name,
            active = person.active,
            onToggle = { vm.toggleMonitoredPerson(person) },
            onDelete = { vm.removePerson(person) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AddressChip(
    label: String,
    active: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (active) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (active) "Pause" else "Resume",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RecipientSection(recipients: List<Recipient>, vm: ConfigViewModel) {
    var newValue by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RecipientType.EMAIL) }

    SectionHeader("Notification Recipients")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedType == RecipientType.EMAIL,
            onClick = { selectedType = RecipientType.EMAIL },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, null, Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = selectedType == RecipientType.WHATSAPP,
            onClick = { selectedType = RecipientType.WHATSAPP },
            label = { Text("WhatsApp") },
            leadingIcon = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) }
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = newValue,
        onValueChange = { newValue = it },
        label = { Text(if (selectedType == RecipientType.EMAIL) "Email address" else "Phone number (+44...)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (selectedType == RecipientType.EMAIL) KeyboardType.Email else KeyboardType.Phone
        ),
        trailingIcon = {
            IconButton(onClick = { vm.addRecipient(selectedType, newValue); newValue = "" }) {
                Icon(Icons.Default.Add, contentDescription = "Add recipient")
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    recipients.forEach { r ->
        AddressChip(
            label = "[${r.type}] ${r.value}",
            active = r.active,
            onToggle = { vm.toggleRecipient(r) },
            onDelete = { vm.removeRecipient(r) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun NotificationSettingsSection(vm: ConfigViewModel, testStatus: String?) {
    var smtpHost by remember { mutableStateOf(vm.getPref(PrefsKeys.SMTP_HOST, "smtp.gmail.com")) }
    var smtpPort by remember { mutableStateOf(vm.getPref(PrefsKeys.SMTP_PORT, "587")) }
    var smtpUser by remember { mutableStateOf(vm.getPref(PrefsKeys.SMTP_USERNAME)) }
    var smtpPass by remember { mutableStateOf(vm.getPref(PrefsKeys.SMTP_PASSWORD)) }
    var smtpFrom by remember { mutableStateOf(vm.getPref(PrefsKeys.SMTP_FROM_NAME, "Denmark Arms Scraper")) }
    var passwordVisible by remember { mutableStateOf(false) }
    var twilioSid by remember { mutableStateOf(vm.getPref(PrefsKeys.TWILIO_ACCOUNT_SID)) }
    var twilioToken by remember { mutableStateOf(vm.getPref(PrefsKeys.TWILIO_AUTH_TOKEN)) }
    var twilioFrom by remember { mutableStateOf(vm.getPref(PrefsKeys.TWILIO_FROM_NUMBER)) }

    SectionHeader("Email (SMTP) Settings")
    OutlinedTextField(smtpHost, { smtpHost = it; vm.setPref(PrefsKeys.SMTP_HOST, it) },
        label = { Text("SMTP Host") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(smtpPort, { smtpPort = it; vm.setPref(PrefsKeys.SMTP_PORT, it) },
        label = { Text("SMTP Port") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(smtpUser, { smtpUser = it; vm.setPref(PrefsKeys.SMTP_USERNAME, it) },
        label = { Text("Username / From address") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        smtpPass, { smtpPass = it; vm.setPref(PrefsKeys.SMTP_PASSWORD, it) },
        label = { Text("Password / App password") }, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(smtpFrom, { smtpFrom = it; vm.setPref(PrefsKeys.SMTP_FROM_NAME, it) },
        label = { Text("Sender display name") }, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(16.dp))
    SectionHeader("WhatsApp (Twilio) Settings")
    OutlinedTextField(twilioSid, { twilioSid = it; vm.setPref(PrefsKeys.TWILIO_ACCOUNT_SID, it) },
        label = { Text("Twilio Account SID") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(twilioToken, { twilioToken = it; vm.setPref(PrefsKeys.TWILIO_AUTH_TOKEN, it) },
        label = { Text("Twilio Auth Token") }, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(twilioFrom, { twilioFrom = it; vm.setPref(PrefsKeys.TWILIO_FROM_NUMBER, it) },
        label = { Text("Twilio WhatsApp From number (+1...)") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { vm.sendTestNotification() },
        modifier = Modifier.fillMaxWidth(),
        enabled = testStatus != "Sending…"
    ) {
        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (testStatus == "Sending…") "Sending…" else "Send test notification")
    }
}

@Composable
private fun DataManagementSection(onNavigate: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    SectionHeader("Data Management")
    Text(
        "View and delete stored applications, people, and log entries. Useful for resetting test data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    OutlinedButton(
        onClick = onNavigate,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Manage Database Entries")
    }
}

@Composable
private fun ApiSettingsSection(vm: ConfigViewModel) {
    var chApiKey by remember { mutableStateOf(vm.getPref(PrefsKeys.COMPANIES_HOUSE_API_KEY)) }

    Spacer(Modifier.height(8.dp))
    SectionHeader("Companies House API")
    Text(
        "Get a free API key at developer.company-information.service.gov.uk",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    OutlinedTextField(
        chApiKey,
        { chApiKey = it; vm.setPref(PrefsKeys.COMPANIES_HOUSE_API_KEY, it) },
        label = { Text("Companies House API Key") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation()
    )
}
