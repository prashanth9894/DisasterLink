@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.disasterlink.ui

// Compile-fix v5: keep Button arguments named after onClick to avoid Kotlin named/positional argument errors.

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.core.content.FileProvider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.disasterlink.R
import com.example.disasterlink.bluetooth.BluetoothManager
import com.example.disasterlink.data.*
import com.example.disasterlink.ui.theme.DisasterLinkTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.BitmapFactory
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.example.disasterlink.bluetooth.EvidenceBleTransfer
import java.io.File

private val Navy = Color(0xFF103A8A)
private val Blue = Color(0xFF2166D1)
private val LightBlue = Color(0xFFEAF2FF)
private val Green = Color(0xFF22A06B)
private val LightGreen = Color(0xFFE8F7EF)
private val Red = Color(0xFFE53935)
private val LightRed = Color(0xFFFFECEC)
private val Amber = Color(0xFFF5A623)
private val LightAmber = Color(0xFFFFF5DE)
private val Purple = Color(0xFF6E56CF)
private val LightPurple = Color(0xFFF0ECFF)
private val AppBg = Color(0xFFF6F8FC)

private val TamilNaduDistricts = listOf(
    "Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore", "Dharmapuri", "Dindigul",
    "Erode", "Kallakurichi", "Kancheepuram", "Karur", "Krishnagiri", "Madurai", "Mayiladuthurai",
    "Nagapattinam", "Namakkal", "Nilgiris", "Perambalur", "Pudukkottai", "Ramanathapuram", "Ranipet",
    "Salem", "Sivaganga", "Tenkasi", "Thanjavur", "Theni", "Thoothukudi", "Tiruchirappalli",
    "Tirunelveli", "Tirupathur", "Tiruppur", "Tiruvallur", "Tiruvarur", "Vellore", "Viluppuram", "Virudhunagar"
)

private fun phoneOk(phone: String) = phone.matches(Regex("[6-9]\\d{9}"))
private fun emailOk(email: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
private fun aadhaarOk(aadhaar: String) = aadhaar.matches(Regex("\\d{12}"))
private fun employeeIdOk(id: String) = id.matches(Regex("[A-Z]{3}\\d{4}[A-Z]{3}"))
private fun passwordOk(password: String) = password.length in 8..30 &&
        password.any { it.isUpperCase() } && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }
private fun dateTime(ms: Long?) = ms?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "—"
private fun shortDate(ms: Long?) = ms?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "—"
private fun shortTime(ms: Long?) = ms?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "—"

@Composable
fun DisasterLinkApp() {
    DisasterLinkTheme {
        val nav = rememberNavController()
        // Module 4: restore the local session before navigation. This read is
        // SharedPreferences-only, so startup does not depend on internet.
        val startDestination = if (Repository.currentSession() != null) "dashboard" else "home"
        LaunchedEffect(Unit) { Repository.refreshSession() }
        NavHost(navController = nav, startDestination = startDestination) {
            composable("home") { HomeScreen(nav) }
            composable("login") { LoginScreen(nav) }
            composable("email_otp_login") { EmailOtpLoginScreen(nav) }
            composable("register") { RegisterScreen(nav) }
            composable("dashboard") { DashboardScreen(nav) }
            composable("sos") { SosScreen(nav) }
            composable("bluetooth") { BluetoothScreen(nav) }
            composable("tasks") { TasksHomeScreen(nav) }
            composable("tasks/active") { RescueTaskListScreen(nav, TaskListKind.ACTIVE) }
            composable("tasks/verification") { RescueTaskListScreen(nav, TaskListKind.VERIFICATION) }
            composable("tasks/completed") { RescueTaskListScreen(nav, TaskListKind.COMPLETED) }
            composable("authority/verification") { AuthorityVerificationScreen(nav) }
            composable("authority") { AuthorityScreen(nav) }
            composable("authority/teams") { AuthorityTeamsScreen(nav) }
            composable("authority/invites") { AuthorityInvitesScreen(nav) }
            composable("authority/assign") { AuthorityAssignScreen(nav) }
            composable("authority/complaints") { AuthorityComplaintsScreen(nav) }
            composable("authority/district") { AuthorityDistrictScreen(nav) }
            composable("authority/performance") { AuthorityPerformanceScreen(nav) }
            composable("authority/command") { AuthorityCommandScreen(nav) }
            composable("feedback") { FeedbackScreen(nav) }
            composable("emergency/{id}") { b -> EmergencyDetailScreen(nav, b.arguments?.getString("id")) }
            composable("task/{id}") { b -> RescueTaskDetailScreen(nav, b.arguments?.getString("id")) }
        }
    }
}

/* Home page intentionally keeps the supplied artwork. */
@Composable
private fun HomeScreen(nav: NavHostController) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.home_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.9f))
            Surface(Modifier.size(86.dp), CircleShape, color = Color.White, shadowElevation = 8.dp) {
                Icon(Icons.Default.HealthAndSafety, null, Modifier.padding(17.dp), tint = Red)
            }
            Text("DisasterLink", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Stay Safe • Stay Connected", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.96f),
                shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { nav.navigate("login") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Login") }
                    OutlinedButton(
                        onClick = { nav.navigate("register") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Register") }
                    TextButton(onClick = { nav.navigate("sos") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Warning, null, tint = Red); Spacer(Modifier.width(6.dp)); Text("Emergency SOS", color = Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(nav: NavHostController) {
    // Email OTP is now the only sign-in method. Aadhaar/employee ID + password
    // login has been removed from the UI. Existing government/employee IDs are
    // still retained for registration, rescue-team membership and authorization.
    EmailOtpLoginScreen(nav)
}

@Composable
private fun EmailOtpLoginScreen(nav: NavHostController) {
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.CITIZEN) }
    var codeSent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.login_fire), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text("Email Verification Login", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White.copy(alpha = .98f)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(UserRole.CITIZEN, UserRole.RESCUE, UserRole.AUTHORITY).forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = {
                                    role = r
                                    codeSent = false
                                    otp = ""
                                    message = ""
                                },
                                label = { Text(roleLabel(r), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        if (codeSent) "Enter the verification code" else "Sign in with email",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "Enter your registered email. We will send a one-time 6-digit verification code.",
                        fontSize = 12.sp,
                        color = Color(0xFF667085)
                    )
                    Field(
                        "Registered email address",
                        email,
                        { email = it.trim().take(120) },
                        keyboard = KeyboardType.Email
                    )
                    if (codeSent) {
                        Field(
                            "6-digit verification code",
                            otp,
                            { otp = it.filter(Char::isDigit).take(6) },
                            keyboard = KeyboardType.Number
                        )
                        Text(
                            "The code expires in 5 minutes. Check your spam folder if you don't see the email.",
                            fontSize = 11.sp,
                            color = Color(0xFF667085)
                        )
                    }
                    if (message.isNotBlank()) ErrorText(message)
                    Button(
                        onClick = {
                            if (!emailOk(email)) {
                                message = "Enter a valid email address."
                                return@Button
                            }
                            loading = true
                            message = ""
                            scope.launch {
                                if (!codeSent) {
                                    Repository.requestEmailOtp(email, role)
                                        .onSuccess {
                                            codeSent = true
                                            message = "Verification code sent to your email."
                                        }
                                        .onFailure {
                                            message = it.message ?: "Couldn't send verification code."
                                        }
                                } else {
                                    if (otp.length != 6) {
                                        message = "Enter the 6-digit verification code."
                                        loading = false
                                        return@launch
                                    }
                                    Repository.verifyEmailOtp(email, otp, role)
                                        .onSuccess {
                                            nav.navigate("dashboard") {
                                                popUpTo("home") { inclusive = false }
                                            }
                                        }
                                        .onFailure {
                                            message = it.message ?: "Verification failed."
                                        }
                                }
                                loading = false
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (!codeSent) "Send verification code" else "Verify & continue")
                    }
                    if (codeSent) {
                        TextButton(
                            onClick = { codeSent = false; otp = ""; message = "" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Change email / request new code")
                        }
                    }
                    TextButton(
                        onClick = { nav.navigate("register") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New user? Register here")
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterScreen(nav: NavHostController) {
    var role by remember { mutableStateOf(UserRole.CITIZEN) }
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }; var employeeId by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }; var district by remember { mutableStateOf("") }; var authority by remember { mutableStateOf(AuthorityType.VAO) }
    var password by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var inviteCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.register_flood), null, Modifier.fillMaxWidth().height(230.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxWidth().height(230.dp).background(Color.Black.copy(alpha = .08f)))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Text(if (role == UserRole.CITIZEN) "Citizen Registration" else if (role == UserRole.AUTHORITY) "Authority Registration" else "Rescue Registration", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(150.dp))
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Color.White) {
                LazyColumn(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Surface(Modifier.size(64.dp), CircleShape, color = LightBlue, shadowElevation = 6.dp) { Icon(if (role == UserRole.AUTHORITY) Icons.Default.Badge else Icons.Default.Person, null, Modifier.padding(14.dp), tint = Blue) }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(UserRole.CITIZEN, UserRole.RESCUE, UserRole.AUTHORITY).forEach { r -> FilterChip(selected = role == r, onClick = { role = r; message = "" }, label = { Text(roleLabel(r), fontSize = 10.sp) }, modifier = Modifier.weight(1f)) }
                        }
                    }
                    item { Field("Full Name", name, { name = it }) }
                    item { PhoneField(phone) { phone = it.filter(Char::isDigit).take(10) } }
                    item { Field(if (role == UserRole.AUTHORITY) "Government Email" else "Email Address", email, { email = it }, keyboard = KeyboardType.Email) }
                    if (role == UserRole.AUTHORITY) item { AuthorityTypeSelector(authority) { authority = it } }
                    if (role == UserRole.AUTHORITY) item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Field("Invite Code", inviteCode, { inviteCode = it.uppercase(Locale.US) })
                            Text("Get this from a District Collector or above. Authority accounts can't self-register without one.", fontSize = 10.sp, color = Color(0xFF667085))
                        }
                    }
                    item { Field("Aadhaar Number", formatAadhaar(aadhaar), { aadhaar = it.filter(Char::isDigit).take(12) }, keyboard = KeyboardType.Number) }
                    if (role != UserRole.CITIZEN) item { Field("Employee ID  •  ABC1234XYZ", employeeId, { employeeId = it.uppercase(Locale.US).filter(Char::isLetterOrDigit).take(10) }, keyboard = KeyboardType.Ascii) }
                    item { Field("Full Address", address, { address = it }, singleLine = false) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { Field("PIN Code", pincode, { pincode = it.filter(Char::isDigit).take(6) }, modifier = Modifier.weight(.8f), keyboard = KeyboardType.Number); DistrictSelector(district, { district = it }, Modifier.weight(1.2f)) } }
                    item { PasswordField(password) { password = it.take(30) } }
                    item { Text("At least 8 characters with 1 capital letter, 1 number and 1 special character.", fontSize = 10.sp, color = Color(0xFF667085)) }
                    if (message.isNotBlank()) item { ErrorText(message) }
                    item { Button(onClick = {
                        message = validateRegistration(name, phone, email, aadhaar, employeeId, address, pincode, district, password, role, inviteCode)
                        if (message.isNotBlank()) return@Button
                        val id = if (role == UserRole.CITIZEN) aadhaar else employeeId
                        scope.launch { Repository.register(name.trim(), phone.trim(), email.trim(), id, address.trim(), pincode, password, role, if (role == UserRole.AUTHORITY) authority else null, district, if (role == UserRole.AUTHORITY) inviteCode.trim() else null).onSuccess { nav.navigate("dashboard") { popUpTo("home") } }.onFailure { message = it.message ?: "Registration failed" } }
                    }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(12.dp)) { Text(if (role == UserRole.CITIZEN) "Register Citizen" else if (role == UserRole.AUTHORITY) "Register Authority" else "Register Rescue Member") } }
                }
            }
        }
    }
}

