package com.deepline.privateapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import android.media.AudioManager
import android.media.ToneGenerator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Green = Color(0xFF06C755)
private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF7A8580)
private val Page = Color(0xFFF4F7FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var accent by remember {
                mutableStateOf(
                    try {
                        Color(
                            android.graphics.Color.parseColor(
                                LocalStore.load(context).optString("theme", "#06C755")
                            )
                        )
                    } catch (_: Exception) {
                        Green
                    }
                )
            }
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = accent,
                    background = Page
                )
            ) {
                DeepLineApp(onThemeChange = { accent = it })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeepLineApp(onThemeChange: (Color) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf(LocalStore.init(context)) }
    var revision by remember { mutableIntStateOf(0) }
    val revisionSnapshot = revision

    fun persist() {
        LocalStore.save(context, data)
        revision++
    }

    var tab by remember { mutableStateOf("聊天") }
    var page by remember { mutableStateOf("home") }
    var currentId by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var personaApiKey by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf(data.optString("myName", "我")) }
    var myBio by remember { mutableStateOf(data.optString("myBio")) }
    var themeHex by remember { mutableStateOf(data.optString("theme", "#06C755")) }

    var personaName by remember { mutableStateOf("") }
    var personaBio by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("其他") }
    var relation by remember { mutableStateOf("朋友") }
    var likes by remember { mutableStateOf("") }
    var dislikes by remember { mutableStateOf("") }
    var hobbies by remember { mutableStateOf("") }

    var feedText by remember { mutableStateOf("") }
    var postImage by remember { mutableStateOf("") }
    var commentPostId by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var autoAiEnabled by remember {
        mutableStateOf(data.optBoolean("autoAiEnabled", false))
    }

    var editAvatar by remember { mutableStateOf("") }
    var editingPersonaId by remember { mutableStateOf("") }
    var glassUi by remember { mutableStateOf(data.optBoolean("glassUi", true)) }
    var soundEnabled by remember { mutableStateOf(data.optBoolean("soundEnabled", true)) }
    var bubbleHex by remember { mutableStateOf(data.optString("bubbleColor", "#E5F3FF")) }
    var myBubbleHex by remember { mutableStateOf(data.optString("myBubbleColor", "#DCFCE7")) }
    var bubbleShape by remember {
        mutableStateOf(data.optString("bubbleShape", "柔和圆角"))
    }
    var bubbleBackgroundUri by remember {
        mutableStateOf(data.optString("bubbleBackground", ""))
    }
    var backgroundAlpha by remember {
        mutableFloatStateOf(data.optDouble("backgroundAlpha", 0.22).toFloat())
    }
    var fontChoice by remember { mutableStateOf(data.optString("fontChoice", "系统")) }
    var appTitle by remember { mutableStateOf(data.optString("appTitle", "DeepLine")) }
    var inputHint by remember { mutableStateOf(data.optString("inputHint", "发送消息…")) }
    var selectedImageTarget by remember { mutableStateOf("avatar") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val target = selectedImageTarget
            val storedImage = try {
                copyPickedImageToAppStorage(context, uri, target)
            } catch (e: Exception) {
                error = "图片保存失败：${e.message ?: "请重新选择图片"}"
                ""
            }

            if (storedImage.isNotBlank()) {
                when (target) {
                    "myAvatar" -> {
                        data.put("myAvatar", storedImage)
                        persist()
                    }

                    "personaAvatar" -> editAvatar = storedImage

                    "background" -> {
                        data.put("background", storedImage)
                        persist()
                    }

                    "bubbleBackground" -> {
                        bubbleBackgroundUri = storedImage
                        data.put("bubbleBackground", storedImage)
                        persist()
                    }

                    "chatImage" -> {
                        try {
                            val image = InputImage.fromFilePath(
                                context,
                                Uri.fromFile(File(storedImage))
                            )
                            val labeler = ImageLabeling.getClient(
                                ImageLabelerOptions.DEFAULT_OPTIONS
                            )

                            labeler.process(image)
                                .addOnSuccessListener { labels ->
                                    val found = labels
                                        .sortedByDescending { it.confidence }
                                        .take(8)
                                        .joinToString("、") {
                                            "${it.text} ${(it.confidence * 100).toInt()}%"
                                        }

                                    composer = if (found.isBlank()) {
                                        "我选了一张图片，请帮我判断图片内容。设备识图暂时没有识别到明确物体。"
                                    } else {
                                        "请根据这张图片的识图结果帮我分析：$found。请说明这些物体可能是什么、图片大致内容。"
                                    }

                                    error = if (found.isBlank()) {
                                        "识图未找到明确标签；可以尝试更清晰的图片。"
                                    } else {
                                        "本地识图完成，可发送识别结果给 AI。"
                                    }
                                    labeler.close()
                                }
                                .addOnFailureListener { e ->
                                    error = "识图失败：${e.message ?: "请重试"}"
                                    labeler.close()
                                }
                        } catch (e: Exception) {
                            error = "无法读取图片：${e.message ?: "请重新选择"}"
                        }
                    }

                    "postImage" -> postImage = storedImage
                }
            }
        }
    }

    fun saveData() {
        data.put("myName", myName)
            .put("myBio", myBio)
            .put("theme", themeHex)

        try {
            onThemeChange(
                Color(android.graphics.Color.parseColor(themeHex))
            )
        } catch (_: Exception) {
        }

        persist()
    }

    fun personas(): JSONArray =
        data.optJSONArray("personas")
            ?: JSONArray().also { data.put("personas", it) }

    fun findPersona(id: String): JSONObject? {
        val all = personas()
        for (i in 0 until all.length()) {
            if (all.getJSONObject(i).optString("id") == id) {
                return all.getJSONObject(i)
            }
        }
        return null
    }

    fun createPersona() {
        if (personaName.isBlank() || persona.isBlank()) {
            error = "名字和人设为必填项"
            return
        }

        if (personaApiKey.isBlank() && editingPersonaId.isBlank()) {
            error = "请填写这个 AI 使用的 API Key"
            return
        }

        if (editingPersonaId.isBlank() && personas().length() >= 10) {
            error = "最多创建 10 个聊天对象"
            return
        }

        val existing = if (editingPersonaId.isNotBlank()) {
            findPersona(editingPersonaId)
        } else {
            null
        }

        if (existing != null) {
            existing.put("name", personaName)
                .put("bio", personaBio)
                .put("persona", persona)
                .put("gender", gender)
                .put("relationship", relation)
                .put("likes", likes)
                .put("dislikes", dislikes)
                .put("hobbies", hobbies)

            if (editAvatar.isNotBlank()) existing.put("avatar", editAvatar)
            if (personaApiKey.isNotBlank()) existing.put("apiKey", personaApiKey.trim())
        } else {
            val created = JSONObject()
                .put("id", System.currentTimeMillis().toString())
                .put("name", personaName)
                .put("bio", personaBio)
                .put("persona", persona)
                .put("gender", gender)
                .put("relationship", relation)
                .put("likes", likes)
                .put("dislikes", dislikes)
                .put("hobbies", hobbies)
                .put("avatar", editAvatar)
                .put("apiKey", personaApiKey.trim())
                .put("messages", JSONArray())

            personas().put(created)
        }

        persist()
        personaName = ""
        personaBio = ""
        persona = ""
        personaApiKey = ""
        likes = ""
        dislikes = ""
        hobbies = ""
        editAvatar = ""
        editingPersonaId = ""
        error = ""
        page = "home"
        tab = "聊天"
    }

    LaunchedEffect(autoAiEnabled) {
        if (autoAiEnabled) {
            while (true) {
                kotlinx.coroutines.delay(60L * 60L * 1000L)

                val all = personas()
                for (i in 0 until all.length()) {
                    val ai = all.optJSONObject(i) ?: continue

                    try {
                        val history = JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    "请根据你的人设，写一条自然、简短、像你自己主动分享的动态。只返回动态正文。"
                                )
                        )

                        val system =
                            "你正在扮演 ${ai.optString("name")}。" +
                            "人设：${ai.optString("persona")}。" +
                            "喜欢：${ai.optString("likes")}。" +
                            "爱好：${ai.optString("hobbies")}。"

                        val postText = DeepSeek.reply(
                            ai.optString("apiKey"),
                            system,
                            history
                        )

                        val posts = data.optJSONArray("posts")
                            ?: JSONArray().also { data.put("posts", it) }

                        posts.put(
                            0,
                            JSONObject()
                                .put("id", System.currentTimeMillis().toString())
                                .put("author", ai.optString("name"))
                                .put("avatar", ai.optString("avatar"))
                                .put("content", postText)
                                .put("likes", 0)
                                .put("comments", JSONArray())
                                .put("image", "")
                                .put("ai", true)
                        )

                        val messages = ai.optJSONArray("messages")
                            ?: JSONArray().also { ai.put("messages", it) }

                        messages.put(
                            JSONObject()
                                .put("role", "assistant")
                                .put("content", "我刚刚发了一条动态：$postText")
                        )

                        LocalStore.save(context, data)
                    } catch (_: Exception) {
                    }
                }

                data = LocalStore.load(context)
                revision++
            }
        }
    }

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontFamily = when (fontChoice) {
                "圆润" -> FontFamily.SansSerif
                "衬线" -> FontFamily.Serif
                "等宽" -> FontFamily.Monospace
                else -> FontFamily.Default
            }
        )
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = Page
        ) {
            Box(Modifier.fillMaxSize()) {
                if (data.optString("background").isNotBlank()) {
                    PickedPhoto(
                        data.optString("background"),
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = 1f - backgroundAlpha),
                        rounded = false
                    )
                }

                AnimatedContent(
                    page,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "pages"
                ) { screen ->
                    when (screen) {
                        "create" -> PersonaEditor(
                            editing = editingPersonaId.isNotBlank(),
                            name = personaName,
                            onName = { personaName = it },
                            bio = personaBio,
                            onBio = { personaBio = it },
                            persona = persona,
                            onPersona = { persona = it },
                            apiKey = personaApiKey,
                            onApiKey = { personaApiKey = it },
                            gender = gender,
                            onGender = { gender = it },
                            relation = relation,
                            onRelation = { relation = it },
                            likes = likes,
                            onLikes = { likes = it },
                            dislikes = dislikes,
                            onDislikes = { dislikes = it },
                            hobbies = hobbies,
                            onHobbies = { hobbies = it },
                            avatar = editAvatar,
                            onPickAvatar = {
                                selectedImageTarget = "personaAvatar"
                                imagePicker.launch("image/*")
                            },
                            error = error,
                            onBack = { page = "home" },
                            onCreate = { createPersona() }
                        )

                        "chat" -> {
                            val p = findPersona(currentId)

                            if (p == null) {
                                page = "home"
                            } else {
                                ChatScreen(
                                    p = p,
                                    revision = revisionSnapshot,
                                    myName = myName,
                                    myAvatar = data.optString("myAvatar"),
                                    composer = composer,
                                    onComposer = { composer = it },
                                    busy = busy,
                                    error = error,
                                    backgroundUri = data.optString("background"),
                                    bubbleBackgroundUri = bubbleBackgroundUri,
                                    glassUi = glassUi,
                                    bubbleHex = bubbleHex,
                                    myBubbleHex = myBubbleHex,
                                    bubbleShape = bubbleShape,
                                    backgroundAlpha = backgroundAlpha,
                                    inputHint = inputHint,
                                    onBack = { page = "home" },
                                    onQuote = { quote -> composer = quote },
                                    onDeleteMessage = { index ->
                                        val messages = p.optJSONArray("messages")
                                            ?: JSONArray()
                                        messages.remove(index)
                                        persist()
                                    },
                                    onPickImage = {
                                        selectedImageTarget = "chatImage"
                                        imagePicker.launch("image/*")
                                    },
                                    onSend = {
                                        val msg = composer.trim()

                                        if (msg.isNotEmpty() && !busy) {
                                            val arr = p.optJSONArray("messages")
                                                ?: JSONArray().also {
                                                    p.put("messages", it)
                                                }

                                            arr.put(
                                                JSONObject()
                                                    .put("id", UUID.randomUUID().toString())
                                                    .put("time", System.currentTimeMillis())
                                                    .put("role", "user")
                                                    .put("content", msg)
                                            )

                                            arr.put(
                                                JSONObject()
                                                    .put("id", UUID.randomUUID().toString())
                                                    .put("time", System.currentTimeMillis())
                                                    .put("role", "assistant")
                                                    .put("content", "---")
                                            )

                                            composer = ""
                                            persist()

                                            if (soundEnabled) {
                                                try {
                                                    ToneGenerator(
                                                        AudioManager.STREAM_NOTIFICATION,
                                                        75
                                                    ).startTone(
                                                        ToneGenerator.TONE_PROP_ACK,
                                                        100
                                                    )
                                                } catch (_: Exception) {
                                                }
                                            }

                                            scope.launch {
                                                busy = true
                                                error = ""

                                                try {
                                                    val history = JSONArray()
                                                    val start = maxOf(0, arr.length() - 31)

                                                    for (i in start until arr.length() - 1) {
                                                        val m = arr.getJSONObject(i)
                                                        if (m.optString("content") != "---") {
                                                            history.put(
                                                                JSONObject()
                                                                    .put("role", m.optString("role"))
                                                                    .put("content", m.optString("content"))
                                                            )
                                                        }
                                                    }

                                                    val system =
                                                        "你正在扮演 AI 角色。" +
                                                        "名字：${p.optString("name")}。" +
                                                        "简介：${p.optString("bio")}。" +
                                                        "人设：${p.optString("persona")}。" +
                                                        "性别：${p.optString("gender")}。" +
                                                        "与用户关系：${p.optString("relationship")}。" +
                                                        "喜欢：${p.optString("likes")}。" +
                                                        "讨厌：${p.optString("dislikes")}。" +
                                                        "爱好：${p.optString("hobbies")}。" +
                                                        "请遵循人设并用用户使用的语言自然回复。你是 AI，不要声称现实中不存在的经历。"

                                                    val reply = DeepSeek.reply(
                                                        p.optString("apiKey"),
                                                        system,
                                                        history
                                                    )

                                                    if (soundEnabled) {
                                                        try {
                                                            ToneGenerator(
                                                                AudioManager.STREAM_NOTIFICATION,
                                                                75
                                                            ).startTone(
                                                                ToneGenerator.TONE_PROP_BEEP2,
                                                                130
                                                            )
                                                        } catch (_: Exception) {
                                                        }
                                                    }

                                                    arr.put(
                                                        arr.length() - 1,
                                                        JSONObject()
                                                            .put("id", UUID.randomUUID().toString())
                                                            .put("time", System.currentTimeMillis())
                                                            .put("role", "assistant")
                                                            .put("content", reply)
                                                    )
                                                    persist()
                                                } catch (e: Exception) {
                                                    arr.put(
                                                        arr.length() - 1,
                                                        JSONObject()
                                                            .put("id", UUID.randomUUID().toString())
                                                            .put("time", System.currentTimeMillis())
                                                            .put("role", "assistant")
                                                            .put("content", "请求失败：${e.message}")
                                                    )
                                                    persist()
                                                    error = e.message ?: "请求失败"
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        else -> Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                                                Color.White.copy(alpha = .82f)
                                            )
                                        ),
                                        RoundedCornerShape(
                                            bottomStart = 28.dp,
                                            bottomEnd = 28.dp
                                        )
                                    )
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(data.optString("myAvatar"), myName, 44)
                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        when (tab) {
                                            "聊天" -> appTitle
                                            "动态" -> "动态广场"
                                            else -> "个性化设置"
                                        },
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Ink
                                    )
                                    Text(
                                        if (tab == "聊天") "和你的 AI 好友聊聊吧"
                                        else "DeepLine · 私人空间",
                                        fontSize = 12.sp,
                                        color = Muted
                                    )
                                }

                                if (tab == "聊天") {
                                    IconButton(
                                        onClick = {
                                            page = "create"
                                        },
                                        modifier = Modifier.background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                                            CircleShape
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(27.dp)
                                        )
                                    }
                                }
                            }

                            when (tab) {
                                "聊天" -> {
                                    val all = personas()

                                    LazyColumn(
                                        Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 14.dp)
                                    ) {
                                        if (all.length() == 0) {
                                            item {
                                                EmptyState(
                                                    "还没有聊天对象",
                                                    "点击右上角 + 创建第一个 AI"
                                                )
                                            }
                                        }

                                        items(
                                            (0 until all.length()).map {
                                                all.getJSONObject(it)
                                            },
                                            key = {
                                                it.optString("id") + "-r" + revisionSnapshot
                                            }
                                        ) { p ->
                                            var menuOpen by remember(
                                                p.optString("id"),
                                                revisionSnapshot
                                            ) {
                                                mutableStateOf(false)
                                            }

                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 5.dp)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                Color.White.copy(
                                                                    alpha = if (glassUi) .84f else 1f
                                                                ),
                                                                Color(0xFFF0F3FF).copy(
                                                                    alpha = if (glassUi) .84f else 1f
                                                                )
                                                            )
                                                        ),
                                                        RoundedCornerShape(24.dp)
                                                    )
                                                    .animateContentSize()
                                                    .combinedClickable(
                                                        onClick = {
                                                            currentId = p.optString("id")
                                                            page = "chat"
                                                        },
                                                        onLongClick = {
                                                            menuOpen = true
                                                        }
                                                    )
                                                    .padding(horizontal = 15.dp, vertical = 15.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Avatar(
                                                    p.optString("avatar"),
                                                    p.optString("name"),
                                                    52
                                                )
                                                Spacer(Modifier.width(12.dp))

                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        p.optString("name"),
                                                        fontWeight = FontWeight.SemiBold
                                                    )

                                                    val lastMessage =
                                                        p.optJSONArray("messages")?.let { messages ->
                                                            if (messages.length() > 0) {
                                                                messages.optJSONObject(
                                                                    messages.length() - 1
                                                                )?.optString("content")
                                                            } else {
                                                                null
                                                            }
                                                        }

                                                    val preview =
                                                        if (lastMessage == "---") {
                                                            "正在输入…"
                                                        } else {
                                                            lastMessage?.takeIf { it.isNotBlank() }
                                                                ?: p.optString("bio")
                                                                    .ifBlank { p.optString("persona") }
                                                        }

                                                    Text(
                                                        preview,
                                                        maxLines = 1,
                                                        color = Color.Gray,
                                                        fontSize = 13.sp
                                                    )
                                                }

                                                Box {
                                                    IconButton(
                                                        onClick = { menuOpen = true }
                                                    ) {
                                                        Icon(
                                                            Icons.Default.MoreVert,
                                                            null,
                                                            tint = Color.Gray
                                                        )
                                                    }

                                                    DropdownMenu(
                                                        expanded = menuOpen,
                                                        onDismissRequest = {
                                                            menuOpen = false
                                                        }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("编辑 AI") },
                                                            leadingIcon = {
                                                                Icon(Icons.Default.Edit, null)
                                                            },
                                                            onClick = {
                                                                menuOpen = false
                                                                editingPersonaId =
                                                                    p.optString("id")
                                                                personaName =
                                                                    p.optString("name")
                                                                personaBio =
                                                                    p.optString("bio")
                                                                persona =
                                                                    p.optString("persona")
                                                                gender =
                                                                    p.optString("gender", "其他")
                                                                relation =
                                                                    p.optString("relationship", "朋友")
                                                                likes = p.optString("likes")
                                                                dislikes = p.optString("dislikes")
                                                                hobbies = p.optString("hobbies")
                                                                editAvatar = p.optString("avatar")
                                                                personaApiKey = ""
                                                                error = ""
                                                                page = "create"
                                                            }
                                                        )

                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    "删除 AI",
                                                                    color = Color(0xFFD64255)
                                                                )
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    Icons.Default.Delete,
                                                                    null,
                                                                    tint = Color(0xFFD64255)
                                                                )
                                                            },
                                                            onClick = {
                                                                menuOpen = false
                                                                val old = personas()
                                                                val keep = JSONArray()

                                                                for (i in 0 until old.length()) {
                                                                    val item = old.optJSONObject(i)
                                                                    if (
                                                                        item?.optString("id") !=
                                                                        p.optString("id") &&
                                                                        item != null
                                                                    ) {
                                                                        keep.put(item)
                                                                    }
                                                                }

                                                                data.put("personas", keep)
                                                                persist()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                "动态" -> {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    Color.White.copy(
                                                        alpha = if (glassUi) .86f else 1f
                                                    ),
                                                    RoundedCornerShape(24.dp)
                                                )
                                                .animateContentSize()
                                                .padding(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Avatar(
                                                    data.optString("myAvatar"),
                                                    myName,
                                                    42
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    "分享你的日常",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Ink
                                                )
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            OutlinedTextField(
                                                feedText,
                                                { feedText = it },
                                                placeholder = {
                                                    Text("今天有什么新鲜事？")
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2,
                                                shape = RoundedCornerShape(18.dp)
                                            )

                                            if (postImage.isNotBlank()) {
                                                Spacer(Modifier.height(8.dp))
                                                PickedPhoto(
                                                    postImage,
                                                    Modifier.fillMaxWidth().height(150.dp)
                                                )
                                                TextButton(onClick = { postImage = "" }) {
                                                    Text("移除图片")
                                                }
                                            }

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        selectedImageTarget = "postImage"
                                                        imagePicker.launch("image/*")
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Image, null)
                                                    Spacer(Modifier.width(5.dp))
                                                    Text("添加图片")
                                                }

                                                Button(
                                                    onClick = {
                                                        if (feedText.isNotBlank()) {
                                                            val posts =
                                                                data.optJSONArray("posts")
                                                                    ?: JSONArray()

                                                            posts.put(
                                                                0,
                                                                JSONObject()
                                                                    .put(
                                                                        "id",
                                                                        UUID.randomUUID().toString()
                                                                    )
                                                                    .put("author", myName)
                                                                    .put(
                                                                        "avatar",
                                                                        data.optString("myAvatar")
                                                                    )
                                                                    .put("content", feedText.trim())
                                                                    .put("likes", 0)
                                                                    .put("comments", JSONArray())
                                                                    .put("image", postImage)
                                                                    .put("ai", false)
                                                            )

                                                            data.put("posts", posts)
                                                            feedText = ""
                                                            postImage = ""
                                                            persist()
                                                        }
                                                    }
                                                ) {
                                                    Text("发布")
                                                }
                                            }
                                        }

                                        val posts = data.optJSONArray("posts") ?: JSONArray()

                                        LazyColumn(
                                            Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(vertical = 12.dp)
                                        ) {
                                            items(
                                                (0 until posts.length()).map {
                                                    posts.getJSONObject(it)
                                                },
                                                key = {
                                                    it.optString("id") + "-r" + revisionSnapshot
                                                }
                                            ) { post ->
                                                Column(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            Color.White.copy(
                                                                alpha = if (glassUi) .88f else 1f
                                                            ),
                                                            RoundedCornerShape(22.dp)
                                                        )
                                                        .padding(15.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Avatar(
                                                            post.optString("avatar"),
                                                            post.optString("author"),
                                                            40
                                                        )
                                                        Spacer(Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                post.optString("author", "我"),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                "私人动态",
                                                                color = Muted,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    Spacer(Modifier.height(10.dp))

                                                    Text(
                                                        post.optString("content"),
                                                        fontSize = 15.sp
                                                    )

                                                    if (post.optString("image").isNotBlank()) {
                                                        Spacer(Modifier.height(10.dp))
                                                        PickedPhoto(
                                                            post.optString("image"),
                                                            Modifier.fillMaxWidth().height(180.dp)
                                                        )
                                                    }

                                                    Spacer(Modifier.height(8.dp))

                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        TextButton(
                                                            onClick = {
                                                                post.put(
                                                                    "likes",
                                                                    post.optInt("likes") + 1
                                                                )
                                                                persist()
                                                            }
                                                        ) {
                                                            Icon(
                                                                Icons.Default.FavoriteBorder,
                                                                null
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("${post.optInt("likes")}")
                                                        }

                                                        TextButton(
                                                            onClick = {
                                                                commentPostId =
                                                                    post.optString("id")
                                                                commentText = ""
                                                            }
                                                        ) {
                                                            Icon(Icons.Default.ChatBubbleOutline, null)
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("评论")
                                                        }
                                                    }

                                                    if (commentPostId == post.optString("id")) {
                                                        OutlinedTextField(
                                                            commentText,
                                                            { commentText = it },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            placeholder = {
                                                                Text("写下评论…")
                                                            }
                                                        )
                                                        Row(
                                                            horizontalArrangement = Arrangement.End,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    commentPostId = ""
                                                                }
                                                            ) {
                                                                Text("取消")
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    if (commentText.isNotBlank()) {
                                                                        val comments =
                                                                            post.optJSONArray("comments")
                                                                                ?: JSONArray()

                                                                        comments.put(
                                                                            JSONObject()
                                                                                .put("author", myName)
                                                                                .put("content", commentText.trim())
                                                                                .put("time", System.currentTimeMillis())
                                                                        )

                                                                        post.put("comments", comments)
                                                                        persist()
                                                                        commentPostId = ""
                                                                    }
                                                                }
                                                            ) {
                                                                Text("发送")
                                                            }
                                                        }
                                                    }

                                                    val comments =
                                                        post.optJSONArray("comments") ?: JSONArray()

                                                    for (i in 0 until comments.length()) {
                                                        val comment =
                                                            comments.optJSONObject(i) ?: continue
                                                        Text(
                                                            "${comment.optString("author")}: ${comment.optString("content")}",
                                                            modifier = Modifier.padding(
                                                                top = 4.dp
                                                            ),
                                                            fontSize = 12.sp,
                                                            color = Muted
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    SettingsScreen(
                                        myName = myName,
                                        onMyName = {
                                            myName = it
                                            saveData()
                                        },
                                        myBio = myBio,
                                        onMyBio = {
                                            myBio = it
                                            saveData()
                                        },
                                        themeHex = themeHex,
                                        onThemeHex = {
                                            themeHex = it
                                            saveData()
                                        },
                                        glassUi = glassUi,
                                        onGlassUi = {
                                            glassUi = it
                                            data.put("glassUi", it)
                                            persist()
                                        },
                                        soundEnabled = soundEnabled,
                                        onSoundEnabled = {
                                            soundEnabled = it
                                            data.put("soundEnabled", it)
                                            persist()
                                        },
                                        bubbleHex = bubbleHex,
                                        onBubbleHex = {
                                            bubbleHex = it
                                            data.put("bubbleColor", it)
                                            persist()
                                        },
                                        myBubbleHex = myBubbleHex,
                                        onMyBubbleHex = {
                                            myBubbleHex = it
                                            data.put("myBubbleColor", it)
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
                                        fontChoice = fontChoice,
                                        onFontChoice = {
                                            fontChoice = it
                                            data.put("fontChoice", it)
                                            persist()
                                        },
                                        inputHint = inputHint,
                                        onInputHint = {
                                            inputHint = it
                                            data.put("inputHint", it)
                                            persist()
                                        },
                                        appTitle = appTitle,
                                        onAppTitle = {
                                            appTitle = it
                                            data.put("appTitle", it)
                                            persist()
                                        },
                                        onPickAvatar = {
                                            selectedImageTarget = "myAvatar"
                                            imagePicker.launch("image/*")
                                        },
                                        onPickBackground = {
                                            selectedImageTarget = "background"
                                            imagePicker.launch("image/*")
                                        },
                                        onPickBubbleBackground = {
                                            selectedImageTarget = "bubbleBackground"
                                            imagePicker.launch("image/*")
                                        }
                                    )
                                }
                            }

                            NavigationBar(
                                containerColor = Color.White.copy(
                                    alpha = if (glassUi) .88f else 1f
                                )
                            ) {
                                listOf(
                                    "聊天" to Icons.Default.ChatBubbleOutline,
                                    "动态" to Icons.Default.DynamicFeed,
                                    "设置" to Icons.Default.Settings
                                ).forEach { (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == label,
                                        onClick = { tab = label },
                                        icon = { Icon(icon, null) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (commentPostId.isNotBlank()) {
            // 评论输入框在动态卡片内显示，此处保留状态供列表使用。
        }
    }
}

@Composable
fun Avatar(path: String, name: String, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        try {
            if (path.isBlank()) null
            else BitmapFactory.decodeFile(path)
        } catch (_: Exception) {
            null
        }
    }

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
                color = Green
            )
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline,
            null,
            modifier = Modifier.size(54.dp),
            tint = Muted
        )
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = Muted)
    }
}

