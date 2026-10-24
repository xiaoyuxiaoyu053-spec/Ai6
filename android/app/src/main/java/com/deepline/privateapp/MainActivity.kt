package com.deepline.privateapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.res.stringResource
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
                mutableStateOf(try {
                    Color(android.graphics.Color.parseColor(LocalStore.load(context).optString("theme", "#06C755")))
                } catch (_: Exception) { Green })
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = accent, background = Page)) {
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
    fun persist() { LocalStore.save(context, data); revision++ }
    var tab by remember { mutableStateOf("聊天") }
    var page by remember { mutableStateOf("home") }
    var currentId by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var personaApiKey by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf(data.optString("myName","我")) }
    var myBio by remember { mutableStateOf(data.optString("myBio")) }
    var themeHex by remember { mutableStateOf(data.optString("theme","#06C755")) }
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
    var autoAiEnabled by remember { mutableStateOf(data.optBoolean("autoAiEnabled", false)) }
    var editAvatar by remember { mutableStateOf("") }
    var editBackground by remember { mutableStateOf("") }
    var editingPersonaId by remember { mutableStateOf("") }
    var glassUi by remember { mutableStateOf(data.optBoolean("glassUi", true)) }
    var soundEnabled by remember { mutableStateOf(data.optBoolean("soundEnabled", true)) }
    var bubbleHex by remember { mutableStateOf(data.optString("bubbleColor", "#E5F3FF")) }
    var myBubbleHex by remember { mutableStateOf(data.optString("myBubbleColor", "#DCFCE7")) }
    var bubbleShape by remember { mutableStateOf(data.optString("bubbleShape", "柔和圆角")) }
    var bubbleBackgroundUri by remember { mutableStateOf(data.optString("bubbleBackground", "")) }
    var backgroundAlpha by remember { mutableFloatStateOf(data.optDouble("backgroundAlpha", 0.22).toFloat()) }
    var fontChoice by remember { mutableStateOf(data.optString("fontChoice", "系统")) }
    var appTitle by remember { mutableStateOf(data.optString("appTitle", "DeepLine")) }
    var inputHint by remember { mutableStateOf(data.optString("inputHint", "发送消息…")) }
    var selectedImageTarget by remember { mutableStateOf("avatar") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val target = selectedImageTarget
            val storedImage = try { copyPickedImageToAppStorage(context, uri, target) } catch (e: Exception) {
                error = "图片保存失败：${e.message ?: "请重新选择图片"}"
                ""
            }
            if (storedImage.isNotBlank()) {
                when (target) {
                    "myAvatar" -> { data.put("myAvatar", storedImage); persist() }
                    "personaAvatar" -> editAvatar = storedImage
                    "background" -> { data.put("background", storedImage); persist() }
                    "bubbleBackground" -> { bubbleBackgroundUri = storedImage; data.put("bubbleBackground", storedImage); persist() }
                    "chatImage" -> {
                        try {
                            val image = InputImage.fromFilePath(context, Uri.parse(storedImage))
                            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                            labeler.process(image).addOnSuccessListener { labels ->
                                val found = labels.sortedByDescending { it.confidence }.take(8).joinToString("、") { "${it.text} ${(it.confidence * 100).toInt()}%" }
                                composer = if(found.isBlank()) "我选了一张图片，请帮我判断图片内容。设备识图暂时没有识别到明确物体。" else "请根据这张图片的识图结果帮我分析：$found。请说明这些物体可能是什么、图片大致内容。"
                                error = if(found.isBlank()) "识图未找到明确标签；可以尝试更清晰的图片。" else "本地识图完成，可发送识别结果给 AI。"
                                labeler.close()
                            }.addOnFailureListener { e -> error = "识图失败：${e.message ?: "请重试"}"; labeler.close() }
                        } catch (e: Exception) { error = "无法读取图片：${e.message ?: "请重新选择"}" }
                    }
                    "postImage" -> postImage = storedImage
                }
            }
        }
    }
    fun saveData() {
        data.put("myName",myName).put("myBio",myBio).put("theme",themeHex)
        try { onThemeChange(Color(android.graphics.Color.parseColor(themeHex))) } catch (_: Exception) { }
        persist()
    }
    fun personas(): JSONArray = data.optJSONArray("personas") ?: JSONArray().also { data.put("personas",it) }
    fun findPersona(id:String): JSONObject? { val a=personas(); for(i in 0 until a.length()) if(a.getJSONObject(i).optString("id")==id)return a.getJSONObject(i); return null }
    fun createPersona() {
        if(personaName.isBlank()||persona.isBlank()) { error="名字和人设为必填项"; return }
        if(personaApiKey.isBlank() && editingPersonaId.isBlank()) { error="请填写这个 AI 使用的 API Key"; return }
        if(editingPersonaId.isBlank() && personas().length()>=10) { error="最多创建 10 个聊天对象"; return }
        val existing = if(editingPersonaId.isNotBlank()) findPersona(editingPersonaId) else null
        if (existing != null) {
            existing.put("name",personaName).put("bio",personaBio).put("persona",persona)
                .put("gender",gender).put("relationship",relation).put("likes",likes).put("dislikes",dislikes).put("hobbies",hobbies)
            if(editAvatar.isNotBlank()) existing.put("avatar",editAvatar)
            if(personaApiKey.isNotBlank()) existing.put("apiKey",personaApiKey.trim())
        } else {
            val created=JSONObject().put("id",System.currentTimeMillis().toString()).put("name",personaName).put("bio",personaBio).put("persona",persona)
                .put("gender",gender).put("relationship",relation).put("likes",likes).put("dislikes",dislikes).put("hobbies",hobbies)
                .put("avatar",editAvatar).put("apiKey",personaApiKey.trim()).put("messages",JSONArray())
            personas().put(created)
        }
        persist();personaName="";personaBio="";persona="";personaApiKey="";likes="";dislikes="";hobbies="";editAvatar="";editingPersonaId="";error="";page="home";tab="聊天"
    }

    LaunchedEffect(autoAiEnabled) {
        if (autoAiEnabled) {
            while (true) {
                kotlinx.coroutines.delay(60L * 60L * 1000L)
                val all = personas()
                for (i in 0 until all.length()) {
                    val ai = all.optJSONObject(i) ?: continue
                    try {
                        val history = JSONArray()
                        history.put(JSONObject().put("role", "user").put("content", "请根据你的人设，写一条自然、简短、像你自己主动分享的动态。只返回动态正文。"))
                        val sys = "你正在扮演 ${ai.optString("name")}。人设：${ai.optString("persona")}。喜欢：${ai.optString("likes")}。爱好：${ai.optString("hobbies")}。"
                        val postText = DeepSeek.reply(ai.optString("apiKey"), sys, history)
                        val posts = data.optJSONArray("posts") ?: JSONArray().also { data.put("posts", it) }
                        posts.put(0, JSONObject().put("id", System.currentTimeMillis().toString()).put("author", ai.optString("name")).put("avatar", ai.optString("avatar")).put("content", postText).put("likes", 0).put("comments", JSONArray()).put("image", "").put("ai", true))
                        val msgs = ai.optJSONArray("messages") ?: JSONArray().also { ai.put("messages", it) }
                        msgs.put(JSONObject().put("role", "assistant").put("content", "我刚刚发了一条动态：$postText"))
                        LocalStore.save(context, data)
                    } catch (_: Exception) { }
                }
                data = LocalStore.load(context)
            }
        }
    }

    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = when(fontChoice) { "圆润" -> FontFamily.SansSerif; "衬线" -> FontFamily.Serif; "等宽" -> FontFamily.Monospace; else -> FontFamily.Default })) {
    Surface(Modifier.fillMaxSize().then(if (revisionSnapshot >= 0) Modifier else Modifier),color=Page) {
        Box(Modifier.fillMaxSize()) {
        if(data.optString("background").isNotBlank()) PickedPhoto(data.optString("background"),Modifier.fillMaxSize().graphicsLayer(alpha=1f-backgroundAlpha),rounded=false)
        AnimatedContent(page,transitionSpec={fadeIn(tween(220)) togetherWith fadeOut(tween(180))},label="pages") { screen ->
            when(screen) {
                "create" -> PersonaEditor(
                    editing=editingPersonaId.isNotBlank(),
                    name=personaName,onName={personaName=it},bio=personaBio,onBio={personaBio=it},persona=persona,onPersona={persona=it},apiKey=personaApiKey,onApiKey={personaApiKey=it},
                    gender=gender,onGender={gender=it},relation=relation,onRelation={relation=it},likes=likes,onLikes={likes=it},
                    dislikes=dislikes,onDislikes={dislikes=it},hobbies=hobbies,onHobbies={hobbies=it},avatar=editAvatar,
                    onPickAvatar={selectedImageTarget="personaAvatar";imagePicker.launch("image/*")},error=error,onBack={page="home"},onCreate={createPersona()})
                "chat" -> {
                    val p=findPersona(currentId)
                    if(p==null) { page="home" } else ChatScreen(p=p,revision=revisionSnapshot,myName=myName,myAvatar=data.optString("myAvatar"),composer=composer,onComposer={composer=it},busy=busy,error=error,backgroundUri=data.optString("background"),bubbleBackgroundUri=bubbleBackgroundUri,glassUi=glassUi,bubbleHex=bubbleHex,myBubbleHex=myBubbleHex,bubbleShape=bubbleShape,backgroundAlpha=backgroundAlpha,inputHint=inputHint,onBack={page="home"},onQuote={quote -> composer=quote},onDeleteMessage={index -> val messages=p.optJSONArray("messages") ?: JSONArray();messages.remove(index);persist()},onPickImage={selectedImageTarget="chatImage";imagePicker.launch("image/*")},onSend={
                        val msg=composer.trim()
                        if(msg.isNotEmpty()) {
                            val arr=p.optJSONArray("messages")?:JSONArray().also{p.put("messages",it)}
                            arr.put(JSONObject().put("id", UUID.randomUUID().toString()).put("time", System.currentTimeMillis()).put("role","user").put("content",msg))
                            arr.put(JSONObject().put("id", UUID.randomUUID().toString()).put("time", System.currentTimeMillis()).put("role","assistant").put("content","---"))
                            composer="";persist()
                            if(soundEnabled) try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75).startTone(ToneGenerator.TONE_PROP_ACK, 100) } catch (_: Exception) { }
                            scope.launch {
                                busy=true;error=""
                                try {
                                    val history=JSONArray()
                                    val start=maxOf(0,arr.length()-31)
                                    for(i in start until arr.length()-1) { val m=arr.getJSONObject(i); if(m.optString("content")!="---") history.put(JSONObject().put("role",m.optString("role")).put("content",m.optString("content"))) }
                                    val system="你正在扮演 AI 角色。名字：${p.optString("name")}。简介：${p.optString("bio")}。人设：${p.optString("persona")}。性别：${p.optString("gender")}。与用户关系：${p.optString("relationship")}。喜欢：${p.optString("likes")}。讨厌：${p.optString("dislikes")}。爱好：${p.optString("hobbies")}。请遵循人设并用用户使用的语言自然回复。你是 AI，不要声称现实中不存在的经历。"
                                    val reply=DeepSeek.reply(p.optString("apiKey"),system,history)
                                    if(soundEnabled) try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75).startTone(ToneGenerator.TONE_PROP_BEEP2, 130) } catch (_: Exception) { }
                                    arr.put(arr.length()-1,JSONObject().put("id", UUID.randomUUID().toString()).put("time", System.currentTimeMillis()).put("role","assistant").put("content",reply));persist()
                                } catch(e:Exception) { arr.put(arr.length()-1,JSONObject().put("id", UUID.randomUUID().toString()).put("time", System.currentTimeMillis()).put("role","assistant").put("content","请求失败：${e.message}"));persist();error=e.message ?: "请求失败" }
                                finally { busy=false }
                            }
                        }
                    })
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha=.16f),Color.White.copy(alpha=.82f))),RoundedCornerShape(bottomStart=28.dp,bottomEnd=28.dp)).padding(horizontal=20.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(data.optString("myAvatar"),myName,44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(when(tab){"聊天"->appTitle;"动态"->"动态广场";else->"个性化设置"},fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=Ink);Text(if(tab=="聊天") "和你的 AI 好友聊聊吧" else "DeepLine · 私人空间",fontSize=12.sp,color=Muted) }
                        if(tab=="聊天") IconButton(onClick={page="create"},modifier=Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=.12f),CircleShape)){Icon(Icons.Default.Add,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp))}
                    }
                    when(tab) {
                        "聊天" -> {
                            val a=personas()
                            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=14.dp)) {
                                if(a.length()==0)item{EmptyState("还没有聊天对象","点击右上角 + 创建第一个 AI")}
                                items((0 until a.length()).map{a.getJSONObject(it)},key={it.optString("id") + "-r" + revisionSnapshot}) { p ->
                                    var menuOpen by remember(p.optString("id"), revisionSnapshot) { mutableStateOf(false) }
                                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp).background(Brush.linearGradient(listOf(Color.White.copy(alpha=if(glassUi) .84f else 1f), Color(0xFFF0F3FF).copy(alpha=if(glassUi) .84f else 1f))),RoundedCornerShape(24.dp)).animateContentSize().combinedClickable(onClick={currentId=p.optString("id");page="chat"}, onLongClick={menuOpen=true}).padding(horizontal=15.dp, vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Avatar(p.optString("avatar"),p.optString("name"),52)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(p.optString("name"),fontWeight=FontWeight.SemiBold)
                                            val lastMessage = p.optJSONArray("messages")?.let { messages -> if (messages.length() > 0) messages.optJSONObject(messages.length() - 1)?.optString("content") else null }
                                            val preview = if (lastMessage == "---") "正在输入…" else lastMessage?.takeIf { it.isNotBlank() } ?: p.optString("bio").ifBlank { p.optString("persona") }
                                            Text(preview,maxLines=1,color=Color.Gray,fontSize=13.sp)
                                        }
                                        Box {
                                            IconButton(onClick={menuOpen=true}) { Icon(Icons.Default.MoreVert,null,tint=Color.Gray) }
                                            DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                                                DropdownMenuItem(text={Text("编辑 AI")},leadingIcon={Icon(Icons.Default.Edit,null)},onClick={menuOpen=false;editingPersonaId=p.optString("id");personaName=p.optString("name");personaBio=p.optString("bio");persona=p.optString("persona");gender=p.optString("gender","其他");relation=p.optString("relationship","朋友");likes=p.optString("likes");dislikes=p.optString("dislikes");hobbies=p.optString("hobbies");editAvatar=p.optString("avatar");personaApiKey="";error="";page="create"})
                                                DropdownMenuItem(text={Text("删除 AI",color=Color(0xFFD64255))},leadingIcon={Icon(Icons.Default.Delete,null,tint=Color(0xFFD64255))},onClick={menuOpen=false;val old=personas();val keep=JSONArray();for(i in 0 until old.length()){val item=old.optJSONObject(i);if(item?.optString("id")!=p.optString("id")&&item!=null)keep.put(item)};data.put("personas",keep);persist()})
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "动态" -> {
                            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=if(glassUi) .86f else 1f), RoundedCornerShape(24.dp)).animateContentSize().padding(14.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) { Avatar(data.optString("myAvatar"),myName,42); Spacer(Modifier.width(10.dp)); Text("分享你的日常",fontWeight=FontWeight.SemiBold,color=Ink) }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(feedText,{feedText=it},placeholder={Text("今天有什么新鲜事？")},modifier=Modifier.fillMaxWidth(),minLines=2,shape=RoundedCornerShape(18.dp))
                                    if(postImage.isNotBlank()) { Spacer(Modifier.height(8.dp)); PickedPhoto(postImage, Modifier.fillMaxWidth().height(150.dp)); TextButton(onClick={postImage=""}) { Text("移除图片") } }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={selectedImageTarget="postImage";imagePicker.launch("image/*")}) { Icon(Icons.Default.Image,null); Spacer(Modifier.width(5.dp)); Text("添加图片") }
                                        Button(onClick={if(feedText.isNotBlank()||postImage.isNotBlank()){val posts=data.optJSONArray("posts")?:JSONArray().also{data.put("posts",it)};posts.put(0,JSONObject().put("id",System.currentTimeMillis().toString()).put("author",myName).put("avatar",data.optString("myAvatar")).put("content",feedText).put("image",postImage).put("likes",0).put("comments",JSONArray()).put("ai",false));feedText="";postImage="";persist()}},shape=RoundedCornerShape(22.dp)){Text("发布动态")}
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                LazyColumn(contentPadding=PaddingValues(bottom=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    items((0 until (data.optJSONArray("posts")?.length()?:0)).map{data.getJSONArray("posts").getJSONObject(it)},key={it.optString("id") + "-r" + revisionSnapshot}){post->
                                        Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=if(glassUi) .86f else 1f),RoundedCornerShape(22.dp)).animateContentSize().padding(16.dp)) {
                                            Row(verticalAlignment=Alignment.CenterVertically) { Avatar(post.optString("avatar"),post.optString("author"),42); Spacer(Modifier.width(10.dp)); Column { Text(post.optString("author"),fontWeight=FontWeight.Bold,color=Ink); Text(if(post.optBoolean("ai")) "AI 好友" else "刚刚",fontSize=11.sp,color=Muted) } }
                                            if(post.optString("content").isNotBlank()) Text(post.optString("content"),modifier=Modifier.padding(top=12.dp),fontSize=15.sp,color=Ink)
                                            if(post.optString("image").isNotBlank()) { Spacer(Modifier.height(10.dp)); PickedPhoto(post.optString("image"), Modifier.fillMaxWidth().height(190.dp)) }
                                            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=8.dp)) {
                                                TextButton(onClick={post.put("likes",post.optInt("likes")+1);persist()}) { Icon(Icons.Default.FavoriteBorder,null,tint=Color(0xFFFF6680)); Spacer(Modifier.width(4.dp)); Text("${post.optInt("likes")}") }
                                                TextButton(onClick={commentPostId=post.optString("id")}) { Icon(Icons.Default.ChatBubbleOutline,null,tint=Muted); Spacer(Modifier.width(4.dp)); Text("评论 ${post.optJSONArray("comments")?.length()?:0}") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp)) {
                            item {
                                Text("个人资料",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(vertical=10.dp))
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Avatar(data.optString("myAvatar"),myName,68)
                                    Spacer(Modifier.width(12.dp))
                                    OutlinedButton(onClick={selectedImageTarget="myAvatar";imagePicker.launch("image/*")}){Text("从相册选择头像")}
                                }
                                OutlinedTextField(myName,{myName=it},label={Text("名字")},modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(myBio,{myBio=it},label={Text("简介")},modifier=Modifier.fillMaxWidth(),minLines=2)
                                Button(onClick={saveData()},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("保存资料")}
                                Spacer(Modifier.height(18.dp));Text("AI 接口说明",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Text("创建 AI 时单独填写 API Key。密钥仅保存在本机应用数据中，不会上传到 GitHub 或任何自建后端。请勿把含密钥的备份分享给他人。",fontSize=13.sp,color=Color.Gray)
                                Spacer(Modifier.height(18.dp));Text("主题颜色",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Text("点选颜色",fontSize=12.sp,color=Muted)
                                ColorPalette(selected=themeHex, onSelect={themeHex=it;saveData()}, colors=listOf("#6C63FF","#4F7DFF","#19B6A4","#FF8A65","#EC5B91","#FFC857","#4F6D7A","#2E3440","#A7C7E7","#D7BDE2","#F7D6E0","#DDECCF"))
                                Spacer(Modifier.height(18.dp));Text("界面外观",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("玻璃拟态 UI",fontWeight=FontWeight.SemiBold);Text("半透明卡片与柔和层次",fontSize=12.sp,color=Muted)};Switch(checked=glassUi,onCheckedChange={glassUi=it;data.put("glassUi",it);persist()})}
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("发送/接收音效",fontWeight=FontWeight.SemiBold);Text("轻提示音，可随时关闭",fontSize=12.sp,color=Muted)};Switch(checked=soundEnabled,onCheckedChange={soundEnabled=it;data.put("soundEnabled",it);persist()})}
                                Text("全局字体",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp))
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("系统","圆润","衬线","等宽").forEach{opt->FilterChip(selected=fontChoice==opt,onClick={fontChoice=opt;data.put("fontChoice",opt);persist()},label={Text(opt)})}}
                                OutlinedTextField(appTitle,{appTitle=it;data.put("appTitle",it);persist()},label={Text("聊天首页标题")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                                OutlinedTextField(inputHint,{inputHint=it;data.put("inputHint",it);persist()},label={Text("聊天输入框提示文字")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                                Text("聊天气泡样式",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp))
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("柔和圆角","胶囊","方圆").forEach{opt->FilterChip(selected=bubbleShape==opt,onClick={bubbleShape=opt;data.put("bubbleShape",opt);persist()},label={Text(opt)})}}
                                Text("AI 气泡颜色",fontSize=13.sp,fontWeight=FontWeight.Medium)
                                ColorPalette(selected=bubbleHex,onSelect={bubbleHex=it;data.put("bubbleColor",it);persist()},colors=listOf("#FFFFFF","#EAF2FF","#E5F3FF","#FCE7F3","#F3E8FF","#FFF0D9","#E2F7EF","#F1F5F9","#DDE7F0","#D7DDFB"))
                                Text("我的气泡颜色",fontSize=13.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=6.dp))
                                ColorPalette(selected=myBubbleHex,onSelect={myBubbleHex=it;data.put("myBubbleColor",it);persist()},colors=listOf("#DCFCE7","#DDF7F0","#DCEBFF","#E9E5FF","#FFE4ED","#FFF0D9","#F1F5F9","#E8E8E8","#D9F0FF","#E7F5D5"))
                                OutlinedButton(onClick={selectedImageTarget="bubbleBackground";imagePicker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Wallpaper,null);Spacer(Modifier.width(8.dp));Text(if(bubbleBackgroundUri.isBlank()) "设置气泡背景图" else "更换气泡背景图")}
                                Text("背景图透明度 ${(backgroundAlpha*100).toInt()}%",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp))
                                Slider(value=backgroundAlpha,onValueChange={backgroundAlpha=it;data.put("backgroundAlpha",it.toDouble());persist()},valueRange=0f..1f)
                                OutlinedButton(onClick={selectedImageTarget="background";imagePicker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("选择全局背景图（聊天等页面）")}
                                Spacer(Modifier.height(18.dp))
                                Text("AI 主动互动",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                Text("开启后，应用运行期间每隔约 1 小时尝试让每个 AI 发布一条动态，并向自己的聊天记录发送动态通知。会消耗对应 API 额度。",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Text("允许 AI 自动发布动态",modifier=Modifier.weight(1f)); Switch(checked=autoAiEnabled,onCheckedChange={autoAiEnabled=it;data.put("autoAiEnabled",it);persist()}) }
                                Text("聊天记录和动态保存在此设备。卸载应用或清除应用数据可能会删除它们。",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=10.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=10.dp).background(Color.White.copy(alpha=if(glassUi) .82f else 1f),RoundedCornerShape(30.dp)).padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceAround) {
                        Nav("聊天",tab=="聊天",Icons.Default.ChatBubbleOutline){tab="聊天"}
                        Nav("动态",tab=="动态",Icons.Default.DynamicFeed){tab="动态"}
                        Nav("设置",tab=="设置",Icons.Default.Settings){tab="设置"}
                    }
                }
            }
        }
        if (commentPostId.isNotBlank()) {
            val post = (0 until (data.optJSONArray("posts")?.length() ?: 0)).mapNotNull { data.optJSONArray("posts")?.optJSONObject(it) }.firstOrNull { it.optString("id") == commentPostId }
            if (post != null) {
                val comments = post.optJSONArray("comments") ?: JSONArray().also { post.put("comments", it) }
                AlertDialog(
                    onDismissRequest={commentPostId=""},
                    title={Text("评论与回复")},
                    text={Column {
                        LazyColumn(Modifier.heightIn(max=240.dp)) {
                            items((0 until comments.length()).mapNotNull { comments.optJSONObject(it) }) { c ->
                                Column(Modifier.fillMaxWidth().padding(vertical=5.dp).background(Page,RoundedCornerShape(12.dp)).padding(10.dp)) {
                                    Text(c.optString("author"),fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                    Text(c.optString("content"),fontSize=14.sp,color=Ink)
                                    if(c.optString("reply").isNotBlank()) Text("回复：${c.optString("reply")}",fontSize=12.sp,color=Muted)
                                    TextButton(onClick={commentText="回复 ${c.optString("author")}: "}) { Text("回复") }
                                }
                            }
                        }
                        OutlinedTextField(commentText,{commentText=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("写下评论或回复…")},maxLines=3)
                    }},
                    confirmButton={TextButton(onClick={if(commentText.isNotBlank()){comments.put(JSONObject().put("author",myName).put("content",commentText).put("reply",""));persist();commentText="";commentPostId=""}}){Text("发送")}},
                    dismissButton={TextButton(onClick={commentPostId=""}){Text("关闭")}}
                )
            }
        }
        }
    }
    }
    }