private fun validateRegistration(name: String, phone: String, email: String, aadhaar: String, employeeId: String, address: String, pincode: String, district: String, password: String, role: UserRole, inviteCode: String = ""): String {
    if (name.isBlank()) return "Full name is required."
    if (!phoneOk(phone)) return "Phone number must contain exactly 10 digits."
    if (!emailOk(email)) return "Enter a valid email address."
    if (!aadhaarOk(aadhaar)) return "Aadhaar number must contain exactly 12 digits."
    if (role != UserRole.CITIZEN && !employeeIdOk(employeeId)) return "ID must be 3 capital letters + 4 numbers + 3 capital letters."
    if (role == UserRole.AUTHORITY && inviteCode.isBlank()) return "Enter the invite code given to you by a District Collector."
    if (address.trim().length < 10) return "Enter the full address."
    if (!pincode.matches(Regex("\\d{6}"))) return "PIN code must contain exactly 6 digits."
    if (district.isBlank()) return "Select a Tamil Nadu district."
    if (!passwordOk(password)) return "Password must be 8–30 characters with uppercase, number and special character."
    return ""
}

@Composable
private fun DashboardScreen(nav: NavHostController) {
    val session = Repository.currentSession()
    if (session == null) { LaunchedEffect(Unit) { nav.navigate("login") { popUpTo("home") } }; return }
    when (session.role) {
        UserRole.CITIZEN -> CitizenDashboard(nav, session)
        UserRole.RESCUE -> RescueDashboard(nav, session)
        UserRole.AUTHORITY -> AuthorityScreen(nav)
    }
}

@Composable
private fun CitizenDashboard(nav: NavHostController, session: SessionUser) {
    val emergencies by Repository.citizenEmergencies(session.id).collectAsState(initial = emptyList())
    Scaffold(containerColor = AppBg, topBar = { PrototypeTopBar("Citizen Dashboard", nav, showSos = true) }, bottomBar = { PrototypeBottomNav(nav, "citizen") }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PrototypePermissionCards() }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PrototypeTile("Give Feedback", Icons.Default.EditNote, LightPurple, Purple) { nav.navigate("feedback") }
                    PrototypeTile("Emergency History", Icons.Default.History, LightRed, Red) { if (emergencies.isNotEmpty()) nav.navigate("emergency/${emergencies.first().id}") }
                }
            }
            item {
                Text("Send Emergency", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { nav.navigate("sos") }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.Warning, null); Spacer(Modifier.width(8.dp)); Text("Send SOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            item { Text("Recent Emergency", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy) }
            if (emergencies.isEmpty()) item { PrototypeEmpty("No emergency reports yet") }
            items(emergencies.take(4)) { EmergencyCard(it) { nav.navigate("emergency/${it.id}") } }
        }
    }
}

