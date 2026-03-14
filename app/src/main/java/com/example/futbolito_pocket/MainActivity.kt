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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            Futbolito_PocketTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FutbolitoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class Wall(val x: Float, val y: Float, val width: Float, val height: Float)

@Composable
fun FutbolitoScreen(modifier: Modifier = Modifier) {
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
        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    
    var scoreTop by remember { mutableIntStateOf(0) }
    var scoreBottom by remember { mutableIntStateOf(0) }
    
    var isGameRunning by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val ballRadius = with(density) { 8.dp.toPx() } // Un poco más pequeña para el laberinto
    val goalWidth = with(density) { 100.dp.toPx() }
    val goalHeight = with(density) { 12.dp.toPx() }
    val wallThickness = with(density) { 8.dp.toPx() }

    // Definición de obstáculos Complejos (Paso 2 Avanzado)
    val walls = remember(screenSize) {
        if (screenSize.width == 0) emptyList()
        else {
            val w = screenSize.width.toFloat()
            val h = screenSize.height.toFloat()
            val list = mutableListOf<Wall>()

            // Paredes de portería
            list.add(Wall(0f, 0f, (w - goalWidth) / 2, goalHeight))
            list.add(Wall((w + goalWidth) / 2, 0f, (w - goalWidth) / 2, goalHeight))
            list.add(Wall(0f, h - goalHeight, (w - goalWidth) / 2, goalHeight))
            list.add(Wall((w + goalWidth) / 2, h - goalHeight, (w - goalWidth) / 2, goalHeight))

            // Laberinto Superior
            list.add(Wall(w * 0.15f, h * 0.15f, w * 0.3f, wallThickness)) // Horizontal Izq
            list.add(Wall(w * 0.55f, h * 0.15f, w * 0.3f, wallThickness)) // Horizontal Der
            list.add(Wall(w * 0.15f, h * 0.15f, wallThickness, h * 0.1f)) // Vertical Izq
            list.add(Wall(w * 0.85f - wallThickness, h * 0.15f, wallThickness, h * 0.1f)) // Vertical Der

            // Área Central
            list.add(Wall(w * 0.35f, h * 0.3f, w * 0.3f, wallThickness)) // Bloque central arriba
            list.add(Wall(w * 0.2f, h * 0.4f, wallThickness, h * 0.2f))  // Vertical lateral izq
            list.add(Wall(w * 0.8f - wallThickness, h * 0.4f, wallThickness, h * 0.2f)) // Vertical lateral der
            
            // Circunferencia central (simulada con 4 bloques)
            list.add(Wall(w * 0.45f, h * 0.45f, w * 0.1f, wallThickness))
            list.add(Wall(w * 0.45f, h * 0.55f, w * 0.1f, wallThickness))
            
            // Laberinto Inferior (Simétrico)
            list.add(Wall(w * 0.35f, h * 0.7f, w * 0.3f, wallThickness)) // Bloque central abajo
            list.add(Wall(w * 0.15f, h * 0.75f, w * 0.3f, wallThickness)) 
            list.add(Wall(w * 0.55f, h * 0.75f, w * 0.3f, wallThickness))
            list.add(Wall(w * 0.15f, h * 0.75f - h * 0.1f, wallThickness, h * 0.1f)) 
            list.add(Wall(w * 0.85f - wallThickness, h * 0.75f - h * 0.1f, wallThickness, h * 0.1f))

            list
        }
    }

    LaunchedEffect(screenSize, isGameRunning) {
        if (screenSize.width > 0 && ballX == 0f) {
            ballX = screenSize.width / 2f
            ballY = screenSize.height / 2f
        }
        
        if (!isGameRunning) return@LaunchedEffect

        while (isGameRunning) {
            val sensitivity = 0.6f
            val friction = 0.98f
            
            velX = (velX + accelX * sensitivity) * friction
            velY = (velY + accelY * sensitivity) * friction
            
            ballX += velX
            ballY += velY

            if (screenSize.width > 0) {
                // Colisión con obstáculos
                walls.forEach { wall ->
                    val ballRect = Rect(ballX - ballRadius, ballY - ballRadius, ballX + ballRadius, ballY + ballRadius)
                    val wallRect = Rect(wall.x, wall.y, wall.x + wall.width, wall.y + wall.height)
                    
                    if (ballRect.overlaps(wallRect)) {
                        val overlapX = (ballX - (wall.x + wall.width / 2))
                        val overlapY = (ballY - (wall.y + wall.height / 2))
                        
                        if (abs(overlapX) / wall.width > abs(overlapY) / wall.height) {
                            velX *= -0.6f
                            ballX += if (overlapX > 0) 6f else -6f
                        } else {
                            velY *= -0.6f
                            ballY += if (overlapY > 0) 6f else -6f
                        }
                    }
                }

                // Lógica de Porterías
                val goalStart = (screenSize.width - goalWidth) / 2
                val goalEnd = (screenSize.width + goalWidth) / 2

                if (ballY < ballRadius) {
                    if (ballX in goalStart..goalEnd) {
                        scoreBottom++
                        isGameRunning = false
                        ballX = screenSize.width / 2f
                        ballY = screenSize.height / 2f
                        velX = 0f; velY = 0f
                    } else {
                        ballY = ballRadius
                        velY *= -0.5f
                    }
                } else if (ballY > screenSize.height - ballRadius) {
                    if (ballX in goalStart..goalEnd) {
                        scoreTop++
                        isGameRunning = false
                        ballX = screenSize.width / 2f
                        ballY = screenSize.height / 2f
                        velX = 0f; velY = 0f
                    } else {
                        ballY = screenSize.height - ballRadius
                        velY *= -0.5f
                    }
                }

                // Bordes laterales
                if (ballX < ballRadius) {
                    ballX = ballRadius
                    velX *= -0.5f
                } else if (ballX > screenSize.width - ballRadius) {
                    ballX = screenSize.width - ballRadius
                    velX *= -0.5f
                }
            }
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20)) // Verde más oscuro para contraste
            .onSizeChanged { screenSize = it }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Líneas estéticas de la cancha
            drawRect(Color.White.copy(alpha = 0.3f), style = Stroke(width = 2f))
            drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, canvasHeight / 2), Offset(canvasWidth, canvasHeight / 2), strokeWidth = 2f)
            drawCircle(Color.White.copy(alpha = 0.3f), radius = 100f, center = Offset(canvasWidth/2, canvasHeight/2), style = Stroke(width = 2f))
            
            // Dibujar Obstáculos con estilo (Bordes redondeados)
            walls.forEach { wall ->
                drawRoundRect(
                    color = Color.LightGray,
                    topLeft = Offset(wall.x, wall.y),
                    size = Size(wall.width, wall.height),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                // Borde de los obstáculos
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(wall.x, wall.y),
                    size = Size(wall.width, wall.height),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 2f)
                )
            }

            // Porterías resaltadas
            val goalStart = (canvasWidth - goalWidth) / 2
            drawRect(Color.Yellow.copy(alpha = 0.8f), Offset(goalStart, 0f), Size(goalWidth, 8f))
            drawRect(Color.Yellow.copy(alpha = 0.8f), Offset(goalStart, canvasHeight - 8f), Size(goalWidth, 8f))

            // Pelota con efecto de brillo
            drawCircle(
                color = Color.White,
                radius = ballRadius,
                center = Offset(ballX, ballY)
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = ballRadius + 4f,
                center = Offset(ballX, ballY),
                style = Stroke(width = 2f)
            )
        }

        // Interfaz de Usuario mejorada
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("V: $scoreTop", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("L: $scoreBottom", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            
            if (!isGameRunning) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { isGameRunning = true },
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .height(60.dp)
                        .width(200.dp)
                ) {
                    Text(
                        if (scoreTop == 0 && scoreBottom == 0) "INICIAR PARTIDO" else "SIGUIENTE RONDA",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