@Composable fun Nav(label:String,selected:Boolean,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit){val accent=MaterialTheme.colorScheme.primary;Column(Modifier.clickable{click()}.padding(horizontal=16.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=if(selected)accent else Color.Gray);Text(label,fontSize=11.sp,color=if(selected)accent else Color.Gray)}}
private fun copyPickedImageToAppStorage(context: android.content.Context, source: Uri, prefix: String): String {
    val directory = File(context.filesDir, "deepline-media").apply { mkdirs() }
    val destination = File(directory, "${prefix}_${UUID.randomUUID()}.img")
    context.contentResolver.openInputStream(source).use { input ->
        requireNotNull(input) { "无法读取所选图片" }
        destination.outputStream().use { output -> input.copyTo(output) }
    }
    return Uri.fromFile(destination).toString()
}

private fun loadLocalBitmap(context: android.content.Context, value: String) = try {
    if (value.isBlank()) null else {
        val uri = Uri.parse(value)
        val bitmap = if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path)
        else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        bitmap?.asImageBitmap()
    }
} catch (_: Exception) { null }

@Composable fun ColorPalette(selected:String,onSelect:(String)->Unit,colors:List<String>) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp)) {
        colors.chunked(6).forEach { rowColors ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                rowColors.forEach { hex ->
                    val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_:Exception) { Color.LightGray }
                    Box(Modifier.size(32.dp).clip(CircleShape).background(color).then(if(selected.equals(hex,true)) Modifier.border(3.dp,MaterialTheme.colorScheme.primary,CircleShape) else Modifier.border(1.dp,Color.Black.copy(alpha=.12f),CircleShape)).clickable { onSelect(hex) },contentAlignment=Alignment.Center) {
                        if(selected.equals(hex,true)) Icon(Icons.Default.Check,null,tint=if(color.luminance() < .45f) Color.White else Color.Black,modifier=Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable fun Avatar(uri:String,name:String,size:Int){
    val context=LocalContext.current
    val bitmap=remember(uri){loadLocalBitmap(context,uri)}
    if(bitmap!=null) Image(bitmap,null,Modifier.size(size.dp).border(2.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFFFFC86B), MaterialTheme.colorScheme.primary)), CircleShape).padding(3.dp).clip(CircleShape).background(Color.White),contentScale=ContentScale.Crop)
    else Box(Modifier.size(size.dp).border(2.dp, Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFFFFC86B), MaterialTheme.colorScheme.primary)), CircleShape).padding(3.dp).background(Color(0xFFE4F6EA),CircleShape),contentAlignment=Alignment.Center){Text(name.take(1).ifBlank{"?"},color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=(size/2).sp)}
}
@Composable fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(top=60.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Forum,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(48.dp));Spacer(Modifier.height(12.dp));Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(subtitle,color=Color.Gray,fontSize=13.sp)}}

