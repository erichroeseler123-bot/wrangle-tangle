package com.wrangletangle.text

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val TealAccent = Color(0xFF2DD4BF)
private val DarkSurface = Color(0xFF071613)
private val DarkBackground = Color(0xFF03110F)

data class ContactRecipient(
    val name: String,
    val phoneNumber: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WrangleTangleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WrangleTangleScreen(activity = this)
                }
            }
        }
    }

    // The "WrangleTangle" Loop
    fun sendIndividualSms(message: String, recipients: List<String>) {
        val smsManager = getSystemService(SmsManager::class.java)
        recipients.forEach { number ->
            smsManager.sendTextMessage(number, null, message, null, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrangleTangleScreen(activity: MainActivity) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val recipients = remember { mutableStateListOf<ContactRecipient>() }

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedRecipients = extractRecipientsFromIntent(context, result.data)
            val newRecipients = pickedRecipients.filterNot { candidate ->
                recipients.any { existing -> existing.phoneNumber == candidate.phoneNumber }
            }
            recipients.addAll(newRecipients)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val contactsGranted = grantResults[Manifest.permission.READ_CONTACTS] == true
        val smsGranted = grantResults[Manifest.permission.SEND_SMS] == true

        when {
            contactsGranted && smsGranted -> contactPicker.launch(buildContactPickerIntent())
            !contactsGranted -> toast(context, "Contacts permission is required.")
            !smsGranted -> toast(context, "SMS permission is required.")
        }
    }

    fun launchPickerWithFallback() {
        if (hasPermissions(context)) {
            contactPicker.launch(buildContactPickerIntent())
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.SEND_SMS
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WrangleTangle Text")
                        Text(
                            text = "Individual 1-on-1 SMS sender",
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
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            maxLines = 10,
                            label = { Text("Type the message to send") },
                            supportingText = {
                                Text("Each contact receives a separate SMS thread. On older phones, use Add another after each pick.")
                            }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = ::launchPickerWithFallback
                            ) {
                                Icon(Icons.Outlined.People, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pick contacts")
                            }

                            if (recipients.isNotEmpty()) {
                                OutlinedButton(onClick = ::launchPickerWithFallback) {
                                    Text("Add another")
                                }
                            }

                            Button(
                                onClick = {
                                    when {
                                        message.isBlank() -> toast(context, "Enter a message first.")
                                        recipients.isEmpty() -> toast(context, "Pick at least one contact.")
                                        !hasSmsPermission(context) -> {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.SEND_SMS
                                                )
                                            )
                                        }
                                        else -> {
                                            activity.sendIndividualSms(
                                                message = message.trim(),
                                                recipients = recipients.map { it.phoneNumber }
                                            )
                                            toast(
                                                context,
                                                "Sent ${recipients.size} individual SMS messages."
                                            )
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send to ${recipients.size} recipient${if (recipients.size == 1) "" else "s"}")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recipients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (recipients.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "No contacts selected yet. Tap Pick contacts to add recipients.",
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recipients, key = { it.phoneNumber }) { recipient ->
                    RecipientCard(recipient = recipient)
                }
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
private fun WrangleTangleTheme(content: @Composable () -> Unit) {
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

private fun hasPermissions(context: Context): Boolean {
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

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
