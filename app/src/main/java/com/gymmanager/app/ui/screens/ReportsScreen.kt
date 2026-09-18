package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymmanager.app.data.PeriodReview
import com.gymmanager.app.ui.MemberViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: MemberViewModel, onBack: () -> Unit) {
    val weeklyReviews by viewModel.weeklyBranchReviews.collectAsState()
    val monthlyReviews by viewModel.monthlyBranchReviews.collectAsState()
    val branches by viewModel.branches.collectAsState()
    var selectedPeriod by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadBranchReviews() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fee Review") }, navigationIcon = {
                TextButton(onClick = onBack) { Text("Back") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TabRow(selectedTabIndex = selectedPeriod) {
                Tab(selected = selectedPeriod == 0, onClick = { selectedPeriod = 0 }, text = { Text("Weekly") })
                Tab(selected = selectedPeriod == 1, onClick = { selectedPeriod = 1 }, text = { Text("Monthly") })
            }

            val reviews = if (selectedPeriod == 0) weeklyReviews else monthlyReviews
            val periodTitle = if (selectedPeriod == 0) "This Week (Mon–Today)" else "This Month"

            Text(periodTitle, style = MaterialTheme.typography.titleLarge)
            if (branches.isEmpty()) {
                Text("No branches available.")
            } else {
                branches.forEach { branch ->
                    ReviewCard(
                        title = branch.name,
                        review = reviews[branch.id] ?: PeriodReview(0.0, 0)
                    )
                }
            }

            val total = reviews.values.fold(PeriodReview(0.0, 0)) { acc, item ->
                PeriodReview(
                    feesCollected = acc.feesCollected + item.feesCollected,
                    newAdmissions = acc.newAdmissions + item.newAdmissions,
                    cash = acc.cash + item.cash,
                    gpay = acc.gpay + item.gpay,
                    paymentCount = acc.paymentCount + item.paymentCount
                )
            }
            ReviewCard(title = "All Branches", review = total)

            OutlinedButton(onClick = { viewModel.loadBranchReviews() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun ReviewCard(title: String, review: PeriodReview) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("₹${"%.0f".format(review.feesCollected)}", style = MaterialTheme.typography.headlineSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GPay ₹${"%.0f".format(review.gpay)}", style = MaterialTheme.typography.bodySmall)
                Text("Cash ₹${"%.0f".format(review.cash)}", style = MaterialTheme.typography.bodySmall)
                Text("Payments ${review.paymentCount}", style = MaterialTheme.typography.bodySmall)
            }
            Text("New admissions ${review.newAdmissions}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
