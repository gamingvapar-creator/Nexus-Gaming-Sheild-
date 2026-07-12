package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Pink
import com.example.ui.theme.Purple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class InstalledApp(val name: String, val packageName: String)

suspend fun getInstalledApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    resolveInfos.map {
        InstalledApp(
            name = it.loadLabel(pm).toString(),
            packageName = it.activityInfo.packageName
        )
    }.distinctBy { it.packageName }.sortedBy { it.name }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { innerPadding ->
                    GamingShieldScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun NetworkTrafficMonitor(isShieldActive: Boolean) {
    var trafficData by remember { mutableStateOf(List(40) { 0f }) }
    var currentSpeed by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        while (true) {
            delay(1000)
            val currentBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
            val bytesPerSec = (currentBytes - lastBytes).coerceAtLeast(0)
            lastBytes = currentBytes
            
            val mbps = bytesPerSec / 1024f / 1024f
            currentSpeed = mbps
            trafficData = (trafficData.drop(1) + mbps)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121214).copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Network Traffic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("%.2f MB/s".format(currentSpeed), color = Pink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val maxData = trafficData.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val canvasWidth = size.width
            val canvasHeight = size.height
            val stepX = canvasWidth / (trafficData.size - 1).coerceAtLeast(1)
            
            val path = Path().apply {
                trafficData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = canvasHeight - (value / maxData * canvasHeight)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            
            drawPath(
                path = path,
                color = Purple,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f, cap = StrokeCap.Round)
            )
            
            val fillPath = Path().apply {
                addPath(path)
                lineTo(canvasWidth, canvasHeight)
                lineTo(0f, canvasHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Purple.copy(alpha = 0.3f), Color.Transparent)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamingShieldScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isShieldActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }
    
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var allowedApps by remember { mutableStateOf<List<InstalledApp?>>(listOf(null, null, null)) }
    var showAppSelectorForIndex by remember { mutableStateOf<Int?>(null) }
    var showBlockedApps by remember { mutableStateOf(false) }
    var isCallBlockEnabled by remember { mutableStateOf(false) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            isCallBlockEnabled = true
        } else {
            isCallBlockEnabled = false
            Toast.makeText(context, "Permissions required for call blocking", Toast.LENGTH_SHORT).show()
        }
    }

    val initialPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle initial permissions if needed
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initialPermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        installedApps = getInstalledApps(context)
        delay(300)
        isLoaded = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEXUS",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Subtitle
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(tween(1000)) + slideInVertically(tween(1000)) { it / 2 }
            ) {
                Text(
                    text = "GAMING SHIELD",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3 Cubes for Allowed Apps
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(tween(1200))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    for (i in 0..2) {
                        AppCube(
                            app = allowedApps[i],
                            onClick = {
                                showAppSelectorForIndex = i
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Allowed Network Apps",
                color = Color.Gray,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            AnimatedVisibility(
                visible = isLoaded,
                enter = fadeIn(tween(1400))
            ) {
                NetworkTrafficMonitor(isShieldActive = isShieldActive)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main Shield Button
            ShieldButton(
                isActive = isShieldActive,
                onClick = { isShieldActive = !isShieldActive }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status Panels (Glassmorphism)
            AnimatedVisibility(
                visible = isShieldActive,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 2 },
                exit = fadeOut(tween(500)) + slideOutVertically(tween(500)) { it / 2 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val allowedNames = allowedApps.filterNotNull().map { it.name }.joinToString(", ")
                    val subtitleText = if (allowedNames.isNotEmpty()) "Allowed: $allowedNames" else "No apps allowed"
                    GlassPanel(
                        icon = Icons.Default.WifiOff,
                        title = "Network Isolated",
                        subtitle = subtitleText,
                        onClick = { showBlockedApps = true }
                    )
                    GlassPanel(
                        icon = Icons.Default.Phone,
                        title = "Calls Blocked",
                        subtitle = if (isCallBlockEnabled) "Auto-rejecting calls" else "Call blocking disabled",
                        trailing = {
                            Switch(
                                checked = isCallBlockEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val hasReadPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                                        val hasAnswerPhoneCalls = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                                        
                                        if (hasReadPhoneState && hasAnswerPhoneCalls) {
                                            isCallBlockEnabled = true
                                        } else {
                                            callPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_PHONE_STATE,
                                                    Manifest.permission.ANSWER_PHONE_CALLS
                                                )
                                            )
                                        }
                                    } else {
                                        isCallBlockEnabled = false
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Purple,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
                    )
                }
            }
            
            if (!isShieldActive) {
                Text(
                    text = "System Ready. Tap to initialize.",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }

        // Slide-in Menu
        AnimatedVisibility(
            visible = showMenu,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF121214).copy(alpha = 0.95f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Settings", color = Color.White, fontWeight = FontWeight.Bold)
                    MenuRow(icon = Icons.Default.Security, text = "Target App: Custom")
                    MenuRow(icon = Icons.Default.Notifications, text = "Alert Preferences")
                }
            }
        }
    }
    
    if (showAppSelectorForIndex != null) {
        AppSelectorDialog(
            apps = installedApps,
            onDismiss = { showAppSelectorForIndex = null },
            onAppSelected = { app ->
                val newList = allowedApps.toMutableList()
                newList[showAppSelectorForIndex!!] = app
                allowedApps = newList
                showAppSelectorForIndex = null
            }
        )
    }
    
    if (showBlockedApps) {
        BlockedAppsDialog(
            installedApps = installedApps,
            allowedApps = allowedApps.filterNotNull(),
            onDismiss = { showBlockedApps = false }
        )
    }
}

@Composable
fun AppCube(app: InstalledApp?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121214).copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (app == null) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add App",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = app.name.take(1).uppercase(),
                    color = Purple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = app.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121214)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f)) {
            Text("Select App to Allow Network", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(apps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(app.name.take(1).uppercase(), color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(app.name, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsDialog(
    installedApps: List<InstalledApp>,
    allowedApps: List<InstalledApp>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121214)
    ) {
        val blockedApps = installedApps.filter { app -> allowedApps.none { it.packageName == app.packageName } }
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f)) {
            Text("Blocked Apps (${blockedApps.size})", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("These apps will not have network access while Shield is active.", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(blockedApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(app.name, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Pink, modifier = Modifier.size(20.dp))
        Text(text = text, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun GlassPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121214).copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Purple.copy(alpha = 0.2f), Pink.copy(alpha = 0.2f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Pink)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
fun ShieldButton(isActive: Boolean, onClick: () -> Unit) {
    val transition = updateTransition(targetState = isActive, label = "ShieldTransition")
    
    val scale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "Scale"
    ) { active -> if (active) 1.05f else 1f }

    val glowAlpha by transition.animateFloat(
        transitionSpec = { tween(1000) },
        label = "Glow"
    ) { active -> if (active) 0.6f else 0.1f }

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .scale(if (isActive) pulseScale * scale else scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Outer Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Pink.copy(alpha = glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width / 2
                        )
                    )
                }
        )

        // Inner Button Background
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isActive) listOf(Purple, Pink) else listOf(Color(0xFF1A1A1E), Color(0xFF1A1A1E))
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(listOf(Purple, Pink)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isActive) "ACTIVE" else "ENGAGE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