@Composable fun PersonaEditor(editing:Boolean=false,name:String,onName:(String)->Unit,bio:String,onBio:(String)->Unit,persona:String,onPersona:(String)->Unit,apiKey:String,onApiKey:(String)->Unit,gender:String,onGender:(String)->Unit,relation:String,onRelation:(String)->Unit,likes:String,onLikes:(String)->Unit,dislikes:String,onDislikes:(String)->Unit,hobbies:String,onHobbies:(String)->Unit,avatar:String,onPickAvatar:()->Unit,error:String,onBack:()->Unit,onCreate:()->Unit){
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text(if(editing) "编辑 AI 人设" else "创建 AI 人设",fontWeight=FontWeight.Bold,fontSize=20.sp)}
        LazyColumn(Modifier.weight(1f).padding(horizontal=18.dp),contentPadding=PaddingValues(bottom=16.dp)){
            item{
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(vertical=12.dp)){Avatar(avatar,name,72);Spacer(Modifier.width(12.dp));OutlinedButton(onClick=onPickAvatar){Text("从相册选择头像")}}
                OutlinedTextField(name,onName,label={Text("名字 *")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(bio,onBio,label={Text("简介")},modifier=Modifier.fillMaxWidth(),minLines=2)
                OutlinedTextField(persona,onPersona,label={Text("人设 *")},modifier=Modifier.fillMaxWidth(),minLines=3)
                OutlinedTextField(apiKey,onApiKey,label={Text(if(editing) "更新 API Key（留空保持原密钥）" else "DeepSeek API Key *")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
                Text(if(editing) "编辑资料不会删除现有聊天记录。API Key 留空会保留原密钥。" else "必须填写可用的 API Key 才能创建此 AI。费用和额度由 API 服务商决定。",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=4.dp))
                Text("性别",fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=12.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("男","女","其他").forEach{FilterChip(selected=gender==it,onClick={onGender(it)},label={Text(it)})}}
                Text("与你的关系",fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("家人","情侣","哥哥","弟弟","朋友","亲戚").forEach{FilterChip(selected=relation==it,onClick={onRelation(it)},label={Text(it)})}}
                OutlinedTextField(relation,onRelation,label={Text("自定义关系（可直接输入）")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                Text("选填：角色偏好",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp,bottom=8.dp))
                OutlinedTextField(likes,onLikes,label={Text("喜欢什么")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(dislikes,onDislikes,label={Text("讨厌什么")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(hobbies,onHobbies,label={Text("爱好")},modifier=Modifier.fillMaxWidth())
                if(error.isNotBlank())Text(error,color=Color.Red,modifier=Modifier.padding(top=8.dp))
            }
        }
        Button(onClick=onCreate,modifier=Modifier.fillMaxWidth().padding(16.dp).height(52.dp),shape=RoundedCornerShape(28.dp)){Text(if(editing) "保存修改" else "创建 AI")}
    }
}

@Composable fun PickedPhoto(uri:String, modifier:Modifier=Modifier, rounded:Boolean=true) {
    val context=LocalContext.current
    val bitmap=remember(uri){loadLocalBitmap(context,uri)}
    if(bitmap!=null) Image(bitmap,contentDescription="已选择的图片",modifier=if(rounded) modifier.clip(RoundedCornerShape(16.dp)) else modifier.clip(RectangleShape),contentScale=ContentScale.Crop)
}

@Composable fun FloatingTypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val y by transition.animateFloat(
                initialValue = 2f, targetValue = -5f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 420, delayMillis = index * 120, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "typing-dot-$index"
            )
            Box(Modifier.offset(y = y.dp).size(7.dp).background(Color(0xFF87948D), CircleShape))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ChatScreen(p:JSONObject,revision:Int,myName:String,myAvatar:String,composer:String,onComposer:(String)->Unit,busy:Boolean,error:String,backgroundUri:String,bubbleBackgroundUri:String,glassUi:Boolean,bubbleHex:String,myBubbleHex:String,bubbleShape:String,backgroundAlpha:Float,inputHint:String,onBack:()->Unit,onQuote:(String)->Unit,onDeleteMessage:(Int)->Unit,onPickImage:()->Unit,onSend:()->Unit){
    val arr=p.optJSONArray("messages")?:JSONArray()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(arr.length(), revision, busy) { if (arr.length() > 0) listState.animateScrollToItem(arr.length() - 1) }
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Avatar(p.optString("avatar"),p.optString("name"),42);Spacer(Modifier.width(10.dp));Column{Text(p.optString("name"),fontWeight=FontWeight.Bold,fontSize=18.sp);Text(p.optString("relationship"),fontSize=12.sp,color=Color.Gray)}}
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(horizontal=14.dp),contentPadding=PaddingValues(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items((0 until arr.length()).map{arr.getJSONObject(it)},key={it.optString("id",it.optString("content")+it.optString("role")+it.hashCode()) + "-r" + revision}){m->
                val user=m.optString("role")=="user"
                var menuOpen by remember(m.optString("content"), arr.length()) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Bottom) {
                        if (!user) { Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.width(42.dp)) { Avatar(p.optString("avatar"), p.optString("name"), 32); Text(p.optString("name"),fontSize=9.sp,maxLines=1,color=Muted) }; Spacer(Modifier.width(6.dp)) }
                        Column(horizontalAlignment=if(user) Alignment.End else Alignment.Start) {
                            Box {
                            val bubbleColor = try { Color(android.graphics.Color.parseColor(if(user) myBubbleHex else bubbleHex)).copy(alpha=if(glassUi) .84f else 1f) } catch (_:Exception) { if(user) Color(0xFFDCFCE7) else Color.White }
                            val shape = when(bubbleShape) { "胶囊" -> RoundedCornerShape(28.dp); "方圆" -> RoundedCornerShape(9.dp); else -> RoundedCornerShape(topStart=21.dp,topEnd=21.dp,bottomStart=if(user)21.dp else 6.dp,bottomEnd=if(user)6.dp else 21.dp) }
                            Box(Modifier.widthIn(max=300.dp).clip(shape).background(bubbleColor).combinedClickable(onClick={},onLongClick={menuOpen=true})) {
                                if(bubbleBackgroundUri.isNotBlank()) PickedPhoto(bubbleBackgroundUri, Modifier.matchParentSize().graphicsLayer(alpha=1f-backgroundAlpha).clip(shape),rounded=false)
                                Column(Modifier.padding(horizontal=12.dp, vertical=8.dp)) {
                                    if (!user && m.optString("content") == "---") FloatingTypingIndicator()
                                    else Text(m.optString("content"),fontSize=14.sp,color=Ink)
                                    DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                                        DropdownMenuItem(text={Text("复制")},leadingIcon={Icon(Icons.Default.ContentCopy,null)},onClick={clipboard.setText(AnnotatedString(m.optString("content")));menuOpen=false})
                                        DropdownMenuItem(text={Text("引用")},leadingIcon={Icon(Icons.Default.FormatQuote,null)},onClick={onQuote("「${m.optString("content").take(500)}」\n");menuOpen=false})
                                        DropdownMenuItem(text={Text("删除此消息")},leadingIcon={Icon(Icons.Default.Delete,null)},onClick={val idx=(0 until arr.length()).firstOrNull{arr.optJSONObject(it)===m};if(idx!=null)onDeleteMessage(idx);menuOpen=false})
                                    }
                                }
                            }
                            val timeValue = m.optLong("time", 0L)
                            val timeText = if(timeValue > 0L) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timeValue)) else "刚刚"
                            Text(if(user) "已发送 · $timeText" else if(m.optString("content")=="---") "正在输入…" else timeText, modifier=Modifier.padding(horizontal=4.dp, vertical=2.dp), fontSize=9.sp, color=Muted)
                        }
                        if (user) { Spacer(Modifier.width(6.dp)); Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.width(42.dp)) { Avatar(myAvatar, myName, 32); Text(myName,fontSize=9.sp,maxLines=1,color=Muted) } }
                    }
            }
        }
        }
        if(error.isNotBlank())Text(error,color=Color.Red,modifier=Modifier.padding(horizontal=12.dp))
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=if(glassUi) .86f else 1f),RoundedCornerShape(topStart=20.dp,topEnd=20.dp)).padding(horizontal=5.dp, vertical=4.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onPickImage){Icon(Icons.Default.ImageSearch,null,tint=MaterialTheme.colorScheme.primary)};OutlinedTextField(composer,onComposer,modifier=Modifier.weight(1f),placeholder={Text(inputHint,fontSize=13.sp)},shape=RoundedCornerShape(22.dp),maxLines=3,textStyle=LocalTextStyle.current.copy(fontSize=14.sp));IconButton(onClick=onSend,enabled=!busy){Icon(Icons.Default.Send,null,tint=MaterialTheme.colorScheme.primary)}}
    }
}
