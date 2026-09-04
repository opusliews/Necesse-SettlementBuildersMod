package opus.damage;

import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MaterialWeatheringClassifier {
	private static final Set<String> woodFurnitureCategories = setOf(
			"oak", "spruce", "pine", "willow", "palm", "maple", "birch", "dryad", "bamboo", "deadwood"
	);

	private static final Map<String, WeatheringMaterialTier> furnitureCategoryTiers = new HashMap<>();
	private static final Map<String, WeatheringMaterialTier> materialPrefixTiers = new HashMap<>();
	private static final Map<String, WeatheringMaterialTier> wallPrefixTiers = new HashMap<>();
	private static final Map<String, WeatheringMaterialTier> fencePrefixTiers = new HashMap<>();
	private static final Map<String, WeatheringMaterialTier> explicitObjectTiers = new HashMap<>();
	private static final Map<String, WeatheringMaterialTier> tileTiers = new HashMap<>();

	static {
		for (String category : woodFurnitureCategories) {
			furnitureCategoryTiers.put(category, WeatheringMaterialTier.WOOD);
		}
		furnitureCategoryTiers.put("dungeon", WeatheringMaterialTier.REINFORCED);

		// General material prefixes used only after an object has already been
		// identified as belonging to a construction-like family.
		putMaterialPrefixes(
				WeatheringMaterialTier.WOOD,
				"deadwood", "spruce", "willow", "bamboo", "maple", "birch",
				"dryad", "pine", "palm", "oak", "wood"
		);
		putMaterialPrefixes(
				WeatheringMaterialTier.SURFACE_MASONRY,
				"swampstone", "snowstone", "sandstone", "granite", "stone", "brick"
		);
		putMaterialPrefixes(
				WeatheringMaterialTier.REINFORCED,
				"deepstone", "dungeon", "obsidian", "ice", "iron"
		);
		putMaterialPrefixes(
				WeatheringMaterialTier.ADVANCED_BIOME_STONE,
				"deepswampstone", "deepsnowstone", "deepsandstone", "basalt"
		);
		putMaterialPrefixes(
				WeatheringMaterialTier.LATE_GAME_MASONRY,
				"spidercastle", "ancientruin", "factory", "arcanic", "raven", "dawn", "dusk",
				"crystal", "amethyst", "sapphire", "emerald", "ruby", "topaz"
		);

		// Walls and their generated door variants.
		putWallPrefixes(
				WeatheringMaterialTier.WOOD,
				"wood", "pine", "palm", "willow", "dryad", "bamboo"
		);
		putWallPrefixes(
				WeatheringMaterialTier.SURFACE_MASONRY,
				"stone", "sandstone", "swampstone", "snowstone", "granite", "brick"
		);
		putWallPrefixes(
				WeatheringMaterialTier.REINFORCED,
				"dungeon", "deepstone", "ice", "obsidian"
		);
		putWallPrefixes(
				WeatheringMaterialTier.ADVANCED_BIOME_STONE,
				"deepsnowstone", "basalt", "deepswampstone", "deepsandstone"
		);
		putWallPrefixes(
				WeatheringMaterialTier.LATE_GAME_MASONRY,
				"spidercastle", "dawn", "dusk", "ancientruin", "raven", "arcanic", "factory"
		);

		fencePrefixTiers.put("wood", WeatheringMaterialTier.WOOD);
		fencePrefixTiers.put("stone", WeatheringMaterialTier.SURFACE_MASONRY);
		fencePrefixTiers.put("iron", WeatheringMaterialTier.REINFORCED);

		// Outliers where category/class/prefix is not enough.
		putExplicitObjects(
				WeatheringMaterialTier.WOOD,
				"sprucelogbench",
				"willowlogbench",
				"dryadlogbench",
				"bamboologbench",
				"storagebox",
				"barrel",
				"sign",
				"woodencandleset"
		);

		putExplicitObjects(
				WeatheringMaterialTier.SURFACE_MASONRY,
				"stonecandlepedestal",
				"snowcandlepedestal",
				"swampcandlepedestal",
				"desertcandlepedestal"
		);

		putTiles(
				WeatheringMaterialTier.WOOD,
				"woodfloor",
				"pinefloor",
				"palmfloor",
				"willowfloor",
				"dryadfloor",
				"bamboofloor",
				"deadwoodfloor",
				"woodpathtile",
				"dryadpath"
		);

		putTiles(
				WeatheringMaterialTier.SURFACE_MASONRY,
				"stonefloor",
				"stonebrickfloor",
				"stonetiledfloor",
				"stonepathtile",
				"sandstonetile",
				"sandstonefloor",
				"sandstonebrickfloor",
				"sandstonepathtile",
				"sandbrick",
				"swampstonefloor",
				"swampstonebrickfloor",
				"swampstonepathtile",
				"snowstonefloor",
				"snowstonebrickfloor",
				"snowstonepathtile",
				"granitefloor",
				"granitebrickfloor",
				"granitepathtile",
				"puddlecobble"
		);

		putTiles(
				WeatheringMaterialTier.REINFORCED,
				"dungeonfloor",
				"deepstonefloor",
				"deepstonebrickfloor",
				"deepstonetiledfloor",
				"deepicetile"
		);

		putTiles(
				WeatheringMaterialTier.ADVANCED_BIOME_STONE,
				"deepsnowstonefloor",
				"deepsnowstonebrickfloor",
				"basaltfloor",
				"basaltpathtile",
				"deepswampstonefloor",
				"deepswampstonebrickfloor",
				"deepsandstonetile"
		);

		putTiles(
				WeatheringMaterialTier.LATE_GAME_MASONRY,
				"spidercastlefloor",
				"spidercobbletile",
				"spidercastlecarpet",
				"dawnpath",
				"lavapath",
				"moonpath",
				"darkmoonpath",
				"darkfullmoonpath",
				"ancientruinfloor",
				"ravenfloor",
				"arcanicfloor",
				"arcanicpath",
				"crystaltile",
				"amethystgravel",
				"sapphiregravel",
				"emeraldgravel",
				"rubygravel",
				"topazgravel"
		);
	}

	private MaterialWeatheringClassifier() {
	}

	public static WeatheringMaterialTier getObjectTier(GameObject object) {
		if (object == null || object.getID() == 0 || object.objectHealth <= 0) {
			return null;
		}

		String objectID = object.getStringID();

		WeatheringMaterialTier explicitTier = explicitObjectTiers.get(objectID);
		if (explicitTier != null) {
			return explicitTier;
		}

		// Walls include their associated door variants.
		if (object.isWall) {
			return getPrefixTier(objectID, wallPrefixTiers);
		}

		// Only known actual construction fence families are included.
		// Living hedges and other special fences return null.
		if (object.isFence) {
			return getPrefixTier(objectID, fencePrefixTiers);
		}

		String[] category = object.itemCategoryTree;

		WeatheringMaterialTier categoryTier = getFurnitureCategoryTier(category);
		if (categoryTier != null) {
			return categoryTier;
		}

		// Some later furniture is registered as furniture/misc rather than
		// receiving a useful material category. In that case the ID prefix
		// is still reliable because we already know it is furniture.
		if (isCategory(category, "objects", "furniture")) {
			return getPrefixTier(objectID, materialPrefixTiers);
		}

		// ColumnObject uses objects/columns rather than furniture.
		if (isCategory(category, "objects", "columns")) {
			return getPrefixTier(objectID, materialPrefixTiers);
		}

		if (isCategory(category, "objects", "landscaping", "masonry")) {
			return getPrefixTier(objectID, materialPrefixTiers);
		}

		// Vanilla material pressure plates all use the generic "wiring"
		// category, so the suffix identifies the construction family and
		// the material prefix determines its tier.
		if (objectID.endsWith("pressureplate")) {
			return getPrefixTier(objectID, materialPrefixTiers);
		}

		return null;
	}

	public static WeatheringMaterialTier getTileTier(GameTile tile) {
		if (tile == null || tile.getID() == 0 || tile.tileHealth <= 0) {
			return null;
		}

		return tileTiers.get(tile.getStringID());
	}

	public static boolean isWeatherableFence(GameObject object) {
		WeatheringMaterialTier tier = getObjectTier(object);
		return object != null && object.isFence && tier != null && tier.isWeatherable();
	}

	private static WeatheringMaterialTier getFurnitureCategoryTier(String[] category) {
		if (category == null || category.length < 3) {
			return null;
		}

		if (!"objects".equals(category[0]) || !"furniture".equals(category[1])) {
			return null;
		}

		return furnitureCategoryTiers.get(category[2]);
	}

	private static boolean isCategory(String[] category, String... expected) {
		if (category == null || category.length < expected.length) {
			return false;
		}

		for (int i = 0; i < expected.length; i++) {
			if (!expected[i].equals(category[i])) {
				return false;
			}
		}

		return true;
	}

	private static WeatheringMaterialTier getPrefixTier(String stringID, Map<String, WeatheringMaterialTier> tiers) {
		WeatheringMaterialTier bestTier = null;
		int bestLength = -1;

		for (Map.Entry<String, WeatheringMaterialTier> entry : tiers.entrySet()) {
			String prefix = entry.getKey();

			if (stringID.startsWith(prefix) && prefix.length() > bestLength) {
				bestTier = entry.getValue();
				bestLength = prefix.length();
			}
		}

		return bestTier;
	}

	private static void putMaterialPrefixes(WeatheringMaterialTier tier, String... prefixes) {
		for (String prefix : prefixes) {
			materialPrefixTiers.put(prefix, tier);
		}
	}

	private static void putWallPrefixes(WeatheringMaterialTier tier, String... prefixes) {
		for (String prefix : prefixes) {
			wallPrefixTiers.put(prefix, tier);
		}
	}

	private static void putExplicitObjects(WeatheringMaterialTier tier, String... objectIDs) {
		for (String objectID : objectIDs) {
			explicitObjectTiers.put(objectID, tier);
		}
	}

	private static void putTiles(WeatheringMaterialTier tier, String... tileIDs) {
		for (String tileID : tileIDs) {
			tileTiers.put(tileID, tier);
		}
	}

	private static Set<String> setOf(String... values) {
		return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
	}
}