@Composable
fun SettingsScreen(
    myName: String,
    onMyName: (String) -> Unit,
    myBio: String,
    onMyBio: (String) -> Unit,
    themeHex: String,
    onThemeHex: (String) -> Unit,
    glassUi: Boolean,
    onGlassUi: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabled: (Boolean) -> Unit,
    bubbleHex: String,
    onBubbleHex: (String) -> Unit,
    myBubbleHex: String,
    onMyBubbleHex: (String) -> Unit,
    bubbleShape: String,
    onBubbleShape: (String) -> Unit,
    backgroundAlpha: Float,
    onBackgroundAlpha: (Float) -> Unit,
    fontChoice: String,
    onFontChoice: (String) -> Unit,
    inputHint: String,
    onInputHint: (String) -> Unit,
    appTitle: String,
    onAppTitle: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onPickBackground: () -> Unit,
    onPickBubbleBackground: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        Text("设置", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("个人资料", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
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
            }

            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("外观设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("玻璃拟态 UI", modifier = Modifier.weight(1f))
                            Switch(glassUi, onGlassUi)
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("发送音效", modifier = Modifier.weight(1f))
                            Switch(soundEnabled, onSoundEnabled)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("主题颜色", color = Muted)
                        ColorPalette(themeHex, onThemeHex)

                        Spacer(Modifier.height(12.dp))
                        Text("我的气泡颜色", color = Muted)
                        ColorPalette(myBubbleHex, onMyBubbleHex)

                        Spacer(Modifier.height(12.dp))
                        Text("AI 气泡颜色", color = Muted)
                        ColorPalette(bubbleHex, onBubbleHex)

                        Spacer(Modifier.height(12.dp))
                        Text("气泡样式")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("柔和圆角", "胶囊", "方圆").forEach { shape ->
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

                        Spacer(Modifier.height(12.dp))
                        Text("全局字体")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("系统", "圆润", "衬线", "等宽").forEach { font ->
                                FilterChip(
                                    selected = fontChoice == font,
                                    onClick = { onFontChoice(font) },
                                    label = { Text(font) }
                                )
                            }
                        }

                        OutlinedTextField(
                            appTitle,
                            onAppTitle,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("首页标题") },
                            singleLine = true
                        )

                        OutlinedTextField(
                            inputHint,
                            onInputHint,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("输入框提示文字") },
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPalette(selectedHex: String, onSelected: (String) -> Unit) {
    val colors = listOf(
        "#06C755", "#8C7CF6", "#D78AF5", "#F18AB6",
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
                    val color = try {
                        Color(android.graphics.Color.parseColor(hex))
                    } catch (_: Exception) {
                        Color.Gray
                    }

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
                            .clickable { onSelected(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hex.equals(selectedHex, true)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选中",
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
fun PersonaEditor(
    editing: Boolean,
    name: String,
    onName: (String) -> Unit,
    bio: String,
    onBio: (String) -> Unit,
    persona: String,
    onPersona: (String) -> Unit,
    apiKey: String,
    onApiKey: (String) -> Unit,
    gender: String,
    onGender: (String) -> Unit,
    relation: String,
    onRelation: (String) -> Unit,
    likes: String,
    onLikes: (String) -> Unit,
    dislikes: String,
    onDislikes: (String) -> Unit,
    hobbies: String,
    onHobbies: (String) -> Unit,
    avatar: String,
    onPickAvatar: () -> Unit,
    error: String,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null)
            }
            Text(
                if (editing) "编辑 AI 人设" else "创建 AI 人设",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Avatar(avatar, name, 72)
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onPickAvatar) {
                        Text("从相册选择头像")
                    }
                }

                OutlinedTextField(
                    name,
                    onName,
                    label = { Text("名字 *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    bio,
                    onBio,
                    label = { Text("简介") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    persona,
                    onPersona,
                    label = { Text("人设 *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    apiKey,
                    onApiKey,
                    label = {
                        Text(
                            if (editing) "更新 API Key（留空保持原密钥）"
                            else "DeepSeek API Key *"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Text(
                    if (editing) {
                        "编辑资料不会删除现有聊天记录。API Key 留空会保留原密钥。"
                    } else {
                        "必须填写可用的 API Key 才能创建此 AI。费用和额度由 API 服务商决定。"
                    },
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    "性别",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("男", "女", "其他").forEach {
                        FilterChip(
                            selected = gender == it,
                            onClick = { onGender(it) },
                            label = { Text(it) }
                        )
                    }
                }

                Text(
                    "与你的关系",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("家人", "情侣", "哥哥", "弟弟", "朋友", "亲戚").forEach {
                        FilterChip(
                            selected = relation == it,
                            onClick = { onRelation(it) },
                            label = { Text(it) }
                        )
                    }
                }

                OutlinedTextField(
                    relation,
                    onRelation,
                    label = { Text("自定义关系（可直接输入）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    "选填：角色偏好",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                OutlinedTextField(
                    likes,
                    onLikes,
                    label = { Text("喜欢什么") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    dislikes,
                    onDislikes,
                    label = { Text("讨厌什么") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    hobbies,
                    onHobbies,
                    label = { Text("爱好") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(if (editing) "保存修改" else "创建 AI")
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
    val bitmap = remember(uri) {
        try {
            if (uri.isBlank()) null
            else BitmapFactory.decodeFile(uri)
        } catch (_: Exception) {
            null
        }
    }

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
                .background(Color.White)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null)
            }

            Avatar(p.optString("avatar"), p.optString("name"), 42)
            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    p.optString("name"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    p.optString("relationship"),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    (0 until arr.length()).map { arr.getJSONObject(it) },
                    key = {
                        it.optString(
                            "id",
                            it.optString("content") + it.optString("role") + it.hashCode()
                        ) + "-r" + revision
                    }
                ) { m ->
                    val user = m.optString("role") == "user"

                    var menuOpen by remember(
                        m.optString("content"),
                        arr.length()
                    ) {
                        mutableStateOf(false)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (user) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        },
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
                            horizontalAlignment = if (user) {
                                Alignment.End
                            } else {
                                Alignment.Start
                            }
                        ) {
                            Box {
                                val bubbleColor = try {
                                    Color(
                                        android.graphics.Color.parseColor(
                                            if (user) myBubbleHex else bubbleHex
                                        )
                                    ).copy(alpha = if (glassUi) .84f else 1f)
                                } catch (_: Exception) {
                                    if (user) Color(0xFFDCFCE7) else Color.White
                                }

                                val shape = when (bubbleShape) {
                                    "胶囊" -> RoundedCornerShape(28.dp)
                                    "方圆" -> RoundedCornerShape(9.dp)
                                    else -> RoundedCornerShape(
                                        topStart = 21.dp,
                                        topEnd = 21.dp,
                                        bottomStart = if (user) 21.dp else 6.dp,
                                        bottomEnd = if (user) 6.dp else 21.dp
                                    )
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
                                            Modifier
                                                .matchParentSize()
                                                .graphicsLayer(
                                                    alpha = 1f - backgroundAlpha
                                                )
                                                .clip(shape),
                                            rounded = false
                                        )
                                    }

                                    Column(
                                        Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
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
                                                        AnnotatedString(
                                                            m.optString("content")
                                                        )
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
                                                    val index = (0 until arr.length())
                                                        .firstOrNull {
                                                            arr.optJSONObject(it) === m
                                                        }

                                                    if (index != null) {
                                                        onDeleteMessage(index)
                                                    }
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val timeValue = m.optLong("time", 0L)
                            val timeText = if (timeValue > 0L) {
                                java.text.SimpleDateFormat(
                                    "HH:mm",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(timeValue))
                            } else {
                                "刚刚"
                            }

                            Text(
                                if (user) "已发送 · $timeText"
                                else if (m.optString("content") == "---") "正在输入…"
                                else timeText,
                                modifier = Modifier.padding(
                                    horizontal = 4.dp,
                                    vertical = 2.dp
                                ),
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
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp
                    )
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

            IconButton(
                onClick = onSend,
                enabled = !busy
            ) {
                Icon(
                    Icons.Default.Send,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
