package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.zIndex
import android.Manifest
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private val CHANNEL_ID = "cricket_admin_alerts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        // Ask for runtime notification permission on Android 13+ (Tiramisu)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        setContent {
            MyApplicationTheme {
                val model: CricketViewModel = viewModel()

                // Register native OS notification trigger
                LaunchedEffect(model) {
                    model.showNativeSystemNotification = { title: String, message: String ->
                        sendSystemNotification(title, message)
                    }
                }

                MainAdminConsoleApp(model)
            }
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Cricket Admin Alerts"
            val descriptionText = "Notifications for Wickets and Match Outcomes"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSystemNotification(title: String, message: String) {
        val notificationId = System.currentTimeMillis().toInt()

        // Simple Intent to bring admin back into the app
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // platform standard backup icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(this)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(notificationId, builder.build())
                }
            } else {
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainAdminConsoleApp(model: CricketViewModel) {
    val isLoggedIn by model.isLoggedIn.collectAsStateWithLifecycle()
    val activeAlert by model.activeInAppAlert.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!isLoggedIn) {
                AdminOtpLoginScreen(model)
            } else {
                AdminDashboardContainer(model)
            }
        }

        // Global Dynamic Heads-Up Alert Overlay
        AnimatedVisibility(
            visible = activeAlert != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .zIndex(9999f)
        ) {
            activeAlert?.let { alert ->
                HeadsUpAlertBanner(
                    alert = alert,
                    onDismiss = { model.dismissActiveAlert() }
                )
            }
        }
    }
}

