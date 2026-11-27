package com.mawjalbarq.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Small text button + confirm dialog. Only ever composed for admins. */
@Composable
fun AdminConfirmButton(label: String, question: String, onConfirm: suspend () -> Boolean, onDone: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { ask = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
        Text(label, fontSize = 12.sp, fontFamily = Cairo, color = MaterialTheme.colorScheme.error)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(label, fontFamily = Cairo) },
        text = { Text(question, fontFamily = Cairo) },
        confirmButton = { TextButton(onClick = { ask = false; scope.launch { if (onConfirm()) onDone() else msg = "فشل التنفيذ" } }) { Text("تأكيد", fontFamily = Cairo) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("إلغاء", fontFamily = Cairo) } }
    )
    msg?.let { m -> AlertDialog(onDismissRequest = { msg = null }, confirmButton = { TextButton(onClick = { msg = null }) { Text("حسناً") } }, text = { Text(m, fontFamily = Cairo) }) }
}

/** Admin strip for one video: remove it from feeds, or ban its owner (account + devices). */
@Composable
fun AdminVideoBar(v: Video, onRemoved: () -> Unit) {
    if (!Notifs.isAdmin) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AdminConfirmButton("إدارة: حظر الفيديو", "إخفاء هذا الفيديو من التطبيق؟", { AdminApi.removeVideo(v.id, "admin") }, onRemoved)
        AdminConfirmButton("إدارة: تقييد + إشعار", "تقييد هذا الفيديو وإبلاغ صاحبه (تم تقييد منشورك)؟", { AdminApi.restrictVideo(v.id, "مخالفة قواعد المحتوى") }, onRemoved)
        if (v.userId.isNotBlank() && v.userId != App.userId)
            AdminConfirmButton("إدارة: حظر الحساب", "حظر ${v.author} وأجهزته نهائياً؟ سيتم إخفاء فيديوهاته ومنعه من الدخول.", { AdminApi.banUser(v.userId, "admin") }, onRemoved)
    }
}

/** Admin strip for one comment: delete it, or ban the commenter. */
@Composable
fun AdminCommentBar(c: Comment, onDeleted: () -> Unit) {
    if (!Notifs.isAdmin) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        AdminConfirmButton("حذف التعليق", "حذف هذا التعليق؟", { AdminApi.deleteComment(c.id) }, onDeleted)
        if (c.userId.isNotBlank() && c.userId != App.userId)
            AdminConfirmButton("حظر المعلّق", "حظر ${c.author} وأجهزته نهائياً؟", { AdminApi.banUser(c.userId, "admin: comment") }, onDeleted)
    }
}
