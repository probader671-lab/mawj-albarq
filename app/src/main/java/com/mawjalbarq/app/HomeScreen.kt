package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun HomeScreen(onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text("موج البرق", color = MaterialTheme.colorScheme.primary, fontSize = 26.sp, fontFamily = Cairo, modifier = Modifier.padding(vertical = 14.dp))
        if (Feed.videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (Feed.loading) CircularProgressIndicator() else Text("لا توجد مقاطع بعد", color = Color.Gray)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
            itemsIndexed(Feed.videos) { i, v ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF12141A))
                    .clickable { Feed.jumpTo = i; onOpen() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.dp, 112.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E222B))) {
                        if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(v.title, color = Color.White, fontFamily = Cairo, fontSize = 16.sp, maxLines = 2)
                        Text(v.author, color = Color(0xFF8A8F9C), fontSize = 13.sp)
                        Text("${v.likes} إعجاب  ·  ${v.comments} تعليق", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
