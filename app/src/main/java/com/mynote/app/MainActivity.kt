package com.mynote.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.mynote.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = MyNoteDatabase.get(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF34436D), secondary = Color(0xFF6076B5))) { MyNoteApp(db.dao()) } }
    }
}

data class StrokeData(val points: List<Pair<Float, Float>>, val color: Long = 0xFFE5A93D, val width: Float = 0.004f)

private fun encodeStrokes(strokes: List<StrokeData>): String = JSONArray().apply {
    strokes.forEach { stroke -> put(JSONObject().apply {
        put("color", stroke.color); put("width", stroke.width)
        put("points", JSONArray().apply { stroke.points.forEach { put(JSONArray().put(it.first).put(it.second)) } })
    }) }
}.toString()

private fun decodeStrokes(json: String): List<StrokeData> = runCatching {
    val arr = JSONArray(json); (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i); val pts = obj.getJSONArray("points")
        StrokeData((0 until pts.length()).map { j -> val p = pts.getJSONArray(j); p.getDouble(0).toFloat() to p.getDouble(1).toFloat() }, obj.optLong("color", 0xFFE5A93D), obj.optDouble("width", 0.004).toFloat())
    }
}.getOrDefault(emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyNoteApp(dao: MyNoteDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by dao.documents().collectAsState(initial = emptyList())
    val notes by dao.notes().collectAsState(initial = emptyList())
    var selectedDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var tab by remember { mutableStateOf("Home") }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var showNewNote by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { picked -> scope.launch {
            val file = withContext(Dispatchers.IO) {
                val target = File(context.filesDir, "pdfs").apply { mkdirs() }
                val out = File(target, "${UUID.randomUUID()}.pdf")
                context.contentResolver.openInputStream(picked)?.use { input -> out.outputStream().use(input::copyTo) }
                out
            }
            val doc = DocumentEntity(UUID.randomUUID().toString(), queryDisplayName(context, picked) ?: file.name, file.absolutePath)
            dao.putDocument(doc); selectedDocument = doc; tab = "Reader"
        } }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(9.dp)); Text("MyNote", fontWeight = FontWeight.Bold) } }, actions = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudOff, null, tint = Color(0xFF269B78)); Spacer(Modifier.width(5.dp)); Text("Offline ready", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(12.dp)) } })
    }, bottomBar = {
        NavigationBar { listOf("Home", "PDFs", "Notes").forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item; selectedDocument = null }, icon = { Icon(when(item){"Home"->Icons.Default.Book;"PDFs"->Icons.Default.Description;else->Icons.Default.Edit}, null) }, label = { Text(item) }) } }
    }, floatingActionButton = {
        if (tab == "PDFs" || tab == "Home") FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.FileOpen, "Import PDF") }
        else FloatingActionButton(onClick = { showNewNote = true }) { Icon(Icons.Default.NoteAdd, "New note") }
    }) { padding ->
        if (selectedDocument != null && tab == "Reader") {
            PdfReaderScreen(selectedDocument!!, dao, Modifier.padding(padding), onBack = { selectedDocument = null; tab = "PDFs" })
        } else when(tab) {
            "Home" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text("Your personal knowledge space", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text("A calmer way to learn.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Your PDFs and notes stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("PDF library", documents.size.toString(), Modifier.weight(1f)); StatCard("Quick notes", notes.size.toString(), Modifier.weight(1f)) } }
                item { Text("Recent PDFs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(documents.take(5)) { doc -> DocumentRow(doc) { selectedDocument = doc; tab = "Reader" } }
                item { Text("Getting started", style = MaterialTheme.typography.titleMedium); Text("Import a PDF using the + button. Open it to move through pages and draw annotations. Create notes from the Notes tab.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            "PDFs" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Your PDF library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Imported documents are stored locally.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (documents.isEmpty()) item { EmptyState("No PDFs yet", "Tap + to import a PDF from your tablet.") }
                items(documents) { doc -> DocumentRow(doc) { selectedDocument = doc; tab = "Reader" } }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Quick notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Small ideas, saved locally.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (notes.isEmpty()) item { EmptyState("No notes yet", "Tap + to create your first note.") }
                items(notes) { note -> Card(Modifier.fillMaxWidth(), onClick = { noteTitle = note.title; noteBody = note.body; showNewNote = true }) { Column(Modifier.padding(16.dp)) { Text(note.title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(5.dp)); Text(note.body, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            }
        }
    }
    if (showNewNote) AlertDialog(onDismissRequest = { showNewNote = false }, title = { Text("New note") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(noteTitle, { noteTitle = it }, label = { Text("Title") }, singleLine = true); OutlinedTextField(noteBody, { noteBody = it }, label = { Text("Write your note") }, minLines = 4) } }, confirmButton = { TextButton(onClick = { if (noteTitle.isNotBlank() || noteBody.isNotBlank()) scope.launch { dao.putNote(NoteEntity(UUID.randomUUID().toString(), noteTitle.ifBlank { "Untitled note" }, noteBody)) }; noteTitle = ""; noteBody = ""; showNewNote = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showNewNote = false }) { Text("Cancel") } })
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) { Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun EmptyState(title: String, message: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.secondary); Text(title, fontWeight = FontWeight.SemiBold); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun DocumentRow(doc: DocumentEntity, onClick: () -> Unit) { Card(Modifier.fillMaxWidth(), onClick = onClick) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(doc.name, fontWeight = FontWeight.SemiBold); Text("PDF document", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
fun PdfReaderScreen(doc: DocumentEntity, dao: MyNoteDao, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageIndex by remember(doc.id) { mutableIntStateOf(0) }
    var pageCount by remember(doc.id) { mutableIntStateOf(0) }
    var bitmap by remember(doc.id, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var strokes by remember(doc.id, pageIndex) { mutableStateOf(emptyList<StrokeData>()) }
    var currentPoints by remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc.id, pageIndex) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(File(doc.localPath), ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(fd).use { renderer ->
                    pageCount = renderer.pageCount
                    val p = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1))
                    val scale = minOf(1800f / p.width, 2200f / p.height, 2.5f)
                    val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close(); bmp
                }
            }
        }
        result.onSuccess { bitmap = it; error = null }.onFailure { error = it.message ?: "Unable to open PDF" }
        strokes = decodeStrokes(dao.annotation(doc.id, pageIndex)?.strokesJson ?: "[]")
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(doc.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Text("${pageIndex + 1}/${pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { if (pageIndex > 0) pageIndex-- }) { Text("‹") }
            IconButton(onClick = { if (pageIndex + 1 < pageCount) pageIndex++ }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✎  Draw with finger or stylus", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { strokes = emptyList() }) { Text("Clear page") }
            IconButton(onClick = { scope.launch { dao.putAnnotation(PageAnnotationEntity(doc.id, pageIndex, encodeStrokes(strokes))) } }) { Icon(Icons.Default.Save, "Save annotations") }
        }
        if (error != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "PDF error") }
        else bitmap?.let { bmp ->
            Box(Modifier.fillMaxSize().background(Color(0xFFE8EAF0)).padding(8.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().fillMaxHeight(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize().pointerInput(doc.id, pageIndex) {
                        detectDragGestures(onDragStart = { start -> currentPoints = listOf(start.x / size.width to start.y / size.height) }, onDrag = { change, _ -> change.consume(); currentPoints = currentPoints + (change.position.x / size.width to change.position.y / size.height) }, onDragEnd = { if (currentPoints.isNotEmpty()) { strokes = strokes + StrokeData(currentPoints); currentPoints = emptyList() } })
                    }) {
                        fun drawStroke(s: StrokeData) { if (s.points.size > 1) { val path = androidx.compose.ui.graphics.Path(); s.points.forEachIndexed { i, pt -> val x = pt.first * size.width; val y = pt.second * size.height; if (i == 0) path.moveTo(x,y) else path.lineTo(x,y) }; drawPath(path, Color(s.color), style = Stroke(width = s.width * size.width)) } }
                        strokes.forEach(::drawStroke); if (currentPoints.size > 1) drawStroke(StrokeData(currentPoints))
                    }
                }
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()
