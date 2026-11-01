@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Warm gradient placeholders so recipes without photos still look intentional. */
private val placeholderGradients = listOf(
    listOf(Color(0xFFE76F51), Color(0xFFF4A261)),
    listOf(Color(0xFF606C38), Color(0xFFDDA15E)),
    listOf(Color(0xFF9C6644), Color(0xFFE6B8A2)),
    listOf(Color(0xFFBC4749), Color(0xFFF2CC8F)),
    listOf(Color(0xFF3D5A40), Color(0xFFA3B18A)),
)

@Composable
fun CoverImage(uri: String?, title: String, modifier: Modifier = Modifier) {
    val gradient = placeholderGradients[(title.hashCode() and 0x7fffffff) % placeholderGradients.size]
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().firstOrNull()?.uppercase() ?: "",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
    if (uri == null) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            CoverImage(
                uri = recipe.coverUri,
                title = recipe.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 10f else 4f / 3.4f)
                    .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)),
            )
            if (recipe.isFavorite) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaInline(Icons.Outlined.Schedule, formatMinutes(recipe.totalMinutes))
                Spacer(Modifier.width(12.dp))
                MetaInline(Icons.Outlined.Restaurant, "${recipe.servings}")
            }
        }
    }
}

@Composable
private fun MetaInline(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun CertaintyDot(certainty: Certainty, size: Dp = 8.dp) {
    val c = LocalCertaintyColors.current
    val color = when (certainty) {
        Certainty.CONFIRMED -> c.confirmed
        Certainty.ESTIMATED -> c.estimated
        Certainty.MISSING -> c.missing
    }
    Box(Modifier.size(size).clip(CircleShape).background(color))
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
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