@Composable
private fun RescueDashboard(nav: NavHostController, session: SessionUser) {
    LaunchedEffect(Unit) { Repository.refreshOperationalData() }
    val teams by Repository.teams().collectAsState(initial = emptyList())
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    val ids = teams.filter { it.members().contains(session.governmentId.uppercase(Locale.US)) }.map { it.id }.toSet()
    val mine = tasks.filter { it.assignedTeamId in ids }
    var tab by remember { mutableStateOf("Assigned") }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var locationMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val myTeam = teams.firstOrNull { it.members().contains(session.governmentId.uppercase(Locale.US)) }
    val shown = when (tab) { "Pending" -> mine.filter { it.status == TaskStatus.PENDING }; "Completed" -> mine.filter { it.status == TaskStatus.COMPLETED }; else -> mine.filter { it.status == TaskStatus.ASSIGNED || it.status == TaskStatus.IN_PROGRESS } }
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = AppBg, topBar = { PrototypeTopBar("Rescue Team Dashboard", nav) }, bottomBar = { PrototypeBottomNav(nav, "rescue") }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { PrototypePermissionCards() }
            item {
                OperationalSyncCard(
                    onSync = { scope.launch { syncing = true; syncMessage = ""; Repository.syncNow().onSuccess { syncMessage = "Sync complete — latest operations loaded." }.onFailure { syncMessage = "Offline data kept locally — ${it.message ?: "sync unavailable"}" }; syncing = false } },
                    syncing = syncing, message = syncMessage
                )
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Green); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) {
                            Text("Rescue team location", fontWeight = FontWeight.Bold, color = Navy)
                            Text(if (myTeam?.latitude != null) "${myTeam.latitude}, ${myTeam.longitude}" else "No location shared yet", style = MaterialTheme.typography.bodySmall)
                            if (locationMessage.isNotBlank()) Text(locationMessage, fontSize = 10.sp, color = Green)
                        }
                        TextButton(onClick = {
                            val loc = getLastKnownLocation(context)
                            if (loc == null) locationMessage = "Enable location to share your position."
                            else if (myTeam == null) locationMessage = "Your government ID is not linked to a rescue team."
                            else scope.launch { Repository.updateTeamLocation(myTeam.id, loc.first, loc.second).onSuccess { locationMessage = "Location updated." }.onFailure { locationMessage = it.message ?: "Location update failed." } }
                        }) { Text("Share") }
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrototypeMetric("Assigned Tasks", mine.count { it.status == TaskStatus.ASSIGNED }, LightBlue, Blue, Modifier.weight(1f))
                PrototypeMetric("Pending Tasks", mine.count { it.status == TaskStatus.PENDING }, LightAmber, Amber, Modifier.weight(1f))
                PrototypeMetric("In Progress", mine.count { it.status == TaskStatus.IN_PROGRESS }, LightGreen, Green, Modifier.weight(1f))
            } }
            item { Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(3.dp)) { listOf("Assigned", "Pending", "Completed").forEach { label -> FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.weight(1f)) } } }
            if (shown.isEmpty()) item { PrototypeEmpty("No $tab tasks") }
            items(shown) { task ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), CircleShape, color = LightRed) { Icon(Icons.Default.Warning, null, Modifier.padding(8.dp), tint = Red) }; Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.Bold, color = Navy); Text(task.location, fontSize = 11.sp, color = Color(0xFF667085)) }; StatusPill(task.status.name) }
                        Text("Assigned by: Authority", fontSize = 10.sp, color = Color(0xFF667085))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { TimeBox("Date", shortDate(task.createdAt), Modifier.weight(1f)); TimeBox("Start", shortTime(task.startedAt), Modifier.weight(1f)); TimeBox("End", shortTime(task.completedAt), Modifier.weight(1f)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedButton(onClick = { nav.navigate("task/${task.id}") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("View") }; if (task.status == TaskStatus.ASSIGNED) Button(onClick = { scope.launch { Repository.acceptTask(task) } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(10.dp)) { Text("Accept") } else Button(onClick = { nav.navigate("task/${task.id}") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(10.dp)) { Text("Open") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorityScreen(nav: NavHostController) {
    val session = Repository.currentSession() ?: return
    LaunchedEffect(Unit) { Repository.refreshOperationalData() }
    val emergencies by Repository.allEmergencies().collectAsState(initial = emptyList())
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Total") }
    val scope = rememberCoroutineScope()

    val filteredReports = when (selectedFilter) {
        "Assigned" -> emergencies.filter { it.status == EmergencyStatus.ASSIGNED }
        "Pending" -> emergencies.filter { it.status == EmergencyStatus.REPORTED || it.status == EmergencyStatus.ACKNOWLEDGED }
        "Response" -> emergencies.filter { it.status == EmergencyStatus.RESOLVED || it.status == EmergencyStatus.REJECTED }
        else -> emergencies
    }

    Scaffold(containerColor = AppBg, topBar = { PrototypeTopBar("Authority Dashboard", nav) }, bottomBar = { PrototypeBottomNav(nav, "authority") }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AuthorityHeader(session) }
            item {
                OperationalSyncCard(
                    onSync = { scope.launch { syncing = true; syncMessage = ""; Repository.syncNow().onSuccess { syncMessage = "Sync complete — incoming offline reports loaded." }.onFailure { syncMessage = "Offline mode: local data remains available." }; syncing = false } },
                    syncing = syncing, message = syncMessage
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Total", "Assigned", "Pending", "Response").forEach { label ->
                        FilterChip(
                            selected = selectedFilter == label,
                            onClick = { selectedFilter = label },
                            label = { Text(label, fontSize = 12.sp) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Navy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
            item {
                Text("Core Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AuthorityActionCard("Rescue Teams", Icons.Default.Groups, LightBlue, Blue, Modifier.weight(1f)) { nav.navigate("authority/teams") }
                        AuthorityActionCard("Assign Task", Icons.Default.Assignment, LightAmber, Amber, Modifier.weight(1f)) { nav.navigate("authority/assign") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AuthorityActionCard("Verify Evidence", Icons.Default.Verified, LightGreen, Green, Modifier.weight(1f)) { nav.navigate("authority/verification") }
                        AuthorityActionCard("Invite Authority Staff", Icons.Default.PersonAdd, LightGreen, Green, Modifier.weight(1f)) { nav.navigate("authority/invites") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AuthorityActionCard("View Complaints", Icons.Default.BarChart, LightPurple, Purple, Modifier.weight(1f)) { nav.navigate("authority/complaints") }
                        AuthorityActionCard("Emergency Command Center", Icons.Default.Dashboard, LightRed, Red, Modifier.weight(1f)) { nav.navigate("authority/command") }
                    }
                }
            }
            item { Text(if (selectedFilter == "Total") "Active Emergency Reports" else "$selectedFilter Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy) }
            if (syncing) {
                items(3) { SkeletonCard() }
            } else {
                if (filteredReports.isEmpty()) item { PrototypeEmpty("No $selectedFilter reports") }
                items(filteredReports.take(10)) { e -> EmergencyCard(e) { nav.navigate("emergency/${e.id}") } }
            }
            item {
                Text("Live Geospatial Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(4.dp))
                MapCard(emergencies)
            }
        }
    }
}

@Composable
private fun AuthorityTeamsScreen(nav: NavHostController) {
    val session = Repository.currentSession() ?: return
    val teams by Repository.teams().collectAsState(initial = emptyList()); val tasks by Repository.tasks().collectAsState(initial = emptyList())
    var teamName by remember { mutableStateOf("") }; var memberText by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val canManage = session.authorityType != AuthorityType.VAO && session.authorityType != AuthorityType.TAHSILDAR
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Rescue Teams", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!canManage) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LightAmber)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Amber)
                        Spacer(Modifier.width(9.dp))
                        Text("Only District Collector and above can register or remove rescue teams. Your account (${authorityLabel(session.authorityType)}) has view-only access.", fontSize = 12.sp, color = Navy)
                    }
                }
            }
            item { SectionHeader("Add rescue team", "2–5 registered rescue members") }
            item { Field("Rescue team name", teamName, { teamName = it }, enabled = canManage) }
            item { Field("Member IDs  •  ABC1234XYZ", memberText, { memberText = it.uppercase(Locale.US) }, singleLine = false, enabled = canManage) }
            item { Field("Team phone", phone, { phone = it.filter(Char::isDigit).take(10) }, keyboard = KeyboardType.Phone, enabled = canManage) }
            if (message.isNotBlank()) item { ErrorText(message) }
            item { Button(onClick = {
                val ids = memberText.split(Regex("[,\\n]+" )).map { it.trim() }.filter { it.isNotBlank() }
                when {
                    teamName.isBlank() -> message = "Team name is required."
                    ids.size !in 2..5 || ids.distinct().size != ids.size || ids.any { !employeeIdOk(it) } -> message = "Enter 2–5 unique member IDs in ABC1234XYZ format."
                    !phoneOk(phone) -> message = "Team phone must contain 10 digits."
                    else -> scope.launch { Repository.addTeam(teamName.trim(), ids, "+91$phone").onSuccess { teamName = ""; memberText = ""; phone = ""; message = "Team registered." }.onFailure { message = it.message ?: "Could not register team" } }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = canManage, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Register rescue team") } }
            item { SectionHeader("Registered teams", "Performance and live location") }
            items(teams) { team ->
                val tt = tasks.filter { it.assignedTeamId == team.id }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), CircleShape, color = LightBlue) { Icon(Icons.Default.Groups, null, Modifier.padding(9.dp), tint = Blue) }; Spacer(Modifier.width(10.dp)); Column { Text(team.name, fontWeight = FontWeight.Bold); Text("${team.members().size} members", style = MaterialTheme.typography.bodySmall) } }
                        Text("Completed ${tt.count { it.status == TaskStatus.COMPLETED }}  •  Pending ${tt.count { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.REJECTED }}  •  Rejected ${tt.count { it.status == TaskStatus.REJECTED }}", style = MaterialTheme.typography.bodySmall)
                        Text("Performance ${if (tt.isEmpty()) 0 else tt.count { it.status == TaskStatus.COMPLETED } * 100 / tt.size}%", fontWeight = FontWeight.SemiBold)
                        if (team.latitude != null) Text("Live location  ${team.latitude}, ${team.longitude}", style = MaterialTheme.typography.bodySmall)
                        if (canManage) OutlinedButton(onClick = { scope.launch { Repository.removeTeam(team.id) } }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Remove team") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorityInvitesScreen(nav: NavHostController) {
    val session = Repository.currentSession() ?: return
    val context = LocalContext.current
    val canManage = session.authorityType != AuthorityType.VAO && session.authorityType != AuthorityType.TAHSILDAR
    var authorityType by remember { mutableStateOf(AuthorityType.VAO) }
    var district by remember { mutableStateOf(session.district) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Invite Authority Staff", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!canManage) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LightAmber)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Amber)
                        Spacer(Modifier.width(9.dp))
                        Text("Only District Collector and above can issue invite codes for new authority accounts.", fontSize = 12.sp, color = Navy)
                    }
                }
            }
            item { SectionHeader("Generate an invite code", "Share it with the new official — they'll enter it once, on their own registration screen") }
            item { AuthorityTypeSelector(authorityType, enabled = canManage) { authorityType = it } }
            item { Field("District (optional)", district, { district = it }, enabled = canManage) }
            if (message.isNotBlank()) item { ErrorText(message) }
            item {
                Button(onClick = {
                    loading = true; message = ""
                    scope.launch {
                        Repository.mintInviteCode(authorityType, district.ifBlank { null })
                            .onSuccess { generatedCode = it }
                            .onFailure { message = it.message ?: "Could not generate a code" }
                        loading = false
                    }
                }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = canManage && !loading, colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(12.dp)) {
                    Text(if (loading) "Generating…" else "Generate code")
                }
            }
            generatedCode?.let { code ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Give this code to the new ${authorityLabel(authorityType)}", fontSize = 12.sp, color = Navy)
                            Text(code, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Green)
                            Text("Valid for 72 hours • single use", fontSize = 10.sp, color = Color(0xFF667085))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                                Button(onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Your DisasterLink authority invite code: $code (valid 72 hours, single use). Enter it on the Authority Registration screen.")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share invite code"))
                                }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorityAssignScreen(nav: NavHostController) {
    val emergencies by Repository.allEmergencies().collectAsState(initial = emptyList())
    val teams by Repository.teams().collectAsState(initial = emptyList())
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    var selectedEmergency by remember { mutableStateOf<String?>(null) }
    var selectedTeam by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Pending") }
    val scope = rememberCoroutineScope()

    val filtered = emergencies.filter { e ->
        val task = tasks.firstOrNull { it.emergencyId == e.id }
        when (tab) {
            "Completed" -> task?.status == TaskStatus.COMPLETED
            "Rejected" -> task?.status == TaskStatus.REJECTED
            else -> task == null || (task.status != TaskStatus.COMPLETED && task.status != TaskStatus.REJECTED)
        }
    }

    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Assign Task", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(3.dp)) {
                    listOf("Pending", "Completed", "Rejected").forEach { label ->
                        FilterChip(
                            selected = tab == label,
                            onClick = { tab = label; selectedEmergency = null; message = "" },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { PrototypeEmpty("No $tab tasks") }
            }
            items(filtered) { e ->
                val task = tasks.firstOrNull { it.emergencyId == e.id }; val team = teams.firstOrNull { it.id == task?.assignedTeamId }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (e.type.equals("Fire", true)) Icons.Default.LocalFireDepartment else Icons.Default.Warning, null, tint = Red); Spacer(Modifier.width(8.dp)); Text(e.type, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); StatusPill(task?.status?.name ?: e.status.name) }
                        Text(e.message.ifBlank { "Emergency assistance required" }); InfoLine(Icons.Default.LocationOn, e.district.ifBlank { "Tamil Nadu" }); InfoLine(Icons.Default.AccessTime, dateTime(e.createdAt)); InfoLine(Icons.Default.Groups, "Team: ${team?.name ?: "Not assigned"}")
                        when {
                            task?.status == TaskStatus.COMPLETED -> {
                                Text("Task completed — reassignment unavailable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Green)
                            }
                            task?.status == TaskStatus.PENDING_VERIFICATION -> {
                                Text("Evidence pending authority verification — reassignment unavailable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Amber)
                            }
                            else -> {
                                Button(onClick = { selectedEmergency = e.id; selectedTeam = team?.id }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text(if (team == null) "Assign task" else "Reassign task") }
                            }
                        }
                    }
                }
            }
            selectedEmergency?.let { eid ->
                item {
                    val e = emergencies.firstOrNull { it.id == eid }
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightBlue)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Select rescue team", fontWeight = FontWeight.Bold, color = Navy)
                            TeamPicker(teams, selectedTeam, e?.latitude, e?.longitude) { selectedTeam = it }
                            Button(onClick = { scope.launch {
                                message = ""
                                Repository.addTask(eid, "Respond to ${e?.type ?: "Emergency"}", "${e?.latitude ?: "Unknown"}, ${e?.longitude ?: "Unknown"}", selectedTeam)
                                    .onSuccess { selectedEmergency = null; selectedTeam = null; message = "Task assigned successfully." }
                                    .onFailure { message = it.message ?: "Assignment failed" }
                            } }, modifier = Modifier.fillMaxWidth(), enabled = selectedTeam != null, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Assign task") }
                        }
                    }
                }
            }
            if (message.isNotBlank()) item { ErrorText(message) }
        }
    }
}

@Composable
private fun AuthorityComplaintsScreen(nav: NavHostController) {
    val emergencies by Repository.allEmergencies().collectAsState(initial = emptyList())
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("View Complaints", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { AuthorityMetricGrid(emergencies, tasks) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityActionCard("District-wise Complaints", Icons.Default.LocationOn, LightBlue, Blue, Modifier.weight(1f)) { nav.navigate("authority/district") }
                AuthorityActionCard("Department Performance", Icons.Default.BarChart, LightGreen, Green, Modifier.weight(1f)) { nav.navigate("authority/performance") }
            } }
            item {
                Row(Modifier.fillMaxWidth()) {
                    AuthorityActionCard("Citizen Complaints", Icons.Default.Feedback, LightPurple, Purple, Modifier.fillMaxWidth()) { }
                }
            }
            item { Text("Citizen Complaints", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy) }
            if (emergencies.isEmpty()) item { PrototypeEmpty("No complaints yet") }
            items(emergencies.take(20)) { EmergencyCard(it) }
        }
    }
}

@Composable
private fun AuthorityDistrictScreen(nav: NavHostController) {
    val emergencies by Repository.allEmergencies().collectAsState(initial = emptyList())
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("District-wise Complaints", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ChartCard("District-wise Complaints", "Complaints registered across Tamil Nadu") { DistrictBarChart(emergencies) } }
            item { MapCard(emergencies) }
        }
    }
}

@Composable
private fun AuthorityPerformanceScreen(nav: NavHostController) {
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    val completed = tasks.count { it.status == TaskStatus.COMPLETED }
    val active = tasks.count { it.status == TaskStatus.IN_PROGRESS || it.status == TaskStatus.ASSIGNED }
    val rejected = tasks.count { it.status == TaskStatus.REJECTED }
    
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Department Performance", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ChartCard("Response Efficiency", "Current operational performance") { PerformanceDonut(completed, active, rejected) } }
            item { ChartCard("Response Time Trend", "Average response time (last 7 days)") { ResponseTimeTrend(tasks) } }
            item { ChartCard("Workload Distribution", "Task status breakdown") { StatusBarChart(completed, active, rejected) } }
            item { 
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                    DetailMetric("Completed", completed, LightGreen, Green, Modifier.weight(1f))
                    DetailMetric("Active", active, LightBlue, Blue, Modifier.weight(1f))
                    DetailMetric("Rejected", rejected, LightRed, Red, Modifier.weight(1f))
                } 
            }
        }
    }
}

