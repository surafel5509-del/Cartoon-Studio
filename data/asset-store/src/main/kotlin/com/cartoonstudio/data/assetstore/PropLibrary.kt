package com.cartoonstudio.data.assetstore

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.model.ArtworkAsset
import com.cartoonstudio.domain.model.AssetCategory
import com.cartoonstudio.domain.model.AssetDescriptor
import com.cartoonstudio.domain.model.AssetOrigin

/**
 * Built-in world artwork: props, vehicles, buildings, nature, animals and
 * backgrounds, all generated as editable vector shapes.
 */
object PropLibrary {

    private fun asset(
        id: String,
        name: String,
        category: AssetCategory,
        tags: List<String>,
        strokes: List<Stroke>,
    ) = ArtworkAsset(
        descriptor = AssetDescriptor(
            id = id,
            name = name,
            category = category,
            origin = AssetOrigin.BuiltIn,
            tags = tags + category.displayName.lowercase(),
            packId = BuiltInPacks.WORLD,
            description = "$name — editable vector ${category.displayName.lowercase().removeSuffix("s")}",
        ),
        cel = Cel(id = "cel_$id", strokes = strokes),
    )

    private val green = Rgba.of(76, 175, 80)
    private val darkGreen = Rgba.of(46, 105, 60)
    private val brown = Rgba.of(121, 85, 72)
    private val grey = Rgba.of(158, 158, 158)
    private val red = Rgba.of(229, 57, 53)
    private val blue = Rgba.of(33, 150, 243)
    private val yellow = Rgba.of(255, 214, 0)
    private val sand = Rgba.of(222, 196, 145)

    // ---- Vegetation -------------------------------------------------------

    private fun tree(id: String, name: String, crown: Rgba, tall: Boolean) = asset(
        id, name, AssetCategory.Vegetation, listOf("tree", "plant", "outdoor"),
        listOf(
            ShapeFactory.roundedRect(Vec2(0f, 60f), 26f, 140f, 8f, brown),
            ShapeFactory.circle(Vec2(0f, if (tall) -60f else -30f), if (tall) 70f else 90f, crown),
            ShapeFactory.circle(Vec2(-52f, 10f), 52f, crown),
            ShapeFactory.circle(Vec2(52f, 10f), 52f, crown),
        ),
    )

    private fun bush(id: String, name: String, color: Rgba) = asset(
        id, name, AssetCategory.Vegetation, listOf("bush", "shrub"),
        listOf(
            ShapeFactory.ellipse(Vec2(-30f, 0f), 42f, 34f, color),
            ShapeFactory.ellipse(Vec2(30f, 0f), 42f, 34f, color),
            ShapeFactory.ellipse(Vec2(0f, -18f), 48f, 40f, color),
        ),
    )

    // ---- Buildings --------------------------------------------------------

    private fun house(id: String, name: String, wall: Rgba, roof: Rgba) = asset(
        id, name, AssetCategory.Building, listOf("house", "home", "town"),
        listOf(
            ShapeFactory.roundedRect(Vec2(0f, 40f), 240f, 170f, 6f, wall),
            ShapeFactory.triangle(Vec2(0f, -70f), 290f, 120f, roof),
            ShapeFactory.roundedRect(Vec2(0f, 75f), 56f, 100f, 4f, brown),
            ShapeFactory.roundedRect(Vec2(-72f, 20f), 52f, 52f, 4f, Rgba.of(179, 229, 252)),
            ShapeFactory.roundedRect(Vec2(72f, 20f), 52f, 52f, 4f, Rgba.of(179, 229, 252)),
        ),
    )

    // ---- Vehicles ---------------------------------------------------------

    private fun car(id: String, name: String, body: Rgba) = asset(
        id, name, AssetCategory.Vehicle, listOf("car", "vehicle", "drive"),
        listOf(
            ShapeFactory.roundedRect(Vec2(0f, 0f), 260f, 70f, 22f, body),
            ShapeFactory.roundedRect(Vec2(-10f, -48f), 150f, 60f, 20f, body),
            ShapeFactory.roundedRect(Vec2(-10f, -48f), 130f, 42f, 12f, Rgba.of(179, 229, 252)),
            ShapeFactory.circle(Vec2(-80f, 42f), 32f, Rgba.of(40, 40, 45)),
            ShapeFactory.circle(Vec2(80f, 42f), 32f, Rgba.of(40, 40, 45)),
            ShapeFactory.circle(Vec2(-80f, 42f), 14f, grey),
            ShapeFactory.circle(Vec2(80f, 42f), 14f, grey),
        ),
    )