// ----------------------------------------------------
// OTP LOGIN SCREEN (Bento Glassmorphic Aesthetic)
// ----------------------------------------------------
@Composable
fun AdminOtpLoginScreen(model: CricketViewModel) {
    val phone by model.phone.collectAsStateWithLifecycle()
    val otpCode by model.otpCode.collectAsStateWithLifecycle()
    val currentStep by model.currentOtpStep.collectAsStateWithLifecycle()

    var loginError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BentoPrimaryContainer.copy(alpha = 0.15f), BentoDarkBg),
                    radius = 2000f
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(BentoTileBg)
                .border(1.dp, BentoBorder, RoundedCornerShape(32.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Brand Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BentoPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsCricket,
                        contentDescription = "Cricket Logo",
                        tint = BentoOnPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "FCC SCORING",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = BentoPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Admin Control Panel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BentoTextMuted
                    )
                }
            }

            Text(
                text = "Secure Mobile Authentication",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTextLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Platform officers and scorers require active mobile authorization.",
                fontSize = 12.sp,
                color = BentoTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            AnimatedContent(
                targetState = currentStep,
                label = "OTPStepTransition"
            ) { step ->
                if (step == 1) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { model.setPhone(it) },
                            label = { Text("Mobile Number") },
                            placeholder = { Text("+1 (555) 012-3456") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoPrimary,
                                unfocusedBorderColor = BentoBorder,
                                focusedLabelColor = BentoPrimary,
                                unfocusedLabelColor = BentoTextMuted,
                                focusedTextColor = BentoTextLight,
                                unfocusedTextColor = BentoTextLight
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (phone.isBlank()) {
                                    loginError = "Please enter a valid mobile number."
                                } else {
                                    loginError = ""
                                    model.requestOtp()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BentoPrimary,
                                contentColor = BentoOnPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("request_otp_button")
                        ) {
                            Text("Send One-Time Password", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sandbox convenience bypass
                        TextButton(
                            onClick = {
                                model.setPhone("+1234567890")
                                model.requestOtp()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Bypass with Demo Account", color = BentoSecondary)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Sent OTP code to $phone",
                            fontSize = 12.sp,
                            color = BentoSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { model.setOtp(it) },
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("112233") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Code") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoPrimary,
                                unfocusedBorderColor = BentoBorder,
                                focusedLabelColor = BentoPrimary,
                                unfocusedLabelColor = BentoTextMuted,
                                focusedTextColor = BentoTextLight,
                                unfocusedTextColor = BentoTextLight
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sandbox Verification Tip: Enter '112233' or any 6 digits.",
                            fontSize = 11.sp,
                            color = BentoTextMuted,
                            textAlign = TextAlign.Start
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (otpCode.length < 6) {
                                    loginError = "OTP code must be 6 digits."
                                } else {
                                    loginError = ""
                                    model.verifyOtp()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BentoPrimary,
                                contentColor = BentoOnPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("verify_otp_button")
                        ) {
                            Text("Verify & Enter Dashboard", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { model.logout() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Change Phone Number", color = BentoTextMuted)
                        }
                    }
                }
            }

            if (loginError.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loginError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


// ----------------------------------------------------
// ADMIN DASHBOARD CONTAINER (Multi-Pane Navigation)
// ----------------------------------------------------
@Composable
fun AdminDashboardContainer(model: CricketViewModel) {
    var activeTab by remember { mutableStateOf("dashboard") } // "dashboard", "scoring", "rosters", "history"
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp > 720

    // Force setup of live match view if a live match id becomes active
    val activeMatchId by model.activeMatchId.collectAsStateWithLifecycle()

    var showNotificationCenter by remember { mutableStateOf(false) }
    val alerts by model.alertHistory.collectAsStateWithLifecycle()
    val unreadCount = alerts.count { !it.isRead }

    Row(modifier = Modifier.fillMaxSize()) {
        // Lateral Navigation Rail (For Tablets / Big screen dashboards)
        if (isTablet) {
            NavigationRail(
                containerColor = BentoTileBg,
                contentColor = BentoTextLight,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .border(width = 1.dp, color = BentoBorder, shape = RoundedCornerShape(0.dp))
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BentoPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsCricket,
                        contentDescription = "Admin Console",
                        tint = BentoPrimary
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Rails items
                NavigationRailIcon(
                    icon = Icons.Default.Dashboard,
                    label = "Bento",
                    selected = activeTab == "dashboard",
                    onSelect = { activeTab = "dashboard" },
                    modifier = Modifier.testTag("rail_bento")
                )
                NavigationRailIcon(
                    icon = Icons.Default.Scoreboard,
                    label = "Scoring",
                    selected = activeTab == "scoring",
                    onSelect = { activeTab = "scoring" },
                    enabled = activeMatchId != null,
                    modifier = Modifier.testTag("rail_scoring")
                )
                NavigationRailIcon(
                    icon = Icons.Default.Group,
                    label = "Rosters",
                    selected = activeTab == "rosters",
                    onSelect = { activeTab = "rosters" },
                    modifier = Modifier.testTag("rail_rosters")
                )
                NavigationRailIcon(
                    icon = Icons.Default.History,
                    label = "Matches",
                    selected = activeTab == "history",
                    onSelect = { activeTab = "history" },
                    modifier = Modifier.testTag("rail_history")
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { model.logout() },
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = BentoWicketRed
                    )
                }
            }
        }

        // Screen Body Layout
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(BentoTileBg)
                        .border(width = 1.dp, color = BentoBorder, shape = RoundedCornerShape(0.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isTablet) {
                            IconButton(onClick = { model.logout() }) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = BentoWicketRed)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "FCC COMMAND HUB",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = BentoPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    // Bell Notification Icon with Badge Count
                    IconButton(
                        onClick = { showNotificationCenter = true },
                        modifier = Modifier.testTag("btn_alert_center")
                    ) {
                        Box {
                            Icon(
                                imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Alert desk",
                                tint = if (unreadCount > 0) BentoPrimary else BentoTextLight,
                                modifier = Modifier.size(24.dp)
                            )
                            
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-4).dp)
                                        .clip(CircleShape)
                                        .background(BentoWicketRed)
                                        .size(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$unreadCount",
                                        color = BentoWicketText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isTablet) {
                    NavigationBar(
                        containerColor = BentoTileBg,
                        contentColor = BentoTextLight,
                        modifier = Modifier.border(width = 1.dp, color = BentoBorder, shape = RoundedCornerShape(0.dp))
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "dashboard",
                            onClick = { activeTab = "dashboard" },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Bento") },
                            label = { Text("Bento", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BentoOnPrimary,
                                selectedTextColor = BentoPrimary,
                                indicatorColor = BentoPrimary,
                                unselectedIconColor = BentoTextMuted,
                                unselectedTextColor = BentoTextMuted
                            ),
                            modifier = Modifier.testTag("nav_bento")
                        )
                        NavigationBarItem(
                            selected = activeTab == "scoring",
                            onClick = { activeTab = "scoring" },
                            icon = { Icon(Icons.Default.Scoreboard, contentDescription = "Scoring") },
                            label = { Text("Scoring", fontSize = 10.sp) },
                            enabled = activeMatchId != null,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BentoOnPrimary,
                                selectedTextColor = BentoPrimary,
                                indicatorColor = BentoPrimary,
                                unselectedIconColor = BentoTextMuted,
                                unselectedTextColor = BentoTextMuted
                            ),
                            modifier = Modifier.testTag("nav_scoring")
                        )
                        NavigationBarItem(
                            selected = activeTab == "rosters",
                            onClick = { activeTab = "rosters" },
                            icon = { Icon(Icons.Default.Group, contentDescription = "Rosters") },
                            label = { Text("Rosters", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BentoOnPrimary,
                                selectedTextColor = BentoPrimary,
                                indicatorColor = BentoPrimary,
                                unselectedIconColor = BentoTextMuted,
                                unselectedTextColor = BentoTextMuted
                            ),
                            modifier = Modifier.testTag("nav_rosters")
                        )
                        NavigationBarItem(
                            selected = activeTab == "history",
                            onClick = { activeTab = "history" },
                            icon = { Icon(Icons.Default.History, contentDescription = "Matches") },
                            label = { Text("Matches", fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BentoOnPrimary,
                                selectedTextColor = BentoPrimary,
                                indicatorColor = BentoPrimary,
                                unselectedIconColor = BentoTextMuted,
                                unselectedTextColor = BentoTextMuted
                            ),
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            },
            containerColor = BentoDarkBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    "dashboard" -> BentoDashboardHubScreen(model, onNavigateToScoring = { activeTab = "scoring" })
                    "scoring" -> ActiveScoringConsoleInterventionScreen(model)
                    "rosters" -> TeamAndPlayerDatabaseScreen(model)
                    "history" -> MatchHistoryListCatalogScreen(model, onOpenMatch = { activeTab = "scoring" })
                }
            }
        }

        // Alert Control Desk dialog popup
        if (showNotificationCenter) {
            Dialog(
                onDismissRequest = {
                    // Mark all as read when window is dismissed
                    alerts.forEach { model.toggleAlertRead(it.id) }
                    showNotificationCenter = false
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                ) {
                    AdminNotificationDeskSheet(
                        model = model,
                        onDismiss = {
                            alerts.forEach { model.toggleAlertRead(it.id) }
                            showNotificationCenter = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationRailIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val containerC = if (selected) BentoPrimary else Color.Transparent
    val contentC = if (selected) BentoOnPrimary else if (enabled) BentoTextLight else BentoTextMuted.copy(alpha = 0.4f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect() }
            .padding(vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerC),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentC,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) BentoPrimary else if (enabled) BentoTextMuted else BentoTextMuted.copy(alpha = 0.4f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}


// ----------------------------------------------------
// SCREEN 1: BENTO DASHBOARD HUB
// ----------------------------------------------------
@Composable
fun BentoDashboardHubScreen(model: CricketViewModel, onNavigateToScoring: () -> Unit) {
    val teams by model.allTeams.collectAsStateWithLifecycle()
    val matches by model.allMatches.collectAsStateWithLifecycle()
    val activeMatchId by model.activeMatchId.collectAsStateWithLifecycle()
    val activeMatch by model.activeMatch.collectAsStateWithLifecycle()
    val ballEvents by model.activeBallEvents.collectAsStateWithLifecycle()
    val users by model.allUsers.collectAsStateWithLifecycle()

    var showSetupMatchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFormatFilter by remember { mutableStateOf("ALL") }
    var includeAllMatches by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Applet Banner Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "FCC ADMINISTRATIVE CONSOLE",
                        fontSize = 12.sp,
                        color = BentoPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "System Monitor & Scoring Intervention",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = BentoTextLight
                    )
                }

                IconButton(
                    onClick = { model.logout() },
                    modifier = Modifier
                        .border(1.dp, BentoBorder, CircleShape)
                        .background(BentoTileBg)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = BentoWicketRed)
                }
            }
        }

        // The Mosaic Grid Block
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row of quick statistics cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stat Block 1: Teams
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BentoTileBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Icon(Icons.Default.Group, contentDescription = "Teams", tint = BentoSecondary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = "${teams.size}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BentoTextLight)
                            Text(text = "Registered Roster Teams", fontSize = 12.sp, color = BentoTextMuted)
                        }
                    }

                    // Stat Block 2: Matches Count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BentoTileBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Icon(Icons.Default.SportsBasketball, contentDescription = "Games", tint = BentoGold)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = "${matches.size}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BentoTextLight)
                            Text(text = "Total System Matches", fontSize = 12.sp, color = BentoTextMuted)
                        }
                    }

                    // Stat Block 3: Live matches status
                    val activeGamesCount = matches.count { it.status.startsWith("LIVE") }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (activeGamesCount > 0) BentoPrimaryContainer.copy(alpha = 0.2f) else BentoTileBg)
                            .border(1.dp, if (activeGamesCount > 0) BentoPrimary else BentoBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (activeGamesCount > 0) BentoGreenGrass else BentoTextMuted)
                                    .size(10.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(text = "$activeGamesCount", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BentoTextLight)
                            Text(text = "Ongoing Live Games", fontSize = 12.sp, color = BentoTextMuted)
                        }
                    }
                }
            }
        }

        // Search & Filter Active Matches Component
        item {
            val activeMatches = matches.filter { it.status.startsWith("LIVE") }
            val targetMatchesList = if (includeAllMatches) matches else activeMatches
            val finalSearchResults = targetMatchesList.filter { match ->
                val queryMatches = if (searchQuery.isBlank()) {
                    true
                } else {
                    match.id.toString() == searchQuery.trim() ||
                    match.format.contains(searchQuery, ignoreCase = true) ||
                    match.teamAName.contains(searchQuery, ignoreCase = true) ||
                    match.teamBName.contains(searchQuery, ignoreCase = true)
                }
                val formatMatches = if (selectedFormatFilter == "ALL") {
                    true
                } else {
                    match.format.equals(selectedFormatFilter, ignoreCase = true)
                }
                queryMatches && formatMatches
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Title and badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BentoPrimaryContainer)
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = BentoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ACTIVE MATCH LOOKUP DESK",
                                    fontSize = 11.sp,
                                    color = BentoPrimary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = "Search & Filter Live Scoring Feeds",
                                    fontSize = 15.sp,
                                    color = BentoTextLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BentoHighlight)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${finalSearchResults.size} Found",
                                color = BentoTextLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by Tournament, Team, or Match ID...", fontSize = 12.sp, color = BentoTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("search_active_matches"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = BentoTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = BentoTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoPrimary,
                            unfocusedBorderColor = BentoBorder,
                            focusedTextColor = BentoTextLight,
                            unfocusedTextColor = BentoTextLight,
                            focusedContainerColor = BentoCardBg,
                            unfocusedContainerColor = BentoCardBg
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Filters Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val categories = listOf("ALL", "T10", "T20", "ODI", "Test")
                            items(categories) { category ->
                                val isSelected = selectedFormatFilter == category
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) BentoPrimary else BentoCardBg)
                                        .border(1.dp, if (isSelected) BentoPrimary else BentoBorder, RoundedCornerShape(8.dp))
                                        .clickable { selectedFormatFilter = category }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) BentoOnPrimary else BentoTextLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (includeAllMatches) BentoPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (includeAllMatches) BentoPrimary else BentoBorder, RoundedCornerShape(8.dp))
                                .clickable { includeAllMatches = !includeAllMatches }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter toggle",
                                tint = if (includeAllMatches) BentoPrimary else BentoTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Show All Records",
                                color = if (includeAllMatches) BentoPrimary else BentoTextLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Results
                    if (finalSearchResults.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Not found",
                                tint = BentoTextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No matches matching filters",
                                color = BentoTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            finalSearchResults.take(4).forEach { match ->
                                val isMatchLive = match.status.startsWith("LIVE")
                                val isMatchCompleted = match.status.equals("COMPLETED", ignoreCase = true)
                                val isMatchSelected = activeMatchId == match.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BentoCardBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isMatchSelected) BentoPrimary else BentoBorder.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            model.openExistingMatch(match.id)
                                            onNavigateToScoring()
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        when {
                                                            isMatchLive -> BentoWicketRed.copy(alpha = 0.2f)
                                                            isMatchCompleted -> BentoGreenGrass.copy(alpha = 0.2f)
                                                            else -> BentoHighlight
                                                        }
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = when {
                                                        isMatchLive -> "LIVE"
                                                        isMatchCompleted -> "FINISHED"
                                                        else -> "PRE-MATCH"
                                                    },
                                                    color = when {
                                                        isMatchLive -> BentoWicketRed
                                                        isMatchCompleted -> BentoGreenGrass
                                                        else -> BentoTextMuted
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(BentoHighlight)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ID: ${match.id}",
                                                    color = BentoTextLight,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = "${match.format} Series",
                                                color = BentoTextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "${match.teamAName} vs ${match.teamBName}",
                                            color = BentoTextLight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (match.winnerMessage != null) {
                                            Text(
                                                text = match.winnerMessage,
                                                color = BentoPrimary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            model.openExistingMatch(match.id)
                                            onNavigateToScoring()
                                        },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isMatchSelected) BentoPrimary else BentoHighlight)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Open match",
                                            tint = if (isMatchSelected) BentoOnPrimary else BentoTextLight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Match Master Widget
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BentoWicketRed)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("LIVE", fontSize = 10.sp, color = BentoWicketText, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Active Scoring & Intervention Desk",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextLight
                            )
                        }

                        if (activeMatchId != null && activeMatch != null) {
                            Button(
                                onClick = onNavigateToScoring,
                                colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Open Console", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { showSetupMatchDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("launch_match_dialog")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Launch", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Match", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (activeMatchId != null && activeMatch != null) {
                        val m = activeMatch!!
                        val summary = model.calculateMatchStats(m, ballEvents)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = m.format + " • Over Limit: " + m.totalOvers,
                                    fontSize = 11.sp,
                                    color = BentoTextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${m.teamAName} vs ${m.teamBName}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BentoTextLight
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val scoreText = if (m.currentInnings == 1) {
                                    "${summary.innings1Summary.totalRuns}/${summary.innings1Summary.totalWickets} in ${summary.innings1Summary.totalOvers} overs"
                                } else {
                                    val inn2 = summary.innings2Summary ?: summary.currentInningsSummary
                                    "${inn2.totalRuns}/${inn2.totalWickets} in ${inn2.totalOvers} overs"
                                }

                                Text(
                                    text = scoreText,
                                    color = BentoPrimary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Innings: ${if (m.currentInnings == 1) "1st Inning" else "2nd Inning"} • Batting: ${summary.currentInningsSummary.battingTeamName}",
                                    fontSize = 12.sp,
                                    color = BentoTextMuted
                                )
                            }

                            // Dynamic circular chart inside Bento showing current run rate visually
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(BentoCardBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val crr = summary.currentInningsSummary.runRate
                                    // Target CRR is e.g. 10.0 representing 100%
                                    val sweep = (crr / 15.0) * 360f
                                    drawArc(
                                        color = BentoPrimary,
                                        startAngle = -90f,
                                        sweepAngle = sweep.toFloat().coerceIn(0f, 360f),
                                        useCenter = false,
                                        style = Stroke(width = 8.dp.toPx())
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = String.format("%.2f", summary.currentInningsSummary.runRate),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoPrimary
                                    )
                                    Text(text = "R.Rate", fontSize = 10.sp, color = BentoTextMuted)
                                }
                            }
                        }
                    } else {
                        // Empty state inside the Bento block
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SportsCricket, contentDescription = "Bat", tint = BentoTextMuted.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "No Ongoing Match is Currently Active", color = BentoTextLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "Seed mock rosters and create a live scoreboard below.", color = BentoTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // Custom Visual Run Distribution Analytics Graphic (Canvas Custom Draw Widget)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "LIVE INNINGS ANALYTICS",
                        color = BentoSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Delivery Run Distribution",
                        color = BentoTextLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val eventList = ballEvents.filter { activeMatch?.let { m -> it.innings == m.currentInnings } ?: true }
                    val dots = eventList.count { it.runsScored == 0 && !it.isWide && !it.isNoBall }
                    val singles = eventList.count { it.runsScored == 1 && !it.isWide && !it.isNoBall }
                    val boundaries = eventList.count { (it.runsScored == 4 || it.runsScored == 6) && !it.isWide && !it.isNoBall }
                    val extras = eventList.count { it.isWide || it.isNoBall || it.isBye || it.isLegBye }

                    val totalBalls = eventList.size.coerceAtLeast(1)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        CanvasBar(value = dots, total = totalBalls, label = "Dots", color = BentoTextMuted)
                        CanvasBar(value = singles, total = totalBalls, label = "Runs", color = BentoPrimary)
                        CanvasBar(value = boundaries, total = totalBalls, label = "Fours/Six", color = BentoGold)
                        CanvasBar(value = extras, total = totalBalls, label = "Extras", color = BentoSecondary)
                    }
                }
            }
        }

        // Platform-wide Metrics and Visualizations
        item {
            PlatformAnalyticsSection(allMatches = matches, users = users)
        }

        // Administrative Activity Log Component
        item {
            val adminLogs by model.allAdminActivityLogs.collectAsStateWithLifecycle()
            var logSearchQuery by remember { mutableStateOf("") }
            var selectedTypeFilter by remember { mutableStateOf("ALL") }

            val filteredLogs = adminLogs.filter { log ->
                val matchesQuery = logSearchQuery.isBlank() || 
                    log.details.contains(logSearchQuery, ignoreCase = true) ||
                    log.actionType.contains(logSearchQuery, ignoreCase = true)
                
                val matchesType = selectedTypeFilter == "ALL" || log.actionType.equals(selectedTypeFilter, ignoreCase = true)
                
                matchesQuery && matchesType
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BentoGold.copy(alpha = 0.15f))
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Activity Log",
                                    tint = BentoGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ADMINISTRATIVE AUDIT LOG",
                                    fontSize = 11.sp,
                                    color = BentoGold,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = "Live scoring and setup changes",
                                    fontSize = 15.sp,
                                    color = BentoTextLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Clear Button
                        IconButton(
                            onClick = { model.clearAdminLogs() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BentoHighlight)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear logs",
                                tint = BentoWicketRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Log Quick Filter Row & Search Bar
                    OutlinedTextField(
                        value = logSearchQuery,
                        onValueChange = { logSearchQuery = it },
                        placeholder = { Text("Filter audit trail details...", fontSize = 12.sp, color = BentoTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("search_audit_trail"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = BentoTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (logSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { logSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear audit filter",
                                        tint = BentoTextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoGold,
                            unfocusedBorderColor = BentoBorder,
                            focusedTextColor = BentoTextLight,
                            unfocusedTextColor = BentoTextLight,
                            focusedContainerColor = BentoCardBg,
                            unfocusedContainerColor = BentoCardBg
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Type Category Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val types = listOf("ALL", "SCORE_OVERRIDE", "LAUNCH_MATCH", "REDO_UNDO", "TEAM_MANAGE")
                        items(types) { type ->
                            val isSelected = selectedTypeFilter == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) BentoGold else BentoCardBg)
                                    .border(1.dp, if (isSelected) BentoGold else BentoBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedTypeFilter = type }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = when(type) {
                                        "LAUNCH_MATCH" -> "Setup"
                                        "REDO_UNDO" -> "Undo"
                                        "TEAM_MANAGE" -> "Roster"
                                        "SCORE_OVERRIDE" -> "Override"
                                        else -> "All"
                                    },
                                    color = if (isSelected) Color.Black else BentoTextLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Audit Logs List
                    if (filteredLogs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No matching audit records in index",
                                color = BentoTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                        ) {
                            filteredLogs.forEach { log ->
                                val dateStr = java.text.SimpleDateFormat("hh:mm aa, dd MMM", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BentoCardBg.copy(alpha = 0.5f))
                                        .border(1.dp, BentoBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        when (log.actionType) {
                                                            "SCORE_OVERRIDE" -> BentoWicketRed.copy(alpha = 0.2f)
                                                            "LAUNCH_MATCH" -> BentoPrimary.copy(alpha = 0.2f)
                                                            "REDO_UNDO" -> BentoGold.copy(alpha = 0.2f)
                                                            else -> BentoSecondary.copy(alpha = 0.2f)
                                                        }
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = log.actionType,
                                                    color = when (log.actionType) {
                                                        "SCORE_OVERRIDE" -> BentoWicketRed
                                                        "LAUNCH_MATCH" -> BentoPrimary
                                                        "REDO_UNDO" -> BentoGold
                                                        else -> BentoSecondary
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }

                                            Text(
                                                text = dateStr,
                                                color = BentoTextMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = log.details,
                                            color = BentoTextLight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fast Setup / Seed Control Widget
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "ADMINISTRATION SYSTEM TOOLS",
                        color = BentoGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Roster Seeding & Sample Generator",
                        color = BentoTextLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Instantly restore pre-configured high-fidelity matches (India vs Australia rosters containing complete playing XIs).",
                        fontSize = 12.sp,
                        color = BentoTextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                model.addTeam("ICC Warriors", "#8E24AA")
                                model.addTeam("Bento Masters", "#0097A7")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoCardBg, contentColor = BentoPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate Custom Teams", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                // Close and reset matches
                                model.activeMatchId.value = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoCardBg, contentColor = BentoWicketRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Deactivate Live Session", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to configure and launch a cricket match.
    if (showSetupMatchDialog) {
        MatchSetupDialog(
            model = model,
            onDismiss = { showSetupMatchDialog = false },
            onLaunch = {
                showSetupMatchDialog = false
                onNavigateToScoring()
            }
        )
    }
}

@Composable
fun RowScope.CanvasBar(value: Int, total: Int, label: String, color: Color) {
    val faction = if (total == 0) 0f else (value.toFloat() / total.toFloat())

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        val heightMultiplier = (faction * 60f).coerceAtLeast(4f).dp

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(heightMultiplier)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "$value", fontSize = 12.sp, color = BentoTextLight, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 10.sp, color = BentoTextMuted)
    }
}


// ----------------------------------------------------
// SCENE 2: SCORING CONSOLE WITH SYSTEM INTERVENTION
// ----------------------------------------------------
@Composable
fun ActiveScoringConsoleInterventionScreen(model: CricketViewModel) {
    val activeMatch by model.activeMatch.collectAsStateWithLifecycle()
    val ballEvents by model.activeBallEvents.collectAsStateWithLifecycle()

    val currentOverBalls by model.currentOverBalls.collectAsStateWithLifecycle()

    val strikerId by model.strikerId.collectAsStateWithLifecycle()
    val nonStrikerId by model.nonStrikerId.collectAsStateWithLifecycle()
    val currentBowlerId by model.currentBowlerId.collectAsStateWithLifecycle()

    val teamAPlayingXI by model.teamAPlayingXI.collectAsStateWithLifecycle()
    val teamBPlayingXI by model.teamBPlayingXI.collectAsStateWithLifecycle()

    var showWicketDialog by remember { mutableStateOf(false) }
    var selectedInningsTab by remember { mutableIntStateOf(1) } // Display Scorecard tabs

    // Admin intervention variables
    var editingBallEvent by remember { mutableStateOf<BallEvent?>(null) }

    if (activeMatch == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Please load an active match from the Dashboard.", color = BentoTextMuted)
        }
        return
    }

    val match = activeMatch!!
    val summary = model.calculateMatchStats(match, ballEvents)

    // Sync showing innings tab with match status, but allow switching manually
    LaunchedEffect(match.currentInnings) {
        selectedInningsTab = match.currentInnings
    }

    // Resolve details
    val battingTeamId = if (match.currentInnings == 1) match.battingFirstTeamId else match.battingSecondTeamId
    val bowlingTeamId = if (match.currentInnings == 1) match.battingSecondTeamId else match.battingFirstTeamId

    val batXI = if (battingTeamId == match.teamAId) teamAPlayingXI else teamBPlayingXI
    val bowlXI = if (bowlingTeamId == match.teamAId) teamAPlayingXI else teamBPlayingXI

    val currentStriker = batXI.find { it.id == strikerId }
    val currentNonStriker = batXI.find { it.id == nonStrikerId }
    val currentBowlerName = bowlXI.find { it.id == currentBowlerId }?.name ?: "Opponent Bowler"

    // Responsive split layout (Left Pane is scoring control center, Right Pane is scorecard/log)
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp > 900

    Row(modifier = Modifier.fillMaxSize()) {
        // SCORING CONTROLS SUB-PANE
        Column(
            modifier = Modifier
                .weight(if (isTablet) 1.2f else 1f)
                .fillMaxHeight()
                .background(BentoDarkBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live score top bento panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${match.teamAName} vs ${match.teamBName}",
                            color = BentoTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BentoPrimaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "INNINGS ${match.currentInnings}",
                                fontSize = 10.sp,
                                color = BentoPrimary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val scoreRuns = if (match.currentInnings == 1) summary.innings1Summary.totalRuns else summary.innings2Summary?.totalRuns ?: 0
                    val scoreWkts = if (match.currentInnings == 1) summary.innings1Summary.totalWickets else summary.innings2Summary?.totalWickets ?: 0
                    val scoreOvs = if (match.currentInnings == 1) summary.innings1Summary.totalOvers else summary.innings2Summary?.totalOvers ?: 0.0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            val batTeamName = if (battingTeamId == match.teamAId) match.teamAName else match.teamBName
                            Text(text = batTeamName, fontSize = 12.sp, color = BentoTextMuted)
                            Text(
                                text = "$scoreRuns/$scoreWkts",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                color = BentoPrimary
                            )
                        }

                        Text(
                            text = "$scoreOvs / ${match.totalOvers} Overs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextLight,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (match.currentInnings == 2 && summary.innings2Summary != null) {
                        val remainingRuns = (summary.innings1Summary.totalRuns + 1 - summary.innings2Summary.totalRuns).coerceAtLeast(0)
                        val legalBallsBowled = ballEvents.filter { it.innings == 2 && !it.isWide && !it.isNoBall }.size
                        val ballsRemaining = ((match.totalOvers * 6) - legalBallsBowled).coerceAtLeast(0)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Target: ${summary.innings1Summary.totalRuns + 1} | Need $remainingRuns runs in $ballsRemaining balls",
                            color = BentoSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Striker & Non-Striker details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "CURRENT BATSMEN", fontSize = 11.sp, color = BentoTextMuted, fontWeight = FontWeight.Bold)

                    // Striker Row
                    val strEntry = summary.currentInningsSummary.batsmanScores.find { it.playerId == strikerId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★", color = BentoPrimary, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                text = currentStriker?.name ?: "Striker",
                                color = BentoTextLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (strEntry != null) {
                            Text(
                                text = "${strEntry.runs} (${strEntry.balls})",
                                color = BentoPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Non-Striker Row
                    val nonStrEntry = summary.currentInningsSummary.batsmanScores.find { it.playerId == nonStrikerId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(18.dp))
                            Text(
                                text = currentNonStriker?.name ?: "Non-Striker",
                                color = BentoTextMuted
                            )
                        }
                        if (nonStrEntry != null) {
                            Text(
                                text = "${nonStrEntry.runs} (${nonStrEntry.balls})",
                                color = BentoTextMuted
                            )
                        }
                    }

                    Divider(color = BentoBorder)

                    // Bowler Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "BOWLER", fontSize = 10.sp, color = BentoTextMuted, fontWeight = FontWeight.Bold)
                            Text(text = currentBowlerName, color = BentoSecondary, fontWeight = FontWeight.Bold)
                        }

                        // Bowler custom selection dropdown triggers
                        var showBowlerSelector by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { showBowlerSelector = true },
                            border = BorderStroke(1.dp, BentoBorder),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoPrimary)
                        ) {
                            Text("Change Bowler", fontSize = 11.sp)
                        }

                        if (showBowlerSelector) {
                            Dialog(onDismissRequest = { showBowlerSelector = false }) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BentoTileBg)
                                        .padding(20.dp)
                                ) {
                                    Column {
                                        Text("Select Bowler", color = BentoTextLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 240.dp)) {
                                            items(bowlXI) { player ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            model.changeActiveBowler(player.id)
                                                            showBowlerSelector = false
                                                        }
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(player.name, color = BentoTextLight)
                                                    Text(player.role, color = BentoTextMuted, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Current Over ball log strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "THIS OVER", fontSize = 11.sp, color = BentoTextMuted, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (currentOverBalls.isEmpty()) {
                            Text("No balls recorded yet", color = BentoTextMuted, fontSize = 11.sp)
                        } else {
                            currentOverBalls.takeLast(6).forEach { event ->
                                val badgeText = if (event.isWide) "Wd"
                                else if (event.isNoBall) "NB"
                                else if (event.wicketDismissedPlayerId != null) "W"
                                else if (event.isBye) "B"
                                else if (event.isLegBye) "LB"
                                else "${event.runsScored}"

                                val badgeColor = if (event.isWide || event.isNoBall) BentoSecondary
                                else if (event.wicketDismissedPlayerId != null) BentoWicketRed
                                else BentoPrimaryContainer

                                val textColor = if (event.isWide || event.isNoBall) BentoOnSecondary
                                else if (event.wicketDismissedPlayerId != null) BentoWicketText
                                else BentoPrimary

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Runs Keyboard Input Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "STANDARD DELIVERIES (RUNS)", fontSize = 11.sp, color = BentoTextMuted, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScoringQuickButton(text = "0", onClick = { model.recordBall(0) }, modifier = Modifier.weight(1f).testTag("btn_run_0"))
                        ScoringQuickButton(text = "1", onClick = { model.recordBall(1) }, modifier = Modifier.weight(1f).testTag("btn_run_1"))
                        ScoringQuickButton(text = "2", onClick = { model.recordBall(2) }, modifier = Modifier.weight(1f).testTag("btn_run_2"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScoringQuickButton(text = "3", onClick = { model.recordBall(3) }, modifier = Modifier.weight(1f).testTag("btn_run_3"))
                        ScoringQuickButton(text = "4", onClick = { model.recordBall(4) }, container = BentoPrimary, content = BentoOnPrimary, modifier = Modifier.weight(1f).testTag("btn_run_4"))
                        ScoringQuickButton(text = "6", onClick = { model.recordBall(6) }, container = BentoPrimary, content = BentoOnPrimary, modifier = Modifier.weight(1f).testTag("btn_run_6"))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "EXTRAS & SPECIAL DELIVERIES", fontSize = 11.sp, color = BentoTextMuted, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScoringExtraButton(text = "WD", onClick = { model.recordBall(0, isWide = true) }, modifier = Modifier.weight(1f).testTag("btn_wide"))
                        ScoringExtraButton(text = "NB", onClick = { model.recordBall(0, isNoBall = true) }, modifier = Modifier.weight(1f).testTag("btn_noball"))
                        ScoringExtraButton(text = "BYE", onClick = { model.recordBall(1, isBye = true) }, modifier = Modifier.weight(1f).testTag("btn_bye"))
                        ScoringExtraButton(text = "LB", onClick = { model.recordBall(1, isLegBye = true) }, modifier = Modifier.weight(1f).testTag("btn_legbye"))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showWicketDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoWicketRed, contentColor = BentoWicketText),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                                .testTag("btn_wicket_trigger")
                        ) {
                            Text("WICKET OUT", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { model.undoLastBall() },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoHighlight, contentColor = BentoWicketRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_undo")
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("UNDO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Secondary manual overrides
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { model.manualSwapStriker() }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Strike")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Swap Strike Batsmen", color = BentoSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // STATS & ADMIN OVERVIEW INTEGRATED INTERVENTION LOG (RIGHT PANE)
        Column(
            modifier = Modifier
                .weight(if (isTablet) 1.5f else 1.2f)
                .fillMaxHeight()
                .background(BentoDarkBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Header for scorecard/admin intervention selection
            var rightPaneTab by remember { mutableStateOf("intervene") } // "scorecard" or "intervene"

            TabRow(
                selectedTabIndex = if (rightPaneTab == "intervene") 0 else 1,
                containerColor = BentoTileBg,
                contentColor = BentoPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = rightPaneTab == "intervene",
                    onClick = { rightPaneTab = "intervene" },
                    text = { Text("Intervention Log", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_intervene")
                )
                Tab(
                    selected = rightPaneTab == "scorecard",
                    onClick = { rightPaneTab = "scorecard" },
                    text = { Text("M3 Scorecard", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_m3_scorecard")
                )
            }

            AnimatedContent(
                targetState = rightPaneTab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "ScoringRightView"
            ) { view ->
                if (view == "intervene") {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(BentoTileBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "OFFICIAL MATCH EVENT STREAM",
                            fontSize = 11.sp,
                            color = BentoSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scoring & Ball Corrections",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextLight
                        )
                        Text(
                            text = "Tap on any recorded ball delivery to modify runs, adjust extras, change wickets or purge the entry entirely.",
                            fontSize = 12.sp,
                            color = BentoTextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val inningsEvents = ballEvents.filter { it.innings == match.currentInnings }

                        if (inningsEvents.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Log", tint = BentoTextMuted.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Ball event stream is currently empty", color = BentoTextMuted, fontSize = 13.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(inningsEvents.asReversed()) { event ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { editingBallEvent = event }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Over indicator badge
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(BentoPrimaryContainer)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Over ${event.overNumber}.${event.ballNumberInOver}",
                                                        fontSize = 10.sp,
                                                        color = BentoPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))

                                                // Ball descriptive content
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${event.batsmanName} faced ${event.bowlerName}",
                                                        color = BentoTextLight,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val typeDetails = if (event.isWide) "Wide Extras"
                                                    else if (event.isNoBall) "No Ball Extras"
                                                    else if (event.isBye) "Bye runs"
                                                    else if (event.isLegBye) "Leg Bye runs"
                                                    else "Legal Delivery"

                                                    Text(
                                                        text = typeDetails,
                                                        color = BentoTextMuted,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            // Scored Run Value Label
                                            val runDisplay = if (event.wicketDismissedPlayerId != null) "OUT (W)"
                                            else if (event.isWide) "${event.runsScored} + WD"
                                            else if (event.isNoBall) "${event.runsScored} + NB"
                                            else "${event.runsScored} Runs"

                                            Text(
                                                text = runDisplay,
                                                color = if (event.wicketDismissedPlayerId != null) BentoWicketRed else BentoPrimary,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // FULL MATERIAL 3 SCORECARD PANEL
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(BentoTileBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Innings Toggle Tab row
                        TabRow(
                            selectedTabIndex = selectedInningsTab - 1,
                            containerColor = BentoCardBg,
                            contentColor = BentoPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 12.dp)
                        ) {
                            Tab(
                                selected = selectedInningsTab == 1,
                                onClick = { selectedInningsTab = 1 },
                                text = { Text("1st Innings (Scorecard)", fontSize = 11.sp) }
                            )
                            Tab(
                                selected = selectedInningsTab == 2,
                                onClick = { selectedInningsTab = 2 },
                                text = { Text("2nd Innings (Scorecard)", fontSize = 11.sp) }
                            )
                        }

                        val shownSummary = if (selectedInningsTab == 1) summary.innings1Summary else summary.innings2Summary

                        if (shownSummary == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Innings 2 has not started yet.", color = BentoTextMuted, fontSize = 13.sp)
                            }
                        } else {
                            // Display Scorecard table
                            Text(
                                text = "${shownSummary.battingTeamName} Batting Roster",
                                color = BentoPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Header row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BentoCardBg)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Batter", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                Text("R", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                Text("B", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                Text("4s", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                Text("6s", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                Text("SR", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                            }

                            // Items
                            shownSummary.batsmanScores.forEach { bScore ->
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(2f)) {
                                            Text(bScore.name, color = BentoTextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(bScore.outSummary, color = BentoTextMuted, fontSize = 10.sp)
                                        }
                                        Text("${bScore.runs}", color = BentoTextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                        Text("${bScore.balls}", color = BentoTextLight, fontSize = 12.sp, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                        Text("${bScore.fours}", color = BentoTextLight, fontSize = 12.sp, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                        Text("${bScore.sixes}", color = BentoTextLight, fontSize = 12.sp, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                                        Text(String.format("%.1f", bScore.strikeRate), color = BentoPrimary, fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }
                                    HorizontalDivider(color = BentoBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bowling section
                            Text(
                                text = "Bowling Statistics",
                                color = BentoSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Bowling Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BentoCardBg)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Bowler", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                Text("O", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                Text("R", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                Text("W", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                Text("Econ", color = BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                            }

                            if (shownSummary.bowlerScores.isEmpty()) {
                                Text("No deliveries bowled in this innings yet.", color = BentoTextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                shownSummary.bowlerScores.forEach { bowl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(bowl.name, color = BentoTextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                        Text("${bowl.overs}", color = BentoTextLight, fontSize = 12.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                        Text("${bowl.runsConceded}", color = BentoTextLight, fontSize = 12.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                        Text("${bowl.wickets}", color = BentoWicketRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                        Text(String.format("%.2f", bowl.economy), color = BentoSecondary, fontSize = 12.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = BentoBorder.copy(alpha = 0.5f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            val activeEvents by model.activeBallEvents.collectAsStateWithLifecycle()
                            PlayerPerformanceVisualizer(
                                events = activeEvents,
                                shownSummary = shownSummary
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to trigger Wicket Type & Fall Information
    if (showWicketDialog) {
        WicketDismissalSelectionDialog(
            dismissedPlayerId1 = strikerId ?: -1,
            dismissedPlayerId2 = nonStrikerId ?: -1,
            batXI = batXI,
            onDismiss = { showWicketDialog = false },
            onConfirmDismissal = { dismissedId, type, nextBatsmanId ->
                showWicketDialog = false
                model.changeDismissedBatsman(dismissedId, type, nextBatsmanId)
            }
        )
    }

    // Admin scoring override dialog modal - Intervention portal!
    if (editingBallEvent != null) {
        ScoringOverrideInterventionDialog(
            event = editingBallEvent!!,
            onDismiss = { editingBallEvent = null },
            onApplyOverride = { updatedBall ->
                editingBallEvent = null
                model.recordBall(
                    runsScored = updatedBall.runsScored,
                    isWide = updatedBall.isWide,
                    isNoBall = updatedBall.isNoBall,
                    isBye = updatedBall.isBye,
                    isLegBye = updatedBall.isLegBye,
                    wicketType = updatedBall.wicketType,
                    wicketDismissedPlayerId = updatedBall.wicketDismissedPlayerId
                )
                model.logAdminAction(
                    actionType = "SCORE_OVERRIDE",
                    details = "Manual score override applied: Set Match #${updatedBall.matchId} (Innings #${updatedBall.innings}, Over #${updatedBall.overNumber}) delivery to ${updatedBall.runsScored} runs"
                )
            },
            onDeleteEvent = { targetId ->
                editingBallEvent = null
                // Trigger customized repo delete
                // and restore state sequence
                model.undoLastBall()
            }
        )
    }
}

@Composable
fun ScoringQuickButton(
    text: String,
    onClick: () -> Unit,
    container: Color = BentoCardBg,
    content: Color = BentoTextLight,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(44.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ScoringExtraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BentoHighlight, contentColor = BentoSecondary),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(40.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}


// ----------------------------------------------------
// SCENE 3: TEAM & PLAYER DATABASE MANAGEMENT
// ----------------------------------------------------
@Composable
fun TeamAndPlayerDatabaseScreen(model: CricketViewModel) {
    val teams by model.allTeams.collectAsStateWithLifecycle()
    var selectedTeam by remember { mutableStateOf<Team?>(null) }

    // Forms
    var newTeamName by remember { mutableStateOf("") }
    var newTeamColor by remember { mutableStateOf("#2196F3") }

    var newPlayerName by remember { mutableStateOf("") }
    var newPlayerRole by remember { mutableStateOf("Batsman") }

    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp > 760

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Teams Panel (Left)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(BentoTileBg)
                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Text(text = "FCC TEAMS RACK", fontSize = 11.sp, color = BentoPrimary, fontWeight = FontWeight.Bold)
            Text(text = "Roster Registry", fontSize = 20.sp, fontWeight = FontWeight.Black, color = BentoTextLight)
            Spacer(modifier = Modifier.height(14.dp))

            // Add Team forms
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTeamName,
                    onValueChange = { newTeamName = it },
                    placeholder = { Text("New Team Name", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BentoPrimary,
                        unfocusedBorderColor = BentoBorder,
                        focusedTextColor = BentoTextLight,
                        unfocusedTextColor = BentoTextLight
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick hex picker trigger circles
                val colorsList = listOf("#1E88E5", "#FDD835", "#E53935", "#43A047")
                IconButton(
                    onClick = {
                        val index = (colorsList.indexOf(newTeamColor) + 1) % colorsList.size
                        newTeamColor = colorsList[index]
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(newTeamColor)))
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Pick Color", tint = Color.Black)
                }

                Button(
                    onClick = {
                        if (newTeamName.isNotBlank()) {
                            model.addTeam(newTeamName, newTeamColor)
                            newTeamName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Teams list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(teams) { team ->
                    val colorObj = try {
                        Color(android.graphics.Color.parseColor(team.colorHex))
                    } catch (e: Exception) {
                        BentoPrimary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTeam?.id == team.id) BentoCardBg else Color.Transparent)
                            .border(1.dp, if (selectedTeam?.id == team.id) BentoPrimary else BentoBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { selectedTeam = team }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(colorObj)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = team.name, color = BentoTextLight, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { model.deleteTeam(team) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BentoWicketRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Players Panel (Right - adaptive if screen is wide, or displays inside dialog)
        if (isTablet || selectedTeam != null) {
            Column(
                modifier = Modifier
                    .weight(if (isTablet) 1.2f else 1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                if (selectedTeam == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a team on the left to see roster listings", color = BentoTextMuted)
                    }
                } else {
                    val team = selectedTeam!!
                    val teamPlayersFlow = model.getPlayersForTeamFlow(team.id).collectAsStateWithLifecycle(emptyList())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = team.name.uppercase() + " PLAYERS", fontSize = 11.sp, color = BentoSecondary, fontWeight = FontWeight.Bold)
                            Text(text = "Roster Pool", fontSize = 20.sp, fontWeight = FontWeight.Black, color = BentoTextLight)
                        }

                        if (!isTablet) {
                            TextButton(onClick = { selectedTeam = null }) {
                                Text("Close", color = BentoTextMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Add Player Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BentoCardBg)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPlayerName,
                            onValueChange = { newPlayerName = it },
                            placeholder = { Text("Enter Athlete Name", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoPrimary,
                                unfocusedBorderColor = BentoBorder,
                                focusedTextColor = BentoTextLight,
                                unfocusedTextColor = BentoTextLight
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Player Role Picker Dropdown Simulation
                            val roles = listOf("Batsman", "Bowler", "All-Rounder", "Wicket-Keeper")
                            roles.forEach { role ->
                                val selected = newPlayerRole == role
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) BentoPrimary else BentoHighlight)
                                        .clickable { newPlayerRole = role }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = role,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) BentoOnPrimary else BentoTextLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (newPlayerName.isNotBlank()) {
                                    model.addPlayer(team.id, newPlayerName, newPlayerRole)
                                    newPlayerName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Register In Roster", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Players list LazyColumn
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(teamPlayersFlow.value) { player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BentoCardBg.copy(alpha = 0.5f))
                                    .border(1.dp, BentoBorder.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = player.name, color = BentoTextLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = player.role, color = BentoTextMuted, fontSize = 11.sp)
                                }

                                IconButton(onClick = { model.deletePlayer(player) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Player", tint = BentoWicketRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ----------------------------------------------------
// SCENE 4: MATCH HISTORY CATALOG (Overseer center)
// ----------------------------------------------------
@Composable
fun MatchHistoryListCatalogScreen(model: CricketViewModel, onOpenMatch: () -> Unit) {
    val matches by model.allMatches.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "CRICKET CONSOLE RECORDS", fontSize = 11.sp, color = BentoSecondary, fontWeight = FontWeight.Bold)
        Text(text = "Historical & Live Match Archives", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BentoTextLight)

        if (matches.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SportsGolf, contentDescription = "No Matches Placed", tint = BentoTextMuted, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Match records saved inside system storage.", color = BentoTextMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(matches) { match ->
                    val isLive = match.status.startsWith("LIVE")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(BentoTileBg)
                            .border(1.dp, if (isLive) BentoPrimary else BentoBorder, RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isLive) BentoWicketRed else BentoHighlight)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = match.status,
                                            fontSize = 9.sp,
                                            color = if (isLive) BentoWicketText else BentoTextLight,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = match.format + " Series",
                                        fontSize = 11.sp,
                                        color = BentoTextMuted
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "${match.teamAName} vs ${match.teamBName}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BentoTextLight
                                )

                                if (match.winnerMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = match.winnerMessage,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BentoPrimary
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    model.openExistingMatch(match.id)
                                    onOpenMatch()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BentoCardBg, contentColor = BentoPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enter Control Panel", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


// ----------------------------------------------------
// POPUPS / DIALOG MODALS
// ----------------------------------------------------

@Composable
fun MatchSetupDialog(
    model: CricketViewModel,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit
) {
    val teams by model.allTeams.collectAsStateWithLifecycle()

    var step by remember { mutableIntStateOf(1) } // 1: team select, 2: format/toss/playing xi select

    var teamAId by remember { mutableStateOf<Long?>(null) }
    var teamBId by remember { mutableStateOf<Long?>(null) }

    var selectedFormat by remember { mutableStateOf("T20") }
    var inputCustomOvers by remember { mutableIntStateOf(5) }
    var tossWinnerId by remember { mutableStateOf<Long?>(null) }
    var tossDecision by remember { mutableStateOf("BAT") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BentoTileBg)
                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEW MATCH CREATION",
                        color = BentoPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BentoTextMuted)
                    }
                }

                if (step == 1) {
                    Text(
                        text = "Step 1: Select Two Teams",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextLight
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Team A (Home Squad):", color = BentoTextMuted, fontSize = 12.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                teams.forEach { team ->
                                    val isSel = teamAId == team.id
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) BentoPrimary else BentoCardBg)
                                            .clickable { teamAId = team.id }
                                            .padding(vertical = 10.dp, horizontal = 16.dp)
                                    ) {
                                        Text(team.name, color = if (isSel) BentoOnPrimary else BentoTextLight, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Team B (Away Squad):", color = BentoTextMuted, fontSize = 12.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                teams.filter { it.id != teamAId }.forEach { team ->
                                    val isSel = teamBId == team.id
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) BentoPrimary else BentoCardBg)
                                            .clickable { teamBId = team.id }
                                            .padding(vertical = 10.dp, horizontal = 16.dp)
                                    ) {
                                        Text(team.name, color = if (isSel) BentoOnPrimary else BentoTextLight, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (teamAId != null && teamBId != null) {
                                step = 2
                                model.startMatchSetup(teamAId!!, teamBId!!)
                                tossWinnerId = teamAId
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = teamAId != null && teamBId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("match_setup_next_step")
                    ) {
                        Text("Continue to Toss & Roster", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // STEP 2: FORMATS & TOSS CONFIG
                    Text(
                        text = "Step 2: Toss & Match Conditions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextLight
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Overs / Match Format Limit:", color = BentoTextMuted, fontSize = 12.sp)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            val formats = listOf("T10", "T20", "ODI", "Test", "Custom")
                            formats.forEach { fmt ->
                                val sel = selectedFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) BentoPrimary else BentoCardBg)
                                        .clickable {
                                            selectedFormat = fmt
                                            model.matchFormat.value = fmt
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                ) {
                                    Text(fmt, color = if (sel) BentoOnPrimary else BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (selectedFormat == "Custom") {
                            Slider(
                                value = inputCustomOvers.toFloat(),
                                onValueChange = {
                                    inputCustomOvers = it.toInt()
                                    model.customOvers.value = it.toInt()
                                },
                                valueRange = 1f..100f,
                                colors = SliderDefaults.colors(thumbColor = BentoPrimary, activeTrackColor = BentoPrimary)
                            )
                            Text("Overs count selected: $inputCustomOvers Overs", color = BentoTextLight, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Toss Setup
                        val teamAName = teams.find { it.id == teamAId }?.name ?: "Team A"
                        val teamBName = teams.find { it.id == teamBId }?.name ?: "Team B"

                        Text("Toss Winner Decision:", color = BentoTextMuted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    tossWinnerId = teamAId
                                    model.tossWinnerId.value = teamAId
                                }
                            ) {
                                RadioButton(
                                    selected = tossWinnerId == teamAId,
                                    onClick = {
                                        tossWinnerId = teamAId
                                        model.tossWinnerId.value = teamAId
                                    }
                                )
                                Text(teamAName, color = BentoTextLight, fontSize = 12.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    tossWinnerId = teamBId
                                    model.tossWinnerId.value = teamBId
                                }
                            ) {
                                RadioButton(
                                    selected = tossWinnerId == teamBId,
                                    onClick = {
                                        tossWinnerId = teamBId
                                        model.tossWinnerId.value = teamBId
                                    }
                                )
                                Text(teamBName, color = BentoTextLight, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Toss Decision Elect:", color = BentoTextMuted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    tossDecision = "BAT"
                                    model.tossDecision.value = "BAT"
                                }
                            ) {
                                RadioButton(selected = tossDecision == "BAT", onClick = { tossDecision = "BAT"; model.tossDecision.value = "BAT" })
                                Text("Choose BAT first", color = BentoTextLight, fontSize = 12.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    tossDecision = "BOWL"
                                    model.tossDecision.value = "BOWL"
                                }
                            ) {
                                RadioButton(selected = tossDecision == "BOWL", onClick = { tossDecision = "BOWL"; model.tossDecision.value = "BOWL" })
                                Text("Choose BOWL first", color = BentoTextLight, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { step = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoCardBg, contentColor = BentoPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }

                        Button(
                            onClick = {
                                model.createAndStartMatch()
                                onLaunch()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                            modifier = Modifier
                                .weight(2f)
                                .testTag("match_setup_launch_final")
                        ) {
                            Text("Spawn Scoreboard", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun WicketDismissalSelectionDialog(
    dismissedPlayerId1: Long,
    dismissedPlayerId2: Long,
    batXI: List<Player>,
    onDismiss: () -> Unit,
    onConfirmDismissal: (Long, String, Long) -> Unit
) {
    var outPlayerId by remember { mutableStateOf(dismissedPlayerId1) }
    var selectedType by remember { mutableStateOf("Bowled") }
    var nextPlayerId by remember { mutableStateOf<Long?>(null) }

    // Filter batters who are NOT yet dismissed or currently on strike
    // To simplify, we pick any roster batter not on strike or next on card
    val outTypes = listOf("Bowled", "Caught", "LBW", "Run Out", "Stumped", "Hit Wicket", "Retired")

    val activeBatters = listOf(dismissedPlayerId1, dismissedPlayerId2)
    val benchBatters = batXI.filter { it.id !in activeBatters }

    LaunchedEffect(benchBatters) {
        if (benchBatters.isNotEmpty() && nextPlayerId == null) {
            nextPlayerId = benchBatters.first().id
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BentoTileBg)
                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "REGISTER WICKET FALL",
                    color = BentoWicketRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Which Batsman was dismissed?",
                    color = BentoTextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val p1 = batXI.find { it.id == dismissedPlayerId1 }
                    val p2 = batXI.find { it.id == dismissedPlayerId2 }

                    if (p1 != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (outPlayerId == dismissedPlayerId1) BentoWicketRed else BentoCardBg)
                                .clickable { outPlayerId = dismissedPlayerId1 }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p1.name, color = if (outPlayerId == dismissedPlayerId1) BentoWicketText else BentoTextLight, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (p2 != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (outPlayerId == dismissedPlayerId2) BentoWicketRed else BentoCardBg)
                                .clickable { outPlayerId = dismissedPlayerId2 }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p2.name, color = if (outPlayerId == dismissedPlayerId2) BentoWicketText else BentoTextLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Dismissal Fall Type:",
                    color = BentoTextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        outTypes.forEach { type ->
                            val isSel = selectedType == type
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) BentoSecondary else BentoHighlight)
                                    .clickable { selectedType = type }
                                    .padding(vertical = 8.dp, horizontal = 14.dp)
                            ) {
                                Text(type, color = if (isSel) BentoOnSecondary else BentoTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Select Next Incoming Batsman:",
                    color = BentoTextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                if (benchBatters.isEmpty()) {
                    Text("No bench rosters left to bat. All Out imminent.", color = BentoWicketRed, fontSize = 12.sp)
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            benchBatters.forEach { p ->
                                val isSel = nextPlayerId == p.id
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) BentoPrimary else BentoCardBg)
                                        .clickable { nextPlayerId = p.id }
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                ) {
                                    Text(p.name, color = if (isSel) BentoOnPrimary else BentoTextLight, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoCardBg, contentColor = BentoTextLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onConfirmDismissal(
                                outPlayerId,
                                selectedType,
                                nextPlayerId ?: -1L
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoWicketRed, contentColor = BentoWicketText),
                        modifier = Modifier.weight(1.5f),
                        enabled = benchBatters.isEmpty() || nextPlayerId != null
                    ) {
                        Text("Confirm Wicket Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun ScoringOverrideInterventionDialog(
    event: BallEvent,
    onDismiss: () -> Unit,
    onApplyOverride: (BallEvent) -> Unit,
    onDeleteEvent: (Long) -> Unit
) {
    var overrideRuns by remember { mutableIntStateOf(event.runsScored) }
    var isWide by remember { mutableStateOf(event.isWide) }
    var isNoBall by remember { mutableStateOf(event.isNoBall) }
    var isBye by remember { mutableStateOf(event.isBye) }
    var isLegBye by remember { mutableStateOf(event.isLegBye) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BentoTileBg)
                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "ADMINISTRATIVE FORCE SCORES OVERRIDE",
                    color = BentoPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Correction Portal for Over ${event.overNumber}.${event.ballNumberInOver}",
                    color = BentoTextLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Editing play: ${event.batsmanName} vs ${event.bowlerName}",
                    fontSize = 12.sp,
                    color = BentoTextMuted
                )

                Divider(color = BentoBorder)

                // Runs adjustment row
                Text("Select Actual Scored Runs off Bat/Delivery:", color = BentoTextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val scoreOptions = listOf(0, 1, 2, 3, 4, 6)
                    scoreOptions.forEach { score ->
                        val isSel = overrideRuns == score
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) BentoPrimary else BentoCardBg)
                                .clickable { overrideRuns = score }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$score",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) BentoOnPrimary else BentoTextLight
                            )
                        }
                    }
                }

                // Extras flags Checklist
                Text("Toggle Correct Delivery Extras:", color = BentoTextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isWide = !isWide
                                if (isWide) { isNoBall = false; isBye = false; isLegBye = false }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isWide, onCheckedChange = {
                            isWide = it
                            if (it) { isNoBall = false; isBye = false; isLegBye = false }
                        })
                        Text("Wide ball delivery flag (Wd)", color = BentoTextLight, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isNoBall = !isNoBall
                                if (isNoBall) { isWide = false; isBye = false; isLegBye = false }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isNoBall, onCheckedChange = {
                            isNoBall = it
                            if (it) { isWide = false; isBye = false; isLegBye = false }
                        })
                        Text("No Ball penalty delivery flag (NB)", color = BentoTextLight, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isBye = !isBye
                                if (isBye) { isWide = false; isNoBall = false; isLegBye = false }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isBye, onCheckedChange = {
                            isBye = it
                            if (it) { isWide = false; isNoBall = false; isLegBye = false }
                        })
                        Text("Bye un-touched delivery runs (B)", color = BentoTextLight, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isLegBye = !isLegBye
                                if (isLegBye) { isWide = false; isNoBall = false; isBye = false }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isLegBye, onCheckedChange = {
                            isLegBye = it
                            if (it) { isWide = false; isNoBall = false; isBye = false }
                        })
                        Text("Leg Bye contact runs (LB)", color = BentoTextLight, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onDeleteEvent(event.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoWicketRed, contentColor = BentoWicketText),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Purge Ball", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onApplyOverride(
                                event.copy(
                                    runsScored = overrideRuns,
                                    isWide = isWide,
                                    isNoBall = isNoBall,
                                    isBye = isBye,
                                    isLegBye = isLegBye
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = BentoOnPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Apply Override", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ============================================================================
// PLATFORM VISUALIZATION AND METRICS DASHBOARD SECTION
// ============================================================================
@Composable
fun PlatformAnalyticsSection(
    allMatches: List<CricketMatch>,
    users: List<PlatformUser>
) {
    var chartType by remember { mutableStateOf("bars") } // "bars" or "line"
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BentoPrimaryContainer)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Analytics",
                    tint = BentoPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "PLATFORM MONITOR",
                    fontSize = 11.sp,
                    color = BentoPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Real-Time System Insights",
                    fontSize = 16.sp,
                    color = BentoTextLight,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Row of 2 Bento Tiles: Active Matches & Formats Popularity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bento Tile 1: Active Match Pulse
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                ActiveMatchesPulseWidget(allMatches)
            }

            // Bento Tile 2: Match Format Popularity
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoTileBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                MatchFormatPopularityWidget(allMatches)
            }
        }

        // Bento Tile 3: 30-Day Registrations Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BentoTileBg)
                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            UserRegistrationsWidget(users, chartType, onChartTypeChange = { chartType = it })
        }
    }
}

@Composable
fun ActiveMatchesPulseWidget(matches: List<CricketMatch>) {
    val activeMatches = matches.filter { it.status.startsWith("LIVE") }
    
    // Beacon pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACTIVE SESSIONS",
                color = BentoSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Box(
                modifier = Modifier.size(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (activeMatches.isNotEmpty()) BentoGreenGrass.copy(alpha = alpha) else BentoTextMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "${activeMatches.size}",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = BentoTextLight
        )
        Text(
            text = "Active Live Matches",
            fontSize = 11.sp,
            color = BentoTextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        HorizontalDivider(color = BentoBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        if (activeMatches.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected, 
                    contentDescription = "Radar", 
                    tint = BentoTextMuted.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Awaiting Live Feeds",
                    color = BentoTextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.height(72.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeMatches.forEach { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BentoCardBg)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${match.teamAName} vs ${match.teamBName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Format: ${match.format} • ${if (match.currentInnings == 1) "1st Inn" else "2nd Inn"}",
                                fontSize = 9.sp,
                                color = BentoTextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BentoWicketRed.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = BentoWicketRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchFormatPopularityWidget(matches: List<CricketMatch>) {
    val totalMatches = matches.size.coerceAtLeast(1)
    
    // Group matches by format
    val formats = listOf("T20", "T10", "ODI", "Test", "Custom")
    val formatCounts = formats.associateWith { format ->
        matches.count { it.format.equals(format, ignoreCase = true) }
    }
    
    val mostPopularFormat = formatCounts.maxByOrNull { it.value }?.key ?: "T20"
    val maxCount = formatCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    val formatColors = mapOf(
        "T20" to BentoPrimary,
        "T10" to BentoGold,
        "ODI" to BentoCyan,
        "Test" to BentoSecondary,
        "Custom" to BentoTertiary
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "MATCH FORMATS POPULARITY",
            color = BentoGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = mostPopularFormat,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = BentoTextLight
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "is most popular",
                fontSize = 11.sp,
                color = BentoTextMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            formats.forEach { format ->
                val count = formatCounts[format] ?: 0
                val progress = count.toFloat() / maxCount.toFloat()
                val color = formatColors[format] ?: BentoPrimary

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = format,
                        color = BentoTextLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(50.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(BentoCardBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0.01f, 1f))
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$count",
                        color = BentoTextLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UserRegistrationsWidget(
    users: List<PlatformUser>,
    chartType: String,
    onChartTypeChange: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    val dayMillis = 24 * 60 * 60 * 1000L
    
    // Compute last 30 days registrations
    val registrationsPerDay = IntArray(30)
    
    users.forEach { u ->
        val diff = now - u.registeredTimestamp
        if (diff >= 0) {
            val dayIndex = (diff / dayMillis).toInt()
            if (dayIndex in 0..29) {
                registrationsPerDay[29 - dayIndex]++
            }
        }
    }

    // Cumulative sum
    val cumulativeRegistrations = IntArray(30)
    var currentSum = users.size - registrationsPerDay.sum()
    for (i in 0 until 30) {
        currentSum += registrationsPerDay[i]
        cumulativeRegistrations[i] = currentSum
    }

    val totalSignups = registrationsPerDay.sum()
    val maxSingleDay = registrationsPerDay.maxOrNull()?.coerceAtLeast(1) ?: 1
    val averageSignup = String.format("%.1f", totalSignups.toFloat() / 30f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACQUISITION HUB",
                    color = BentoPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "New User Registrations (30 Days)",
                    color = BentoTextLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(BentoCardBg)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (chartType == "bars") BentoPrimary else Color.Transparent)
                        .clickable { onChartTypeChange("bars") }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Daily",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chartType == "bars") BentoOnPrimary else BentoTextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (chartType == "line") BentoPrimary else Color.Transparent)
                        .clickable { onChartTypeChange("line") }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Cumulative",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chartType == "line") BentoOnPrimary else BentoTextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Total Segments", fontSize = 10.sp, color = BentoTextMuted)
                Text("$totalSignups Users", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoTextLight)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily Average", fontSize = 10.sp, color = BentoTextMuted)
                Text("$averageSignup users/day", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Single Day Peak", fontSize = 10.sp, color = BentoTextMuted)
                Text("$maxSingleDay Users", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoGold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val paddingBottom = 20.dp.toPx()
                val chartHeight = height - paddingBottom
                val stepX = width / 29f

                val gridLines = 4
                for (g in 0..gridLines) {
                    val y = chartHeight * (g.toFloat() / gridLines.toFloat())
                    drawLine(
                        color = BentoBorder.copy(alpha = 0.2f),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (chartType == "bars") {
                    for (i in 0 until 30) {
                        val count = registrationsPerDay[i]
                        val barHeightFactor = count.toFloat() / maxSingleDay.toFloat()
                        val barHeight = (chartHeight * barHeightFactor).coerceAtLeast(4f)
                        
                        val x = i * stepX
                        val barWidth = (stepX * 0.7f).coerceAtLeast(4f)

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(BentoPrimary, BentoPrimaryContainer)
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(x + (stepX - barWidth)/2f, chartHeight - barHeight),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                } else {
                    val maxCumulative = cumulativeRegistrations.last().coerceAtLeast(1)
                    val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
                    
                    for (i in 0 until 30) {
                        val count = cumulativeRegistrations[i]
                        val fact = count.toFloat() / maxCumulative.toFloat()
                        val cy = chartHeight - (chartHeight * fact)
                        val cx = i * stepX
                        points.add(androidx.compose.ui.geometry.Offset(cx, cy))
                    }

                    val path = androidx.compose.ui.graphics.Path()
                    val fillPath = androidx.compose.ui.graphics.Path()
                    
                    if (points.isNotEmpty()) {
                        path.moveTo(points[0].x, points[0].y)
                        fillPath.moveTo(points[0].x, chartHeight)
                        fillPath.lineTo(points[0].x, points[0].y)

                        for (i in 1 until points.size) {
                            val pPrev = points[i - 1]
                            val pCurr = points[i]
                            val cpX1 = pPrev.x + (pCurr.x - pPrev.x) / 2f
                            val cpY1 = pPrev.y
                            val cpX2 = pPrev.x + (pCurr.x - pPrev.x) / 2f
                            val cpY2 = pCurr.y

                            path.cubicTo(cpX1, cpY1, cpX2, cpY2, pCurr.x, pCurr.y)
                            fillPath.cubicTo(cpX1, cpY1, cpX2, cpY2, pCurr.x, pCurr.y)
                        }
                        
                        fillPath.lineTo(points.last().x, chartHeight)
                        fillPath.lineTo(points[0].x, chartHeight)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(BentoPrimary.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )

                        drawPath(
                            path = path,
                            color = BentoPrimary,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("30 Days Ago", fontSize = 10.sp, color = BentoTextMuted)
            Text("15 Days Ago", fontSize = 10.sp, color = BentoTextMuted)
            Text("Today", fontSize = 10.sp, color = BentoPrimary, fontWeight = FontWeight.Bold)
        }
    }
}


// ============================================================================
// SYSTEM NOTIFICATION AND CONTROLS SECTION
// ============================================================================
@Composable
fun HeadsUpAlertBanner(
    alert: AdminAlert,
    onDismiss: () -> Unit
) {
    val icon = when (alert.type) {
        "Wicket Fall" -> Icons.Default.SportsCricket
        "Match End" -> Icons.Default.CheckCircle
        else -> Icons.Default.NotificationsActive
    }
    
    val iconColor = when (alert.type) {
        "Wicket Fall" -> BentoWicketRed
        "Match End" -> BentoGold
        else -> BentoPrimary
    }

    val bannerBorderColor = when (alert.type) {
        "Wicket Fall" -> BentoWicketRed.copy(alpha = 0.6f)
        "Match End" -> BentoGold.copy(alpha = 0.6f)
        else -> BentoPrimary.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BentoCardBg.copy(alpha = 0.95f))
            .border(1.dp, bannerBorderColor, RoundedCornerShape(16.dp))
            .clickable { onDismiss() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = alert.type,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.title,
                        color = iconColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BentoGreenGrass)
                            .size(6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = alert.message,
                    color = BentoTextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss Alert",
                    tint = BentoTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AdminNotificationDeskSheet(
    model: CricketViewModel,
    onDismiss: () -> Unit
) {
    val alerts by model.alertHistory.collectAsStateWithLifecycle()
    val soundOn by model.isSoundEnabled.collectAsStateWithLifecycle()
    val wicketOn by model.isWicketAlertEnabled.collectAsStateWithLifecycle()
    val matchOn by model.isMatchEndAlertEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .background(BentoDarkBg)
            .padding(24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Alert Command Center",
                    tint = BentoPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ALERT COMMAND DESK",
                        fontSize = 11.sp,
                        color = BentoPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "System Notifications & Sounds",
                        fontSize = 16.sp,
                        color = BentoTextLight,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = BentoTextMuted)
            }
        }

        HorizontalDivider(color = BentoBorder.copy(alpha = 0.3f))
        
        Spacer(modifier = Modifier.height(16.dp))

        // Preference Settings Row
        Text(
            text = "CHANNELS & OUTPUT CONTROLS",
            fontSize = 10.sp,
            color = BentoTextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Channel 1: Wickets
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .border(1.dp, if (wicketOn) BentoWicketRed.copy(alpha = 0.3f) else BentoBorder, RoundedCornerShape(16.dp))
                    .clickable { model.isWicketAlertEnabled.value = !wicketOn }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.SportsCricket,
                    contentDescription = "Wicket Alerts",
                    tint = if (wicketOn) BentoWicketRed else BentoTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Wickets", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BentoTextLight)
                Spacer(modifier = Modifier.height(4.dp))
                Text(if (wicketOn) "ENABLED" else "MUTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (wicketOn) BentoWicketRed else BentoTextMuted)
            }

            // Channel 2: Match Ends
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .border(1.dp, if (matchOn) BentoGold.copy(alpha = 0.3f) else BentoBorder, RoundedCornerShape(16.dp))
                    .clickable { model.isMatchEndAlertEnabled.value = !matchOn }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Match Alerts",
                    tint = if (matchOn) BentoGold else BentoTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Match Ends", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BentoTextLight)
                Spacer(modifier = Modifier.height(4.dp))
                Text(if (matchOn) "ENABLED" else "MUTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (matchOn) BentoGold else BentoTextMuted)
            }

            // Channel 3: Sound
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .border(1.dp, if (soundOn) BentoPrimary.copy(alpha = 0.3f) else BentoBorder, RoundedCornerShape(16.dp))
                    .clickable { model.isSoundEnabled.value = !soundOn }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (soundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Beep Sounds",
                    tint = if (soundOn) BentoPrimary else BentoTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Chime Sound", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BentoTextLight)
                Spacer(modifier = Modifier.height(4.dp))
                Text(if (soundOn) "ON" else "OFF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (soundOn) BentoPrimary else BentoTextMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Sandbox Trigger (Testing)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SANDBOX SIMULATION",
                fontSize = 10.sp,
                color = BentoTextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Text(
                text = "Test Alert Flow",
                fontSize = 10.sp,
                color = BentoPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    model.triggerAlert(
                        title = "CONSOLE TEST!",
                        message = "System diagnostic check OK. Alert pipeline functional.",
                        type = "System"
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    model.triggerAlert(
                        title = "WICKET FALL!",
                        message = "Arjun Sharma was dismissed (Caught) off the bowling of Nathan Ellis!",
                        type = "Wicket Fall"
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BentoWicketRed.copy(alpha = 0.2f), contentColor = BentoWicketRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Simulate Wicket", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    model.triggerAlert(
                        title = "MATCH COMPLETED!",
                        message = "India vs Australia finished: India won by 15 runs",
                        type = "Match End"
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BentoGold.copy(alpha = 0.2f), contentColor = BentoGold),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Simulate Match End", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Notification Log list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "ALERT LOG HISTORY (${alerts.size})",
                fontSize = 10.sp,
                color = BentoTextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (alerts.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    fontSize = 11.sp,
                    color = BentoWicketRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { model.clearAlertHistory() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "None",
                        tint = BentoTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No historic events captured yet.", color = BentoTextMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alerts) { alert ->
                    val color = when (alert.type) {
                        "Wicket Fall" -> BentoWicketRed
                        "Match End" -> BentoGold
                        else -> BentoPrimary
                    }
                    
                    val bulletIcon = when (alert.type) {
                        "Wicket Fall" -> Icons.Default.SportsCricket
                        "Match End" -> Icons.Default.CheckCircle
                        else -> Icons.Default.Notifications
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoDarkBg.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = bulletIcon,
                            contentDescription = alert.type,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(alert.title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                
                                val timeStr = android.text.format.DateFormat.format("hh:mm aa", alert.timestamp).toString()
                                Text(timeStr, color = BentoTextMuted, fontSize = 9.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(alert.message, color = BentoTextLight, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// PLAYER PERFORMANCE ANALYTICS SECTION
// ============================================================================
data class PlayerPerformancePoint(
    val index: Int,
    val xLabel: String,
    val yValue: Double,
    val tooltipDetails: String
)

@Composable
fun PlayerPerformanceVisualizer(
    events: List<BallEvent>,
    shownSummary: InningsScoreSummary
) {
    var selectedTab by remember { mutableStateOf("batsman") } // "batsman" or "bowler"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_performance_visualizer")
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = BentoGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PLAYER PERFORMANCE ANALYTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoGold,
                letterSpacing = 1.sp
            )
        }

        // Toggle segment chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            val isBatsman = selectedTab == "batsman"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBatsman) BentoPrimary else BentoCardBg)
                    .border(1.dp, if (isBatsman) BentoPrimary else BentoBorder, RoundedCornerShape(8.dp))
                    .clickable { selectedTab = "batsman" }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Batsman Strike Rate",
                    color = if (isBatsman) Color.Black else BentoTextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val isBowler = selectedTab == "bowler"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBowler) BentoSecondary else BentoCardBg)
                    .border(1.dp, if (isBowler) BentoSecondary else BentoBorder, RoundedCornerShape(8.dp))
                    .clickable { selectedTab = "bowler" }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Bowler Economy",
                    color = if (isBowler) Color.Black else BentoTextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (selectedTab == "batsman") {
            // Batsmen List & Progression Chart
            val activeBatsmen = shownSummary.batsmanScores.filter { it.balls > 0 }
            if (activeBatsmen.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoCardBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No batting statistics compiled for this innings yet.", color = BentoTextMuted, fontSize = 11.sp)
                }
            } else {
                var selectedBatsmanId by remember(shownSummary.innings) { mutableStateOf(activeBatsmen.first().playerId) }
                val currentBatsman = activeBatsmen.find { it.playerId == selectedBatsmanId } ?: activeBatsmen.first()
                if (selectedBatsmanId == -1L || activeBatsmen.none { it.playerId == selectedBatsmanId }) {
                    selectedBatsmanId = currentBatsman.playerId
                }

                // Horizontal list of batsman selection chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(activeBatsmen) { bEntry ->
                        val isSel = bEntry.playerId == selectedBatsmanId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) BentoPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (isSel) BentoPrimary else BentoBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { selectedBatsmanId = bEntry.playerId }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = bEntry.name,
                                color = if (isSel) BentoPrimary else BentoTextLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Extract batsman events in chronological order for this batsman
                val batsmanEvents = events.filter { it.innings == shownSummary.innings && it.batsmanId == selectedBatsmanId }.sortedBy { it.id }
                
                var runsSoFar = 0
                var ballsSoFar = 0
                val pointsList = mutableListOf<PlayerPerformancePoint>()
                
                // Add initial origin point
                pointsList.add(
                    PlayerPerformancePoint(
                        index = 0,
                        xLabel = "0",
                        yValue = 0.0,
                        tooltipDetails = "Faced 0 balls, SR: 0.0%"
                    )
                )

                for (idx in batsmanEvents.indices) {
                    val ball = batsmanEvents[idx]
                    if (!ball.isWide) {
                        ballsSoFar++
                        if (!ball.isBye && !ball.isLegBye) {
                            runsSoFar += ball.runsScored
                        }
                        val sr = if (ballsSoFar == 0) 0.0 else (runsSoFar.toDouble() / ballsSoFar) * 100.0
                        pointsList.add(
                            PlayerPerformancePoint(
                                index = ballsSoFar,
                                xLabel = ballsSoFar.toString(),
                                yValue = sr,
                                tooltipDetails = "Ball $ballsSoFar (Over ${ball.overNumber + 1}.${ball.ballNumberInOver}): Score $runsSoFar runs off $ballsSoFar balls (SR: ${String.format("%.1f", sr)}%)"
                            )
                        )
                    }
                }

                if (pointsList.size <= 1) {
                    Text("Selected batsman has faced no legal deliveries yet.", color = BentoTextMuted, fontSize = 11.sp)
                } else {
                    PerformanceTrendChart(
                        points = pointsList,
                        lineColor = BentoPrimary,
                        labelSuffix = "%",
                        playerName = currentBatsman.name,
                        statSummaryLabel = "${currentBatsman.runs} Runs / ${currentBatsman.balls} Balls (SR: ${String.format("%.1f", currentBatsman.strikeRate)}%)"
                    )
                }
            }
        } else {
            // Bowler List & Progression Chart
            val activeBowlers = shownSummary.bowlerScores.filter { it.overs > 0.0 || it.runsConceded > 0 }
            if (activeBowlers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoCardBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No bowling statistics compiled for this innings yet.", color = BentoTextMuted, fontSize = 11.sp)
                }
            } else {
                var selectedBowlerId by remember(shownSummary.innings) { mutableStateOf(activeBowlers.first().playerId) }
                val currentBowler = activeBowlers.find { it.playerId == selectedBowlerId } ?: activeBowlers.first()
                if (selectedBowlerId == -1L || activeBowlers.none { it.playerId == selectedBowlerId }) {
                    selectedBowlerId = currentBowler.playerId
                }

                // Horizontal list of bowler selection chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(activeBowlers) { bEntry ->
                        val isSel = bEntry.playerId == selectedBowlerId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) BentoSecondary.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (isSel) BentoSecondary else BentoBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { selectedBowlerId = bEntry.playerId }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = bEntry.name,
                                color = if (isSel) BentoSecondary else BentoTextLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Extract bowler events in chronological order for this bowler
                val bowlerEvents = events.filter { it.innings == shownSummary.innings && it.bowlerId == selectedBowlerId }.sortedBy { it.id }
                
                var runsConcededSoFar = 0
                var legalBallsSoFar = 0
                val pointsList = mutableListOf<PlayerPerformancePoint>()
                
                pointsList.add(
                    PlayerPerformancePoint(
                        index = 0,
                        xLabel = "0.0",
                        yValue = 0.0,
                        tooltipDetails = "0.0 Overs bowled, Econ: 0.00"
                    )
                )

                for (idx in bowlerEvents.indices) {
                    val ball = bowlerEvents[idx]
                    // Add runs conceded
                    if (ball.isWide) {
                        runsConcededSoFar += 1 + ball.runsScored
                    } else if (ball.isNoBall) {
                        runsConcededSoFar += 1 + ball.runsScored
                    } else {
                        legalBallsSoFar++
                        if (!ball.isBye && !ball.isLegBye) {
                            runsConcededSoFar += ball.runsScored
                        }
                    }
                    
                    // Economy is based on legal balls bowled so far
                    val divs = if (legalBallsSoFar == 0) (if (ball.isWide || ball.isNoBall) 1 else 0) else legalBallsSoFar
                    val runningEcon = if (divs == 0) 0.0 else (runsConcededSoFar.toDouble() / divs) * 6.0
                    
                    pointsList.add(
                        PlayerPerformancePoint(
                            index = idx + 1,
                            xLabel = "${legalBallsSoFar / 6}.${legalBallsSoFar % 6}",
                            yValue = runningEcon,
                            tooltipDetails = "Delivery ${idx + 1} (Over ${ball.overNumber + 1}.${ball.ballNumberInOver}): Conceded $runsConcededSoFar runs (Econ: ${String.format("%.2f", runningEcon)} rpo)"
                        )
                    )
                }

                if (pointsList.size <= 1) {
                    Text("Selected bowler has bowled no deliveries yet.", color = BentoTextMuted, fontSize = 11.sp)
                } else {
                    PerformanceTrendChart(
                        points = pointsList,
                        lineColor = BentoSecondary,
                        labelSuffix = " rpo",
                        playerName = currentBowler.name,
                        statSummaryLabel = "${currentBowler.overs} Overs / ${currentBowler.runsConceded} Runs / ${currentBowler.wickets} Wickets (Econ: ${String.format("%.2f", currentBowler.economy)})"
                    )
                }
            }
        }
    }
}

@Composable
fun PerformanceTrendChart(
    points: List<PlayerPerformancePoint>,
    lineColor: Color,
    labelSuffix: String,
    playerName: String,
    statSummaryLabel: String
) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val activeIndex = selectedIndex ?: (points.size - 1)
    val activePoint = points.getOrNull(activeIndex) ?: points.last()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoCardBg.copy(alpha = 0.5f))
            .border(1.dp, BentoBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            // Touch Inspector Readout details
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = playerName,
                        color = BentoTextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statSummaryLabel,
                        color = BentoTextMuted,
                        fontSize = 10.sp
                    )
                }
                
                // Hovered point detail bubble style!
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(lineColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Val: ${String.format("%.1f", activePoint.yValue)}$labelSuffix",
                        color = lineColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Helpful dynamic instructions
            Text(
                text = activePoint.tooltipDetails,
                color = BentoPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Canvas Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val width = size.width
                    val height = size.height
                    val chartHeight = height - 15.dp.toPx()
                    
                    // Draw grid reference lines
                    val gridLinesCount = 3
                    for (g in 0..gridLinesCount) {
                        val gy = chartHeight * (g.toFloat() / gridLinesCount.toFloat())
                        drawLine(
                            color = BentoBorder.copy(alpha = 0.15f),
                            start = androidx.compose.ui.geometry.Offset(0f, gy),
                            end = androidx.compose.ui.geometry.Offset(width, gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Calculate scales
                    val maxVal = points.maxOf { it.yValue }.toFloat().coerceAtLeast(10f)
                    val stepMultiplier = if (points.size <= 1) width else width / (points.size - 1)

                    // Map points to device coordinates
                    val deviceCoords = points.indices.map { i ->
                        val cx = i * stepMultiplier
                        val ratio = if (maxVal == 0f) 0f else points[i].yValue.toFloat() / maxVal
                        val cy = chartHeight - (chartHeight * ratio)
                        androidx.compose.ui.geometry.Offset(cx, cy)
                    }

                    // Draw the smooth path and fill the baseline with sports gradient
                    if (deviceCoords.isNotEmpty()) {
                        val path = androidx.compose.ui.graphics.Path()
                        val fillPath = androidx.compose.ui.graphics.Path()
                        
                        path.moveTo(deviceCoords[0].x, deviceCoords[0].y)
                        fillPath.moveTo(deviceCoords[0].x, chartHeight)
                        fillPath.lineTo(deviceCoords[0].x, deviceCoords[0].y)

                        for (i in 1 until deviceCoords.size) {
                            val pPrev = deviceCoords[i - 1]
                            val pCurr = deviceCoords[i]
                            val cpX1 = pPrev.x + (pCurr.x - pPrev.x) / 2f
                            val cpY1 = pPrev.y
                            val cpX2 = pPrev.x + (pCurr.x - pPrev.x) / 2f
                            val cpY2 = pCurr.y

                            path.cubicTo(cpX1, cpY1, cpX2, cpY2, pCurr.x, pCurr.y)
                            fillPath.cubicTo(cpX1, cpY1, cpX2, cpY2, pCurr.x, pCurr.y)
                        }
                        
                        fillPath.lineTo(deviceCoords.last().x, chartHeight)
                        fillPath.lineTo(deviceCoords[0].x, chartHeight)
                        fillPath.close()

                        // Fill under line
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )

                        // Draw path stroke
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // Draw Touch Highlight Vertical Overlay Indicator Line
                        val selectedCoord = deviceCoords.getOrNull(activeIndex)
                        if (selectedCoord != null) {
                            drawLine(
                                color = lineColor.copy(alpha = 0.7f),
                                start = androidx.compose.ui.geometry.Offset(selectedCoord.x, 0f),
                                end = androidx.compose.ui.geometry.Offset(selectedCoord.x, chartHeight),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            // Double circle glowing dot
                            drawCircle(
                                color = lineColor,
                                radius = 6.dp.toPx(),
                                center = selectedCoord
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = selectedCoord
                            )
                        }
                    }
                }

                // Transparent touch layer covering the canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val stepX = if (points.size <= 1) this.size.width.toFloat() else this.size.width.toFloat() / (points.size - 1)
                                val idx = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                selectedIndex = idx
                            }
                        }
                        .pointerInput(points) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val stepX = if (points.size <= 1) this.size.width.toFloat() else this.size.width.toFloat() / (points.size - 1)
                                val idx = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                selectedIndex = idx
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start",
                    fontSize = 9.sp,
                    color = BentoTextMuted
                )
                Text(
                    text = "Drag or Tap chart to inspect progression ball-by-ball",
                    fontSize = 8.sp,
                    color = BentoTextMuted,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = if (points.size > 1) "Latest" else "End",
                    fontSize = 9.sp,
                    color = lineColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