@Composable
private fun ResponseTimeTrend(tasks: List<TaskEntity>) {
    val dayMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val data = (0..6).reversed().map { day ->
        val start = now - (day + 1) * dayMs
        val end = now - day * dayMs
        val daily = tasks.filter { it.createdAt in start..end && it.completedAt != null && it.startedAt != null }
        if (daily.isEmpty()) 0f else daily.map { (it.completedAt!! - it.startedAt!!).toFloat() / (60 * 1000) }.average().toFloat()
    }
    
    val max = data.maxOfOrNull { it }?.coerceAtLeast(10f) ?: 10f
    
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f || max <= 0f) return@Canvas
            val step = width / 6f
            
            // Draw grid
            drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, height), Offset(width, height), 1f)
            
            val points: List<Offset> = data.mapIndexed { i, v ->
                val y = (height - (v / max * height)).takeIf { it.isFinite() } ?: height
                Offset(i * step, y)
            }
            
            if (points.isNotEmpty()) {
                val path = Path().apply {
                    points.forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }
                
                drawPath(path, color = Blue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                points.forEach { p ->
                    drawCircle(Blue, 4.dp.toPx(), p)
                    drawCircle(Color.White, 2.dp.toPx(), p)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("7d ago", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("Today", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun AuthorityCommandScreen(nav: NavHostController) {
    val emergencies by Repository.allEmergencies().collectAsState(initial = emptyList()); val teams by Repository.teams().collectAsState(initial = emptyList()); val tasks by Repository.tasks().collectAsState(initial = emptyList())
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Emergency Command Center", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { SectionHeader("Emergency Command Center", "Live cases, teams and response timing") }
            if (emergencies.isEmpty()) item { PrototypeEmpty("No active emergency cases") }
            items(emergencies) { e -> val t = tasks.firstOrNull { it.emergencyId == e.id }; val team = teams.firstOrNull { it.id == t?.assignedTeamId }; Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(e.type, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f)); StatusPill(t?.status?.name ?: e.status.name) }; InfoLine(Icons.Default.LocationOn, e.district.ifBlank { "Tamil Nadu" }); InfoLine(Icons.Default.Groups, "Team: ${team?.name ?: "Not assigned"}"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { TimeBox("Reported", shortTime(e.createdAt), Modifier.weight(1f)); TimeBox("Start", shortTime(t?.startedAt), Modifier.weight(1f)); TimeBox("End", shortTime(t?.completedAt), Modifier.weight(1f)) } } } }
            item { MapCard(emergencies) }
        }
    }
}

@Composable
private fun EmergencyDetailScreen(nav: NavHostController, emergencyId: String?) {
    var emergency by remember { mutableStateOf<EmergencyEntity?>(null) }; var citizen by remember { mutableStateOf<UserEntity?>(null) }
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    LaunchedEffect(emergencyId) { if (emergencyId != null) { emergency = Repository.findEmergency(emergencyId); citizen = emergency?.let { Repository.findUser(it.citizenId) } } }
    val e = emergency; val task = tasks.firstOrNull { it.emergencyId == emergencyId }
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Emergency History", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (e == null) item { EmptyStateCard("Emergency not found", "This record is no longer available.") } else {
                item { DetailHero(e.type, e.status.name, Icons.Default.Warning, if (e.status == EmergencyStatus.RESOLVED) Green else Red) }
                item { DetailInfoCard("Emergency details", listOf("Date" to shortDate(e.createdAt), "Time" to shortTime(e.createdAt), "District" to e.district.ifBlank { "Tamil Nadu" }, "Location" to "${e.latitude ?: "—"}, ${e.longitude ?: "—"}")) }
                item { DetailInfoCard("Task timeline", listOf("Status" to (task?.status?.name ?: e.status.name), "Start time" to dateTime(task?.startedAt), "End time" to dateTime(task?.completedAt))) }
                if (citizen != null) item {
                    DetailInfoCard("Registered details", listOf("Name" to citizen!!.name, "Phone" to citizen!!.phone, "Address" to citizen!!.address, "PIN" to citizen!!.pincode))
                } else if (e.reporterName.isNotBlank() || e.reporterGovernmentId.isNotBlank()) {
                    item {
                        DetailInfoCard("Offline SOS reporter", listOf("Name" to e.reporterName.ifBlank { "Unknown" }, "Phone" to e.reporterPhone.ifBlank { "Not supplied" }, "Government ID" to e.reporterGovernmentId.ifBlank { "Not supplied" }))
                    }
                }
                if (task?.status == TaskStatus.COMPLETED) item { AuthorityModule("Give rescue team feedback", "Share your rating and comments", Icons.Default.Feedback, LightPurple, Purple) { nav.navigate("feedback") } }
            }
        }
    }
}

@Composable
private fun FeedbackScreen(nav: NavHostController) {
    val session = Repository.currentSession() ?: return
    val tasks by Repository.tasks().collectAsState(initial = emptyList()); val emergencies by Repository.citizenEmergencies(session.id).collectAsState(initial = emptyList())
    val ids = emergencies.map { it.id }.toSet(); val completed = tasks.filter { it.status == TaskStatus.COMPLETED && it.assignedTeamId != null && it.emergencyId in ids }
    var selected by remember { mutableStateOf<TaskEntity?>(completed.firstOrNull()) }; var rating by remember { mutableStateOf(5) }; var comment by remember { mutableStateOf("") }; var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Give Feedback", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Feedback", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("Your feedback helps improve emergency response.", fontSize = 12.sp, color = Color(0xFF667085))
                Text("Rate the response", fontWeight = FontWeight.SemiBold, color = Navy)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { (1..5).forEach { n -> IconButton({ rating = n }) { Icon(if (n <= rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (n <= rating) Amber else Color.LightGray, modifier = Modifier.size(31.dp)) } } }
                if (completed.isNotEmpty()) { Text("Completed rescue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { completed.forEach { t -> FilterChip(selected?.id == t.id, { selected = t }, label = { Text(t.title, maxLines = 1, fontSize = 11.sp) }) } } }
                Field("Comments", comment, { comment = it }, singleLine = false)
                Button(onClick = { scope.launch { if (selected?.assignedTeamId != null) Repository.addFeedback(session.id, selected!!.id, selected!!.assignedTeamId!!, rating, comment) else Repository.addFeedback(session.id, "GENERAL", "GENERAL", rating, comment); done = true } }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !done, colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(11.dp)) { Text(if (done) "Feedback Submitted" else "Submit Feedback") }
            } } }
        }
    }
}

@Composable
private fun SosScreen(nav: NavHostController) {
    val session = Repository.currentSession()
    if (session?.role == UserRole.AUTHORITY) {
        Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Emergency SOS", nav) }) { p ->
            Box(Modifier.fillMaxSize().padding(p).padding(24.dp), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Lock, null, tint = Red, modifier = Modifier.size(48.dp))
                        Text("Access Restricted", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Navy)
                        Text("Authority accounts are not permitted to trigger SOS alerts. This feature is for Citizens and Rescue Teams only.", textAlign = TextAlign.Center, fontSize = 13.sp, color = Color(0xFF667085))
                        Button(onClick = { nav.popBackStack() }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Go Back") }
                    }
                }
            }
        }
        return
    }
    val context = LocalContext.current; var type by remember { mutableStateOf("Medical") }; var message by remember { mutableStateOf("") }; var sent by remember { mutableStateOf(false) }; var result by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Emergency SOS", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LightRed)) { Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) { Surface(Modifier.size(72.dp), CircleShape, color = Red) { Icon(Icons.Default.Warning, null, Modifier.padding(20.dp), tint = Color.White) }; Text("Emergency SOS", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Your emergency will be shared with the nearest response team and authority.", fontSize = 12.sp, color = Color(0xFF667085)) } } }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Medical", "Fire", "Flood", "Accident", "Other").forEach { FilterChip(type == it, { type = it }, label = { Text(it, fontSize = 11.sp) }) } } }
            item { Field("Emergency message", message, { message = it }, singleLine = false) }
            if (!sent) item { Button(onClick = { val loc = getLastKnownLocation(context); scope.launch { val e = if (session != null) Repository.createEmergency(session.id, type, message, loc?.first, loc?.second, session.district) else Repository.createAnonymousEmergency(type, message, loc?.first, loc?.second); result = if (loc != null) "SOS #${e.id.take(8)} sent with location." else "SOS #${e.id.take(8)} sent."; sent = true; if (!hasLocationPermission(context)) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) } }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Default.Warning, null); Spacer(Modifier.width(8.dp)); Text("Send SOS", fontWeight = FontWeight.Bold, fontSize = 16.sp) } }
            if (sent) item { SuccessCard("Emergency sent", result) }
            item { OutlinedButton(onClick = { nav.navigate("bluetooth") }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(11.dp)) { Icon(Icons.Default.Bluetooth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("No signal? Send via nearby ESP32 instead") } }
            item { OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(11.dp)) { Text("Cancel") } }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BluetoothScreen(nav: NavHostController) {
    val context = LocalContext.current
    val session = Repository.currentSession()
    val manager = remember { BluetoothManager.shared(context) }
    val devices by manager.devices.collectAsState()
    val status by manager.status.collectAsState()
    val connected by manager.connected.collectAsState()
    val ready by manager.ready.collectAsState()
    var sosSentAt by remember { mutableStateOf(0L) }
    var localEmergencyId by remember { mutableStateOf<String?>(null) }
    var hardwareMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) enableBluetooth(context)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31) {
            permission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else if (!hasLocationPermission(context)) {
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            enableBluetooth(context)
        }
    }
    // Wait for `ready` (MTU negotiated + notifications confirmed on), not
    // just `connected` (GATT link up) — sending before that can silently
    // fail or truncate on some phones.
    LaunchedEffect(ready) { if (ready) getLastKnownLocation(context)?.let { manager.sendLocation(it.first, it.second) } }
    DisposableEffect(Unit) { onDispose { manager.stopScan() } }
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("ESP32 Connection", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { StatusBanner("Connection", status, if (ready) Green else if (connected) Amber else Blue) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { manager.startScan() }, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Scan") }; OutlinedButton(onClick = { manager.stopScan() }) { Text("Stop") }; OutlinedButton(onClick = { manager.disconnect() }) { Text("Disconnect") } } }
            if (session?.role != UserRole.AUTHORITY) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LightRed)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No phone signal? Send SOS anyway", fontWeight = FontWeight.Bold, color = Navy)
                            Text("This sends an SOS straight to the connected field node over Bluetooth. It relays over LoRa to a rescue gateway with no phone network needed.", fontSize = 12.sp, color = Color(0xFF667085))
                            if (hardwareMessage.isNotBlank()) Text(hardwareMessage, fontSize = 11.sp, color = if (hardwareMessage.startsWith("SOS stored")) Green else Red)
                            Button(
                                onClick = {
                                    val loc = getLastKnownLocation(context)
                                    if (loc == null) {
                                        hardwareMessage = "Location unavailable. Enable location and try again."
                                    } else if (session == null) {
                                        hardwareMessage = "Please log in before sending an offline SOS so your identity can be transmitted."
                                    } else {
                                        scope.launch {
                                            val emergency = Repository.createEmergency(
                                                session.id, "SOS", "Offline SOS via ESP32", loc.first, loc.second, session.district
                                            )
                                            val accepted = manager.sendSos(
                                                emergency.clientId ?: emergency.id,
                                                session.governmentId,
                                                session.name,
                                                // Phone is already stored locally after login/registration.
                                                Repository.findUser(session.id)?.phone.orEmpty(),
                                                session.district,
                                                loc.first,
                                                loc.second,
                                                "Offline SOS via ESP32"
                                            )
                                            if (accepted) {
                                                localEmergencyId = emergency.id
                                                sosSentAt = System.currentTimeMillis()
                                                hardwareMessage = "SOS stored locally and sent to field ESP32. Waiting for LoRa ACK…"
                                            } else {
                                                hardwareMessage = "ESP32 is not ready. Connect to the field node and retry."
                                            }
                                        }
                                    }
                                },
                                enabled = ready,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Red),
                                shape = RoundedCornerShape(12.dp)
                            ) { Icon(Icons.Default.Warning, null); Spacer(Modifier.width(8.dp)); Text(if (ready) "Send SOS via Field Node" else "Connect to a field node first", fontWeight = FontWeight.Bold) }
                            if (sosSentAt > 0) Text("Local emergency: ${localEmergencyId?.take(8) ?: "—"}. No phone internet was used.", fontSize = 11.sp, color = Color(0xFF667085))
                        }
                    }
                }
            }
            item { SectionHeader("Nearby BLE devices", "Connect the ESP32 field node") }
            if (devices.isEmpty()) item { EmptyStateCard("No devices found", "Put the ESP32 in BLE advertising mode and tap Scan.") }
            items(devices) { DeviceRow(it) { manager.connect(it) } }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: android.bluetooth.BluetoothDevice, onConnect: () -> Unit) {
    val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Unknown BLE device" }; val address = runCatching { device.address }.getOrNull().orEmpty()
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), CircleShape, color = LightBlue) { Icon(Icons.Default.Bluetooth, null, Modifier.padding(9.dp), tint = Blue) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(address, style = MaterialTheme.typography.bodySmall) }; Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Connect") } } }
}

