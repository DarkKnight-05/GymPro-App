package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gymmanager.app.auth.AuthViewModel
import com.gymmanager.app.auth.Role
import com.gymmanager.app.auth.ThemeMode
import com.gymmanager.app.auth.SettingsStore
import com.gymmanager.app.ui.MemberViewModel
import com.gymmanager.app.util.MemberExcelExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(authViewModel:AuthViewModel,settingsStore:SettingsStore,memberViewModel:MemberViewModel,onBack:()->Unit){
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentTheme by settingsStore.theme.collectAsState(initial=ThemeMode.SYSTEM)
    val role by authViewModel.currentRole.collectAsState()
    val email by authViewModel.email.collectAsState()
    var showChange by remember{mutableStateOf(false)}
    var showEmail by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("Settings")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}){padding->Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Appearance",style=MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){
            SegmentedButton(currentTheme==ThemeMode.SYSTEM,{authViewModel.setTheme(ThemeMode.SYSTEM)},SegmentedButtonDefaults.itemShape(0,3)){Text("System")}
            SegmentedButton(currentTheme==ThemeMode.LIGHT,{authViewModel.setTheme(ThemeMode.LIGHT)},SegmentedButtonDefaults.itemShape(1,3)){Text("Light")}
            SegmentedButton(currentTheme==ThemeMode.DARK,{authViewModel.setTheme(ThemeMode.DARK)},SegmentedButtonDefaults.itemShape(2,3)){Text("Dark")}
        }
        if(role==Role.ADMIN){
            Text("Admin account",style=MaterialTheme.typography.titleMedium)
            Text(email?:"Admin email not available")
            if(email?.endsWith("@gymmanager.local") == true) OutlinedButton(onClick={showEmail=true}){Text("Set Recovery Email")}
            OutlinedButton(onClick={showChange=true}){Text("Change Admin Password")}
            OutlinedButton(onClick={
                MemberExcelExporter.createAndShare(
                    context = context,
                    members = memberViewModel.membersSnapshotForExport(),
                    branches = memberViewModel.branches.value
                )
            }){Text("Export Members to Excel")}
            Text("Export creates an Excel file only when you request it. Member data remains stored securely in Firebase.",style=MaterialTheme.typography.bodySmall)
        }
        Text("Cloud account",style=MaterialTheme.typography.titleMedium)
        Text("Gym data is stored in Firebase and follows the account across devices.")
    }}
    if(showEmail) SetAdminEmailDialog({cur,newEmail->authViewModel.setAdminEmail(cur,newEmail){ok->if(ok){showEmail=false;message="Recovery email updated successfully."}else message=authViewModel.error.value?:"Unable to update recovery email."}},{showEmail=false})
    if(showChange) ChangeAdminPasswordDialog({cur,newPw->authViewModel.changeAdminPassword(cur,newPw){ok->if(ok){showChange=false;message="Admin password changed successfully."}else message=authViewModel.error.value?:"Unable to change password."}},{showChange=false})
    message?.let{AlertDialog(onDismissRequest={message=null},title={Text("Account")},text={Text(it)},confirmButton={TextButton(onClick={message=null}){Text("OK")}})}
}

@Composable private fun SetAdminEmailDialog(onSave:(String,String)->Unit,onCancel:()->Unit){
    var current by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onCancel,title={Text("Set Recovery Email")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Enter a real email address for password recovery.");OutlinedTextField(email,{email=it},label={Text("Recovery email")});OutlinedTextField(current,{current=it},label={Text("Current password")},visualTransformation=PasswordVisualTransformation());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={if(email.isBlank()||!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())error="Enter a valid email address." else if(current.isBlank())error="Enter your current password." else onSave(current,email)}){Text("Save")}},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})
}

@Composable private fun ChangeAdminPasswordDialog(onSave:(String,String)->Unit,onCancel:()->Unit){
    var current by remember{mutableStateOf("")};var newPw by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var showCurrent by remember{mutableStateOf(false)};var showNew by remember{mutableStateOf(false)};var showConfirm by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onCancel,title={Text("Change Admin Password")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        PasswordField(current,{current=it},"Current password",showCurrent,{showCurrent=!showCurrent})
        PasswordField(newPw,{newPw=it},"New password",showNew,{showNew=!showNew})
        PasswordField(confirm,{confirm=it},"Confirm password",showConfirm,{showConfirm=!showConfirm})
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={if(current.isBlank())error="Enter your current password." else if(newPw.length<6)error="New password must be at least 6 characters." else if(newPw!=confirm)error="Passwords don't match." else onSave(current,newPw)}){Text("Change")}},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})
}

@Composable private fun PasswordField(value:String,onValueChange:(String)->Unit,label:String,visible:Boolean,onToggle:()->Unit){
    OutlinedTextField(value=value,onValueChange=onValueChange,label={Text(label)},singleLine=true,visualTransformation=if(visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={
        IconButton(onClick=onToggle){Icon(if(visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,contentDescription=if(visible) "Hide password" else "Show password")}
    })
}
