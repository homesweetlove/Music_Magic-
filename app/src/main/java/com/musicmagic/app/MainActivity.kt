package com.musicmagic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Blue = Color(0xFF366BFF)
private val Background = Color(0xFFF6F7FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selected by remember { mutableStateOf<Uri?>(null) }
            var fileName by remember { mutableStateOf("") }
            var title by remember { mutableStateOf("") }
            var artist by remember { mutableStateOf("") }
            var album by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var editable by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    selected = uri
                    val info = AudioMetadata.read(this, uri)
                    editable = info.supported
                    fileName = info.fileName
                    title = info.title
                    artist = info.artist
                    album = info.album
                    status = if (info.supported) "" else "이 파일의 태그 형식은 안전한 저장을 지원하지 않습니다."
                }
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Background, surface = Color.White)) {
                Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text("Music Magic", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12192D))
                    Text("내 음악을, 내 마음대로.", color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 2.dp) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Blue, modifier = Modifier.size(48.dp))
                            Text(if (selected == null) "편집할 음악을 선택하세요" else fileName, fontWeight = FontWeight.SemiBold)
                            Text("기기의 파일을 직접 편집합니다.", color = Color.Gray, fontSize = 13.sp)
                            Button(onClick = { picker.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("음악 파일 선택")
                            }
                        }
                    }
                    if (selected != null) {
                        Text("음악 정보", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("아티스트 · 만든이") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("앨범") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("파일명") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                        }
                        Button(enabled = !busy && editable, onClick = {
                            val uri = selected ?: return@Button
                            busy = true
                            scope.launch {
                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    AudioMetadata.save(this@MainActivity, uri, title, artist, album, fileName)
                                }
                                status = result.fold(onSuccess = { "원본 파일에 저장했어요." }, onFailure = { "저장 실패: ${it.message}" })
                                busy = false
                            }
                        }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                            Text(if (busy) "저장 중..." else "원본에 저장", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (status.isNotEmpty()) Text(status, color = if (status.startsWith("원본")) Color(0xFF168553) else Color(0xFFB45309))
                    Text("현재 버전: MP3 ID3v2.3 안전 편집 · 기타 형식은 추후 지원", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}