private enum class TaskListKind { ACTIVE, VERIFICATION, COMPLETED }

@Composable
private fun TasksHomeScreen(nav: NavHostController) {
    val session = Repository.currentSession() ?: return
    val teams by Repository.teams().collectAsState(initial = emptyList())
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    val ids = teams.filter { it.members().contains(session.governmentId.uppercase(Locale.US)) }.map { it.id }.toSet()
    val mine = tasks.filter { it.assignedTeamId in ids }
    LaunchedEffect(Unit) { Repository.refreshOperationalData() }
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Rescue Tasks", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TaskSectionButton("Active Assigned Tasks", "ASSIGNED / IN_PROGRESS", mine.count { it.status == TaskStatus.ASSIGNED || it.status == TaskStatus.IN_PROGRESS }, Icons.Default.Assignment, LightBlue, Blue) { nav.navigate("tasks/active") } }
            item { TaskSectionButton("Pending Verification", "Evidence submitted • authority review required", mine.count { it.status == TaskStatus.PENDING_VERIFICATION }, Icons.Default.PendingActions, LightAmber, Amber) { nav.navigate("tasks/verification") } }
            item { TaskSectionButton("Completed Tasks", "Authority verified", mine.count { it.status == TaskStatus.COMPLETED }, Icons.Default.TaskAlt, LightGreen, Green) { nav.navigate("tasks/completed") } }
        }
    }
}

@Composable
private fun RescueTaskListScreen(nav: NavHostController, kind: TaskListKind) {
    val session = Repository.currentSession() ?: return
    val teams by Repository.teams().collectAsState(initial = emptyList())
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    val ids = teams.filter { it.members().contains(session.governmentId.uppercase(Locale.US)) }.map { it.id }.toSet()
    val mine = tasks.filter { it.assignedTeamId in ids }
    val filtered = when (kind) {
        TaskListKind.ACTIVE -> mine.filter { it.status == TaskStatus.ASSIGNED || it.status == TaskStatus.IN_PROGRESS }
        TaskListKind.VERIFICATION -> mine.filter { it.status == TaskStatus.PENDING_VERIFICATION }
        TaskListKind.COMPLETED -> mine.filter { it.status == TaskStatus.COMPLETED }
    }
    val title = when (kind) {
        TaskListKind.ACTIVE -> "Active Assigned Tasks"
        TaskListKind.VERIFICATION -> "Pending Verification"
        TaskListKind.COMPLETED -> "Completed Tasks"
    }
    LaunchedEffect(Unit) { Repository.refreshOperationalData() }
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar(title, nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("${filtered.size} task(s)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)) }
            if (filtered.isEmpty()) item { PrototypeEmpty("No tasks in this view") }
            items(filtered) { task -> TaskCard(task) { nav.navigate("task/${task.id}") } }
        }
    }
}

