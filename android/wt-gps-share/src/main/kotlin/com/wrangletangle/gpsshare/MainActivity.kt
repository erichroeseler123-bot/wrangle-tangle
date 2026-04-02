package com.wrangletangle.gpsshare

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val TealAccent = Color(0xFF2DD4BF)
private val DarkSurface = Color(0xFF071613)
private val DarkBackground = Color(0xFF03110F)

private enum class ShareStage {
    PICK_RECIPIENTS,
    PREPARE_SHARE,
    SENT
}

data class ContactRecipient(
    val name: String,
    val phoneNumber: String
)

data class ShareLocation(
    val latitude: Double,
    val longitude: Double
) {
    val mapsUrl: String
        get() = "https://maps.google.com/?q=$latitude,$longitude"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GpsShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GpsShareScreen(activity = this)
                }
            }
        }
    }

    suspend fun sendIndividualSms(message: String, recipients: List<String>) {
        val smsManager = getSystemService(SmsManager::class.java)
        recipients.forEachIndexed { index, number ->
            smsManager.sendTextMessage(number, null, message, null, null)
            if (index != recipients.lastIndex) {
                delay(180)
            }
        }
    }

    suspend fun fetchCurrentLocation(): ShareLocation? {
        val locationManager = getSystemService(LocationManager::class.java)
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return null

        val location = awaitLocation(
            locationManager = locationManager,
            provider = provider,
            executor = ContextCompat.getMainExecutor(this)
        )
            ?: locationManager.getLastKnownLocation(provider)
            ?: return null

        return ShareLocation(
            latitude = location.latitude,
            longitude = location.longitude
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpsShareScreen(activity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(ShareStage.PICK_RECIPIENTS) }
    var note by remember { mutableStateOf("I'm here.") }
    var currentLocation by remember { mutableStateOf<ShareLocation?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var lastSentMessage by remember { mutableStateOf("") }
    val recipients = remember { mutableStateListOf<ContactRecipient>() }
    val lastSentRecipients = remember { mutableStateListOf<ContactRecipient>() }

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedRecipients = extractRecipientsFromIntent(context, result.data)
            val newRecipients = pickedRecipients.filterNot { candidate ->
                recipients.any { existing -> existing.phoneNumber == candidate.phoneNumber }
            }
            recipients.addAll(newRecipients)
            if (recipients.isNotEmpty() && stage == ShareStage.PICK_RECIPIENTS) {
                stage = ShareStage.PREPARE_SHARE
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val contactsGranted = grantResults[Manifest.permission.READ_CONTACTS] == true
        val smsGranted = grantResults[Manifest.permission.SEND_SMS] == true
        val coarseGranted = grantResults[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val fineGranted = grantResults[Manifest.permission.ACCESS_FINE_LOCATION] == true

        when {
            contactsGranted && smsGranted && (coarseGranted || fineGranted) -> contactPicker.launch(buildContactPickerIntent())
            !contactsGranted -> toast(context, "Contacts permission is required.")
            !smsGranted -> toast(context, "SMS permission is required.")
            else -> toast(context, "Location permission is required.")
        }
    }

    fun launchPicker() {
        if (hasContactsAndSmsPermissions(context)) {
            contactPicker.launch(buildContactPickerIntent())
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    fun loadLocation() {
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
            return
        }

        isLoadingLocation = true
        scope.launch {
            val location = activity.fetchCurrentLocation()
            isLoadingLocation = false
            if (location == null) {
                toast(context, "Could not get your current location.")
            } else {
                currentLocation = location
            }
        }
    }

    val shareMessage = remember(note, currentLocation) {
        buildShareMessage(note = note, location = currentLocation)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WT-GPS Share")
                        Text(
                            text = "Send your current location individually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (stage) {
                ShareStage.PICK_RECIPIENTS -> {
                    item {
                        PrimaryCard(title = "Select Contacts") {
                            Text(
                                text = "Choose the people who should get your location. Each person receives a separate SMS thread.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = ::launchPicker,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Outlined.People, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Contacts")
                            }
                        }
                    }

                    if (recipients.isNotEmpty()) {
                        item {
                            StageFooterCard(
                                text = "${recipients.size} recipient${if (recipients.size == 1) "" else "s"} selected"
                            ) {
                                stage = ShareStage.PREPARE_SHARE
                            }
                        }
                        items(recipients, key = { it.phoneNumber }) { recipient ->
                            RecipientCard(recipient = recipient)
                        }
                    }
                }

                ShareStage.PREPARE_SHARE -> {
                    item {
                        PrimaryCard(title = "To") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recipients.forEach { recipient ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text(recipient.name) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = ::launchPicker) {
                                    Icon(Icons.Outlined.People, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (recipients.isEmpty()) "Add People" else "Add another")
                                }
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Current Location") {
                            Text(
                                text = "Grab your current position, then send it to everyone privately.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = ::loadLocation,
                                    enabled = !isLoadingLocation,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isLoadingLocation) "Finding..." else "Use current location")
                                }
                            }

                            currentLocation?.let { location ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Maps link ready:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = location.mapsUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Message") {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                label = { Text("Optional note before the location link") },
                                supportingText = {
                                    Text("Everyone gets the same note, followed by your current location link.")
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    text = shareMessage ?: "Tap Use current location to generate the share message.",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    when {
                                        recipients.isEmpty() -> toast(context, "Pick at least one contact.")
                                        shareMessage == null -> toast(context, "Fetch your location first.")
                                        !hasSmsPermission(context) -> {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.SEND_SMS,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        }
                                        else -> {
                                            isSending = true
                                            scope.launch {
                                                activity.sendIndividualSms(
                                                    message = shareMessage,
                                                    recipients = recipients.map { it.phoneNumber }
                                                )
                                                lastSentMessage = shareMessage
                                                lastSentRecipients.clear()
                                                lastSentRecipients.addAll(recipients)
                                                isSending = false
                                                stage = ShareStage.SENT
                                            }
                                        }
                                    }
                                },
                                enabled = !isSending,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Outlined.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSending) "Sending..." else "Send individually")
                            }
                        }
                    }
                }

                ShareStage.SENT -> {
                    item {
                        PrimaryCard(title = "Sent to ${lastSentRecipients.size} people") {
                            Text(
                                text = "Each person received your location in a separate 1-on-1 SMS thread.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = lastSentMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        currentLocation = null
                                        recipients.clear()
                                        stage = ShareStage.PICK_RECIPIENTS
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Share another")
                                }
                                OutlinedButton(onClick = { stage = ShareStage.PREPARE_SHARE }) {
                                    Text("Edit and resend")
                                }
                            }
                        }
                    }

                    items(lastSentRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientCard(recipient = recipient)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun StageFooterCard(
    text: String,
    onContinue: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onContinue) {
                Text("Prepare share")
            }
        }
    }
}

