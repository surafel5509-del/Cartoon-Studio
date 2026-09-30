package com.cartoonstudio.data.assetstore

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.model.AssetCategory
import com.cartoonstudio.domain.model.AssetFilter
import com.cartoonstudio.domain.model.AssetGroup
import com.cartoonstudio.domain.model.LayerContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetLibraryTest {

    @Test
    fun `asset ids are unique across the whole catalog`() {
        val ids = AssetCatalog.descriptors.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate asset ids in the catalog")
    }

    @Test
    fun `every character exposes one artwork layer per bone`() {
        CharacterLibrary.packages.forEach { pack ->
            val boneIds = pack.skeleton.bones.map { it.id }.toSet()
            val artworkIds = pack.artworkLayers.map { it.id }.toSet()
            assertTrue(
                artworkIds.all { it.removePrefix("art_") in boneIds },
                "${pack.id} has artwork that maps to no bone",
            )
        }
    }

    @Test
    fun `motion clips only drive bones that exist on the biped rig`() {
        val biped = CharacterLibrary.packages.first().skeleton.bones.map { it.id }.toSet()
        AnimationLibrary.clips.forEach { clip ->
            clip.boneTracks.keys.forEach { boneId ->
                assertTrue(boneId in biped, "${clip.id} animates unknown bone $boneId")
            }
        }
    }

    @Test
    fun `a character instance produces a layer per bone with stable ids`() {
        val pack = CharacterLibrary.packages.first()
        val layer = AssetInstancer.instantiateCharacter(pack, Vec2(100f, 200f))
        val group = layer.content as LayerContent.Group
        assertEquals(pack.artworkLayers.size, group.children.size)
        group.children.forEach { child ->
            assertNotNull(AssetInstancer.boneIdForChild(child), "${child.id} has no bone mapping")
            assertTrue(child.id.startsWith(layer.id))
        }
    }

    @Test
    fun `applying a clip animates the instanced layers`() {
        val pack = CharacterLibrary.packages.first()
        val instance = AssetInstancer.instantiateCharacter(pack, Vec2.ZERO)
        val animated = AssetInstancer.applyClip(instance, AnimationLibrary.walk, com.cartoonstudio.core.time.Frame.ZERO)
        val group = animated.content as LayerContent.Group
        assertTrue(group.children.any { it.tracks.isAnimated }, "no child picked up the walk cycle")
    }

    @Test
    fun `search filters by query and group`() {
        val repository = AssetRepository()
        val page = repository.query(AssetFilter(groups = setOf(AssetGroup.Cast)), limit = 500)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it.category.group == AssetGroup.Cast })
    }

    @Test
    fun `paging never returns the same item twice`() {
        val repository = AssetRepository()
        val first = repository.query(AssetFilter(), offset = 0, limit = 25)
        val second = repository.query(AssetFilter(), offset = 25, limit = 25)
        assertTrue(first.items.map { it.id }.intersect(second.items.map { it.id }.toSet()).isEmpty())
        assertTrue(first.hasMore)
    }

    @Test
    fun `props resolve to drawable artwork`() {
        val prop = AssetCatalog.descriptors.first { it.category == AssetCategory.Prop }
        val artwork = PropLibrary.resolve(prop.id)
        assertNotNull(artwork)
        assertTrue(artwork.cel.strokes.isNotEmpty(), "${prop.id} has no drawable geometry")
    }
}