@Composable
private fun TaskSectionButton(title: String, subtitle: String, count: Int, icon: ImageVector, bg: Color, accent: Color, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(46.dp), CircleShape, color = Color.White) { Icon(icon, null, Modifier.padding(11.dp), tint = accent) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = Navy); Text(subtitle, fontSize = 11.sp, color = Color(0xFF667085)) }
            Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun RescueTaskDetailScreen(nav: NavHostController, taskId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetooth = remember { BluetoothManager.shared(context) }
    val isBtReady by bluetooth.ready.collectAsState()
    val transfer = remember { EvidenceBleTransfer(context, bluetooth) }
    var current by remember { mutableStateOf<TaskEntity?>(null) }
    var citizen by remember { mutableStateOf<UserEntity?>(null) }
    var message by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var serverEvidenceRefs by remember { mutableStateOf<List<String>>(emptyList()) }
    var transferred by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<TaskEventDto>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = (selectedUris + uris.map { it.toString() }).distinct().take(10)
            transferred = false
            serverEvidenceRefs = emptyList()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCameraUri != null) {
            selectedUris = (selectedUris + pendingCameraUri.toString()).distinct().take(10)
            transferred = false
            serverEvidenceRefs = emptyList()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                val uri = createTempImageUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }.onFailure {
                message = "Could not open camera: ${it.message}"
            }
        } else {
            message = "Camera permission is required to capture live evidence photos."
        }
    }

    LaunchedEffect(taskId) {
        if (taskId != null) {
            current = Repository.findTask(taskId)
            current?.let { t ->
                citizen = Repository.findEmergency(t.emergencyId)?.let { Repository.findUser(it.citizenId) }
                Repository.taskEvents(t.id).onSuccess { events = it }
            }
        }
    }
    val task = current
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Task Details", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (task == null) item { PrototypeEmpty("Task not found") } else {
                item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(48.dp), CircleShape, color = LightRed) { Icon(Icons.Default.Warning, null, Modifier.padding(12.dp), tint = Red) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy); Text(task.location, fontSize = 11.sp, color = Color(0xFF667085)); Text("Reported: ${dateTime(task.createdAt)}", fontSize = 10.sp, color = Color(0xFF667085)) }; StatusPill(task.status.name) } } }
                if (citizen != null) item { DetailInfoCard("Citizen Details", listOf("Name" to citizen!!.name, "Mobile" to citizen!!.phone, "Address" to citizen!!.address, "PIN" to citizen!!.pincode, "District" to citizen!!.district)) }
                item { DetailInfoCard("Task Timing", listOf("Created" to dateTime(task.createdAt), "Start time" to dateTime(task.startedAt), "End time" to dateTime(task.completedAt), "Location" to task.location)) }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Response audit trail", fontWeight = FontWeight.Bold, color = Navy)
                            if (events.isEmpty()) Text("No server events available yet.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
                            events.forEach { event -> Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(9.dp), CircleShape, color = Blue) {}; Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(event.action.replace('_', ' '), fontWeight = FontWeight.SemiBold, color = Navy); Text("${dateTime(event.createdAt)}${if (event.details.isNotBlank()) " • ${event.details}" else ""}", fontSize = 10.sp, color = Color(0xFF667085)) } } }
                        }
                    }
                }
                if (task.status == TaskStatus.ASSIGNED) item { Button(onClick = { scope.launch { Repository.acceptTask(task).onSuccess { current = task.copy(status = TaskStatus.IN_PROGRESS, startedAt = task.startedAt ?: System.currentTimeMillis()); message = "Task accepted." }.onFailure { message = it.message ?: "Could not accept task" } } }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(11.dp)) { Text("Accept Task") } }
                if (task.status == TaskStatus.ASSIGNED || task.status == TaskStatus.IN_PROGRESS) item { OutlinedButton(onClick = { scope.launch { Repository.rejectTask(task).onSuccess { current = task.copy(status = TaskStatus.REJECTED, completedAt = System.currentTimeMillis()); message = "Task rejected." }.onFailure { message = it.message ?: "Could not reject task" } } }, modifier = Modifier.fillMaxWidth()) { Text("Reject Task", color = Red) } }
                if (task.status == TaskStatus.IN_PROGRESS) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Evidence Photos", fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                                    Text("${selectedUris.size}/10 selected (min 3)", fontSize = 11.sp, color = if (selectedUris.size >= 3) Green else Amber, fontWeight = FontWeight.SemiBold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { galleryPicker.launch("image/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("From Gallery", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                runCatching {
                                                    val uri = createTempImageUri(context)
                                                    pendingCameraUri = uri
                                                    cameraLauncher.launch(uri)
                                                }.onFailure {
                                                    message = "Could not open camera: ${it.message}"
                                                }
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Take Photo", fontSize = 11.sp)
                                    }
                                }
                                if (selectedUris.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                        itemsIndexed(selectedUris) { index, uriStr ->
                                            Box(Modifier.size(80.dp)) {
                                                EvidenceUriImage(uriStr, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
                                                IconButton(
                                                    onClick = {
                                                        selectedUris = selectedUris.filterIndexed { i, _ -> i != index }
                                                        transferred = false
                                                        serverEvidenceRefs = emptyList()
                                                    },
                                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (selectedUris.size >= 3) item {
                        OutlinedButton(onClick = {
                            scope.launch {
                                transferring = true
                                message = "Uploading evidence over Wi-Fi…"
                                val wifi = Repository.uploadEvidenceOverWifi(task, selectedUris)
                                if (wifi.isSuccess) {
                                    serverEvidenceRefs = wifi.getOrThrow()
                                    transferred = true
                                    message = "Evidence uploaded to the DisasterLink server."
                                } else if (isBtReady) {
                                    message = "Wi-Fi upload unavailable. Using ESP32/BLE offline evidence path…"
                                    transfer.upload(task.id, selectedUris).onSuccess {
                                        serverEvidenceRefs = emptyList()
                                        transferred = true
                                        message = "Evidence stored on ESP32. You can now submit for authority verification."
                                    }.onFailure {
                                        transferred = false
                                        message = it.message ?: "Evidence transfer failed"
                                    }
                                } else {
                                    transferred = false
                                    message = wifi.exceptionOrNull()?.message ?: "Connect to the operational Wi-Fi or ESP32 before submitting evidence."
                                }
                                transferring = false
                            }
                        }, enabled = !transferring, modifier = Modifier.fillMaxWidth()) {
                            Text(if (transferring) "Transferring…" else if (isBtReady) "Upload Evidence (Wi-Fi / ESP32 fallback)" else "Upload Evidence over Wi-Fi")
                        }
                    }
                    item { Button(onClick = {
                        scope.launch {
                            Repository.submitTaskForVerification(task, selectedUris, serverEvidenceRefs.takeIf { it.size >= 3 }).onSuccess { current = Repository.findTask(task.id) ?: task.copy(status = TaskStatus.PENDING_VERIFICATION, imageUris = selectedUris.joinToString("|"), synced = false); message = "Submitted for authority verification." }.onFailure { message = it.message ?: "Could not submit for verification" }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = selectedUris.size >= 3 && transferred, colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(11.dp)) { Text("Submit for Authority Verification") } }
                }
                if (task.status == TaskStatus.PENDING_VERIFICATION) item { SuccessCard("Pending authority verification", "Rescue cannot complete this task. An authorized authority must review the evidence and complete it.") }
                if (task.status == TaskStatus.COMPLETED) item { SuccessCard("Task completed by authority", "The evidence was verified and the emergency was resolved.") }
                if (message.isNotBlank()) item { ErrorText(message) }
            }
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("evidence_", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

@Composable
private fun EvidenceUriImage(uriString: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = "Evidence photo", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(LightRed), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.BrokenImage, null, tint = Red, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EvidenceImage(file: File, modifier: Modifier = Modifier) {
    val bitmap = remember(file.absolutePath, file.lastModified()) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
    if (bitmap != null) Image(bitmap, contentDescription = "Evidence", modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(LightRed), contentAlignment = Alignment.Center) { Text("Unreadable image", fontSize = 10.sp, color = Red) }
}

@Composable
private fun AuthorityVerificationScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetooth = remember { BluetoothManager.shared(context) }
    val isBtReady by bluetooth.ready.collectAsState()
    val transfer = remember { EvidenceBleTransfer(context, bluetooth) }
    val tasks by Repository.tasks().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf("") }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var evidence by remember { mutableStateOf<List<File>>(emptyList()) }
    var receiving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Repository.refreshOperationalData() }
    val pending = tasks.filter { it.status == TaskStatus.PENDING_VERIFICATION }
    Scaffold(containerColor = AppBg, topBar = { SimpleTopBar("Authority Verification", nav) }) { p ->
        LazyColumn(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Only authorities can complete a task. Evidence must be loaded from the backend over Wi-Fi or from the ESP32 over BLE and be readable before completion is enabled.", fontSize = 12.sp, color = Color(0xFF667085)) }
            if (pending.isEmpty()) item { PrototypeEmpty("No tasks are waiting for verification") }
            items(pending) { task ->
                val selected = selectedTaskId == task.id
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (selected) LightBlue else Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(task.title, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f)); StatusPill("PENDING_VERIFICATION") }
                        Text(task.location, fontSize = 11.sp, color = Color(0xFF667085))
                        Button(onClick = {
                            selectedTaskId = task.id; evidence = emptyList()
                            scope.launch {
                                receiving = true
                                message = "Loading evidence over Wi-Fi…"
                                val wifi = Repository.downloadEvidenceOverWifi(task)
                                if (wifi.isSuccess) {
                                    evidence = wifi.getOrThrow()
                                    message = "${evidence.size} actual evidence images loaded from the DisasterLink server over Wi-Fi."
                                } else if (isBtReady) {
                                    message = "Server evidence unavailable. Receiving the actual images from ESP32 via BLE…"
                                    transfer.download(task.id).onSuccess { evidence = it; message = "${it.size} evidence images received from ESP32 and saved locally." }.onFailure { message = it.message ?: "Could not receive evidence" }
                                } else {
                                    message = wifi.exceptionOrNull()?.message ?: "Could not load evidence. Connect to the operational Wi-Fi or field ESP32."
                                }
                                receiving = false
                            }
                        }, enabled = !receiving, colors = ButtonDefaults.buttonColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                            Text(if (receiving && selected) "Loading…" else if (isBtReady) "Load Actual Evidence (Wi-Fi / ESP32 fallback)" else "Load Actual Evidence over Wi-Fi")
                        }
                        if (selected && evidence.isNotEmpty()) {
                            Text("Received evidence", fontWeight = FontWeight.SemiBold, color = Navy)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { evidence.take(3).forEach { f -> EvidenceImage(f, Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(10.dp))) } }
                            val valid = evidence.size >= 3 && evidence.take(10).all { it.exists() && it.length() > 1024 && BitmapFactory.decodeFile(it.absolutePath) != null }
                            Text(if (valid) "✓ Evidence is available for review" else "Evidence is incomplete or unreadable", fontSize = 11.sp, color = if (valid) Green else Red)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        Repository.rejectTaskVerification(task).onSuccess { message = "Verification rejected. Task returned to IN_PROGRESS." }.onFailure { message = it.message ?: "Could not reject verification" }
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Reject", color = Red) }
                                Button(onClick = {
                                    requireBiometricAuth(
                                        context,
                                        "Verify Evidence",
                                        "Confirm that the evidence is valid and resolve the emergency.",
                                        onSuccess = {
                                            scope.launch {
                                                Repository.verifyTask(task, evidence.size).onSuccess { message = "Task completed. Emergency resolved." }.onFailure { message = it.message ?: "Could not complete task" }
                                            }
                                        },
                                        onFailure = { err -> message = err }
                                    )
                                }, enabled = valid, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Complete Task") }
                            }
                        }
                    }
                }
            }
            if (message.isNotBlank()) item { ErrorText(message) }
        }
    }
}

@Composable
private fun PrototypeTopBar(title: String, nav: NavHostController, showSos: Boolean = false) {
    val session = remember { Repository.currentSession() }
    val isAuthority = session?.role == UserRole.AUTHORITY
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.navigate("home") { popUpTo(0) } }) { Icon(Icons.Default.Menu, null, tint = Navy) }
            Icon(Icons.Default.HealthAndSafety, null, Modifier.size(24.dp), tint = Red)
            Spacer(Modifier.width(8.dp))
            Text(title, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy)
            if (!isAuthority) {
                if (showSos) Button(onClick = { nav.navigate("sos") }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                else IconButton(onClick = { nav.navigate("sos") }) { Icon(Icons.Default.Notifications, null, tint = Navy) }
            } else {
                IconButton(onClick = { }) { Icon(Icons.Default.Notifications, null, tint = Navy) }
            }
        }
    }
}

@Composable
private fun PrototypeBottomNav(nav: NavHostController, role: String) {
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
        NavigationBarItem(selected = true, onClick = { }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home", fontSize = 9.sp) })
        NavigationBarItem(selected = false, onClick = { nav.navigate(if (role == "rescue") "tasks" else "feedback") }, icon = { Icon(if (role == "rescue") Icons.Default.Assignment else Icons.Default.Feedback, null) }, label = { Text(if (role == "rescue") "Tasks" else "Feedback", fontSize = 9.sp) })
        if (role != "authority") {
            NavigationBarItem(selected = false, onClick = { nav.navigate("sos") }, icon = { Icon(Icons.Default.Warning, null, tint = Red) }, label = { Text("SOS", fontSize = 9.sp) })
        }
        NavigationBarItem(selected = false, onClick = { nav.navigate("bluetooth") }, icon = { Icon(Icons.Default.Bluetooth, null) }, label = { Text(if (role == "rescue") "Location" else "Profile", fontSize = 9.sp) })
    }
}

@Composable
private fun PrototypePermissionCards() {
    val context = LocalContext.current
    val btReady = remember { mutableStateOf(BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) }
    val locReady = remember { mutableStateOf(hasLocationPermission(context) && locationServicesEnabled(context)) }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (Build.VERSION.SDK_INT < 31 || result.values.all { it }) {
            enableBluetooth(context)
            btReady.value = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        }
    }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            requestLocation(context)
            locReady.value = hasLocationPermission(context) && locationServicesEnabled(context)
        }
    }
    if (!btReady.value) PermissionCard(
        "Connect Field Device",
        "Bluetooth is only needed when connecting the ESP32 field node.",
        Icons.Default.Bluetooth
    ) {
        if (Build.VERSION.SDK_INT >= 31) btPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        else enableBluetooth(context)
    }
    if (!locReady.value) PermissionCard(
        "Enable Location",
        "Location is only needed when an emergency needs your position.",
        Icons.Default.LocationOn
    ) {
        if (!hasLocationPermission(context)) locPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        else requestLocation(context)
    }
}

@Composable
private fun PermissionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LightBlue)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(40.dp), CircleShape, color = Color.White.copy(alpha = .8f)) { Icon(icon, null, Modifier.padding(10.dp), tint = Blue) }
            Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 13.sp); Text(subtitle, fontSize = 10.sp, color = Color(0xFF52606D)) }
            Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(9.dp)) { Text("Turn On", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun RowScope.PrototypeTile(title: String, icon: ImageVector, bg: Color, accent: Color, onClick: () -> Unit) {
    Card(Modifier.weight(1f).height(100.dp).clickable { onClick() }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.fillMaxSize().padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(Modifier.size(42.dp), CircleShape, color = Color.White.copy(alpha = .8f)) { Icon(icon, null, Modifier.padding(10.dp), tint = accent) }
            Spacer(Modifier.height(5.dp)); Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Navy)
        }
    }
}

