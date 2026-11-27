package com.mawjalbarq.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Interested / not interested" card for a new creator's video: teaches the taste profile. */
@Composable
fun InterestCard(v: Video, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("حساب جديد قد يعجبك", color = MaterialTheme.colorScheme.secondary, fontFamily = Cairo, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(12.dp))) { VideoThumb(v, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, color = Ink, fontFamily = Cairo, fontSize = 15.sp, maxLines = 2)
                Text(v.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
        }
        Text("مهتم بمحتوى زي ده؟", color = Ink, fontFamily = Cairo, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onAnswer(true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("مهتم", fontFamily = Cairo) }
            OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("مش مهتم", fontFamily = Cairo) }
        }
    }
}
