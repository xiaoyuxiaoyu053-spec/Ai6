package com.deepline.privateapp

import android.os.Bundle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF20232B)
private val Muted = Color(0xFF858B98)
private val AppBg = Color(0xFFF5F6FA)
private val AccentDefault = Color(0xFF7289F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeepLineApp()
        }
    }
}

private fun loadLocalBitmap(context: Context, path: String): Bitmap? {
    return try {
        if (path.isBlank()) return null
        if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))?.use {
                BitmapFactory.decodeStream(it)
            }
        } else {
            val filePath = if (path.startsWith("file://")) {
                Uri.parse(path).path ?: path
            } else path
            BitmapFactory.decodeFile(filePath)
        }
    } catch (_: Exception) {
        null
    }
}

private fun copyPickedImageToAppStorage(context: Context, uri: Uri): String? {
    return try {
        val folder = File(context.filesDir, "deepline-media")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun hexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

private fun colorHex(color: Color): String {
    return String.format(
        Locale.US,
        "#%06X",
        0xFFFFFF and color.toArgb()
    )
}

@Composable
fun DeepLineApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }

    var data by remember {
        mutableStateOf(
            LocalStore.load(context).let {
                if (it.length() == 0) JSONObject() else it
            }
        )
    }

    fun persist() {
        LocalStore.save(context, data)
        revision++
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedAiId by remember { mutableStateOf("") }
    var editingAiId by remember { mutableStateOf("") }
    var showCreateAi by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var myName by remember {
        mutableStateOf(data.optString("myName", "我"))
    }
    var myAvatar by remember {
        mutableStateOf(data.optString("myAvatar", ""))
    }
    var myBio by remember {
        mutableStateOf(data.optString("myBio", ""))
    }

    var themeHex by remember {
        mutableStateOf(data.optString("themeHex", "#7289F5"))
    }
    var myBubbleHex by remember {
        mutableStateOf(data.optString("myBubbleHex", "#DCE8FF"))
    }
    var bubbleHex by remember {
        mutableStateOf(data.optString("bubbleHex", "#FFFFFF"))
    }
    var bubbleShape by remember {
        mutableStateOf(data.optString("bubbleShape", "圆角"))
    }
    var glassUi by remember {
        mutableStateOf(data.optBoolean("glassUi", true))
    }
    var backgroundUri by remember {
        mutableStateOf(data.optString("backgroundUri", ""))
    }
    var bubbleBackgroundUri by remember {
        mutableStateOf(data.optString("bubbleBackgroundUri", ""))
    }
    var backgroundAlpha by remember {
        mutableFloatStateOf(data.optDouble("backgroundAlpha", 0.25).toFloat())
    }
    var inputHint by remember {
        mutableStateOf(data.optString("inputHint", "发送消息…"))
    }
    var globalTitle by remember {
        mutableStateOf(data.optString("globalTitle", "欢迎回家♡"))
    }
    var globalSubtitle by remember {
        mutableStateOf(data.optString("globalSubtitle", "和你的 AI 好友聊聊天吧"))
    }

    var composer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var chatBackground by remember { mutableStateOf("") }

    val accent = hexColor(themeHex, AccentDefault)

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val path = copyPickedImageToAppStorage(context, uri)
                withContext(Dispatchers.Main) {
                    if (path != null) {
                        myAvatar = path
                        data.put("myAvatar", path)
                        persist()
                    }
                }
            }
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val path = copyPickedImageToAppStorage(context, uri)
                withContext(Dispatchers.Main) {
                    if (path != null) {
                        backgroundUri = path
                        data.put("backgroundUri", path)
                        persist()
                    }
                }
            }
        }
    }

    val bubbleBackgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val path = copyPickedImageToAppStorage(context, uri)
                withContext(Dispatchers.Main) {
                    if (path != null) {
                        bubbleBackgroundUri = path
                        data.put("bubbleBackgroundUri", path)
                        persist()
                    }
                }
            }
        }
    }

    val ais = data.optJSONArray("ais") ?: JSONArray()
    val selectedAi = (0 until ais.length())
        .map { ais.optJSONObject(it) }
        .filterNotNull()
        .firstOrNull { it.optString("id") == selectedAiId }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = accent,
            secondary = Color(0xFF9D91F8),
            background = AppBg,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            if (backgroundUri.isNotBlank()) {
                val bitmap = remember(backgroundUri) {
                    loadLocalBitmap(context, backgroundUri)
                }
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = 1f - backgroundAlpha)
                            .blur(0.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            if (selectedAi != null) {
                ChatScreen(
                    p = selectedAi,
                    revision = revision,
                    myName = myName,
                    myAvatar = myAvatar,
                    composer = composer,
                    onComposer = { composer = it },
                    busy = busy,
                    error = error,
                    backgroundUri = backgroundUri,
                    bubbleBackgroundUri = bubbleBackgroundUri,
                    glassUi = glassUi,
                    bubbleHex = bubbleHex,
                    myBubbleHex = myBubbleHex,
                    bubbleShape = bubbleShape,
                    backgroundAlpha = backgroundAlpha,
                    inputHint = inputHint,
                    onBack = {
                        selectedAiId = ""
                        composer = ""
                        error = ""
                    },
                    onQuote = { quote ->
                        composer = quote + composer
                    },
                    onDeleteMessage = { index ->
                        val messages = selectedAi.optJSONArray("messages") ?: JSONArray()
                        if (index in 0 until messages.length()) {
                            val newMessages = JSONArray()
                            for (i in 0 until messages.length()) {
                                if (i != index) newMessages.put(messages.getJSONObject(i))
                            }
                            selectedAi.put("messages", newMessages)
                            persist()
                        }
                    },
                    onPickImage = {
                        error = "请先在输入框中输入文字；图片附件功能需要单独接入 AI 图片接口。"
                    },
                    onSend = {
                        val text = composer.trim()
                        if (text.isNotEmpty() && !busy) {
                            composer = ""
                            error = ""
                            val messages = selectedAi.optJSONArray("messages") ?: JSONArray()
                            val userMessage = JSONObject()
                                .put("id", System.currentTimeMillis().toString())
                                .put("role", "user")
                                .put("content", text)
                                .put("time", System.currentTimeMillis())
                            messages.put(userMessage)
                            selectedAi.put("messages", messages)
                            persist()

                            busy = true
                            val aiSnapshot = JSONObject(selectedAi.toString())
                            scope.launch {
                                try {
                                    val reply = withContext(Dispatchers.IO) {
                                        DeepSeek.reply(context, aiSnapshot, text)
                                    }
                                    val currentAis = data.optJSONArray("ais") ?: JSONArray()
                                    val currentAi = (0 until currentAis.length())
                                        .map { currentAis.optJSONObject(it) }
                                        .filterNotNull()
                                        .firstOrNull { it.optString("id") == selectedAiId }

                                    if (currentAi != null) {
                                        val currentMessages =
                                            currentAi.optJSONArray("messages") ?: JSONArray()
                                        if (currentMessages.length() > 0) {
                                            val last = currentMessages.optJSONObject(
                                                currentMessages.length() - 1
                                            )
                                            if (last?.optString("content") == "---") {
                                                currentMessages.remove(currentMessages.length() - 1)
                                            }
                                        }
                                        currentMessages.put(
                                            JSONObject()
                                                .put("id", System.currentTimeMillis().toString())
                                                .put("role", "assistant")
                                                .put("content", reply)
                                                .put("time", System.currentTimeMillis())
                                        )
                                        currentAi.put("messages", currentMessages)
                                        persist()
                                    }
                                } catch (e: Exception) {
                                    error = "AI 回复失败：${e.message ?: "请检查 API Key 和网络"}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                )
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(myAvatar, myName, 58)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                globalTitle,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(
                                globalSubtitle,
                                fontSize = 14.sp,
                                color = Muted
                            )
                        }
                        FilledIconButton(
                            onClick = { editingAiId = ""; showCreateAi = true },
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新增 AI")
                        }
                    }

                    if (selectedTab == 0) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(
                                (0 until ais.length()).mapNotNull { ais.optJSONObject(it) },
                                key = { it.optString("id") }
                            ) { ai ->
                                AiCard(
                                    ai = ai,
                                    glassUi = glassUi,
                                    onClick = {
                                        selectedAiId = ai.optString("id")
                                    },
                                    onEdit = {
                                        editingAiId = ai.optString("id")
                                        showCreateAi = true
                                    },
                                    onDelete = {
                                        val updated = JSONArray()
                                        for (i in 0 until ais.length()) {
                                            val item = ais.optJSONObject(i)
                                            if (item?.optString("id") != ai.optString("id")) {
                                                if (item != null) updated.put(item)
                                            }
                                        }
                                        data.put("ais", updated)
                                        persist()
                                    }
                                )
                            }
                        }
                    } else if (selectedTab == 1) {
                        FeedScreen(
                            data = data,
                            revision = revision,
                            glassUi = glassUi,
                            onSave = { persist() }
                        )
                    } else {
                        SettingsScreen(
                            myName = myName,
                            onMyName = {
                                myName = it
                                data.put("myName", it)
                                persist()
                            },
                            myBio = myBio,
                            onMyBio = {
                                myBio = it
                                data.put("myBio", it)
                                persist()
                            },
                            onPickAvatar = { avatarPicker.launch("image/*") },
                            onPickBackground = { backgroundPicker.launch("image/*") },
                            onPickBubbleBackground = {
                                bubbleBackgroundPicker.launch("image/*")
                            },
                            themeHex = themeHex,
                            onThemeHex = {
                                themeHex = it
                                data.put("themeHex", it)
                                persist()
                            },
                            myBubbleHex = myBubbleHex,
                            onMyBubbleHex = {
                                myBubbleHex = it
                                data.put("myBubbleHex", it)
                                persist()
                            },
                            bubbleHex = bubbleHex,
                            onBubbleHex = {
                                bubbleHex = it
                                data.put("bubbleHex", it)
                                persist()
                            },
                            glassUi = glassUi,
                            onGlassUi = {
                                glassUi = it
                                data.put("glassUi", it)
                                persist()
                            },
                            bubbleShape = bubbleShape,
                            onBubbleShape = {
                                bubbleShape = it
                                data.put("bubbleShape", it)
                                persist()
                            },
                            backgroundAlpha = backgroundAlpha,
                            onBackgroundAlpha = {
                                backgroundAlpha = it
                                data.put("backgroundAlpha", it.toDouble())
                                persist()
                            },
                            globalTitle = globalTitle,
                            onGlobalTitle = {
                                globalTitle = it
                                data.put("globalTitle", it)
                                persist()
                            },
                            globalSubtitle = globalSubtitle,
                            onGlobalSubtitle = {
                                globalSubtitle = it
                                data.put("globalSubtitle", it)
                                persist()
                            },
                            inputHint = inputHint,
                            onInputHint = {
                                inputHint = it
                                data.put("inputHint", it)
                                persist()
                            }
                        )
                    }

                    NavigationBar(
                        containerColor = Color.White.copy(
                            alpha = if (glassUi) 0.82f else 1f
                        )
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.ChatBubbleOutline, null) },
                            label = { Text("聊天") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.DynamicFeed, null) },
                            label = { Text("动态") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("设置") }
                        )
                    }
                }
            }
        }

        if (showCreateAi) {
            val existing = (0 until ais.length())
                .mapNotNull { ais.optJSONObject(it) }
                .firstOrNull { it.optString("id") == editingAiId }

            AiEditorDialog(
                existing = existing,
                onDismiss = { showCreateAi = false },
                onSave = { ai ->
                    val updated = JSONArray()
                    var replaced = false
                    for (i in 0 until ais.length()) {
                        val item = ais.optJSONObject(i)
                        if (item?.optString("id") == ai.optString("id")) {
                            updated.put(ai)
                            replaced = true
                        } else if (item != null) {
                            updated.put(item)
                        }
                    }
                    if (!replaced) updated.put(ai)
                    data.put("ais", updated)
                    persist()
                    showCreateAi = false
                }
            )
        }
    }
}