    // ---- Animals ----------------------------------------------------------

    private fun animal(id: String, name: String, body: Rgba, earHeight: Float, tags: List<String>) = asset(
        id, name, AssetCategory.Animal, tags + listOf("animal", "creature"),
        listOf(
            ShapeFactory.ellipse(Vec2(0f, 20f), 80f, 55f, body),
            ShapeFactory.circle(Vec2(-70f, -30f), 42f, body),
            ShapeFactory.triangle(Vec2(-86f, -64f), 26f, earHeight, body),
            ShapeFactory.triangle(Vec2(-54f, -64f), 26f, earHeight, body),
            ShapeFactory.circle(Vec2(-82f, -34f), 6f, Rgba.Ink),
            ShapeFactory.circle(Vec2(-58f, -34f), 6f, Rgba.Ink),
            ShapeFactory.limb(Vec2(-40f, 60f), Vec2(-40f, 96f), 20f, body),
            ShapeFactory.limb(Vec2(40f, 60f), Vec2(40f, 96f), 20f, body),
            ShapeFactory.limb(Vec2(70f, 5f), Vec2(110f, -30f), 14f, body),
        ),
    )

    // ---- Props ------------------------------------------------------------

    private val props: List<ArtworkAsset> = listOf(
        asset(
            "prop_ball", "Ball", AssetCategory.Prop, listOf("toy", "round", "play"),
            listOf(
                ShapeFactory.circle(Vec2.ZERO, 60f, red),
                ShapeFactory.arc(Vec2.ZERO, 40f, 200f, 140f, Rgba.White, 10f),
            ),
        ),
        asset(
            "prop_box", "Crate", AssetCategory.Prop, listOf("box", "cargo"),
            listOf(
                ShapeFactory.roundedRect(Vec2.ZERO, 140f, 130f, 6f, Rgba.of(188, 143, 86)),
                ShapeFactory.line(listOf(Vec2(-70f, -65f), Vec2(70f, 65f)), brown, 10f),
                ShapeFactory.line(listOf(Vec2(70f, -65f), Vec2(-70f, 65f)), brown, 10f),
            ),
        ),
        asset(
            "prop_chair", "Chair", AssetCategory.Prop, listOf("furniture", "indoor"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, 0f), 120f, 20f, 6f, brown),
                ShapeFactory.roundedRect(Vec2(-52f, -60f), 16f, 120f, 6f, brown),
                ShapeFactory.roundedRect(Vec2(-46f, 50f), 14f, 90f, 4f, brown),
                ShapeFactory.roundedRect(Vec2(46f, 50f), 14f, 90f, 4f, brown),
            ),
        ),
        asset(
            "prop_table", "Table", AssetCategory.Prop, listOf("furniture", "indoor"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, -40f), 220f, 22f, 8f, brown),
                ShapeFactory.roundedRect(Vec2(-90f, 30f), 16f, 120f, 4f, brown),
                ShapeFactory.roundedRect(Vec2(90f, 30f), 16f, 120f, 4f, brown),
            ),
        ),
        asset(
            "prop_lamp", "Street Lamp", AssetCategory.Prop, listOf("light", "city"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, 60f), 16f, 260f, 6f, Rgba.of(69, 90, 100)),
                ShapeFactory.circle(Vec2(0f, -80f), 34f, Rgba.of(255, 241, 118)),
            ),
        ),
        asset(
            "prop_sign", "Sign Post", AssetCategory.Prop, listOf("sign", "direction"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, 70f), 14f, 200f, 4f, brown),
                ShapeFactory.roundedRect(Vec2(0f, -40f), 180f, 70f, 8f, Rgba.of(255, 235, 205)),
            ),
        ),
        asset(
            "prop_book", "Book", AssetCategory.Prop, listOf("read", "school"),
            listOf(
                ShapeFactory.roundedRect(Vec2.ZERO, 120f, 90f, 6f, Rgba.of(103, 58, 183)),
                ShapeFactory.roundedRect(Vec2(4f, 0f), 100f, 76f, 2f, Rgba.White),
            ),
        ),
        asset(
            "prop_cup", "Cup", AssetCategory.Prop, listOf("drink", "kitchen"),
            listOf(
                ShapeFactory.roundedRect(Vec2.ZERO, 70f, 80f, 10f, Rgba.White),
                ShapeFactory.arc(Vec2(44f, 0f), 26f, -80f, 160f, Rgba.Ink, 8f),
            ),
        ),
        asset(
            "prop_balloon", "Balloon", AssetCategory.Prop, listOf("party", "float"),
            listOf(
                ShapeFactory.ellipse(Vec2(0f, -40f), 48f, 58f, Rgba.of(233, 30, 99)),
                ShapeFactory.line(listOf(Vec2(0f, 18f), Vec2(6f, 90f), Vec2(-4f, 150f)), Rgba.Ink, 3f),
            ),
        ),
        asset(
            "prop_star", "Star", AssetCategory.Prop, listOf("shine", "award"),
            listOf(ShapeFactory.star(Vec2.ZERO, 70f, 30f, 5, yellow)),
        ),
    )

    private val terrain: List<ArtworkAsset> = listOf(
        asset(
            "terrain_grass_ground", "Grass Ground", AssetCategory.Terrain, listOf("ground", "field"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, 60f), 1400f, 220f, 30f, green),
                ShapeFactory.ellipse(Vec2(-300f, -40f), 260f, 60f, darkGreen),
                ShapeFactory.ellipse(Vec2(320f, -30f), 220f, 52f, darkGreen),
            ),
        ),
        asset(
            "terrain_hill", "Rolling Hill", AssetCategory.Terrain, listOf("hill", "field"),
            listOf(ShapeFactory.ellipse(Vec2(0f, 180f), 520f, 260f, green)),
        ),
        asset(
            "terrain_rock", "Rock", AssetCategory.Terrain, listOf("stone", "boulder"),
            listOf(
                ShapeFactory.polygon(
                    listOf(
                        Vec2(-70f, 40f), Vec2(-46f, -30f), Vec2(0f, -56f),
                        Vec2(52f, -26f), Vec2(72f, 40f),
                    ),
                    grey,
                ),
            ),
        ),
        asset(
            "terrain_sand", "Sand Dune", AssetCategory.Terrain, listOf("desert", "beach"),
            listOf(ShapeFactory.ellipse(Vec2(0f, 120f), 460f, 180f, sand)),
        ),
    )

    private val sky: List<ArtworkAsset> = listOf(
        asset(
            "sky_cloud", "Cloud", AssetCategory.Sky, listOf("weather", "soft"),
            listOf(
                ShapeFactory.ellipse(Vec2(-60f, 0f), 60f, 40f, Rgba.White, outline = null),
                ShapeFactory.ellipse(Vec2(0f, -18f), 72f, 52f, Rgba.White, outline = null),
                ShapeFactory.ellipse(Vec2(62f, 4f), 56f, 38f, Rgba.White, outline = null),
            ),
        ),
        asset(
            "sky_sun", "Sun", AssetCategory.Sky, listOf("weather", "day"),
            listOf(ShapeFactory.circle(Vec2.ZERO, 80f, Rgba.of(255, 213, 79), outline = null)),
        ),
        asset(
            "sky_moon", "Moon", AssetCategory.Sky, listOf("weather", "night"),
            listOf(ShapeFactory.circle(Vec2.ZERO, 70f, Rgba.of(255, 249, 196), outline = null)),
        ),
    )

    private val backgrounds: List<ArtworkAsset> = listOf(
        asset(
            "bg_meadow", "Meadow", AssetCategory.Background, listOf("outdoor", "day", "nature"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, -240f), 2000f, 900f, 0f, Rgba.of(141, 211, 255), outline = null),
                ShapeFactory.ellipse(Vec2(-400f, 420f), 700f, 320f, darkGreen, outline = null),
                ShapeFactory.roundedRect(Vec2(0f, 520f), 2000f, 560f, 0f, green, outline = null),
                ShapeFactory.circle(Vec2(520f, -260f), 90f, Rgba.of(255, 213, 79), outline = null),
            ),
        ),
        asset(
            "bg_city", "City Street", AssetCategory.Background, listOf("urban", "day"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, -240f), 2000f, 900f, 0f, Rgba.of(176, 216, 240), outline = null),
                ShapeFactory.roundedRect(Vec2(-420f, 40f), 300f, 640f, 0f, Rgba.of(120, 144, 156), outline = null),
                ShapeFactory.roundedRect(Vec2(0f, -20f), 280f, 760f, 0f, Rgba.of(144, 164, 174), outline = null),
                ShapeFactory.roundedRect(Vec2(420f, 80f), 320f, 560f, 0f, Rgba.of(96, 125, 139), outline = null),
                ShapeFactory.roundedRect(Vec2(0f, 560f), 2000f, 400f, 0f, Rgba.of(84, 84, 90), outline = null),
            ),
        ),
        asset(
            "bg_night_sky", "Night Sky", AssetCategory.Background, listOf("night", "space"),
            listOf(
                ShapeFactory.roundedRect(Vec2.ZERO, 2000f, 1400f, 0f, Rgba.of(21, 27, 61), outline = null),
                ShapeFactory.star(Vec2(-380f, -300f), 16f, 6f, 5, Rgba.White),
                ShapeFactory.star(Vec2(240f, -420f), 12f, 5f, 5, Rgba.White),
                ShapeFactory.star(Vec2(520f, -180f), 18f, 7f, 5, Rgba.White),
                ShapeFactory.circle(Vec2(-460f, -420f), 70f, Rgba.of(255, 249, 196), outline = null),
            ),
        ),
        asset(
            "bg_interior", "Room Interior", AssetCategory.Background, listOf("indoor", "home"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, -100f), 2000f, 1000f, 0f, Rgba.of(255, 236, 209), outline = null),
                ShapeFactory.roundedRect(Vec2(0f, 560f), 2000f, 420f, 0f, Rgba.of(188, 143, 86), outline = null),
                ShapeFactory.roundedRect(Vec2(-380f, -60f), 320f, 380f, 10f, Rgba.of(179, 229, 252)),
            ),
        ),
        asset(
            "bg_forest", "Forest", AssetCategory.Background, listOf("outdoor", "nature"),
            listOf(
                ShapeFactory.roundedRect(Vec2(0f, -200f), 2000f, 900f, 0f, Rgba.of(178, 223, 219), outline = null),
                ShapeFactory.roundedRect(Vec2(0f, 540f), 2000f, 440f, 0f, Rgba.of(85, 139, 47), outline = null),
                ShapeFactory.triangle(Vec2(-500f, 180f), 320f, 620f, darkGreen),
                ShapeFactory.triangle(Vec2(-120f, 120f), 280f, 560f, darkGreen),
                ShapeFactory.triangle(Vec2(300f, 200f), 340f, 640f, darkGreen),
            ),
        ),
    )

    /** Everything drawable in the world library. */
    val artwork: List<ArtworkAsset> by lazy {
        buildList {
            addAll(props)
            addAll(terrain)
            addAll(sky)
            addAll(backgrounds)
            add(tree("veg_oak", "Oak Tree", green, tall = false))
            add(tree("veg_pine", "Pine Tree", darkGreen, tall = true))
            add(tree("veg_palm", "Palm Tree", Rgba.of(102, 187, 106), tall = true))
            add(tree("veg_autumn", "Autumn Tree", Rgba.of(239, 108, 0), tall = false))
            add(bush("veg_bush", "Bush", green))
            add(bush("veg_flower_bush", "Flower Bush", Rgba.of(186, 104, 200)))
            add(house("bld_cottage", "Cottage", Rgba.of(255, 236, 209), red))
            add(house("bld_townhouse", "Town House", Rgba.of(207, 216, 220), Rgba.of(69, 90, 100)))
            add(house("bld_school", "School", Rgba.of(255, 224, 178), Rgba.of(93, 64, 55)))
            add(car("veh_car", "Car", red))
            add(car("veh_taxi", "Taxi", yellow))
            add(car("veh_van", "Van", blue))
            add(animal("ani_cat", "Cat", Rgba.of(255, 167, 38), 30f, listOf("pet", "cat")))
            add(animal("ani_dog", "Dog", Rgba.of(141, 110, 99), 22f, listOf("pet", "dog")))
            add(animal("ani_fox", "Fox", Rgba.of(230, 81, 0), 34f, listOf("wild", "fox")))
            add(animal("ani_bear", "Bear", Rgba.of(93, 64, 55), 18f, listOf("wild", "bear")))
            add(animal("ani_rabbit", "Rabbit", Rgba.of(224, 224, 224), 52f, listOf("pet", "rabbit")))
        }
    }

    val byId: Map<String, ArtworkAsset> by lazy { artwork.associateBy { it.descriptor.id } }

    fun resolve(assetId: String): ArtworkAsset? = byId[assetId]
}
