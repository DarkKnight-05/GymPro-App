package com.gymmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gymmanager.app.R
import com.gymmanager.app.ui.WildcardSemiItalic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gymmanager.app.auth.AuthViewModel
import com.gymmanager.app.auth.Role

@Composable
fun LoginScreen(authViewModel: AuthViewModel, onLoggedIn: (Role) -> Unit) {
    var mode by remember { mutableStateOf("choose") }
    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize(), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.bodytech_logo),
                contentDescription = "BodyTech",
                modifier = Modifier.size(width = 220.dp, height = 150.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                "GYMPRO",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = WildcardSemiItalic
            )
            Text("Smarter gym management", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))
            when(mode) {
                "choose" -> {
                    Button(onClick={mode="adminLogin"},Modifier.fillMaxWidth()){Text("Admin Login")}
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick={mode="setup"},Modifier.fillMaxWidth()){Text("Set Up GymPro")}
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick={mode="trainer"},Modifier.fillMaxWidth()){Text("Trainer Login")}
                }
                "setup" -> AdminSetup(authViewModel,{onLoggedIn(Role.ADMIN)},{mode="choose"})
                "adminLogin" -> AdminLogin(authViewModel,{onLoggedIn(Role.ADMIN)},{mode="choose"})
                else -> TrainerLogin(authViewModel,{onLoggedIn(Role.TRAINER)},{mode="choose"})
            }
        }
    }
}

@Composable
private fun AdminSetup(vm:AuthViewModel,onLoggedIn:()->Unit,onBack:()->Unit){
    var gymName by remember{mutableStateOf("")}; var email by remember{mutableStateOf("")}; var password by remember{mutableStateOf("")}; var confirm by remember{mutableStateOf("")}
    var passwordVisible by remember{mutableStateOf(false)}; var confirmVisible by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}; var loading by remember{mutableStateOf(false)}
    val focus=LocalFocusManager.current
    Text("Set up the admin account for this gym."); Spacer(Modifier.height(10.dp))
    OutlinedTextField(gymName,{gymName=it},label={Text("Gym name")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)}))
    OutlinedTextField(email,{email=it},label={Text("Admin email")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)}))
    PasswordField(password,{password=it},"Password",passwordVisible,{passwordVisible=!passwordVisible},ImeAction.Next)
    PasswordField(confirm,{confirm=it},"Confirm password",confirmVisible,{confirmVisible=!confirmVisible},ImeAction.Done,onDone={
        if(gymName.isBlank()||email.isBlank()||password.length<6) error="Complete all fields; password must be at least 6 characters." else if(password!=confirm) error="Passwords don't match." else { loading=true; vm.createAdmin(gymName,email,password){ok->loading=false;if(ok)onLoggedIn()else error=vm.error.value?:"Could not create account."} }
    })
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}; Spacer(Modifier.height(12.dp))
    Button(enabled=!loading,onClick={if(gymName.isBlank()||email.isBlank()||password.length<6)error="Complete all fields; password must be at least 6 characters." else if(password!=confirm)error="Passwords don't match." else{loading=true;vm.createAdmin(gymName,email,password){ok->loading=false;if(ok)onLoggedIn()else error=vm.error.value?:"Could not create account."}}},modifier=Modifier.fillMaxWidth()){Text(if(loading)"Setting up…" else "Set Up GymPro")}
    TextButton(onClick=onBack){Text("Back")}
}

@Composable private fun AdminLogin(vm:AuthViewModel,onLoggedIn:()->Unit,onBack:()->Unit)=LoginFields("Admin Login",vm,onBack,onLoggedIn,false){email,pwd,done->vm.loginAsAdmin(email,pwd,done)}
@Composable private fun TrainerLogin(vm:AuthViewModel,onLoggedIn:()->Unit,onBack:()->Unit)=LoginFields("Trainer Login",vm,onBack,onLoggedIn,true){username,pwd,done->vm.loginAsTrainer(username,pwd,done)}

@Composable
private fun LoginFields(title:String,vm:AuthViewModel,onBack:()->Unit,onLoggedIn:()->Unit,isTrainer:Boolean,login:(String,String,(Boolean)->Unit)->Unit){
    var identity by remember{mutableStateOf("")}; var password by remember{mutableStateOf("")}; var visible by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}; var loading by remember{mutableStateOf(false)}
    val focus=LocalFocusManager.current
    fun submit(){if(loading)return;loading=true;error=null;login(identity,password){ok->loading=false;if(!ok)error=vm.error.value?:"Incorrect ${if(isTrainer)"username" else "email"} or password."}}
    Text(title,style=MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp))
    OutlinedTextField(identity,{identity=it},label={Text(if(isTrainer)"Username" else "Email")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)}))
    PasswordField(password,{password=it},"Password",visible,{visible=!visible},ImeAction.Done,::submit)
    if(!isTrainer){TextButton(onClick={error=null; if(identity.isBlank()) error="Enter your admin email first." else vm.sendAdminPasswordReset(identity){ok->if(ok)error="Password reset email sent. Check your inbox." else error=vm.error.value?:"Unable to send reset email."}}){Text("Forgot password?")}}
    error?.let{Text(it,color=if(it.contains("sent",true))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}
    Spacer(Modifier.height(12.dp)); Button(enabled=!loading,onClick=::submit,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Signing in…" else "Log In")}; TextButton(onClick=onBack){Text("Back")}
    LaunchedEffect(vm.currentRole.value){if(vm.currentRole.value!=null)onLoggedIn()}
}

@Composable
private fun PasswordField(value:String,onValueChange:(String)->Unit,label:String,visible:Boolean,onVisibilityChange:()->Unit,imeAction:ImeAction,onDone:(()->Unit)?=null){
    val focus=LocalFocusManager.current
    OutlinedTextField(value=value,onValueChange=onValueChange,label={Text(label)},visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick=onVisibilityChange){Icon(if(visible)Icons.Default.VisibilityOff else Icons.Default.Visibility,if(visible)"Hide password" else "Show password")}},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=imeAction),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)},onDone={onDone?.invoke()}) )
}
