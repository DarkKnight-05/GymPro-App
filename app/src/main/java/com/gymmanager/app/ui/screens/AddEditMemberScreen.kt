package com.gymmanager.app.ui.screens

import android.app.DatePickerDialog
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gymmanager.app.data.*
import com.gymmanager.app.auth.Role
import com.gymmanager.app.ui.MemberViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMemberScreen(viewModel: MemberViewModel, existingMember: Member? = null, role: Role = Role.ADMIN, onDone: () -> Unit) {
    val context = LocalContext.current
    val allBranches by viewModel.branches.collectAsState()
    val currentBranchFilter by viewModel.currentBranchId.collectAsState()
    val branches = if (role == Role.TRAINER && currentBranchFilter != null) allBranches.filter { it.id == currentBranchFilter } else allBranches
    val df = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var name by remember { mutableStateOf(existingMember?.name ?: "") }
    var phone by remember { mutableStateOf(existingMember?.phone ?: "+91") }
    var gender by remember { mutableStateOf(existingMember?.gender ?: Gender.GENTS) }
    var age by remember { mutableStateOf(existingMember?.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(existingMember?.weightKg?.toString() ?: "") }
    var height by remember { mutableStateOf(existingMember?.heightCm?.toString() ?: "") }
    var neck by remember { mutableStateOf(existingMember?.neckCm?.toString() ?: "") }
    var waist by remember { mutableStateOf(existingMember?.waistCm?.toString() ?: "") }
    var hip by remember { mutableStateOf(existingMember?.hipCm?.toString() ?: "") }
    var place by remember { mutableStateOf(existingMember?.place ?: "") }
    var goal by remember { mutableStateOf(existingMember?.goal ?: "") }
    var medicalIssues by remember { mutableStateOf(existingMember?.medicalIssues ?: "") }
    var photoUri by remember { mutableStateOf(existingMember?.photoUri) }
    var localPhotoPreview by remember { mutableStateOf<Bitmap?>(null) }
    var category by remember { mutableStateOf(existingMember?.membershipCategory ?: MembershipCategory.STANDARD) }
    var fee by remember { mutableStateOf(existingMember?.feeAmount?.toString() ?: "") }
    var plan by remember { mutableStateOf(existingMember?.planType ?: PlanType.MONTHLY) }
    var customDays by remember { mutableStateOf(existingMember?.customDurationDays?.toString() ?: "30") }
    var joinDate by remember { mutableLongStateOf(existingMember?.joinDateMillis ?: System.currentTimeMillis()) }
    var startDate by remember { mutableLongStateOf(existingMember?.membershipStartDateMillis ?: System.currentTimeMillis()) }
    var selectedBranchId by remember(branches) { mutableStateOf(existingMember?.branchId ?: currentBranchFilter ?: branches.firstOrNull()?.id) }
    var branchExpanded by remember { mutableStateOf(false) }
    var planExpanded by remember { mutableStateOf(false) }
    var savedError by remember { mutableStateOf<String?>(null) }

    // The edit screen is opened before produceState() in MainActivity has finished
    // loading the member. The first composition therefore receives null and the
    // remember{} form states would otherwise stay empty. Populate the form when
    // the actual member arrives.
    LaunchedEffect(existingMember?.id) {
        existingMember?.let { m ->
            name = m.name
            phone = m.phone
            gender = m.gender
            age = m.age?.toString() ?: ""
            weight = m.weightKg?.toString() ?: ""
            height = m.heightCm?.toString() ?: ""
            neck = m.neckCm?.toString() ?: ""
            waist = m.waistCm?.toString() ?: ""
            hip = m.hipCm?.toString() ?: ""
            place = m.place
            goal = m.goal
            medicalIssues = m.medicalIssues
            photoUri = m.photoUri
            localPhotoPreview = null
            category = m.membershipCategory
            fee = m.feeAmount.toString()
            plan = m.planType
            customDays = m.customDurationDays?.toString() ?: "30"
            joinDate = m.joinDateMillis
            startDate = m.membershipStartDateMillis
            selectedBranchId = m.branchId
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // Keep an app-owned copy so both the preview and later Firebase upload can always read it.
        if (uri != null) copyImageToCache(context, uri)?.let { cached ->
            photoUri = cached.toString()
            localPhotoPreview = loadBitmap(context, cached)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "member_${System.currentTimeMillis()}.jpg")
            // TakePicturePreview has no EXIF orientation. This camera returns its preview rotated right,
            // so store normalized pixels rather than relying on each image loader to guess orientation.
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(-90f) }, true)
            file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (upright !== bitmap) upright.recycle()
            val cached = Uri.fromFile(file)
            photoUri = cached.toString()
            localPhotoPreview = loadBitmap(context, cached)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) camera.launch(null) }


    fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (existingMember == null) "Add Member" else "Edit Member") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(104.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (localPhotoPreview != null) Image(bitmap = localPhotoPreview!!.asImageBitmap(), contentDescription = "Member photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (photoUri != null) AsyncImage(model = photoUri, contentDescription = "Member photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Person, null, Modifier.size(48.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Camera") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Gallery") }
            }

            OutlinedTextField(name, { name = it }, label = { Text("Full name *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone *") }, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(branchExpanded, { branchExpanded = it }) {
                OutlinedTextField(branches.find { it.id == selectedBranchId }?.name ?: "Select branch", {}, readOnly = true, label = { Text("Branch *") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(branchExpanded, { branchExpanded = false }) { branches.forEach { b -> DropdownMenuItem({ Text(b.name) }, { selectedBranchId = b.id; branchExpanded = false }) } }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(age, { age = it }, label = { Text("Age") }, modifier = Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it }, label = { Text("Weight kg") }, modifier = Modifier.weight(1f))
                OutlinedTextField(height, { height = it }, label = { Text("Height cm") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(neck, { neck = it }, label = { Text("Neck cm") }, modifier = Modifier.weight(1f))
                OutlinedTextField(waist, { waist = it }, label = { Text("Waist cm") }, modifier = Modifier.weight(1f))
                OutlinedTextField(hip, { hip = it }, label = { Text("Hip cm") }, modifier = Modifier.weight(1f))
            }

            Text("Gender", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(gender == Gender.GENTS, { gender = Gender.GENTS }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Gents") }
                SegmentedButton(gender == Gender.LADY, { gender = Gender.LADY }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Lady") }
            }
            OutlinedTextField(place, { place = it }, label = { Text("Place") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(goal, { goal = it }, label = { Text("Goal") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(medicalIssues, { medicalIssues = it }, label = { Text("Medical issues") }, modifier = Modifier.fillMaxWidth())

            DateButton("Date of Joining", joinDate, df) { pickDate(joinDate, { joinDate = it }) }
            DateButton("Membership Start", startDate, df) { pickDate(startDate, { startDate = it }) }

            Text("Membership", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(category == MembershipCategory.STANDARD, { category = MembershipCategory.STANDARD }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Standard") }
                SegmentedButton(category == MembershipCategory.PREMIUM_PT, { category = MembershipCategory.PREMIUM_PT }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("PT / Premium") }
            }

            ExposedDropdownMenuBox(planExpanded, { planExpanded = it }) {
                OutlinedTextField(planLabel(plan), {}, readOnly = true, label = { Text("Plan") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(planExpanded, { planExpanded = false }) { PlanType.entries.forEach { p -> DropdownMenuItem({ Text(planLabel(p)) }, { plan = p; planExpanded = false }) } }
            }
            if (plan == PlanType.CUSTOM) OutlinedTextField(customDays, { customDays = it.filter(Char::isDigit) }, label = { Text("Custom duration (days)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fee, { fee = it }, label = { Text("Fee amount (₹) *") }, modifier = Modifier.fillMaxWidth())
            Text("Date Added: ${df.format(Date(existingMember?.addedAtMillis ?: System.currentTimeMillis()))}", style = MaterialTheme.typography.bodySmall)

            savedError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val feeValue = fee.toDoubleOrNull()
                val branch = selectedBranchId
                if (name.isBlank() || phone.isBlank() || feeValue == null || branch == null) { savedError = "Please complete name, phone, branch and fee."; return@Button }
                val custom = if (plan == PlanType.CUSTOM) customDays.toIntOrNull()?.coerceAtLeast(1) else null
                val due = viewModel.calculateDueDate(startDate, plan, custom)
                val member = Member(id = existingMember?.id ?: 0, remoteId = existingMember?.remoteId, name = name.trim(), gender = gender, age = age.toIntOrNull(), weightKg = weight.toDoubleOrNull(), heightCm = height.toDoubleOrNull(), neckCm = neck.toDoubleOrNull(), waistCm = waist.toDoubleOrNull(), hipCm = hip.toDoubleOrNull(), medicalIssues = medicalIssues, place = place, goal = goal, phone = phone, photoUri = photoUri, branchId = branch, branchRemoteId = branches.firstOrNull { it.id == branch }?.remoteId, membershipCategory = category, joinDateMillis = joinDate, addedAtMillis = existingMember?.addedAtMillis ?: System.currentTimeMillis(), planType = plan, customDurationDays = custom, feeAmount = feeValue, membershipStartDateMillis = startDate, nextDueDateMillis = if (existingMember == null) due else existingMember.nextDueDateMillis, notes = existingMember?.notes ?: "", isArchived = existingMember?.isArchived ?: false, archivedAtMillis = existingMember?.archivedAtMillis)
                savedError = null
                if (existingMember == null) {
                    viewModel.addMember(
                        member,
                        photoUri?.let(Uri::parse),
                        context,
                        onDone = { onDone() },
                        onError = { savedError = it }
                    )
                } else {
                    val changedPhoto = photoUri
                        ?.takeIf { it != existingMember.photoUri }
                        ?.let(Uri::parse)
                    viewModel.updateMember(
                        member.copy(photoUri = existingMember.photoUri),
                        changedPhoto,
                        context,
                        onDone = { onDone() },
                        onError = { savedError = it }
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (existingMember == null) "Add Member" else "Save Changes") }
        }
    }
}

@Composable
private fun DateButton(label: String, millis: Long, df: SimpleDateFormat, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) { Text(label, style = MaterialTheme.typography.labelSmall); Text(df.format(Date(millis))) } }
}

private fun planLabel(plan: PlanType): String = when (plan) {
    PlanType.DAILY -> "Daily"
    PlanType.WEEKLY -> "Weekly"
    PlanType.HALF_MONTH -> "15 Days / Half Month"
    PlanType.MONTHLY -> "Monthly"
    PlanType.QUARTERLY -> "3 Months"
    PlanType.HALF_YEARLY -> "6 Months"
    PlanType.YEARLY -> "Yearly"
    PlanType.CUSTOM -> "Custom"
}

private fun copyImageToCache(context: android.content.Context, source: Uri): Uri? = runCatching {
    val target = File(context.cacheDir, "member_gallery_${System.currentTimeMillis()}.jpg")
    val input = context.contentResolver.openInputStream(source) ?: return null
    input.use { inputStream -> target.outputStream().use { outputStream -> inputStream.copyTo(outputStream) } }
    Uri.fromFile(target)
}.getOrNull()

private fun loadBitmap(context: android.content.Context, source: Uri): Bitmap? =
    context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
