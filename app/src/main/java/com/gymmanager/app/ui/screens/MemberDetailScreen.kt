package com.gymmanager.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gymmanager.app.auth.Role
import com.gymmanager.app.data.*
import com.gymmanager.app.ui.MemberViewModel
import com.gymmanager.app.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    viewModel: MemberViewModel,
    memberId: Long,
    role: Role,
    onEdit: (Member) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var member by remember { mutableStateOf<Member?>(null) }
    var showPayment by remember { mutableStateOf(false) }
    var showMeasurement by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var paymentSaving by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(memberId) { member = viewModel.getMember(memberId) }

    val m = member ?: return
    val payments by viewModel.paymentsForMember(m.id).collectAsState(initial = emptyList())
    val measurements by viewModel.measurementsForMember(m.id).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { onEdit(m) }) { Text("Edit") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (m.photoUri != null) {
                        AsyncImage(
                            model = m.photoUri,
                            contentDescription = "Member photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(44.dp))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Phone: ${m.phone}")
                    Text("Plan: ${m.planType.name.replace('_', ' ')} • Fee ₹${"%.0f".format(m.feeAmount)}")
                    Text("Last Fees Date: ${df.format(Date(m.membershipStartDateMillis))}")
                    Text("Expiry / next due: ${df.format(Date(m.nextDueDateMillis))}")
                    Text("Date of joining: ${df.format(Date(m.joinDateMillis))}")
                    Text("Date added to app: ${df.format(Date(m.addedAtMillis))}")
                    Text(
                        "Body: ${m.weightKg ?: "—"} kg • ${m.heightCm ?: "—"} cm • " +
                            "Neck ${m.neckCm ?: "—"} • Waist ${m.waistCm ?: "—"} • Hip ${m.hipCm ?: "—"}"
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { WhatsAppHelper.sendFeeReminder(context, m) }) { Text("WhatsApp") }
                    OutlinedButton(onClick = { showPayment = true }) { Text("Payment") }
                }
            }

            item { OutlinedButton(onClick = { showMeasurement = true }) { Text("Add measurement") } }

            item { Text("Measurement history", style = MaterialTheme.typography.titleMedium) }
            items(measurements) { r ->
                ListItem(
                    headlineContent = { Text(df.format(Date(r.recordedAtMillis))) },
                    supportingContent = {
                        Text(
                            "Weight ${r.weightKg ?: "—"} kg • Waist ${r.waistCm ?: "—"} • " +
                                "Hip ${r.hipCm ?: "—"} • Neck ${r.neckCm ?: "—"}"
                        )
                    }
                )
            }

            item { Text("Payment history", style = MaterialTheme.typography.titleMedium) }
            items(payments) { p ->
                ListItem(
                    headlineContent = { Text("₹${"%.0f".format(p.amount)} • ${p.method.name}") },
                    supportingContent = {
                        Text("Paid ${df.format(Date(p.paidOnMillis))} • extends to ${df.format(Date(p.periodCoveredMillis))}")
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "GymPro payment receipt\n" +
                                        "Member: ${m.name}\n" +
                                        "Amount: ₹${"%.0f".format(p.amount)}\n" +
                                        "Method: ${p.method.name}\n" +
                                        "Paid: ${df.format(Date(p.paidOnMillis))}\n" +
                                        "Covered until: ${df.format(Date(p.periodCoveredMillis))}"
                                )
                            }
                            context.startActivity(Intent.createChooser(share, "Share receipt"))
                        }) { Text("Share") }
                    }
                )
            }

            item {
                if (m.isArchived) {
                    TextButton(onClick = { showRestore = true }) { Text("Restore member") }
                } else {
                    TextButton(onClick = { showArchive = true }) { Text("Archive member") }
                }
            }
        }
    }

    if (showMeasurement) {
        var weight by remember { mutableStateOf(m.weightKg?.toString() ?: "") }
        var neck by remember { mutableStateOf(m.neckCm?.toString() ?: "") }
        var waist by remember { mutableStateOf(m.waistCm?.toString() ?: "") }
        var hip by remember { mutableStateOf(m.hipCm?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showMeasurement = false },
            title = { Text("New measurement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(weight, { weight = it }, label = { Text("Weight kg") })
                    OutlinedTextField(neck, { neck = it }, label = { Text("Neck cm") })
                    OutlinedTextField(waist, { waist = it }, label = { Text("Waist cm") })
                    OutlinedTextField(hip, { hip = it }, label = { Text("Hip cm") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    m.remoteId?.let { remote ->
                        viewModel.addMeasurement(
                            MeasurementRecord(
                                memberId = m.id,
                                memberRemoteId = remote,
                                branchRemoteId = m.branchRemoteId,
                                weightKg = weight.toDoubleOrNull(),
                                heightCm = m.heightCm,
                                neckCm = neck.toDoubleOrNull(),
                                waistCm = waist.toDoubleOrNull(),
                                hipCm = hip.toDoubleOrNull()
                            )
                        )
                    }
                    showMeasurement = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showMeasurement = false }) { Text("Cancel") } }
        )
    }

    if (showPayment) {
        var amount by remember { mutableStateOf(m.feeAmount.toString()) }
        var method by remember { mutableStateOf(PaymentMethod.CASH) }

        AlertDialog(
            onDismissRequest = { showPayment = false },
            title = { Text("Record payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount") })
                    Row {
                        FilterChip(
                            selected = method == PaymentMethod.CASH,
                            onClick = { method = PaymentMethod.CASH },
                            label = { Text("Cash") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = method == PaymentMethod.GPAY,
                            onClick = { method = PaymentMethod.GPAY },
                            label = { Text("GPay") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !paymentSaving,
                    onClick = {
                        val value = amount.toDoubleOrNull()
                        if (value != null && value > 0.0 && !paymentSaving) {
                            paymentSaving = true
                            viewModel.recordPayment(
                                member = m,
                                amount = value,
                                method = method,
                                onDone = {
                                    paymentSaving = false
                                    showPayment = false
                                },
                                onError = { paymentSaving = false }
                            )
                        }
                    }
                ) { Text(if (paymentSaving) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(onClick = { showPayment = false }) { Text("Cancel") } }
        )
    }

    if (showArchive) {
        AlertDialog(
            onDismissRequest = { showArchive = false },
            title = { Text("Archive member?") },
            text = { Text("History will be kept and the member can be restored later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveMember(m.id)
                    showArchive = false
                    onBack()
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { showArchive = false }) { Text("Cancel") } }
        )
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("Restore member?") },
            text = { Text("This member will return to the active member list. Their history will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreMember(m.id)
                    showRestore = false
                    onBack()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showRestore = false }) { Text("Cancel") } }
        )
    }
}

