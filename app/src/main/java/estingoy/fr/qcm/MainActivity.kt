package estingoy.fr.qcm

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val DEFAULT_ADMIN_PASSWORD = "QcmSsiap2026"
private const val PASSWORD_PREFS_NAME = "qcm_admin_prefs"
private const val PASSWORD_KEY = "admin_password"
private const val QUESTIONS_FILE_NAME = "questions.json"
private const val QUESTIONS_FORMAT = "qcm-questions-v2"
private const val QUESTIONS_PREFS_NAME = "qcm_questions_prefs"
private const val QUESTIONS_BASE_INITIALIZED_KEY = "questions_base_initialized_v1"
private const val QUIZ_SIZE = 20
private const val CSV_DELIMITER = ';'

// Personnalisation (apparence)
private const val APPEARANCE_PREFS = "qcm_appearance_prefs"
private const val KEY_ACCENT = "accent"
private const val KEY_BACKGROUND = "background"
private const val KEY_EMOJIS = "emojis"

private val SuccessGreen = Color(0xFF4CAF50)
private val ErrorRed = Color(0xFFE53935)

// Fonds possibles
private val WarmBackground = Color(0xFFEDE4D8)
private val WarmSurface = Color(0xFFF5EFE6)
private val WhiteBg = Color(0xFFFFFFFF)
private val WhiteSurface = Color(0xFFFAFAFA)
private val LightGrayBg = Color(0xFFE8E8E8)
private val LightGraySurface = Color(0xFFF0F0F0)

// Couleurs d'accent (primary)
private val AccentBlue = Color(0xFF1976D2)
private val AccentGreen = Color(0xFF2E7D32)
private val AccentOrange = Color(0xFFE65100)
private val AccentPurple = Color(0xFF7B1FA2)

private fun qcmColorScheme(accent: String, background: String) = lightColorScheme(
    primary = when (accent) {
        "green" -> AccentGreen
        "orange" -> AccentOrange
        "purple" -> AccentPurple
        else -> AccentBlue
    },
    surface = when (background) {
        "white" -> WhiteSurface
        "gray" -> LightGraySurface
        else -> WarmSurface
    },
    surfaceVariant = when (background) {
        "white" -> Color(0xFFEEEEEE)
        "gray" -> Color(0xFFE0E0E0)
        else -> Color(0xFFE5DCCE)
    },
    background = when (background) {
        "white" -> WhiteBg
        "gray" -> LightGrayBg
        else -> WarmBackground
    }
)

data class Question(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int
)

data class QuizItem(
    val question: Question,
    val shuffledOptions: List<String>,
    val correctIndex: Int
)

sealed class Screen {
    data object Home : Screen()
    data object Quiz : Screen()
    data object Results : Screen()
    data object AdminPassword : Screen()
    data object AdminPanel : Screen()
    data object Personnalisation : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QcmApp()
        }
    }
}

