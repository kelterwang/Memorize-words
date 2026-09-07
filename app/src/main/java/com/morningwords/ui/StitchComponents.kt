package com.morningwords.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningwords.R

internal val StitchMuted = Color(0xFF7D756A)
internal val StitchSand = Color(0xFFF3EBDD)
internal val StitchDangerSoft = Color(0xFFF7E4DE)
internal val StitchDeep = Color(0xFF3E5442)

@Composable
internal fun BrandHeader(subtitle: String, onProfile: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }
        Image(painterResource(R.drawable.stitch_logo), null, modifier = Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("淇澳背单词", fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = StitchMuted, fontSize = 12.sp)
        }
        onProfile?.let {
            FilledIconButton(onClick = it, colors = IconButtonDefaults.filledIconButtonColors(containerColor = StitchDeep, contentColor = Color.White)) {
                Icon(Icons.Outlined.PersonOutline, "我的设置")
            }
        }
    }
}

@Composable
internal fun PaperPanel(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = color,
        border = BorderStroke(1.dp, Color(0xFFEEE7DF).copy(alpha = .45f)), shadowElevation = 1.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun StudyChip(text: String, color: Color = MaterialTheme.colorScheme.primary, background: Color = StitchSand) {
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
