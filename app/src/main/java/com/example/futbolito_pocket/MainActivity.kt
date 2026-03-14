package com.example.futbolito_pocket

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.futbolito_pocket.ui.theme.Futbolito_PocketTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            Futbolito_PocketTheme {
                // Estado compartido para los controles
                var isGameRunning by remember { mutableStateOf(false) }
                var isSoundEnabled by remember { mutableStateOf(true) }
                var isMusicEnabled by remember { mutableStateOf(true) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { 
                        BottomControls(
                            isRunning = isGameRunning,
                            isSound = isSoundEnabled,
                            isMusic = isMusicEnabled,
                            onTogglePause = { isGameRunning = !isGameRunning },
                            onToggleSound = { isSoundEnabled = !isSoundEnabled },
                            onToggleMusic = { isMusicEnabled = !isMusicEnabled }
                        ) 
                    }
                ) { innerPadding ->
                    FutbolitoScreen(
                        modifier = Modifier.padding(innerPadding),
                        isGameRunning = isGameRunning,
                        onGameStatusChange = { isGameRunning = it }
                    )
                }
            }
        }
    }
}

data class Wall(val x: Float, val y: Float, val width: Float, val height: Float)
data class Coin(val x: Float, val y: Float, val collected: Boolean = false)

@Composable
fun FutbolitoScreen(
    modifier: Modifier = Modifier,
    isGameRunning: Boolean,
    onGameStatusChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var accelX by remember { mutableFloatStateOf(0f) }
    var accelY by remember { mutableFloatStateOf(0f) }

    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    accelX = -event.values[0]
                    accelY = event.values[1]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    
    var scoreTop by remember { mutableIntStateOf(0) }
    var scoreBottom by remember { mutableIntStateOf(0) }
    var coinsCount by remember { mutableIntStateOf(0) }
    var timeSeconds by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val ballRadius = with(density) { 8.dp.toPx() }
    val goalWidth = with(density) { 100.dp.toPx() }
    val goalHeight = with(density) { 15.dp.toPx() }
    val wallThickness = with(density) { 8.dp.toPx() }

    var coins by remember { mutableStateOf(listOf<Coin>()) }

    val walls = remember(screenSize) {
        if (screenSize.width == 0) emptyList()
        else {
            val w = screenSize.width.toFloat()
            val h = screenSize.height.toFloat()
            
            coins = listOf(
                Coin(w * 0.25f, h * 0.2f), Coin(w * 0.75f, h * 0.2f),
                Coin(w * 0.5f, h * 0.4f), Coin(w * 0.5f, h * 0.6f),
                Coin(w * 0.25f, h * 0.8f), Coin(w * 0.75f, h * 0.8f)
            )

            mutableListOf<Wall>().apply {
                add(Wall(0f, 0f, (w - goalWidth) / 2, goalHeight))
                add(Wall((w + goalWidth) / 2, 0f, (w - goalWidth) / 2, goalHeight))
                add(Wall(0f, h - goalHeight, (w - goalWidth) / 2, goalHeight))
                add(Wall((w + goalWidth) / 2, h - goalHeight, (w - goalWidth) / 2, goalHeight))

                add(Wall(w * 0.15f, h * 0.25f, w * 0.25f, wallThickness))
                add(Wall(w * 0.6f, h * 0.25f, w * 0.25f, wallThickness))
                add(Wall(w * 0.15f, h * 0.25f, wallThickness, h * 0.08f))
                add(Wall(w * 0.85f - wallThickness, h * 0.25f, wallThickness, h * 0.08f))

                add(Wall(w * 0.35f, h * 0.45f, w * 0.3f, wallThickness))
                add(Wall(w * 0.35f, h * 0.55f, w * 0.3f, wallThickness))
                
                add(Wall(w * 0.15f, h * 0.75f, w * 0.25f, wallThickness))
                add(Wall(w * 0.6f, h * 0.75f, w * 0.25f, wallThickness))
                add(Wall(w * 0.15f, h * 0.67f, wallThickness, h * 0.08f))
                add(Wall(w * 0.85f - wallThickness, h * 0.67f, wallThickness, h * 0.08f))
            }
        }
    }

    LaunchedEffect(isGameRunning) {
        while (isGameRunning) {
            delay(1000)
            timeSeconds++
        }
    }

    LaunchedEffect(screenSize, isGameRunning) {
        if (screenSize.width > 0 && ballX == 0f) {
            ballX = screenSize.width / 2f
            ballY = screenSize.height / 2f
        }
        
        if (!isGameRunning) return@LaunchedEffect

        while (isGameRunning) {
            val sensitivity = 0.65f
            val friction = 0.985f
            
            velX = (velX + accelX * sensitivity) * friction
            velY = (velY + accelY * sensitivity) * friction
            
            ballX += velX
            ballY += velY

            if (screenSize.width > 0) {
                coins = coins.map { coin ->
                    if (!coin.collected) {
                        val dx = ballX - coin.x
                        val dy = ballY - coin.y
                        if (sqrt(dx*dx + dy*dy) < ballRadius + 15f) {
                            coinsCount++
                            coin.copy(collected = true)
                        } else coin
                    } else coin
                }

                walls.forEach { wall ->
                    val ballRect = Rect(ballX - ballRadius, ballY - ballRadius, ballX + ballRadius, ballY + ballRadius)
                    val wallRect = Rect(wall.x, wall.y, wall.x + wall.width, wall.y + wall.height)
                    
                    if (ballRect.overlaps(wallRect)) {
                        val overlapX = (ballX - (wall.x + wall.width / 2))
                        val overlapY = (ballY - (wall.y + wall.height / 2))
                        
                        if (abs(overlapX) / wall.width > abs(overlapY) / wall.height) {
                            velX *= -0.55f
                            ballX += if (overlapX > 0) 7f else -7f
                        } else {
                            velY *= -0.55f
                            ballY += if (overlapY > 0) 7f else -7f
                        }
                    }
                }

                val goalStart = (screenSize.width - goalWidth) / 2
                val goalEnd = (screenSize.width + goalWidth) / 2

                if (ballY < ballRadius) {
                    if (ballX in goalStart..goalEnd) {
                        scoreBottom++
                        onGameStatusChange(false)
                        ballX = screenSize.width / 2f; ballY = screenSize.height / 2f; velX = 0f; velY = 0f
                    } else { ballY = ballRadius; velY *= -0.5f }
                } else if (ballY > screenSize.height - ballRadius) {
                    if (ballX in goalStart..goalEnd) {
                        scoreTop++
                        onGameStatusChange(false)
                        ballX = screenSize.width / 2f; ballY = screenSize.height / 2f; velX = 0f; velY = 0f
                    } else { ballY = screenSize.height - ballRadius; velY *= -0.5f }
                }

                if (ballX < ballRadius) { ballX = ballRadius; velX *= -0.5f }
                else if (ballX > screenSize.width - ballRadius) { ballX = screenSize.width - ballRadius; velX *= -0.5f }
            }
            delay(16)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreItem("Time", timeSeconds.toString().padStart(2, '0'), Color.Yellow)
            ScoreItem("Home - Visitors", "$scoreBottom - $scoreTop", Color.White)
            ScoreItem("Coins", coinsCount.toString().padStart(3, '0'), Color.Cyan)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
                    )
                )
                .onSizeChanged { screenSize = it }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                drawRect(Color.White.copy(alpha = 0.2f), style = Stroke(width = 3f))
                drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, canvasHeight / 2), Offset(canvasWidth, canvasHeight / 2), strokeWidth = 3f)
                drawCircle(Color.White.copy(alpha = 0.2f), radius = 120f, center = Offset(canvasWidth/2, canvasHeight/2), style = Stroke(width = 3f))

                coins.forEach { coin ->
                    if (!coin.collected) {
                        drawCircle(Color.Yellow, radius = 12f, center = Offset(coin.x, coin.y))
                        drawCircle(Color.White, radius = 6f, center = Offset(coin.x, coin.y), style = Stroke(width = 2f))
                    }
                }

                walls.forEach { wall ->
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(wall.x, wall.y),
                        size = Size(wall.width, wall.height),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }

                val gs = (canvasWidth - goalWidth) / 2
                drawRect(Color.Yellow, Offset(gs, 0f), Size(goalWidth, 6f))
                drawRect(Color.Yellow, Offset(gs, canvasHeight - 6f), Size(goalWidth, 6f))

                drawCircle(Color.White, radius = ballRadius, center = Offset(ballX, ballY))
            }

            if (!isGameRunning) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Futbolito Pocket", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onGameStatusChange(true) }) {
                            Text(if (timeSeconds == 0) "JUGAR" else "CONTINUAR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BottomControls(
    isRunning: Boolean,
    isSound: Boolean,
    isMusic: Boolean,
    onTogglePause: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit
) {
    Surface(
        color = Color.DarkGray,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() 
                .padding(bottom = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleSound) { 
                Text(if (isSound) "🔊" else "🔇", fontSize = 24.sp) 
            }
            IconButton(onClick = onToggleMusic) { 
                Text(if (isMusic) "🎵" else "🔇", fontSize = 24.sp) 
            }
            IconButton(onClick = onTogglePause) { 
                Text(if (isRunning) "⏸️" else "▶️", fontSize = 24.sp) 
            }
        }
    }
}
