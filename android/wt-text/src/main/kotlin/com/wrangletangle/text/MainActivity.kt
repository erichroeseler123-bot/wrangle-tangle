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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TealAccent = Color(0xFF2DD4BF)
private val DarkSurface = Color(0xFF071613)
private val DarkBackground = Color(0xFF03110F)

private enum class WrangleStage {
    PICK_RECIPIENTS,
    COMPOSE,
    SENT
}

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
    suspend fun sendIndividualSms(message: String, recipients: List<String>) {
        val smsManager = getSystemService(SmsManager::class.java)
        recipients.forEachIndexed { index, number ->
            smsManager.sendTextMessage(number, null, message, null, null)
            if (index != recipients.lastIndex) {
                delay(180)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrangleTangleScreen(activity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(WrangleStage.PICK_RECIPIENTS) }
    var message by remember { mutableStateOf("") }
    var lastSentMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
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
            if (recipients.isNotEmpty() && stage == WrangleStage.PICK_RECIPIENTS) {
                stage = WrangleStage.COMPOSE
            }
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

    fun launchPicker() {
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
                            text = "Send one message to multiple people, individually",
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
                WrangleStage.PICK_RECIPIENTS -> {
                    item {
                        PrimaryCard(title = "Select Contacts") {
                            Text(
                                text = "Pick the people who should get the same message, but in separate 1-on-1 SMS threads.",
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
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "On phones that do not support multi-select, pick one contact and then tap Add another.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (recipients.isNotEmpty()) {
                        item {
                            StageFooterCard(
                                text = "${recipients.size} recipient${if (recipients.size == 1) "" else "s"} selected"
                            ) {
                                stage = WrangleStage.COMPOSE
                            }
                        }
                        items(recipients, key = { it.phoneNumber }) { recipient ->
                            RecipientCard(recipient = recipient)
                        }
                    }
                }

                WrangleStage.COMPOSE -> {
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
                                OutlinedButton(onClick = { stage = WrangleStage.PICK_RECIPIENTS }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Edit recipients")
                                }
                            }
                        }
                    }

                    item {
                        PrimaryCard(title = "Message") {
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 6,
                                maxLines = 10,
                                label = { Text("Write the text once") },
                                supportingText = {
                                    Text("WrangleTangle sends this to each person separately, never as a group chat.")
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                                isSending = true
                                                scope.launch {
                                                    activity.sendIndividualSms(
                                                        message = message.trim(),
                                                        recipients = recipients.map { it.phoneNumber }
                                                    )
                                                    lastSentMessage = message.trim()
                                                    lastSentRecipients.clear()
                                                    lastSentRecipients.addAll(recipients)
                                                    isSending = false
                                                    stage = WrangleStage.SENT
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
                                    Text(
                                        if (isSending) "Sending..." else "Send individually"
                                    )
                                }
                            }
                        }
                    }
                }

                WrangleStage.SENT -> {
                    item {
                        PrimaryCard(title = "Sent to ${lastSentRecipients.size} people") {
                            Text(
                                text = "Each recipient got a normal 1-on-1 SMS from your number. Replies will come back in separate threads.",
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
                                        message = ""
                                        recipients.clear()
                                        stage = WrangleStage.PICK_RECIPIENTS
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Send another")
                                }
                                OutlinedButton(onClick = { stage = WrangleStage.COMPOSE }) {
                                    Text("Edit message and resend")
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
                Text("Compose message")
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