@Composable
private fun RecipientCard(recipient: ContactRecipient) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = recipient.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = recipient.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GpsShareTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = TealAccent,
        secondary = TealAccent,
        tertiary = TealAccent,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = Color(0xFF10211D),
        onPrimary = Color.Black
    )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

private fun buildShareMessage(note: String, location: ShareLocation?): String? {
    location ?: return null
    val trimmedNote = note.trim()
    return buildString {
        if (trimmedNote.isNotEmpty()) {
            append(trimmedNote)
            append("\n\n")
        }
        append("My location: ")
        append(location.mapsUrl)
    }
}

private fun buildContactPickerIntent(): Intent {
    return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI).apply {
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
}

private fun extractRecipientsFromIntent(context: Context, data: Intent?): List<ContactRecipient> {
    val uris = buildList {
        data?.data?.let(::add)
        data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
    }

    return uris.mapNotNull { uri -> resolveRecipient(context, uri) }
}

private fun resolveRecipient(context: Context, uri: Uri): ContactRecipient? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        if (cursor.moveToFirst() && nameIndex >= 0 && numberIndex >= 0) {
            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "Unnamed contact" }
            val number = cursor.getString(numberIndex)?.trim().orEmpty()
            if (number.isNotBlank()) {
                return ContactRecipient(name = name, phoneNumber = number)
            }
        }
    }

    return null
}

private fun hasContactsAndSmsPermissions(context: Context): Boolean {
    return hasContactsPermission(context) && hasSmsPermission(context)
}

private fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun hasSmsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun hasLocationPermission(context: Context): Boolean {
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return coarse || fine
}

private suspend fun awaitLocation(
    locationManager: LocationManager,
    provider: String,
    executor: Executor
): Location? = suspendCancellableCoroutine { continuation ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        locationManager.getCurrentLocation(
            provider,
            cancellationSignal,
            executor,
        ) { location ->
            continuation.resume(location)
        }
    } else {
        @Suppress("DEPRECATION")
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                @Suppress("DEPRECATION")
                locationManager.removeUpdates(this)
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }
        }
        continuation.invokeOnCancellation {
            @Suppress("DEPRECATION")
            locationManager.removeUpdates(listener)
        }
        @Suppress("DEPRECATION")
        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
