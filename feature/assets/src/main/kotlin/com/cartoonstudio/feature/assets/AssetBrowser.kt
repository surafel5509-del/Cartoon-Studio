package com.cartoonstudio.feature.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.data.assetstore.AssetRepository
import com.cartoonstudio.data.assetstore.CharacterLibrary
import com.cartoonstudio.data.assetstore.PropLibrary
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.model.AssetDescriptor
import com.cartoonstudio.domain.model.AssetFilter
import com.cartoonstudio.domain.model.AssetGroup
import com.cartoonstudio.engine.drawing.StrokeTessellator
import com.cartoonstudio.engine.rendering.RenderCommand
import com.cartoonstudio.engine.rendering.RenderGraph
import com.cartoonstudio.platform.graphics.CanvasRenderer
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.designsystem.spacing

/**
 * The production asset browser.
 *
 * Results are paged and virtualised so the panel stays responsive with tens
 * of thousands of catalog entries; only descriptors are touched until an
 * asset is actually placed in the scene.
 */
@Composable
fun AssetBrowserPanel(
    repository: AssetRepository,
    filter: AssetFilter,
    onFilterChanged: (AssetFilter) -> Unit,
    onAssetChosen: (AssetDescriptor) -> Unit,
    onToggleFavorite: (AssetDescriptor) -> Unit,
    modifier: Modifier = Modifier,
    pageSize: Int = 90,
) {
    val page = remember(filter, repository, pageSize) { repository.query(filter, 0, pageSize) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilterChanged(filter.copy(query = it)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search ${repository.all().size} assets") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
        )

        OptionRow(
            options = listOf<AssetGroup?>(null) + AssetGroup.entries.toList(),
            selected = filter.groups.firstOrNull(),
            onSelect = { group ->
                onFilterChanged(filter.copy(groups = if (group == null) emptySet() else setOf(group)))
            },
            labelOf = { it?.displayName ?: "All" },
        )

        val activeGroup = filter.groups.firstOrNull()
        if (activeGroup != null) {
            val categories = com.cartoonstudio.domain.model.AssetCategory.inGroup(activeGroup)
            OptionRow(
                modifier = Modifier.padding(top = MaterialTheme.spacing.tiny),
                options = listOf<com.cartoonstudio.domain.model.AssetCategory?>(null) + categories,
                selected = filter.categories.firstOrNull(),
                onSelect = { category ->
                    onFilterChanged(
                        filter.copy(categories = if (category == null) emptySet() else setOf(category)),
                    )
                },
                labelOf = { it?.displayName ?: "Everything" },
            )
        }

        SectionHeader(
            title = "${page.totalMatches} results",
            trailing = {
                IconButton(onClick = { onFilterChanged(filter.copy(favoritesOnly = !filter.favoritesOnly)) }) {
                    Icon(
                        if (filter.favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Favorites only",
                        tint = if (filter.favoritesOnly) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        if (page.items.isEmpty()) {
            com.cartoonstudio.ui.components.EmptyState(
                icon = Icons.Filled.Widgets,
                title = "No matching assets",
                message = "Try a different search or clear the filters.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(page.items.size) { index ->
                    val descriptor = page.items[index]
                    AssetCard(
                        descriptor = descriptor,
                        onClick = { onAssetChosen(descriptor) },
                        onToggleFavorite = { onToggleFavorite(descriptor) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetCard(
    descriptor: AssetDescriptor,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF14141A)),
                contentAlignment = Alignment.Center,
            ) {
                val cel = remember(descriptor.id) { previewCelFor(descriptor) }
                if (cel != null) {
                    CelPreview(cel, Modifier.fillMaxSize().padding(6.dp))
                } else {
                    Icon(
                        Icons.Filled.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        if (descriptor.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (descriptor.favorite) MaterialTheme.colorScheme.tertiary
                        else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    descriptor.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    descriptor.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Resolves a drawable preview for an asset, when one exists. */
private fun previewCelFor(descriptor: AssetDescriptor): Cel? {
    PropLibrary.resolve(descriptor.id)?.let { return it.cel }
    CharacterLibrary.byId(descriptor.id)?.let { pack ->
        val strokes = pack.artworkLayers.flatMap { layer ->
            val drawing = layer.content as? com.cartoonstudio.domain.model.LayerContent.Drawing
            val cel = drawing?.cels?.get(0) ?: return@flatMap emptyList()
            val offset = layer.transform.position
            cel.strokes.map { stroke -> stroke.translated(offset) }
        }
        return Cel(id = "preview_${pack.id}", strokes = strokes)
    }
    return null
}

/** Renders a cel scaled to fit, using the shared engine renderer. */
@Composable
fun CelPreview(cel: Cel, modifier: Modifier = Modifier) {
    val renderer = remember { CanvasRenderer() }
    val bounds = remember(cel) { cel.bounds() }

    Canvas(modifier = modifier) {
        if (bounds.isEmpty || size.width <= 0f || size.height <= 0f) return@Canvas
        val scale = minOf(size.width / bounds.width, size.height / bounds.height) * 0.92f
        val matrix = Matrix3.translation(
            Vec2(size.width / 2f, size.height / 2f),
        ) * Matrix3.scale(scale) * Matrix3.translation(-bounds.center)

        val commands = cel.strokes.mapNotNull { stroke ->
            val geometry = StrokeTessellator.tessellate(stroke, 1f)
            if (geometry.isEmpty) null
            else RenderCommand.FillPath(matrix, geometry.outline, stroke.color.argb)
        }
        drawIntoCanvas { canvas ->
            renderer.render(
                canvas.nativeCanvas,
                RenderGraph(commands, matrix, size.width, size.height, 0),
            )
        }
    }
}

/** Bounds helper exposed for tests and layout maths. */
fun Cel.previewBounds(): Rect2 = bounds()
