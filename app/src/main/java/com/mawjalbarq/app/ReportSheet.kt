package com.mawjalbarq.app
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private val reasons = listOf("محتوى غير لائق", "إزعاج أو سبام", "انتهاك حقوق", "عنف أو كراهية", "معلومات مضللة", "سبب آخر")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(targetType: String, targetId: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sent by remember { mutableStateOf(false) }
    ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = onClose, containerColor = Color(0xFF141A24)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Text(if (sent) "شكراً، وصلنا بلاغك" else "الإبلاغ عن المحتوى", color = Ink, fontSize = 18.sp, fontFamily = Cairo)
            Spacer(Modifier.height(10.dp))
            if (!sent) reasons.forEach { r ->
                Text(r, color = Ink, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        val b = JSONObject().put("target_type", targetType).put("target_id", targetId).put("reason", r)
                        App.userId?.let { b.put("reporter_user_id", it) }
                        api("POST", "/rest/v1/reports", b.toString())
                        sent = true
                    }
                }.padding(vertical = 12.dp))
            } else Button(onClick = onClose) { Text("إغلاق") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fullSheet() = rememberModalBottomSheetState(skipPartiallyExpanded = true)
