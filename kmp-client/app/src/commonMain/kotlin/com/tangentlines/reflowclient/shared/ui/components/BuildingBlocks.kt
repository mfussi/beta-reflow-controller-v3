package com.tangentlines.reflowclient.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorFilter.Companion.tint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.imageResource
import reflow_mpp_client.app.generated.resources.Res
import reflow_mpp_client.app.generated.resources.oven

@Composable
fun ReflowHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val grad = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            )
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(grad)
        ) {

            Image(
                bitmap = imageResource(Res.drawable.oven),
                colorFilter = tint(Color.White),
                contentDescription = "Icon",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )

        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

data class ActionButton(
    val text : String,
    val icon : ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

@Composable
fun OutlineAccentIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    borderWidth: Dp = 1.5.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 22.dp   // icon size inside
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.5f)

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = color,
            disabledContentColor = color
        ),
        modifier = modifier
            .size(size)
            .border(BorderStroke(borderWidth, color), CircleShape)
            .clip(CircleShape)
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PrimaryRoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,      // button diameter
    iconSize: Dp = 22.dp   // icon size inside
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription ?: "" },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,   // orange
            contentColor   = MaterialTheme.colorScheme.onPrimary  // white icon
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: List<ActionButton>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape), // thin soft outline
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // <-- peach tile
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)     // flat like the mock
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))

                if(actions?.isNotEmpty() == true) {
                    Row(horizontalArrangement = spacedBy(8.dp)) {
                        actions.forEach { a ->
                            OutlineAccentIconButton(
                                onClick = a.onClick,
                                enabled = a.enabled,
                                contentDescription = a.text,
                                icon = a.icon,
                                size = 36.dp,
                                iconSize = 20.dp
                            )
                        }
                    }
                }

            }
            if(description?.isNotEmpty() == true) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) { Text(text) }
}

@Composable
fun OutlineAccentButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = ButtonDefaults.outlinedButtonBorder().copy(
            brush = Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
            )
        )
    ) { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}


@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
) {

    val interactions = remember { MutableInteractionSource() }

    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface

    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(50),
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = 0.dp,
        color = bg,
        modifier = modifier
            .combinedClickable(
                interactionSource = interactions,
                indication = null,           // let Surface provide ripple
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongPress,
                onDoubleClick = null,
                onLongClickLabel = "More actions"
            ),
        border = if (selected) null else
            ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                )
            )
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                text = text,
                color = fg,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun ChipRow(
    items: List<String>,
    selected: String?,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onLongPress: ((String) -> Unit)? = null,
) {

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(6.dp),
        verticalArrangement = spacedBy(6.dp)
    ) {
        items.forEach { name ->
            PillChip(
                text = name,
                selected = name == selected,
                onClick = { onClick(name) },
                onLongPress = onLongPress?.let { { it.invoke(name) } },
            )
        }
    }
}