private fun loadAppearancePrefs(context: Context): Triple<String, String, Boolean> {
    val p = context.getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE)
    return Triple(
        p.getString(KEY_ACCENT, "blue") ?: "blue",
        p.getString(KEY_BACKGROUND, "cream") ?: "cream",
        p.getBoolean(KEY_EMOJIS, true)
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QcmApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var questions by remember { mutableStateOf(emptyList<Question>()) }
    var quizItems by remember { mutableStateOf(emptyList<QuizItem>()) }
    var selectedAnswers by remember { mutableStateOf(List(QUIZ_SIZE) { -1 }) }
    var score by remember { mutableStateOf(0) }
    var accent by remember { mutableStateOf("blue") }
    var background by remember { mutableStateOf("cream") }
    var showEmojis by remember { mutableStateOf(true) }
    var settingsVersion by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        questions = loadOrCreateQuestions(context)
    }
    LaunchedEffect(settingsVersion) {
        val (a, b, e) = loadAppearancePrefs(context)
        accent = a
        background = b
        showEmojis = e
    }

    val colorScheme = qcmColorScheme(accent, background)
    val appBackground = colorScheme.background
    val appSurface = colorScheme.surface

    MaterialTheme(colorScheme = colorScheme) {
        val title = when (screen) {
            Screen.Home -> "QCM SSIAP 1"
            Screen.Quiz -> "Test en cours"
            Screen.Results -> "Correction"
            Screen.AdminPassword -> "Accès admin"
            Screen.AdminPanel -> "Administration"
            Screen.Personnalisation -> "Personnalisation"
        }

        Box(modifier = Modifier.fillMaxSize().background(appBackground)) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = { TopAppBar(title = { Text(title) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = appSurface, titleContentColor = MaterialTheme.colorScheme.onSurface)) }
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)
                when (screen) {
                    Screen.Home -> HomeScreen(
                        modifier = contentModifier,
                        totalQuestions = questions.size,
                        showEmojis = showEmojis,
                        onStartQuiz = {
                            val baseQuestions = pickUniqueQuestions(questions, QUIZ_SIZE)
                            quizItems = baseQuestions.map { it.toQuizItem() }
                            selectedAnswers = List(quizItems.size) { -1 }
                            score = 0
                            screen = Screen.Quiz
                        },
                        onAdmin = { screen = Screen.AdminPassword },
                        onPersonalization = { screen = Screen.Personnalisation }
                    )

                    Screen.Quiz -> QuizScreen(
                        modifier = contentModifier,
                        items = quizItems,
                        selectedAnswers = selectedAnswers,
                        showEmojis = showEmojis,
                        onAnswerSelected = { index, selected ->
                            selectedAnswers = selectedAnswers.toMutableList().also { it[index] = selected }
                        },
                        onFinish = {
                            score = quizItems.indices.count { index ->
                                selectedAnswers.getOrNull(index) == quizItems[index].correctIndex
                            }
                            screen = Screen.Results
                        },
                        onCancel = { screen = Screen.Home }
                    )

                    Screen.Results -> ResultsScreen(
                        modifier = contentModifier,
                        items = quizItems,
                        selectedAnswers = selectedAnswers,
                        score = score,
                        onBackHome = { screen = Screen.Home }
                    )

                    Screen.AdminPassword -> AdminPasswordScreen(
                        modifier = contentModifier,
                        context = context,
                        onBack = { screen = Screen.Home },
                        onPasswordValidated = { screen = Screen.AdminPanel }
                    )

                    Screen.AdminPanel -> AdminPanelScreen(
                        modifier = contentModifier,
                        questions = questions,
                        onQuestionsUpdated = {
                            questions = it
                            saveQuestions(context, it)
                        },
                        onBack = { screen = Screen.Home }
                    )

                    Screen.Personnalisation -> PersonnalisationScreen(
                        modifier = contentModifier,
                        context = context,
                        currentAccent = accent,
                        currentBackground = background,
                        currentEmojis = showEmojis,
                        onBack = { screen = Screen.Home },
                        onSettingChanged = { settingsVersion++ }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier,
    totalQuestions: Int,
    showEmojis: Boolean,
    onStartQuiz: () -> Unit,
    onAdmin: () -> Unit,
    onPersonalization: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo / Title Section - Style ludique
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (showEmojis) "🚒" else "•",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp)
                )
                Text(
                    text = if (showEmojis) "🚒 Entraînement SSIAP" else "Entraînement SSIAP",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (showEmojis) "Prêt pour l'examen ? 💪" else "Prêt pour l'examen ?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // Stats / Info - Cartes colorées
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = if (showEmojis) "📚" else "•", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = "$totalQuestions",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Questions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            ElevatedCard(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = if (showEmojis) "✨" else "•", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = "20",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Par test",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Actions - Boutons stylisés
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onStartQuiz,
                enabled = totalQuestions >= QUIZ_SIZE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Text(
                    text = if (showEmojis) "🚀 DÉMARRER L'ENTRAÎNEMENT" else "DÉMARRER L'ENTRAÎNEMENT",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            ElevatedButton(
                onClick = onPersonalization,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = if (showEmojis) "🎨 Personnalisation" else "Personnalisation",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            ElevatedButton(
                onClick = onAdmin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = if (showEmojis) "⚙️ Administration & Réglages" else "Administration & Réglages",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (totalQuestions < QUIZ_SIZE) {
            Spacer(modifier = Modifier.height(24.dp))
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (showEmojis) "⚠️" else "!",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Column {
                        Text(
                            text = "Attention !",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Il manque des questions pour lancer un quiz complet ($totalQuestions/$QUIZ_SIZE).",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuizScreen(
    modifier: Modifier,
    items: List<QuizItem>,
    selectedAnswers: List<Int>,
    showEmojis: Boolean,
    onAnswerSelected: (Int, Int) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    if (items.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Aucune question disponible.", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCancel) {
                Text("Retour")
            }
        }
        return
    }

    var currentIndex by remember { mutableStateOf(0) }
    val currentItem = items[currentIndex]
    val currentSelected = selectedAnswers.getOrNull(currentIndex) ?: -1
    val allAnswered = selectedAnswers.all { it >= 0 }
    val progress = (currentIndex + 1).toFloat() / items.size.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar: Progress & Exit - Style ludique
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(
                    text = "❌ Quitter",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "📝 ${currentIndex + 1} / ${items.size}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        // Barre de progression colorée et arrondie
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.large
        ) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Question Card - Style dessin animé
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // En-tête de question avec emoji humoristique (varie à chaque question) si activé
                val questionEmojis = listOf(
                    "🧐", "🤔", "💡", "🎯", "🔥", "📚", "🧠", "⚡", "🎲", "🎪",
                    "👀", "🤓", "💪", "🦉", "🐛", "🚀", "🎓", "🏆", "✨", "🌟"
                )
                val questionEmoji = if (showEmojis) questionEmojis[currentIndex % questionEmojis.size] else "•"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = questionEmoji,
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        text = "Question ${currentIndex + 1}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = currentItem.question.text,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(currentItem.shuffledOptions.indices.toList()) { optionIndex ->
                        val option = currentItem.shuffledOptions[optionIndex]
                        val isSelected = currentSelected == optionIndex
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onAnswerSelected(currentIndex, optionIndex) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 8.dp else 4.dp
                            ),
                            shape = MaterialTheme.shapes.large,
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val optionEmojis = listOf("😄", "🤷", "🙃", "😎")
                                Text(
                                    text = if (isSelected) "✓" else if (showEmojis) optionEmojis[optionIndex % optionEmojis.size] else "○",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navigation Actions - Boutons ludiques
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedButton(
                onClick = { if (currentIndex > 0) currentIndex -= 1 },
                enabled = currentIndex > 0,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "⬅️ Précédent",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (currentIndex < items.lastIndex) {
                Button(
                    onClick = { currentIndex += 1 },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = "Suivant ➡️",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Button(
                    onClick = onFinish,
                    enabled = allAnswered,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allAnswered) SuccessGreen else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (allAnswered) 8.dp else 2.dp
                    )
                ) {
                    Text(
                        text = "✨ TERMINER",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ResultsScreen(
    modifier: Modifier,
    items: List<QuizItem>,
    selectedAnswers: List<Int>,
    score: Int,
    onBackHome: () -> Unit
) {
    val percentage = (score.toFloat() / items.size.toFloat())
    val percentageInt = (percentage * 100).toInt()
    val passed = percentageInt >= 60

    val primaryColor = if (passed) SuccessGreen else ErrorRed
    val emoji = when {
        percentageInt >= 90 -> "🏆"
        percentageInt >= 75 -> "🎉"
        percentageInt >= 60 -> "👍"
        else -> "💪"
    }
    val message = when {
        percentageInt >= 90 -> "Excellent ! Bravo !"
        percentageInt >= 75 -> "Très bien !"
        percentageInt >= 60 -> "Bien joué !"
        else -> "Continuez à vous entraîner !"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score Header - Style festif
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (passed) 
                        SuccessGreen.copy(alpha = 0.15f) 
                    else 
                        MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp)
                    )
                    
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor
                    )

                    // Score circulaire stylisé
                    androidx.compose.foundation.layout.Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(180.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = percentage,
                            modifier = Modifier.fillMaxSize(),
                            color = primaryColor,
                            strokeWidth = 16.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = primaryColor
                            )
                            Text(
                                text = "/ ${items.size}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$percentageInt %",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor
                            )
                        }
                    }

                    if (passed) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = SuccessGreen
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "✅ Test réussi !",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "Il faut 60% pour valider",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📋",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Détail des réponses",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(items.indices.toList()) { index ->
            val item = items[index]
            val selected = selectedAnswers.getOrNull(index)
            val isCorrect = selected == item.correctIndex
            val selectedText = selected?.let { item.shuffledOptions.getOrNull(it) } ?: "Aucune réponse"
            val correctText = item.shuffledOptions[item.correctIndex]

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) 
                        SuccessGreen.copy(alpha = 0.1f) 
                    else 
                        ErrorRed.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.large,
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = if (isCorrect) SuccessGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${index + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isCorrect) SuccessGreen else ErrorRed
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = if (isCorrect) "✅ Correct" else "❌ Incorrect",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = item.question.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    if (!isCorrect) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "❌",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Votre réponse : $selectedText",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ErrorRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✅",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Bonne réponse : $correctText",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBackHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = "🏠 RETOUR À L'ACCUEIL",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AdminPasswordScreen(
    modifier: Modifier,
    context: Context,
    onBack: () -> Unit,
    onPasswordValidated: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val savedPassword = remember { getAdminPassword(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔐",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp)
                )
                
                Text(
                    text = "Accès Administration",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Entrez le mot de passe",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = false
                    },
                    label = { Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error,
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                
                if (error) {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⚠️", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Mot de passe incorrect",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                
                Button(
                    onClick = {
                        if (password == savedPassword) {
                            onPasswordValidated()
                        } else {
                            error = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = "🔓 VALIDER",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Retour",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun PersonnalisationScreen(
    modifier: Modifier,
    context: Context,
    currentAccent: String,
    currentBackground: String,
    currentEmojis: Boolean,
    onBack: () -> Unit,
    onSettingChanged: () -> Unit
) {
    val prefs = context.getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE)
    fun saveAccent(value: String) {
        prefs.edit().putString(KEY_ACCENT, value).apply()
        onSettingChanged()
    }
    fun saveBackground(value: String) {
        prefs.edit().putString(KEY_BACKGROUND, value).apply()
        onSettingChanged()
    }
    fun saveEmojis(value: Boolean) {
        prefs.edit().putBoolean(KEY_EMOJIS, value).apply()
        onSettingChanged()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Apparence",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Couleur d'accent", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("blue" to "Bleu", "green" to "Vert", "orange" to "Orange", "purple" to "Violet").forEach { (key, label) ->
                        val selected = currentAccent == key
                        OutlinedButton(
                            onClick = { saveAccent(key) },
                            modifier = Modifier.weight(1f),
                            colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                        ) { Text(label) }
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fond de l'écran", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cream" to "Crème", "white" to "Blanc", "gray" to "Gris clair").forEach { (key, label) ->
                        val selected = currentBackground == key
                        OutlinedButton(
                            onClick = { saveBackground(key) },
                            modifier = Modifier.weight(1f),
                            colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                        ) { Text(label) }
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Emojis (quiz et accueil)", style = MaterialTheme.typography.titleMedium)
                Switch(checked = currentEmojis, onCheckedChange = { saveEmojis(it) })
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
            Text("Retour à l'accueil", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun AdminPanelScreen(
    modifier: Modifier,
    questions: List<Question>,
    onQuestionsUpdated: (List<Question>) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val editingQuestion = remember { mutableStateOf<Question?>(null) }
    val isAdding = remember { mutableStateOf(false) }
    var importExportMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = exportQuestionsToUri(context, uri, questions)
            importExportMessage = if (success) {
                "Export JSON réussi: ${questions.size} questions exportées."
            } else {
                "Échec de l'export JSON."
            }
        } else {
            importExportMessage = "Export JSON annulé."
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = exportQuestionsToCsvUri(context, uri, questions)
            importExportMessage = if (success) {
                "Export CSV réussi."
            } else {
                "Échec de l'export CSV."
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val imported = importQuestionsFromUri(context, uri)
            if (imported != null && imported.isNotEmpty()) {
                val result = mergeQuestions(questions, imported)
                onQuestionsUpdated(result.merged)
                importExportMessage =
                    "Import JSON: ${imported.size} lues, ${result.added} ajoutées, ${result.skippedDuplicates} doublons ignorés."
            } else {
                importExportMessage = "Import JSON échoué ou fichier vide."
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val imported = importQuestionsFromCsvUri(context, uri)
            if (imported != null && imported.isNotEmpty()) {
                val result = mergeQuestions(questions, imported)
                onQuestionsUpdated(result.merged)
                importExportMessage =
                    "Import CSV: ${imported.size} lues, ${result.added} ajoutées, ${result.skippedDuplicates} doublons ignorés."
            } else {
                importExportMessage = "Import CSV échoué ou fichier vide."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚙️ Gestion des questions",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedButton(
                    onClick = { isAdding.value = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("➕ Ajouter une question")
                }
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗑️ Supprimer la base")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🏠 Retour à l'accueil")
                }
                OutlinedButton(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔐 Changer le mot de passe")
                }
            }
        }
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Import / Export",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 JSON")
                    }
                    OutlinedButton(
                        onClick = { exportJsonLauncher.launch("questions_ssiap.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📤 JSON")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/plain")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 CSV")
                    }
                    OutlinedButton(
                        onClick = { exportCsvLauncher.launch("questions_ssiap.csv") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📤 CSV")
                    }
                }
            }
        }
        
        if (importExportMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = importExportMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Text(
            text = "📚 ${questions.size} questions dans la base",
            style = MaterialTheme.typography.titleMedium
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(questions) { question ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = question.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "✓ ${question.options[question.correctIndex]}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editingQuestion.value = question }) {
                                Text("✏️ Éditer")
                            }
                            TextButton(onClick = {
                                onQuestionsUpdated(questions.filterNot { it.id == question.id })
                            }) {
                                Text("🗑️ Supprimer")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingQuestion.value != null) {
        EditQuestionDialog(
            title = "Éditer la question",
            initialQuestion = editingQuestion.value!!,
            allQuestions = questions,
            onDismiss = { editingQuestion.value = null },
            onSave = { updated ->
                onQuestionsUpdated(questions.map { if (it.id == updated.id) updated else it })
                editingQuestion.value = null
            }
        )
    }

    if (isAdding.value) {
        val newQuestion = Question(
            id = "q-${System.currentTimeMillis()}",
            text = "",
            options = List(4) { "" },
            correctIndex = 0
        )
        EditQuestionDialog(
            title = "Nouvelle question",
            initialQuestion = newQuestion,
            allQuestions = questions,
            onDismiss = { isAdding.value = false },
            onSave = { created ->
                onQuestionsUpdated(questions + created)
                isAdding.value = false
            }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            context = context,
            onDismiss = { showPasswordDialog = false },
            onPasswordChanged = {
                showPasswordDialog = false
                importExportMessage = "✓ Mot de passe modifié avec succès."
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Supprimer la base de questions",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "Cette action va supprimer toutes les questions actuelles. Aucune base ne sera recréée automatiquement : l'application restera sans questions tant que vous n'aurez pas importé ou fourni un fichier JSON/CSV valide. Voulez-vous continuer ?"
                )
            },
            confirmButton = {
                ElevatedButton(
                    onClick = {
                        // Supprime le fichier de base. Aucune régénération automatique si une base
                        // a déjà existé : l'application restera sans questions tant qu'un JSON/CSV
                        // n'est pas importé.
                        context.deleteFile(QUESTIONS_FILE_NAME)
                        onQuestionsUpdated(emptyList())
                        importExportMessage = "✓ Base supprimée. L'application est maintenant sans questions jusqu'à import d'un nouveau fichier."
                        showResetDialog = false
                    }
                ) {
                    Text("Oui, supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun EditQuestionDialog(
    title: String,
    initialQuestion: Question,
    allQuestions: List<Question>,
    onDismiss: () -> Unit,
    onSave: (Question) -> Unit
) {
    var text by remember { mutableStateOf(initialQuestion.text) }
    val options = remember { mutableStateListOf(*initialQuestion.options.toTypedArray()) }
    var correctIndex by remember { mutableStateOf(initialQuestion.correctIndex) }
    var error by remember { mutableStateOf(false) }
    var duplicateError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            error = false
                            duplicateError = false
                        },
                        label = { Text("Question") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        isError = error || duplicateError
                    )
                }
                
                items(4) { index ->
                    OutlinedTextField(
                        value = options[index],
                        onValueChange = {
                            options[index] = it
                            error = false
                        },
                        label = { Text("Réponse ${index + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = error
                    )
                }
                
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sélectionnez la bonne réponse :",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (0..3).forEach { index ->
                                OutlinedButton(
                                    onClick = { correctIndex = index },
                                    modifier = Modifier.weight(1f),
                                    colors = if (correctIndex == index) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(
                                        text = if (correctIndex == index) "✓" else "${index + 1}",
                                        style = if (correctIndex == index) {
                                            MaterialTheme.typography.titleMedium
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (error) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ Veuillez remplir la question et les 4 réponses.",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                if (duplicateError) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ Cette question existe déjà.",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            ElevatedButton(onClick = {
                val valid = text.isNotBlank() && options.all { it.isNotBlank() }
                val normalized = text.trim().lowercase()
                val duplicate = allQuestions.any {
                    it.id != initialQuestion.id && it.text.trim().lowercase() == normalized
                }
                if (valid && !duplicate) {
                    onSave(
                        initialQuestion.copy(
                            text = text.trim(),
                            options = options.map { it.trim() },
                            correctIndex = correctIndex
                        )
                    )
                } else {
                    error = !valid
                    duplicateError = duplicate
                }
            }) {
                Text("💾 Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val savedPassword = remember { getAdminPassword(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔐 Changer le mot de passe",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        error = null
                    },
                    label = { Text("Mot de passe actuel") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null
                )
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        error = null
                    },
                    label = { Text("Nouveau mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null
                )
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        error = null
                    },
                    label = { Text("Confirmer le mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null
                )
                
                if (error != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "❌ $error",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            ElevatedButton(onClick = {
                when {
                    currentPassword != savedPassword -> {
                        error = "Mot de passe actuel incorrect"
                    }
                    newPassword.length < 6 -> {
                        error = "Le nouveau mot de passe doit contenir au moins 6 caractères"
                    }
                    newPassword != confirmPassword -> {
                        error = "Les mots de passe ne correspondent pas"
                    }
                    else -> {
                        setAdminPassword(context, newPassword)
                        onPasswordChanged()
                    }
                }
            }) {
                Text("💾 Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

private fun Question.toQuizItem(): QuizItem {
    val paired = options.mapIndexed { index, option -> index to option }.shuffled()
    val shuffledOptions = paired.map { it.second }
    val correctIndex = paired.indexOfFirst { it.first == this.correctIndex }
    return QuizItem(this, shuffledOptions, correctIndex)
}

private fun pickUniqueQuestions(
    questions: List<Question>,
    count: Int
): List<Question> {
    if (questions.isEmpty()) return emptyList()
    val shuffled = questions.shuffled()
    val selected = mutableListOf<Question>()
    val seenTexts = mutableSetOf<String>()
    for (question in shuffled) {
        val key = question.text.trim().lowercase()
        if (key.isNotEmpty() && seenTexts.add(key)) {
            selected.add(question)
        }
        if (selected.size == count) break
    }
    if (selected.size < count) {
        val remaining = shuffled.filterNot { selected.any { s -> s.id == it.id } }
        selected.addAll(remaining.take(count - selected.size))
    }
    return selected.take(count)
}

private fun getAdminPassword(context: Context): String {
    val prefs = context.getSharedPreferences(PASSWORD_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PASSWORD_KEY, DEFAULT_ADMIN_PASSWORD) ?: DEFAULT_ADMIN_PASSWORD
}

private fun setAdminPassword(context: Context, password: String) {
    val prefs = context.getSharedPreferences(PASSWORD_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(PASSWORD_KEY, password).apply()
}

private fun loadOrCreateQuestions(context: Context): List<Question> {
    val prefs = context.getSharedPreferences(QUESTIONS_PREFS_NAME, Context.MODE_PRIVATE)
    val baseInitialized = prefs.getBoolean(QUESTIONS_BASE_INITIALIZED_KEY, false)

    val file = context.getFileStreamPath(QUESTIONS_FILE_NAME)
    if (!file.exists()) {
        // Première utilisation : on fournit une base par défaut de 100 questions.
        if (!baseInitialized) {
            val seed = generateSeedQuestions()
            saveQuestions(context, seed)
            prefs.edit().putBoolean(QUESTIONS_BASE_INITIALIZED_KEY, true).apply()
            return seed
        }
        // Base déjà initialisée puis supprimée : on laisse vide pour import manuel.
        return emptyList()
    }

    val loaded = readQuestions(context) ?: emptyList()
    if (!baseInitialized && loaded.isNotEmpty()) {
        // Une base existe (par import ou autre) : on ne recréera plus automatiquement après suppression.
        prefs.edit().putBoolean(QUESTIONS_BASE_INITIALIZED_KEY, true).apply()
    }
    return loaded
}

private fun readQuestions(context: Context): List<Question>? {
    return try {
        val jsonText = context.openFileInput(QUESTIONS_FILE_NAME).bufferedReader().use { it.readText() }
        parseQuestionsFromJson(jsonText)
    } catch (_: Exception) {
        null
    }
}

private fun saveQuestions(context: Context, questions: List<Question>) {
    val json = questionsToJson(questions)
    context.openFileOutput(QUESTIONS_FILE_NAME, Context.MODE_PRIVATE).use { stream ->
        stream.write(json.toByteArray())
    }
}

private fun normalizeQuestionText(text: String): String {
    return text.trim().lowercase()
}

private fun normalizeQuestionKey(question: Question): String {
    val base = normalizeQuestionText(question.text)
    val optionsKey = question.options.joinToString(separator = "|") {
        it.trim().lowercase()
    }
    return "$base||$optionsKey"
}

private data class MergeResult(
    val merged: List<Question>,
    val added: Int,
    val skippedDuplicates: Int
)

private fun mergeQuestions(current: List<Question>, imported: List<Question>): MergeResult {
    val seenKeys = current.map { normalizeQuestionKey(it) }.toMutableSet()
    val merged = current.toMutableList()
    var added = 0
    var skipped = 0

    imported.forEach { question ->
        val key = normalizeQuestionKey(question)
        if (key.isNotEmpty() && seenKeys.add(key)) {
            merged.add(
                question.copy(id = question.id.ifBlank { "import-${System.currentTimeMillis()}" })
            )
            added++
        } else {
            skipped++
        }
    }

    return MergeResult(
        merged = merged,
        added = added,
        skippedDuplicates = skipped
    )
}

private fun questionsToJson(questions: List<Question>): String {
    val array = JSONArray()
    questions.forEach { question ->
        val obj = JSONObject()
        obj.put("id", question.id)
        obj.put("text", question.text)
        obj.put("correctIndex", question.correctIndex)
        val optionsArray = JSONArray()
        question.options.forEach { optionsArray.put(it) }
        obj.put("options", optionsArray)
        array.put(obj)
    }
    val wrapper = JSONObject()
    wrapper.put("format", QUESTIONS_FORMAT)
    wrapper.put("questions", array)
    return wrapper.toString(2)
}

private fun parseQuestionsFromJson(jsonText: String): List<Question>? {
    return try {
        val array = try {
            val wrapper = JSONObject(jsonText)
            if (wrapper.has("questions")) {
                val format = wrapper.optString("format", QUESTIONS_FORMAT)
                if (format != QUESTIONS_FORMAT && format != "qcm-questions-v1") {
                    // Format inconnu
                    return null
                }
                wrapper.getJSONArray("questions")
            } else {
                // Format simple = tableau direct (utilisé surtout pour import externes)
                JSONArray(jsonText)
            }
        } catch (_: Exception) {
            JSONArray(jsonText)
        }
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val optionsArray = obj.getJSONArray("options")
                val options = List(optionsArray.length()) { index -> optionsArray.getString(index) }
                if (options.size == 4) {
                    add(
                        Question(
                            id = obj.getString("id"),
                            text = obj.getString("text"),
                            options = options,
                            correctIndex = obj.getInt("correctIndex")
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun exportQuestionsToUri(context: Context, uri: Uri, questions: List<Question>): Boolean {
    return try {
        val json = questionsToJson(questions)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(json)
            writer.flush()
        } ?: return false
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun importQuestionsFromUri(context: Context, uri: Uri): List<Question>? {
    return try {
        val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
        if (jsonText.isNullOrBlank()) return null
        parseQuestionsFromJson(jsonText)
    } catch (_: Exception) {
        null
    }
}

private fun exportQuestionsToCsvUri(context: Context, uri: Uri, questions: List<Question>): Boolean {
    return try {
        val csv = questionsToCsv(questions)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(csv.toByteArray())
        } ?: return false
        true
    } catch (_: Exception) {
        false
    }
}

private fun importQuestionsFromCsvUri(context: Context, uri: Uri): List<Question>? {
    return try {
        val csvText = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
        if (csvText.isNullOrBlank()) return null
        parseQuestionsFromCsv(csvText)
    } catch (_: Exception) {
        null
    }
}

private fun questionsToCsv(questions: List<Question>): String {
    val header = listOf("id", "question", "reponse1", "reponse2", "reponse3", "reponse4", "correctIndex")
        .joinToString(CSV_DELIMITER.toString())
    val rows = questions.map { q ->
        listOf(
            q.id,
            q.text,
            q.options.getOrNull(0).orEmpty(),
            q.options.getOrNull(1).orEmpty(),
            q.options.getOrNull(2).orEmpty(),
            q.options.getOrNull(3).orEmpty(),
            q.correctIndex.toString()
        ).joinToString(CSV_DELIMITER.toString()) { escapeCsv(it) }
    }
    return (listOf(header) + rows).joinToString("\n")
}

private fun parseQuestionsFromCsv(csvText: String): List<Question>? {
    val lines = csvText.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    val hasHeader = lines.first().lowercase().contains("question") || lines.first().lowercase().contains("reponse")
    val dataLines = if (hasHeader) lines.drop(1) else lines
    val questions = mutableListOf<Question>()
    dataLines.forEachIndexed { index, rawLine ->
        val delimiter = if (rawLine.contains(CSV_DELIMITER)) CSV_DELIMITER else ','
        val parts = rawLine.split(delimiter).map { it.trim() }
        if (parts.size >= 7) {
            val id = parts[0].ifBlank { "csv-${System.currentTimeMillis()}-$index" }
            val text = parts[1]
            val options = listOf(parts[2], parts[3], parts[4], parts[5])
            val correctRaw = parts[6].toIntOrNull() ?: 0
            val correctIndex = if (correctRaw in 1..4) correctRaw - 1 else correctRaw.coerceIn(0, 3)
            if (text.isNotBlank() && options.all { it.isNotBlank() }) {
                questions.add(
                    Question(
                        id = id,
                        text = text,
                        options = options,
                        correctIndex = correctIndex
                    )
                )
            }
        }
    }
    return questions
}

private fun escapeCsv(value: String): String {
    val needsQuotes = value.contains(CSV_DELIMITER) || value.contains(',') || value.contains('"') || value.contains('\n')
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}

private fun generateSeedQuestions(): List<Question> {
    val topics = listOf(
        "le feu et ses conséquences",
        "la sécurité incendie en ERP",
        "la sécurité incendie en IGH",
        "l'évacuation",
        "le désenfumage",
        "les moyens de secours",
        "les extincteurs",
        "les RIA",
        "les colonnes sèches",
        "les colonnes humides",
        "le SSI",
        "les rondes de sécurité",
        "la main courante",
        "l'alarme incendie",
        "l'éclairage de sécurité",
        "la signalisation",
        "l'accueil des secours",
        "la prévention des risques",
        "les consignes de sécurité",
        "l'assistance aux personnes"
    )

    val templates = listOf(
        QuestionTemplate(
            question = "Dans le cadre de %s, l'objectif principal est :",
            correct = "Prévenir les risques et protéger les occupants.",
            wrong = listOf(
                "Réduire uniquement les coûts d'exploitation.",
                "Améliorer l'esthétique du bâtiment.",
                "Augmenter la consommation d'énergie."
            )
        ),
        QuestionTemplate(
            question = "Pour %s, la priorité d'un agent SSIAP 1 est :",
            correct = "Détecter, alerter et mettre en sécurité.",
            wrong = listOf(
                "Intervenir sans alerter les secours.",
                "Attendre un responsable avant toute action.",
                "Couper l'électricité sans diagnostic."
            )
        ),
        QuestionTemplate(
            question = "Au sujet de %s, quelle action est correcte ?",
            correct = "Appliquer les consignes et consigner en main courante.",
            wrong = listOf(
                "Ignorer les anomalies mineures.",
                "Reporter l'action en fin de service.",
                "Se fier uniquement à la mémoire."
            )
        ),
        QuestionTemplate(
            question = "En cas d'incident lié à %s, l'agent doit d'abord :",
            correct = "Donner l'alerte et sécuriser la zone.",
            wrong = listOf(
                "Quitter immédiatement le site.",
                "Chercher un responsable avant toute action.",
                "Éteindre toutes les lumières."
            )
        ),
        QuestionTemplate(
            question = "Dans le cadre de %s, la documentation essentielle est :",
            correct = "Les consignes de sécurité et la main courante.",
            wrong = listOf(
                "Le planning des congés.",
                "La liste des fournisseurs.",
                "Le plan marketing du site."
            )
        ),
        QuestionTemplate(
            question = "Pour %s, la surveillance régulière permet de :",
            correct = "Détecter précocement un départ de feu.",
            wrong = listOf(
                "Remplacer les exercices d'évacuation.",
                "Supprimer la maintenance technique.",
                "Éviter toute formation."
            )
        ),
        QuestionTemplate(
            question = "La mission liée à %s consiste à :",
            correct = "Contrôler le bon fonctionnement des installations.",
            wrong = listOf(
                "Bloquer l'accès aux secours externes.",
                "Débrancher les systèmes d'alarme.",
                "Désactiver les dispositifs de sécurité."
            )
        ),
        QuestionTemplate(
            question = "Sur %s, l'agent SSIAP 1 doit :",
            correct = "Informer et guider les occupants en sécurité.",
            wrong = listOf(
                "Créer un mouvement de panique.",
                "Interdire toute évacuation.",
                "Rester sans communiquer."
            )
        ),
        QuestionTemplate(
            question = "La bonne pratique pour %s est :",
            correct = "Réaliser des rondes et signaler les anomalies.",
            wrong = listOf(
                "Neutraliser les dispositifs de sécurité.",
                "Reporter les anomalies sans traçabilité.",
                "Attendre l'incident pour agir."
            )
        ),
        QuestionTemplate(
            question = "Dans un contexte de %s, l'agent doit :",
            correct = "Appliquer les consignes et assister les personnes.",
            wrong = listOf(
                "Refuser l'accès aux secours.",
                "Conserver l'incident secret.",
                "Ignorer les alarmes."
            )
        ),
        QuestionTemplate(
            question = "Concernant %s, l'alerte consiste à :",
            correct = "Transmettre rapidement les informations utiles.",
            wrong = listOf(
                "Attendre la fin de l'incident.",
                "Informer uniquement le public.",
                "Prévenir sans localisation précise."
            )
        ),
        QuestionTemplate(
            question = "Pour %s, le rôle de l'agent SSIAP 1 est aussi de :",
            correct = "Vérifier l'accessibilité des moyens de secours.",
            wrong = listOf(
                "Condamner les issues de secours.",
                "Retirer la signalisation.",
                "Stocker du matériel devant les portes."
            )
        ),
        QuestionTemplate(
            question = "Dans %s, la réaction attendue est :",
            correct = "Rester calme et suivre les procédures.",
            wrong = listOf(
                "Agir sans coordination.",
                "Ignorer les consignes écrites.",
                "Attendre que la situation s'aggrave."
            )
        ),
        QuestionTemplate(
            question = "Pour %s, la prévention efficace repose sur :",
            correct = "La vigilance, les contrôles et le suivi.",
            wrong = listOf(
                "La suppression des rondes.",
                "La réduction des informations.",
                "L'absence de formation."
            )
        ),
        QuestionTemplate(
            question = "Dans %s, une consigne utile est :",
            correct = "Connaître les accès pour les secours.",
            wrong = listOf(
                "Masquer les plans d'intervention.",
                "Fermer les accès aux secours.",
                "Supprimer les contacts d'urgence."
            )
        ),
        QuestionTemplate(
            question = "Pour %s, le compte rendu doit être :",
            correct = "Clair, factuel et daté.",
            wrong = listOf(
                "Basé sur des suppositions.",
                "Écrit sans mention d'heure.",
                "Rédigé plusieurs jours après."
            )
        ),
        QuestionTemplate(
            question = "En matière de %s, l'agent doit :",
            correct = "Respecter les procédures établies.",
            wrong = listOf(
                "Créer ses propres règles.",
                "Modifier les consignes sans validation.",
                "Ignorer les plans de secours."
            )
        ),
        QuestionTemplate(
            question = "Concernant %s, la coordination vise à :",
            correct = "Faciliter l'action des secours.",
            wrong = listOf(
                "Limiter l'accès aux informations.",
                "Désorganiser les équipes.",
                "Empêcher l'évacuation."
            )
        ),
        QuestionTemplate(
            question = "Dans %s, l'évacuation doit être :",
            correct = "Ordonnée et guidée.",
            wrong = listOf(
                "Rapide sans consigne.",
                "Retardée sans raison.",
                "Lancée sans alerte."
            )
        ),
        QuestionTemplate(
            question = "Pour %s, la vérification visuelle sert à :",
            correct = "Repérer une anomalie ou un danger.",
            wrong = listOf(
                "Remplacer les contrôles techniques.",
                "Se dispenser de consignes.",
                "Éteindre les systèmes."
            )
        )
    )

    // Génère une base de 100 questions inspirées des thèmes SSIAP 1
    return List(100) { index ->
        val topic = topics[index % topics.size]
        val template = templates[index % templates.size]
        val options = (listOf(template.correct) + template.wrong).shuffled(Random(index + 1))
        val correctIndex = options.indexOf(template.correct)
        Question(
            id = "seed-${index + 1}",
            text = template.question.format(topic),
            options = options,
            correctIndex = correctIndex
        )
    }
}

data class QuestionTemplate(
    val question: String,
    val correct: String,
    val wrong: List<String>
)

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    MaterialTheme(colorScheme = qcmColorScheme("blue", "cream")) {
        HomeScreen(
            modifier = Modifier.fillMaxSize(),
            totalQuestions = 200,
            showEmojis = true,
            onStartQuiz = {},
            onAdmin = {},
            onPersonalization = {}
        )
    }
}