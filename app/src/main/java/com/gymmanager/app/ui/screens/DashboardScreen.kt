package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.gymmanager.app.R
import com.gymmanager.app.auth.Role
import com.gymmanager.app.ui.MemberViewModel
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.gymmanager.app.data.PeriodReview


private val WildcardSemiItalic = FontFamily(
    Font(
        R.font.wildcard_semital,
        FontWeight.Normal
    )
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MemberViewModel, role: Role, onMembers: () -> Unit, onAddMember: () -> Unit,
    onOpenArchived: () -> Unit, onOpenReports: () -> Unit, onOpenBranches: () -> Unit,
    onOpenExpiry: () -> Unit, onOpenSettings: () -> Unit, onOpenTrainers: () -> Unit, onLogout: () -> Unit
) {
    val members by viewModel.activeMembers.collectAsState()
    val expired by viewModel.expiredMembers.collectAsState()
    val expiring by viewModel.expiringSoonMembers.collectAsState()
    val review by viewModel.todayReview.collectAsState()
    val todayBranchReviews by viewModel.todayBranchReviews.collectAsState()
    val branchId by viewModel.currentBranchId.collectAsState()
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(role, branchId, members.size) {
        if (role == Role.ADMIN) { viewModel.loadReviews(branchId); viewModel.loadBranchReviews() }
    }
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
    val newMembers = members.count { it.joinDateMillis in sevenDaysAgo..now }
    val greetingName = if (role == Role.ADMIN) "Admin" else "Trainer"
    val todayLabel = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(now))

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(28.dp))

            Text(
                "GYMPRO",
                modifier = Modifier.padding(horizontal = 24.dp),
                fontFamily = WildcardSemiItalic,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.height(28.dp))

            NavigationDrawerItem(
                label = { Text("Dashboard") },
                selected = true,
                onClick = { drawerOpen = false },
                icon = { Icon(Icons.Default.Dashboard, null) }
            )
            NavigationDrawerItem(label = { Text("Members") }, selected = false, onClick = { drawerOpen = false; onMembers() }, icon = { Icon(Icons.Default.People, null) })
            NavigationDrawerItem(label = { Text("Archived Members") }, selected = false, onClick = { drawerOpen = false; onOpenArchived() }, icon = { Icon(Icons.Default.Archive, null) })
            NavigationDrawerItem(label = { Text("Membership Expiry") }, selected = false, onClick = { drawerOpen = false; onOpenExpiry() }, icon = { Icon(Icons.Default.Event, null) })
            if (role == Role.ADMIN) {
                NavigationDrawerItem(label = { Text("Fee Review") }, selected = false, onClick = { drawerOpen = false; onOpenReports() }, icon = { Icon(Icons.Default.Payments, null) })
                NavigationDrawerItem(label = { Text("Trainers") }, selected = false, onClick = { drawerOpen = false; onOpenTrainers() }, icon = { Icon(Icons.Default.Badge, null) })
                NavigationDrawerItem(label = { Text("Branches") }, selected = false, onClick = { drawerOpen = false; onOpenBranches() }, icon = { Icon(Icons.Default.Business, null) })
            }
            NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { drawerOpen = false; onOpenSettings() }, icon = { Icon(Icons.Default.Settings, null) })
            Spacer(Modifier.weight(1f))
            NavigationDrawerItem(label = { Text("Logout") }, selected = false, onClick = { drawerOpen = false; onLogout() }, icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) })
        }
    }) {
        Scaffold(topBar = { TopAppBar(title = { Text("Dashboard") }, navigationIcon = { IconButton(onClick = { drawerOpen = true }) { Icon(Icons.Default.Menu, "Open navigation") } }) }, floatingActionButton = { FloatingActionButton(onClick = onAddMember) { Text("Add") } }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hello, $greetingName 👋", style = MaterialTheme.typography.headlineSmall)
                        Text("Keep going.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Image(
                        painter = painterResource(R.drawable.gympro_dashboard_banner),
                        contentDescription = "GymPro fitness banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Today at a glance", style = MaterialTheme.typography.titleLarge)
                    Text(todayLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardCard("Total members", members.size.toString(), Modifier.weight(1f))
                    DashboardCard("New members", newMembers.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardCard("Due members", expired.size.toString(), Modifier.weight(1f))
                    DashboardCard("Expiring soon", expiring.size.toString(), Modifier.weight(1f))
                }
                if (role == Role.ADMIN) {
                    Text("Today's fee review", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardCard("All branches", "₹${"%.0f".format(review?.feesCollected ?: 0.0)}", Modifier.weight(1f))
                        DashboardCard("Payments", "${review?.paymentCount ?: 0}", Modifier.weight(1f))
                    }
                    viewModel.branches.collectAsState().value.forEach { branch ->
                        val branchReview = todayBranchReviews[branch.id] ?: PeriodReview(0.0, 0)
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(branch.name, style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Fees ₹${"%.0f".format(branchReview.feesCollected)}")
                                    Text("Payments ${branchReview.paymentCount}")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("GPay ₹${"%.0f".format(branchReview.gpay)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Cash ₹${"%.0f".format(branchReview.cash)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Admissions ${branchReview.newAdmissions}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Button(onClick = onMembers, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.People, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage members")
                }
            }
        }
    }
}

@Composable private fun DashboardCard(label: String, value: String, modifier: Modifier) { ElevatedCard(modifier) { Column(Modifier.padding(12.dp)) { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.titleLarge) } } }
