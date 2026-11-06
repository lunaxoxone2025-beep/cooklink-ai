@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.cooklink.ai.model.Certainty
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.formatMinutes
import com.cooklink.ai.ui.theme.LocalCertaintyColors
import com.cooklink.ai.ui.theme.LocalNeon
import com.cooklink.ai.ui.theme.Neon
import com.cooklink.ai.ui.theme.neonGlow

/** Neon colours cycled for recipes without a photo, so each one gets its own look. */
private val placeholderTints = listOf(Neon.Lime, Neon.Orange, Neon.Pink, Neon.Cyan, Neon.Amber)

/** Dark grid with a glowing initial: shown when a recipe has no photo (or while it loads). */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val tint = placeholderTints[(title.hashCode() and 0x7fffffff) % placeholderTints.size]
    Box(modifier.background(Neon.Panel), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            val line = tint.copy(alpha = 0.10f)
            var x = 0f
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), center = center, radius = size.minDimension * 0.6f),
                radius = size.minDimension * 0.6f,
            )
        }
        Text(
            text = title.trim().firstOrNull()?.uppercase() ?: "",
            style = MaterialTheme.typography.displayLarge,
            color = tint,
        )
    }
}

@Composable
fun CoverImage(uri: String?, title: String, modifier: Modifier = Modifier) {
    if (uri == null) {
        CoverPlaceholder(title, modifier)
    } else {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { CoverPlaceholder(title, Modifier.fillMaxSize()) },
            error = { CoverPlaceholder(title, Modifier.fillMaxSize()) },
        )
    }
}

/** Photo-first card: title overlaid on the image, accent bar, time and servings chips. */
@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    val accent = LocalNeon.current.accent
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Neon.Line, shape)
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            uri = recipe.coverUri,
            title = recipe.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 10f else 3f / 4f),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Neon.Ink.copy(alpha = 0.96f))
            )
        )
        if (recipe.isFavorite) {
            Surface(
                shape = CircleShape,
                color = Neon.Ink.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = Neon.Pink, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 3.dp).width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    recipe.title.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Neon.Text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NeonTag(Icons.Outlined.Schedule, formatMinutes(recipe.totalMinutes))
                NeonTag(Icons.Outlined.Restaurant, "${recipe.servings}")
            }
        }
    }
}

/** Small outlined pill with an icon, used for time / servings / difficulty. */
@Composable
fun NeonTag(icon: ImageVector?, text: String, color: Color = LocalNeon.current.accent) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Neon.Ink.copy(alpha = 0.55f))
            .border(1.dp, color.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Uppercase section title with a short neon bar. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val accent = LocalNeon.current.accent
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val accent = LocalNeon.current.accent
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(84.dp).neonGlow(accent, radius = 22.dp, corner = 42.dp, strength = 0.45f)
                .clip(CircleShape).background(Neon.Panel).border(1.5.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

/** Dark panel with a hairline border; optional neon edge for highlighted content. */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .then(if (glow != null) Modifier.neonGlow(glow, radius = 14.dp, strength = 0.35f) else Modifier)
            .clip(shape)
            .background(Neon.Panel)
            .border(1.dp, glow?.copy(alpha = 0.8f) ?: Neon.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun CertaintyDot(certainty: Certainty, size: Dp = 8.dp) {
    val c = LocalCertaintyColors.current
    val color = when (certainty) {
        Certainty.CONFIRMED -> c.confirmed
        Certainty.ESTIMATED -> c.estimated
        Certainty.MISSING -> c.missing
    }
    Box(Modifier.size(size).neonGlow(color, radius = 4.dp, corner = size / 2, strength = 0.7f).clip(CircleShape).background(color))
}

/** Small pill describing data quality: "Estimated" / "Missing". Confirmed shows nothing to keep UI calm. */
@Composable
fun CertaintyTag(certainty: Certainty) {
    if (certainty == Certainty.CONFIRMED) return
    val c = LocalCertaintyColors.current
    val (label, color) = when (certainty) {
        Certainty.ESTIMATED -> "AI estimate" to c.estimated
        else -> "Missing" to c.missing
    }
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
