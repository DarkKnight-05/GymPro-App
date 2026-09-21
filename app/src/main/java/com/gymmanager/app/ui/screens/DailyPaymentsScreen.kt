package com.gymmanager.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymmanager.app.data.PaymentMethod
import com.gymmanager.app.ui.MemberViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPaymentsScreen(
    viewModel: MemberViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val branches by viewModel.branches.collectAsState()
    var selectedDate by remember {
        mutableLongStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    var selectedBranchId by remember { mutableStateOf<Long?>(null) }
    var branchExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val payments by viewModel.paymentsForDate(selectedDate).collectAsState(initial = emptyList())
    val members = viewModel.membersSnapshotForExport()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val visiblePayments = payments.filter { selectedBranchId == null || it.branchId == selectedBranchId }
    val total = visiblePayments.sumOf { it.amount }
    val cash = visiblePayments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amount }
    val gpay = visiblePayments.filter { it.method == PaymentMethod.GPAY }.sumOf { it.amount }

    fun moveDay(days: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = selectedDate }
        c.add(Calendar.DAY_OF_MONTH, days)
        c.set(Calendar.HOUR_OF_DAY, 12)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        selectedDate = c.timeInMillis
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Payments") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { moveDay(-1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                }
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(dateFormat.format(Date(selectedDate)))
                }
                IconButton(onClick = { moveDay(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
            }

            ExposedDropdownMenuBox(
                expanded = branchExpanded,
                onExpandedChange = { branchExpanded = it }
            ) {
                OutlinedTextField(
                    value = branches.firstOrNull { it.id == selectedBranchId }?.name ?: "All branches",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Branch") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = branchExpanded,
                    onDismissRequest = { branchExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All branches") },
                        onClick = { selectedBranchId = null; branchExpanded = false }
                    )
                    branches.forEach { branch ->
                        DropdownMenuItem(
                            text = { Text(branch.name) },
                            onClick = { selectedBranchId = branch.id; branchExpanded = false }
                        )
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Collected", style = MaterialTheme.typography.labelLarge)
                    Text("₹" + "%.0f".format(total), style = MaterialTheme.typography.headlineMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Payments " + visiblePayments.size)
                        Text("Cash ₹" + "%.0f".format(cash))
                        Text("GPay ₹" + "%.0f".format(gpay))
                    }
                }
            }

            if (visiblePayments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No payments recorded for this date.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visiblePayments, key = { it.remoteId ?: it.id }) { payment ->
                        val member = members.firstOrNull { it.id == payment.memberId }
                        val branchName = branches.firstOrNull { it.id == payment.branchId }?.name ?: "Branch"
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(member?.name ?: "Member", style = MaterialTheme.typography.titleMedium)
                                    Text("₹" + "%.0f".format(payment.amount), style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    payment.method.name + " • " + timeFormat.format(Date(payment.paidOnMillis)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(branchName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Covered until " + dateFormat.format(Date(payment.periodCoveredMillis)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                showDatePicker = false
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