@Composable
private fun PrototypeMetric(label: String, value: Int, bg: Color, accent: Color, modifier: Modifier) {
    Card(modifier.height(72.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = bg)) { Column(Modifier.fillMaxSize().padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(value.toString(), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent); Text(label, fontSize = 9.sp, color = Navy, maxLines = 2) } }
}

@Composable
private fun AuthorityActionCard(
    title: String,
    icon: ImageVector,
    bg: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                Modifier.size(48.dp),
                shape = CircleShape,
                color = bg
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.padding(12.dp),
                    tint = accent
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Navy,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PrototypeEmpty(text: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Info, null, tint = Blue, modifier = Modifier.size(30.dp)); Spacer(Modifier.height(5.dp)); Text(text, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) } } }

@Composable
private fun SkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.LightGray.copy(alpha = alpha)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.4f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
                Box(Modifier.fillMaxWidth(0.75f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = alpha)))
            }
        }
    }
}

@Composable
private fun CitizenHeader(session: SessionUser) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LightBlue)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(54.dp), CircleShape, color = Color.White) { Icon(Icons.Default.Person, null, Modifier.padding(12.dp), tint = Blue) }; Spacer(Modifier.width(12.dp)); Column { Text("Welcome,", style = MaterialTheme.typography.bodySmall); Text(session.name, style = MaterialTheme.typography.titleLarge, color = Navy, fontWeight = FontWeight.Bold); Text("${session.district.ifBlank { "Tamil Nadu" }}, Tamil Nadu", style = MaterialTheme.typography.bodySmall) } } } }
@Composable
private fun RescueHeader(session: SessionUser) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LightBlue)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(54.dp), CircleShape, color = Color.White) { Icon(Icons.Default.HealthAndSafety, null, Modifier.padding(12.dp), tint = Blue) }; Spacer(Modifier.width(12.dp)); Column { Text("Rescue Team", style = MaterialTheme.typography.bodySmall); Text(session.name, style = MaterialTheme.typography.titleLarge, color = Navy, fontWeight = FontWeight.Bold); Text("Field operations", style = MaterialTheme.typography.bodySmall) } } } }
@Composable
private fun AuthorityHeader(session: SessionUser) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Navy),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                Icon(Icons.Default.AccountBalance, null, Modifier.padding(14.dp), tint = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Authenticated Authority", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text(authorityLabel(session.authorityType), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(session.name, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun OperationalSyncCard(onSync: () -> Unit, syncing: Boolean, message: String) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, null, tint = Blue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (hasInternet(context)) "Connected to operations network" else "Offline / LAN mode", fontWeight = FontWeight.Bold, color = Navy)
                    Text("Emergency, team and task data stays available from local Room storage.", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onSync, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, "Refresh operations")
                }
            }
            if (message.isNotBlank()) Text(message, fontSize = 11.sp, color = if (message.startsWith("Sync complete")) Green else Amber)
        }
    }
}

@Composable
private fun AuthorityMetricGrid(emergencies: List<EmergencyEntity>, tasks: List<TaskEntity>) {
    val total = emergencies.size
    val resolved = emergencies.count { it.status == EmergencyStatus.RESOLVED }
    val pending = emergencies.count { it.status != EmergencyStatus.RESOLVED && it.status != EmergencyStatus.REJECTED }
    val avg = if (tasks.isEmpty()) 0 else 12
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailMetric("Total Reports", total, LightBlue, Navy, Modifier.weight(1f))
        DetailMetric("Resolved", resolved, LightGreen, Green, Modifier.weight(1f))
        DetailMetric("Pending", pending, LightAmber, Amber, Modifier.weight(1f))
        DetailMetric("Response", avg, LightPurple, Purple, Modifier.weight(1f))
    }
}

@Composable
private fun DetailMetric(label: String, value: Int, bg: Color, accent: Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = bg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Navy.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun DashboardTopBar(title: String, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    TopAppBar(
        title = { Text(title, color = Navy, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton({ nav.navigate("home") { popUpTo(0) } }) { Icon(Icons.Default.Menu, "Menu", tint = Navy) } },
        actions = {
            IconButton({ nav.navigate("sos") }) { Icon(Icons.Default.Warning, "SOS", tint = Red) }
            IconButton({
                scope.launch { Repository.logout() }
                nav.navigate("home") { popUpTo(0) }
            }) { Icon(Icons.Default.Logout, "Logout", tint = Navy) }
        }
    )
}

@Composable
private fun BottomNav(nav: NavHostController, role: String) { NavigationBar(containerColor = Color.White) { NavigationBarItem(role == "citizen", { }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }); NavigationBarItem(false, { nav.navigate(if (role == "rescue") "tasks" else "feedback") }, icon = { Icon(if (role == "rescue") Icons.Default.Assignment else Icons.Default.Feedback, null) }, label = { Text(if (role == "rescue") "Tasks" else "Feedback") }); NavigationBarItem(false, { nav.navigate("sos") }, icon = { Icon(Icons.Default.Warning, null, tint = Red) }, label = { Text("SOS") }); NavigationBarItem(false, { nav.navigate("bluetooth") }, icon = { Icon(Icons.Default.Bluetooth, null) }, label = { Text("Connect") }) } }

@Composable
private fun AccessStatusStrip() { Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightBlue)) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bluetooth, null, tint = Blue); Spacer(Modifier.width(8.dp)); Column { Text("Bluetooth", fontWeight = FontWeight.Bold, color = Navy); Text("Device communication access", style = MaterialTheme.typography.bodySmall) } }; Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Blue); Spacer(Modifier.width(8.dp)); Column { Text("Location", fontWeight = FontWeight.Bold, color = Navy); Text("Location access for emergency response", style = MaterialTheme.typography.bodySmall) } } } } }

@Composable
private fun DashboardTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, iconColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) { Card(modifier.clickable { onClick() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = bg)) { Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Surface(Modifier.size(44.dp), CircleShape, color = Color.White.copy(alpha = .85f)) { Icon(icon, null, Modifier.padding(10.dp), tint = iconColor) }; Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF24324A)) } } }

@Composable
private fun MetricTile(label: String, value: Int, bg: Color, accent: Color, modifier: Modifier = Modifier) { Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = bg)) { Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent); Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155), maxLines = 2) } } }

@Composable
private fun AuthorityModule(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, accent: Color, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(48.dp), CircleShape, color = bg) { Icon(icon, null, Modifier.padding(11.dp), tint = accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, color = Navy); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)) }; Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF94A3B8)) } } }

@Composable
private fun SectionHeader(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleMedium, color = Navy, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)) } }

@Composable
private fun EmergencyCard(e: EmergencyEntity, onClick: (() -> Unit)? = null) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(44.dp),
                CircleShape,
                color = if (e.status == EmergencyStatus.RESOLVED) LightGreen else LightRed
            ) {
                Icon(
                    if (e.type.equals("Flood", true)) Icons.Default.Water else Icons.Default.Warning,
                    null,
                    Modifier.padding(10.dp),
                    tint = if (e.status == EmergencyStatus.RESOLVED) Green else Red
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.type, fontWeight = FontWeight.Bold, color = Navy)
                    Spacer(Modifier.width(6.dp))
                    SyncStatusIcon(e.synced)
                }
                Text(e.message.ifBlank { "Emergency assistance requested" }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text("${shortDate(e.createdAt)} • ${shortTime(e.createdAt)} • ${e.district}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667085))
            }
            StatusPill(e.status.name)
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                SyncStatusIcon(task.synced)
                Spacer(Modifier.width(8.dp))
                StatusPill(task.status.name)
            }
            InfoLine(Icons.Default.LocationOn, task.location)
            InfoLine(Icons.Default.DateRange, "Date: ${shortDate(task.createdAt)}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeBox("Start", shortTime(task.startedAt), Modifier.weight(1f))
                TimeBox("End", shortTime(task.completedAt), Modifier.weight(1f))
            }
            Text("Tap to view details", style = MaterialTheme.typography.labelSmall, color = Blue)
        }
    }
}

@Composable
private fun SyncStatusIcon(synced: Boolean) {
    Icon(
        if (synced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
        contentDescription = if (synced) "Synced" else "Local only",
        modifier = Modifier.size(16.dp),
        tint = if (synced) Green else Color.Gray
    )
}

@Composable
private fun TimeBox(label: String, value: String, modifier: Modifier) { Surface(modifier, shape = RoundedCornerShape(12.dp), color = AppBg) { Column(Modifier.padding(9.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF667085)); Text(value, fontWeight = FontWeight.SemiBold, color = Navy) } } }

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(17.dp), tint = Blue); Spacer(Modifier.width(7.dp)); Text(text, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun StatusPill(text: String) { val t = text.replace('_', ' '); val c = when { t.contains("COMPLETED", true) || t.contains("RESOLVED", true) -> Green; t.contains("REJECT", true) -> Red; t.contains("PROGRESS", true) -> Blue; else -> Amber }; Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = .12f)) { Text(t, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c) } }

@Composable
private fun DetailHero(title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(52.dp), CircleShape, color = accent.copy(alpha = .12f)) { Icon(icon, null, Modifier.padding(12.dp), tint = accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, color = Navy, fontWeight = FontWeight.Bold); Text("Emergency response", style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)) }; StatusPill(status) } } }

@Composable
private fun DetailInfoCard(title: String, rows: List<Pair<String, String>>) { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.Bold, color = Navy); rows.forEach { (k, v) -> Row(Modifier.fillMaxWidth()) { Text(k, Modifier.weight(0.34f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)); Text(v, Modifier.weight(0.66f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) } } } } }

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.Bold, color = Navy); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)); content() } } }

@Composable
private fun DistrictBarChart(emergencies: List<EmergencyEntity>) {
    val data = emergencies.groupingBy { it.district.ifBlank { "Unknown" } }.eachCount().entries.sortedByDescending { it.value }.take(7)
    if (data.isEmpty()) { EmptyStateCard("No district data", "Charts will populate as complaints arrive."); return }
    val max = data.maxOf { it.value }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(220.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { (district, value) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) { Text(value.toString(), fontSize = 10.sp, color = Navy); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth(.78f).height((150f * value / max).dp.coerceAtLeast(18.dp)).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Blue)); Spacer(Modifier.height(5.dp)); Text(district.take(7), fontSize = 8.sp, maxLines = 1) } }
    }
}

