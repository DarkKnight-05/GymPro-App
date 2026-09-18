package com.gymmanager.app
import com.gymmanager.app.data.Gender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymmanager.app.auth.AuthViewModel
import com.gymmanager.app.auth.AuthViewModelFactory
import com.gymmanager.app.auth.Role
import com.gymmanager.app.auth.SettingsStore
import com.gymmanager.app.data.GymRepository
import com.gymmanager.app.data.TrainerCloudRepository
import com.gymmanager.app.ui.MemberViewModel
import com.gymmanager.app.ui.GymManagerTheme
import com.gymmanager.app.auth.ThemeMode
import com.gymmanager.app.data.CloudSyncRepository
import com.gymmanager.app.ui.ViewModelFactory
import com.gymmanager.app.ui.screens.*
import com.gymmanager.app.util.ReminderWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderWorker.schedule(applicationContext)

        val repo = GymRepository()
        val settingsStore = SettingsStore(applicationContext)

        setContent {
            var themeMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ThemeMode.SYSTEM) }
            LaunchedEffect(Unit) { settingsStore.theme.collect { themeMode = it } }
            GymManagerTheme(themeMode) { Surface(modifier = Modifier) { GymManagerApp(repo, settingsStore) } }
        }
    }
}

@Composable
fun GymManagerApp(repo: GymRepository, settingsStore: SettingsStore) {
    val navController = rememberNavController()
    val cloud = remember { CloudSyncRepository() }
    val viewModel: MemberViewModel = viewModel(factory = ViewModelFactory(repo, cloud))
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(com.gymmanager.app.data.CloudAuthRepository(), settingsStore, TrainerCloudRepository()))
    val currentRole by authViewModel.currentRole.collectAsState()
    val trainerBranchRemoteId by authViewModel.trainerBranchRemoteId.collectAsState()
    val gymId by authViewModel.gymId.collectAsState()
    val branches by viewModel.branches.collectAsState()
    LaunchedEffect(currentRole, gymId, trainerBranchRemoteId) {
        if (gymId != null) { viewModel.startCloudSync(gymId!!, if (currentRole == Role.TRAINER) trainerBranchRemoteId else null); viewModel.configureTrainerCloud(gymId!!) }
    }
    LaunchedEffect(currentRole, trainerBranchRemoteId, branches) {
        if (currentRole == Role.TRAINER) viewModel.setSelectedBranch(branches.firstOrNull { it.remoteId == trainerBranchRemoteId }?.id) else viewModel.setSelectedBranch(null)
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen { navController.navigate("login") { popUpTo("splash") { inclusive = true } } } }
        composable("login") {
            LoginScreen(authViewModel = authViewModel) { role ->
                navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
        composable("dashboard") {
            val role = currentRole ?: Role.TRAINER
            DashboardScreen(
                viewModel = viewModel, role = role,
                onMembers = { navController.navigate("list") }, onAddMember = { navController.navigate("add") },
                onOpenArchived = { navController.navigate("archived") }, onOpenReports = { navController.navigate("reports") },
                onOpenBranches = { navController.navigate("branches") }, onOpenExpiry = { navController.navigate("expiry") },
                onOpenSettings = { navController.navigate("settings") }, onOpenTrainers = { navController.navigate("trainers") },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") { popUpTo("dashboard") { inclusive = true } }
                }
            )
        }
        composable("list") {
            val role = currentRole ?: Role.TRAINER // safety fallback, shouldn't normally happen
            MemberListScreen(
                viewModel = viewModel,
                role = role,
                onAddMember = { navController.navigate("add") },
                onOpenMember = { id -> navController.navigate("detail/$id") },
                onEditMember = { id -> navController.navigate("edit/$id") },
                onOpenDashboard = { navController.popBackStack("dashboard", false) },
                onOpenArchived = { navController.navigate("archived") },
                onOpenReports = { navController.navigate("reports") },
                onOpenBranches = { navController.navigate("branches") },
                onOpenExpiry = { navController.navigate("expiry") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenTrainers = { navController.navigate("trainers") },
                onOpenBranch = { id -> navController.navigate("branch-members/$id") },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        composable(
            "branch-members/{branchId}",
            arguments = listOf(navArgument("branchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val branchId = backStackEntry.arguments?.getLong("branchId") ?: 0L
            BranchMembersScreen(
                viewModel = viewModel,
                branchId = branchId,
                onBack = { navController.popBackStack() },
                onOpenGender = { id, gender -> navController.navigate("gender-members/$id/${gender.name}") }
            )
        }
                composable(
            "gender-members/{branchId}/{gender}",
            arguments = listOf(
                navArgument("branchId") { type = NavType.LongType },
                navArgument("gender") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val branchId = backStackEntry.arguments?.getLong("branchId") ?: 0L
            val gender = runCatching { Gender.valueOf(backStackEntry.arguments?.getString("gender") ?: Gender.GENTS.name) }
                .getOrDefault(Gender.GENTS)
            GenderMembersScreen(
                viewModel = viewModel,
                branchId = branchId,
                gender = gender,
                onBack = { navController.popBackStack() },
                onOpenMember = { id -> navController.navigate("detail/$id") },
                onEditMember = { id -> navController.navigate("edit/$id") }
            )
        }
        composable("settings") {
            SettingsScreen(authViewModel = authViewModel, settingsStore = settingsStore, memberViewModel = viewModel) { navController.popBackStack() }
        }
        composable("trainers") {
            TrainersScreen(viewModel = viewModel) { navController.popBackStack() }
        }
        composable("expiry") {
            ExpiryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMember = { id -> navController.navigate("detail/$id") }
            )
        }
        composable("branches") {
            BranchesScreen(viewModel = viewModel) { navController.popBackStack() }
        }
        composable("reports") {
            if (currentRole == Role.ADMIN) {
                ReportsScreen(viewModel = viewModel) { navController.popBackStack() }
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
        composable("add") {
            AddEditMemberScreen(viewModel = viewModel, existingMember = null, role = currentRole ?: Role.TRAINER) {
                navController.popBackStack()
            }
        }
        composable(
            "detail/{memberId}",
            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
        ) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
            val role = currentRole ?: Role.TRAINER
            MemberDetailScreen(
                viewModel = viewModel,
                memberId = memberId,
                role = role,
                onEdit = { member ->
                    navController.navigate("edit/${member.id}")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "edit/{memberId}",
            arguments = listOf(navArgument("memberId") { type = NavType.LongType })
        ) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
            val existing by produceState<com.gymmanager.app.data.Member?>(initialValue = null, memberId) { value = repo.getMember(memberId) }
            AddEditMemberScreen(viewModel = viewModel, existingMember = existing, role = currentRole ?: Role.TRAINER) {
                navController.popBackStack()
            }
        }
        composable("archived") {
            ArchivedMembersScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRestored = { navController.popBackStack() }
            )
        }
    }
}
