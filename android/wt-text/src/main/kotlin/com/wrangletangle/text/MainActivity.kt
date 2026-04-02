package com.wrangletangle.text

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
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

private enum class WrangleStage {
    PICK_RECIPIENTS,
    COMPOSE,
    HANDOFF,
    COMPLETE
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrangleTangleScreen(activity: MainActivity) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf(WrangleStage.PICK_RECIPIENTS) }
    var message by remember { mutableStateOf("") }
    var handoffMessage by remember { mutableStateOf("") }
    var handoffIndex by remember { mutableStateOf(0) }
    val recipients = remember { mutableStateListOf<ContactRecipient>() }
    val handoffRecipients = remember { mutableStateListOf<ContactRecipient>() }

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
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPicker.launch(buildContactPickerIntent())
        } else {
            toast(context, "Contacts permission is required to pick recipients.")
        }
    }

    fun launchPicker() {
        if (hasContactsPermission(context)) {
            contactPicker.launch(buildContactPickerIntent())
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun beginNativeHandoff() {
        when {
            message.isBlank() -> toast(context, "Enter a message first.")
            recipients.isEmpty() -> toast(context, "Pick at least one contact.")
            else -> {
                handoffRecipients.clear()
                handoffRecipients.addAll(recipients)
                handoffMessage = message.trim()
                handoffIndex = 0
                stage = WrangleStage.HANDOFF
            }
        }
    }

    fun openNextThread() {
        val recipient = handoffRecipients.getOrNull(handoffIndex) ?: return
        val intent = buildSmsComposeIntent(recipient.phoneNumber, handoffMessage)

        if (intent.resolveActivity(context.packageManager) == null) {
            toast(context, "No SMS app available on this device.")
            return
        }

        context.startActivity(intent)
        handoffIndex += 1
        if (handoffIndex >= handoffRecipients.size) {
            stage = WrangleStage.COMPLETE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WrangleTangle Text")
                        Text(
                            text = "Write once, then hand off each message to your SMS app privately",
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
                                text = "Pick the people who should get the same message in separate 1-on-1 threads through your normal SMS app.",
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
                                    Text("WrangleTangle opens your SMS app one person at a time so you stay out of group chats.")
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = ::beginNativeHandoff,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in SMS app")
                            }
                        }
                    }
                }

                WrangleStage.HANDOFF -> {
                    val nextRecipient = handoffRecipients.getOrNull(handoffIndex)
                    val openedCount = handoffIndex.coerceAtMost(handoffRecipients.size)

                    item {
                        PrimaryCard(title = "Native SMS Handoff") {
                            Text(
                                text = "WrangleTangle stays contacts-only. It opens your normal SMS app one recipient at a time with the message prefilled.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Opened $openedCount of ${handoffRecipients.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (nextRecipient != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Next up: ${nextRecipient.name}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = nextRecipient.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Tap the button below, send the message in your SMS app, then come back here for the next person.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = ::openNextThread,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (nextRecipient == null) "Done" else "Open next thread"
                                    )
                                }
                                OutlinedButton(onClick = { stage = WrangleStage.COMPOSE }) {
                                    Text("Edit message")
                                }
                            }
                        }
                    }

                    items(handoffRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientStatusCard(
                            recipient = recipient,
                            status = when {
                                handoffRecipients.indexOf(recipient) < handoffIndex -> "Opened in SMS app"
                                recipient.phoneNumber == nextRecipient?.phoneNumber -> "Next"
                                else -> "Queued"
                            }
                        )
                    }
                }

                WrangleStage.COMPLETE -> {
                    item {
                        PrimaryCard(title = "Ready in your SMS app") {
                            Text(
                                text = "WrangleTangle opened ${handoffRecipients.size} private 1-on-1 compose screens through your normal messaging app.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = handoffMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        message = ""
                                        recipients.clear()
                                        handoffRecipients.clear()
                                        handoffMessage = ""
                                        handoffIndex = 0
                                        stage = WrangleStage.PICK_RECIPIENTS
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealAccent,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Send another")
                                }
                                OutlinedButton(
                                    onClick = {
                                        handoffIndex = 0
                                        stage = WrangleStage.HANDOFF
                                    }
                                ) {
                                    Text("Open again")
                                }
                            }
                        }
                    }

                    items(handoffRecipients, key = { it.phoneNumber }) { recipient ->
                        RecipientStatusCard(
                            recipient = recipient,
                            status = "Opened in SMS app"
                        )
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
private fun RecipientStatusCard(
    recipient: ContactRecipient,
    status: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = TealAccent
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

private fun buildSmsComposeIntent(phoneNumber: String, message: String): Intent {
    val destination = Uri.encode(phoneNumber)
    return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$destination")).apply {
        putExtra("sms_body", message)
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

private fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