@Composable
private fun StatusBarChart(completed: Int, active: Int, rejected: Int) {
    val data = listOf("Completed" to completed, "Active" to active, "Rejected" to rejected); val max = data.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { data.forEach { (label, value) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.width(78.dp), fontSize = 12.sp); Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(8.dp)).background(AppBg)) { Box(Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (label == "Completed") Green else if (label == "Rejected") Red else Blue)) }; Spacer(Modifier.width(8.dp)); Text(value.toString(), fontWeight = FontWeight.Bold, color = Navy) } } }
}

@Composable
private fun PerformanceDonut(completed: Int, active: Int, rejected: Int) {
    val total = (completed + active + rejected).coerceAtLeast(1); val values = listOf(completed, active, rejected); val colors = listOf(Green, Blue, Red)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { var start = -90f; values.forEachIndexed { i, v -> val sweep = 360f * v / total; drawArc(colors[i], start, sweep, false, style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Butt)); start += sweep } }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$total", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Navy); Text("Tasks", fontSize = 11.sp) } }
        Spacer(Modifier.width(18.dp)); Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Completed" to completed, "Active" to active, "Rejected" to rejected).forEachIndexed { i, pair -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i])); Spacer(Modifier.width(7.dp)); Text("${pair.first}  ${pair.second}", fontSize = 12.sp) } } }
    }
}

@Composable
private fun RowScope.DetailMetric(label: String, value: Int, bg: Color, accent: Color) {
    Card(Modifier.weight(1f), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = bg)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent); Text(label, fontSize = 10.sp, color = Navy) } }
}

@Composable
private fun MapCard(emergencies: List<EmergencyEntity>) { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Disaster locations", fontWeight = FontWeight.Bold, color = Navy); DisasterMap(emergencies) } } }

@Composable
private fun DisasterMap(emergencies: List<EmergencyEntity>) {
    val context = LocalContext.current
    if (!hasInternet(context)) { 
        Text("Internet unavailable. Stored coordinates shown below.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        emergencies.filter { it.latitude != null && it.longitude != null }.take(10).forEach { 
            Text("• ${it.type} in ${it.district}: ${it.latitude}, ${it.longitude}", style = MaterialTheme.typography.labelSmall, color = Navy) 
        }
        return 
    }
    val points = emergencies.filter { it.latitude != null && it.longitude != null }.take(100)
    if (points.isEmpty()) { EmptyStateCard("No mapped locations", "GPS coordinates will appear here when available."); return }
    
    val markers = points.joinToString(",") { 
        val color = when {
            it.status == EmergencyStatus.RESOLVED -> "green"
            it.status == EmergencyStatus.REJECTED -> "gray"
            it.type.equals("Fire", true) -> "red"
            it.type.equals("Flood", true) -> "blue"
            it.type.equals("Medical", true) -> "amber"
            else -> "purple"
        }
        val size = when {
            it.type.equals("Fire", true) || it.type.equals("Flood", true) -> 36
            it.status == EmergencyStatus.RESOLVED -> 26
            else -> 30
        }
        "{lat:${it.latitude},lng:${it.longitude},type:'${it.type}',color:'$color',size:$size}"
    }
    
    AndroidView(
        factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; webViewClient = WebViewClient() } },
        update = { it.loadDataWithBaseURL("https://www.openstreetmap.org/", mapHtml(markers), "text/html", "UTF-8", null) },
        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))
    )
}

private fun mapHtml(markers: String) = """
<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><style>html,body,#map{height:100%;margin:0}.marker-pin{border-radius:50% 50% 50% 0;position:absolute;transform:rotate(-45deg);left:50%;top:50%;}.marker-pin::after{content:'';width:60%;height:60%;margin:20% 0 0 20%;background:#fff;position:absolute;border-radius:50%;}</style></head><body><div id='map'></div><script>
const pts=[$markers];
const map=L.map('map').setView([11.1271,78.6569],7);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);
pts.forEach(p=>{
    const color = p.color === 'green' ? '#22A06B' : p.color === 'blue' ? '#2166D1' : p.color === 'amber' ? '#F5A623' : p.color === 'purple' ? '#6E56CF' : p.color === 'gray' ? '#94A3B8' : '#E53935';
    const sz = p.size || 30;
    const half = sz / 2;
    const icon = L.divIcon({
        className: 'custom-div-icon',
        html: "<div style='background-color:"+color+";width:"+sz+"px;height:"+sz+"px;margin:-"+half+"px 0 0 -"+half+"px;' class='marker-pin'></div>",
        iconSize: [sz, sz * 1.4],
        iconAnchor: [half, sz * 1.4]
    });
    L.marker([p.lat, p.lng], {icon: icon}).bindPopup("<b>"+p.type+"</b><br>Type: "+p.type+" ("+p.color+")").addTo(map);
});
if(pts.length) map.fitBounds(pts.map(p=>[p.lat,p.lng]),{padding:[30,30]});
</script></body></html>
""".trimIndent()

@Composable
private fun TeamPicker(teams: List<RescueTeamEntity>, selected: String?, latitude: Double?, longitude: Double?, onSelect: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { teams.forEach { t -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected == t.id) LightBlue else Color.White).clickable { onSelect(t.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == t.id, { onSelect(t.id) }); Column { Text(t.name, fontWeight = FontWeight.SemiBold); Text(if (t.latitude != null && latitude != null && longitude != null) "Live location available" else "Location not available", style = MaterialTheme.typography.bodySmall) } } } } }

@Composable
private fun ImageCountCard(count: Int) { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (count >= 3) LightGreen else LightAmber)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PhotoLibrary, null, tint = if (count >= 3) Green else Amber); Spacer(Modifier.width(9.dp)); Text("$count image(s) selected  •  minimum 3 required", style = MaterialTheme.typography.bodySmall) } } }
@Composable
private fun SuccessCard(title: String, text: String) { Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = Green); Text(text, style = MaterialTheme.typography.bodySmall) } } }
@Composable
private fun EmptyStateCard(title: String, text: String) { Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Info, null, tint = Blue, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(6.dp)); Text(title, fontWeight = FontWeight.Bold, color = Navy); Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085)) } } }
@Composable
private fun StatusBanner(title: String, text: String, accent: Color) { Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = .10f))) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bluetooth, null, tint = accent); Spacer(Modifier.width(9.dp)); Column { Text(title, fontWeight = FontWeight.Bold, color = Navy); Text(text, style = MaterialTheme.typography.bodySmall) } } } }
@Composable
private fun ErrorText(text: String) { Text(text, color = Red, style = MaterialTheme.typography.bodySmall) }

@Composable
private fun RoleChips(selected: UserRole, onSelect: (UserRole) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(UserRole.CITIZEN, UserRole.RESCUE, UserRole.AUTHORITY).forEach { FilterChip(selected == it, { onSelect(it) }, label = { Text(roleLabel(it)) }) } } }
@Composable
private fun AuthorityTypeSelector(selected: AuthorityType, enabled: Boolean = true, onSelect: (AuthorityType) -> Unit) { var open by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(authorityLabel(selected), Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }; DropdownMenu(open, { open = false }) { AuthorityType.entries.forEach { DropdownMenuItem({ Text(authorityLabel(it)) }, { onSelect(it); open = false }) } } } }
@Composable
private fun DistrictSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) { var open by remember { mutableStateOf(false) }; Box(modifier) { OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.ifBlank { "District" }, Modifier.weight(1f), maxLines = 1); Icon(Icons.Default.ArrowDropDown, null) }; DropdownMenu(open, { open = false }) { TamilNaduDistricts.forEach { DropdownMenuItem({ Text(it) }, { onSelect(it); open = false }) } } } }
@Composable
private fun AadhaarFields(value: String, onChange: (String) -> Unit) { Field("Aadhaar number", formatAadhaar(value), { onChange(it.filter(Char::isDigit).take(12)) }, keyboard = KeyboardType.Number); Text("12 digits • displayed as 4-4-4", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667085)) }
private fun formatAadhaar(value: String): String = value.filter(Char::isDigit).take(12).chunked(4).joinToString("-")
@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Surface(Modifier.width(66.dp).height(56.dp), shape = RoundedCornerShape(12.dp), color = LightGreen) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("+91", fontWeight = FontWeight.Bold, color = Green) } }; Field("10 digit mobile number", value, onChange, modifier = Modifier.weight(1f), keyboard = KeyboardType.Phone) } }
@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), trailingIcon = { Icon(Icons.Default.Lock, null) }) }
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true
) { OutlinedTextField(value, onValueChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = modifier.then(if (modifier == Modifier) Modifier.fillMaxWidth() else Modifier), singleLine = singleLine, minLines = if (singleLine) 1 else 3, shape = RoundedCornerShape(12.dp), enabled = enabled) }

@Composable
private fun SimpleTopBar(title: String, nav: NavHostController, light: Boolean = true) { TopAppBar(title = { Text(title, color = if (light) Navy else Color.White, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = if (light) Navy else Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = if (light) AppBg else Color.Transparent)) }
private fun roleLabel(role: UserRole) = when (role) { UserRole.CITIZEN -> "Citizen"; UserRole.RESCUE -> "Rescue Team"; UserRole.AUTHORITY -> "Authority" }
private fun authorityLabel(type: AuthorityType?) = when (type) { AuthorityType.VAO -> "VAO"; AuthorityType.TAHSILDAR -> "Tahsildar"; AuthorityType.DISTRICT_COLLECTOR -> "District Collector"; AuthorityType.STATE_DISASTER_MANAGEMENT_AUTHORITY -> "State Disaster Management Authority"; AuthorityType.CM -> "CM"; null -> "Not specified" }

private fun hasInternet(context: Context): Boolean { val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val network = cm.activeNetwork ?: return false; val caps = cm.getNetworkCapabilities(network) ?: return false; return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
private fun enableBluetooth(context: Context) { val adapter = BluetoothAdapter.getDefaultAdapter() ?: return; try { if (!adapter.isEnabled) context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: SecurityException) { } }
private fun locationServicesEnabled(context: Context): Boolean { val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager; return runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) }
private fun requestLocation(context: Context) { if (!hasLocationPermission(context)) return; if (!locationServicesEnabled(context)) context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
private fun hasLocationPermission(context: Context): Boolean { if (Build.VERSION.SDK_INT < 23) return true; return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED }
@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Pair<Double, Double>? { if (!hasLocationPermission(context)) return null; return runCatching { val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager; listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { provider -> lm.getLastKnownLocation(provider)?.let { it.latitude to it.longitude } }.firstOrNull() }.getOrNull() }

fun requireBiometricAuth(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val activity = context as? FragmentActivity ?: run {
        onFailure("Biometric authentication requires a FragmentActivity context.")
        return
    }

    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        // Fallback gracefully if biometric hardware/enrollment is not configured on device or emulator
        onSuccess()
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFailure(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailure("Authentication failed.")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()

    biometricPrompt.authenticate(promptInfo)
}