@Composable
fun Avatar(path: String, name: String, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(path) { loadLocalBitmap(context, path) }

    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFFE8EBF5)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                name.take(1).ifBlank { "?" },
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2).sp,
                color = AccentDefault
            )
        }
    }
}

@Composable
fun AiCard(
    ai: JSONObject,
    glassUi: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Color.White.copy(alpha = if (glassUi) 0.80f else 1f)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ai.optString("avatar"), ai.optString("name"), 66)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ai.optString("name", "AI"),
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Ink
            )
            Spacer(Modifier.height(5.dp))
            Text(
                ai.optString("preview", ai.optString("bio", "开始聊天吧")),
                color = Muted,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, null, tint = Muted)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("编辑 AI") },
                    onClick = {
                        expanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除 AI") },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    myName: String,
    onMyName: (String) -> Unit,
    myBio: String,
    onMyBio: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onPickBackground: () -> Unit,
    onPickBubbleBackground: () -> Unit,
    themeHex: String,
    onThemeHex: (String) -> Unit,
    myBubbleHex: String,
    onMyBubbleHex: (String) -> Unit,
    bubbleHex: String,
    onBubbleHex: (String) -> Unit,
    glassUi: Boolean,
    onGlassUi: (Boolean) -> Unit,
    bubbleShape: String,
    onBubbleShape: (String) -> Unit,
    backgroundAlpha: Float,
    onBackgroundAlpha: (Float) -> Unit,
    globalTitle: String,
    onGlobalTitle: (String) -> Unit,
    globalSubtitle: String,
    onGlobalSubtitle: (String) -> Unit,
    inputHint: String,
    onInputHint: (String) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "设置",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAdvanced = !showAdvanced }) {
                Icon(Icons.Default.Tune, contentDescription = "更多设置")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.9f)
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("个人资料", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    myName,
                    onMyName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("我的名字") },
                    singleLine = true
                )
                OutlinedTextField(
                    myBio,
                    onMyBio,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("个人简介") }
                )
                Button(
                    onClick = onPickAvatar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("更换头像")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .9f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("外观", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("玻璃拟态 UI", modifier = Modifier.weight(1f))
                    Switch(checked = glassUi, onCheckedChange = onGlassUi)
                }
                Text("主题颜色", color = Muted)
                ColorPalette(themeHex, onThemeHex)
                Spacer(Modifier.height(12.dp))
                Text("我的气泡颜色", color = Muted)
                ColorPalette(myBubbleHex, onMyBubbleHex)
                Spacer(Modifier.height(12.dp))
                Text("AI 气泡颜色", color = Muted)
                ColorPalette(bubbleHex, onBubbleHex)
                Spacer(Modifier.height(12.dp))
                Text("气泡形状")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("圆角", "胶囊", "方圆").forEach { shape ->
                        FilterChip(
                            selected = bubbleShape == shape,
                            onClick = { onBubbleShape(shape) },
                            label = { Text(shape) }
                        )
                    }
                }
                Button(
                    onClick = onPickBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, null)
                    Spacer(Modifier.width(8.dp))
                    Text("设置全局背景图")
                }
                Button(
                    onClick = onPickBubbleBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Wallpaper, null)
                    Spacer(Modifier.width(8.dp))
                    Text("设置气泡背景图")
                }
                Text("背景图透明度")
                Slider(
                    value = backgroundAlpha,
                    onValueChange = onBackgroundAlpha,
                    valueRange = 0f..0.9f
                )
            }
        }

        if (showAdvanced) {
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("自定义文本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        globalTitle,
                        onGlobalTitle,
                        label = { Text("首页标题") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        globalSubtitle,
                        onGlobalSubtitle,
                        label = { Text("首页副标题") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        inputHint,
                        onInputHint,
                        label = { Text("输入框提示") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ColorPalette(selectedHex: String, onSelected: (String) -> Unit) {
    val colors = listOf(
        "#7289F5", "#8C7CF6", "#D78AF5", "#F18AB6",
        "#F28D75", "#F3B65F", "#E7D56C", "#79CBA1",
        "#60C5CF", "#76B8F5", "#FFFFFF", "#343845"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(6).forEach { rowColors ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowColors.forEach { hex ->
                    val color = hexColor(hex, Color.Gray)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (hex.equals(selectedHex, true)) 3.dp else 1.dp,
                                color = if (hex.equals(selectedHex, true)) Ink else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable { onSelected(hex) }
                    ) {
                        if (hex.equals(selectedHex, true)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选中",
                                modifier = Modifier.align(Alignment.Center),
                                tint = if (hex == "#FFFFFF") Ink else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedScreen(
    data: JSONObject,
    revision: Int,
    glassUi: Boolean,
    onSave: () -> Unit
) {
    var postText by remember { mutableStateOf("") }
    val posts = data.optJSONArray("posts") ?: JSONArray()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("动态", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            postText,
            onValueChange = { postText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("分享此刻…") },
            shape = RoundedCornerShape(22.dp)
        )
        Button(
            onClick = {
                if (postText.isNotBlank()) {
                    posts.put(
                        JSONObject()
                            .put("id", System.currentTimeMillis().toString())
                            .put("text", postText.trim())
                            .put("time", System.currentTimeMillis())
                            .put("likes", 0)
                    )
                    data.put("posts", posts)
                    postText = ""
                    onSave()
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("发布")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(
                (0 until posts.length()).mapNotNull { posts.optJSONObject(it) },
                key = { it.optString("id") }
            ) { post ->
                var likes by remember(post.optString("id"), revision) {
                    mutableIntStateOf(post.optInt("likes", 0))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = if (glassUi) .85f else 1f))
                        .padding(16.dp)
                ) {
                    Text(post.optString("text"), fontSize = 15.sp, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(post.optLong("time")),
                            fontSize = 11.sp,
                            color = Muted,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            likes++
                            post.put("likes", likes)
                            data.put("posts", posts)
                            onSave()
                        }) {
                            Icon(Icons.Default.FavoriteBorder, null)
                            Spacer(Modifier.width(4.dp))
                            Text("$likes")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(time: Long): String {
    return if (time > 0) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
    } else {
        "刚刚"
    }
}

@Composable
fun AiEditorDialog(
    existing: JSONObject?,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit
) {
    var name by remember { mutableStateOf(existing?.optString("name", "") ?: "") }
    var bio by remember { mutableStateOf(existing?.optString("bio", "") ?: "") }
    var prompt by remember { mutableStateOf(existing?.optString("prompt", "") ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(existing?.optString("gender", "其他") ?: "其他") }
    var relation by remember { mutableStateOf(existing?.optString("relationship", "朋友") ?: "朋友") }
    var likes by remember { mutableStateOf(existing?.optString("likes", "") ?: "") }
    var dislikes by remember { mutableStateOf(existing?.optString("dislikes", "") ?: "") }
    var hobbies by remember { mutableStateOf(existing?.optString("hobbies", "") ?: "") }
    var error by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(.94f)
                .fillMaxHeight(.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (existing == null) "创建 AI 好友" else "编辑 AI 好友",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    bio,
                    { bio = it },
                    label = { Text("简介") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    prompt,
                    { prompt = it },
                    label = { Text("详细人设") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    label = { Text("API Key（编辑时留空保留旧密钥）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("性别")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("男", "女", "其他").forEach {
                        FilterChip(
                            selected = gender == it,
                            onClick = { gender = it },
                            label = { Text(it) }
                        )
                    }
                }

                Text("与你的关系")
                OutlinedTextField(
                    relation,
                    { relation = it },
                    label = { Text("自定义关系") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    likes,
                    { likes = it },
                    label = { Text("喜欢什么") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dislikes,
                    { dislikes = it },
                    label = { Text("讨厌什么") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    hobbies,
                    { hobbies = it },
                    label = { Text("爱好") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Text(error, color = Color.Red)
                }
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "请填写 AI 名称"
                    } else {
                        val ai = existing ?: JSONObject()
                            .put("id", System.currentTimeMillis().toString())
                            .put("messages", JSONArray())

                        ai.put("name", name.trim())
                        ai.put("bio", bio)
                        ai.put("prompt", prompt)
                        ai.put("gender", gender)
                        ai.put("relationship", relation)
                        ai.put("likes", likes)
                        ai.put("dislikes", dislikes)
                        ai.put("hobbies", hobbies)

                        if (apiKey.isNotBlank()) ai.put("apiKey", apiKey.trim())
                        if (!ai.has("messages")) ai.put("messages", JSONArray())

                        onSave(ai)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(if (existing == null) "创建 AI" else "保存修改")
            }
        }
    }
}

@Composable
fun PickedPhoto(
    uri: String,
    modifier: Modifier = Modifier,
    rounded: Boolean = true
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { loadLocalBitmap(context, uri) }

    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "已选择的图片",
            modifier = if (rounded) {
                modifier.clip(RoundedCornerShape(16.dp))
            } else {
                modifier.clip(RectangleShape)
            },
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun FloatingTypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing-dots")

    Row(
        modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val y by transition.animateFloat(
                initialValue = 2f,
                targetValue = -5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 420,
                        delayMillis = index * 120,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "typing-dot-$index"
            )
            Box(
                Modifier
                    .offset(y = y.dp)
                    .size(7.dp)
                    .background(Color(0xFF87948D), CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    p: JSONObject,
    revision: Int,
    myName: String,
    myAvatar: String,
    composer: String,
    onComposer: (String) -> Unit,
    busy: Boolean,
    error: String,
    backgroundUri: String,
    bubbleBackgroundUri: String,
    glassUi: Boolean,
    bubbleHex: String,
    myBubbleHex: String,
    bubbleShape: String,
    backgroundAlpha: Float,
    inputHint: String,
    onBack: () -> Unit,
    onQuote: (String) -> Unit,
    onDeleteMessage: (Int) -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit
) {
    val arr = p.optJSONArray("messages") ?: JSONArray()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(arr.length(), revision, busy) {
        if (arr.length() > 0) {
            listState.animateScrollToItem(arr.length() - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = if (glassUi) .86f else 1f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null)
            }
            Avatar(p.optString("avatar"), p.optString("name"), 42)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(p.optString("name"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    p.optString("relationship"),
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    (0 until arr.length()).map { arr.getJSONObject(it) },
                    key = {
                        it.optString("id") +
                            "-r" + revision
                    }
                ) { m ->
                    val user = m.optString("role") == "user"
                    var menuOpen by remember(m.optString("content"), arr.length()) {
                        mutableStateOf(false)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (!user) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(42.dp)
                            ) {
                                Avatar(p.optString("avatar"), p.optString("name"), 32)
                                Text(
                                    p.optString("name"),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    color = Muted
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }

                        Column(
                            horizontalAlignment = if (user) Alignment.End else Alignment.Start
                        ) {
                            Box {
                                val bubbleColor = hexColor(
                                    if (user) myBubbleHex else bubbleHex,
                                    if (user) Color(0xFFDCE8FF) else Color.White
                                ).copy(alpha = if (glassUi) .84f else 1f)

                                val shape = when (bubbleShape) {
                                    "胶囊" -> RoundedCornerShape(28.dp)
                                    "方圆" -> RoundedCornerShape(9.dp)
                                    else -> RoundedCornerShape(20.dp)
                                }

                                Box(
                                    Modifier
                                        .widthIn(max = 300.dp)
                                        .clip(shape)
                                        .background(bubbleColor)
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = { menuOpen = true }
                                        )
                                ) {
                                    if (bubbleBackgroundUri.isNotBlank()) {
                                        PickedPhoto(
                                            bubbleBackgroundUri,
                                            Modifier.matchParentSize()
                                                .graphicsLayer(alpha = 1f - backgroundAlpha),
                                            rounded = false
                                        )
                                    }

                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        if (!user && m.optString("content") == "---") {
                                            FloatingTypingIndicator()
                                        } else {
                                            Text(
                                                m.optString("content"),
                                                fontSize = 14.sp,
                                                color = Ink
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = menuOpen,
                                            onDismissRequest = { menuOpen = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("复制") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ContentCopy, null)
                                                },
                                                onClick = {
                                                    clipboard.setText(
                                                        AnnotatedString(m.optString("content"))
                                                    )
                                                    menuOpen = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("引用") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.FormatQuote, null)
                                                },
                                                onClick = {
                                                    onQuote(
                                                        "「${m.optString("content").take(500)}」\n"
                                                    )
                                                    menuOpen = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("删除此消息") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, null)
                                                },
                                                onClick = {
                                                    val idx = (0 until arr.length())
                                                        .firstOrNull {
                                                            arr.optJSONObject(it) === m
                                                        }
                                                    if (idx != null) onDeleteMessage(idx)
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val timeValue = m.optLong("time", 0L)
                            val timeText = if (timeValue > 0L) {
                                SimpleDateFormat(
                                    "HH:mm",
                                    Locale.getDefault()
                                ).format(Date(timeValue))
                            } else {
                                "刚刚"
                            }

                            Text(
                                if (user) "已发送 · $timeText"
                                else if (m.optString("content") == "---") "正在输入…"
                                else timeText,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                color = Muted
                            )
                        }

                        if (user) {
                            Spacer(Modifier.width(6.dp))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(42.dp)
                            ) {
                                Avatar(myAvatar, myName, 32)
                                Text(
                                    myName,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    color = Muted
                                )
                            }
                        }
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color.Red,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = if (glassUi) .86f else 1f),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPickImage) {
                Icon(
                    Icons.Default.ImageSearch,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedTextField(
                composer,
                onComposer,
                modifier = Modifier.weight(1f),
                placeholder = { Text(inputHint, fontSize = 13.sp) },
                shape = RoundedCornerShape(22.dp),
                maxLines = 3,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )
            IconButton(onClick = onSend, enabled = !busy) {
                Icon(
                    Icons.Default.Send,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
