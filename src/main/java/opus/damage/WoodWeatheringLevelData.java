package opus.damage;

import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.entity.ObjectDamageResult;
import necesse.entity.manager.ObjectPlacedListenerEntityComponent;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.RegionLevelDataComponent;
import necesse.level.maps.regionSystem.Region;
import opus.logging.Logging;

import java.awt.*;
import java.util.*;

public class WoodWeatheringLevelData extends LevelData implements
		RegionLoadedListenerEntityComponent,
		RegionLevelDataComponent,
		ObjectPlacedListenerEntityComponent {
	public static final String managerKey = "opuswoodweathering";

	private static final long checkInterval = 10000L;
	private static final long normalMinExposure = 15L * 60L * 1000L;
	private static final long normalMaxExposure = 25L * 60L * 1000L;
	private static final long wallMinExposure = 45L * 60L * 1000L;
	private static final long wallMaxExposure = 75L * 60L * 1000L;
	private static final float damageFraction = 0.60F;
	private static final long damageDelayMin = 0L;
	private static final long damageDelayMax = 30000L;

	private final Map<String, WeatheringEntry> entries = new HashMap<>();
	private long nextCheckTime;

	public static WoodWeatheringLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);

		if (existing instanceof WoodWeatheringLevelData) {
			return (WoodWeatheringLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		WoodWeatheringLevelData data = new WoodWeatheringLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	@Override
	public void onLoadingComplete() {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		level.regionManager.forEachLoadedRegions(this::scanRegion);
	}

	@Override
	public void onRegionLoaded(Region region) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		scanRegion(region);
	}

	@Override
	public void onObjectPlaced(GameObject object, int objectLayerID, int tileX, int tileY, ServerClient client) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		Point masterTile = DamageRepairLevelData.getObjectMasterTile(level, objectLayerID, tileX, tileY);
		GameObject masterObject = level.getObject(objectLayerID, masterTile.x, masterTile.y);
		registerObject(masterObject, objectLayerID, masterTile.x, masterTile.y, true);
	}

	@Override
	public void tick() {
		if (!isServer() || !HardcoreDamage.isEnabled(level) || entries.isEmpty()) {
			return;
		}

		long currentTime = level.getTime();

		processPendingDamage(currentTime);

		if (currentTime < nextCheckTime) {
			return;
		}

		nextCheckTime = currentTime + checkInterval;

		if (!level.weatherLayer.isRaining()) {
			return;
		}

		for (WeatheringEntry entry : entries.values()) {
			GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);

			if (!isStillTrackedObject(object, entry)) {
				continue;
			}

			if (entry.pendingDamageTime > 0L) {
				continue;
			}

			if (!isExposedToRain(object, entry.objectLayerID, entry.tileX, entry.tileY)) {
				continue;
			}

			entry.accumulatedRainExposure += checkInterval;

			if (entry.accumulatedRainExposure >= entry.requiredRainExposure) {
				entry.pendingDamageTime = currentTime + rollDamageDelay();
			}
		}

		entries.values().removeIf(entry -> {
			GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);
			return !isStillTrackedObject(object, entry);
		});
	}

	private void processPendingDamage(long currentTime) {
		Iterator<WeatheringEntry> iterator = entries.values().iterator();

		while (iterator.hasNext()) {
			WeatheringEntry entry = iterator.next();

			if (entry.pendingDamageTime <= 0L || currentTime < entry.pendingDamageTime) {
				continue;
			}

			GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);

			if (!isStillTrackedObject(object, entry)) {
				iterator.remove();
				continue;
			}

			int damage = Math.max(1, (int)Math.ceil(object.objectHealth * damageFraction));

			ObjectDamageResult result = level.entityManager.doObjectDamage(
					entry.objectLayerID,
					entry.tileX,
					entry.tileY,
					damage,
					Float.MAX_VALUE,
					null,
					null,
					true,
					entry.tileX * 32 + 16,
					entry.tileY * 32 + 16
			);

			if (result != null && result.destroyed) {
				Logging.logMessage(
						"Rain weathering destroyed "
								+ entry.objectID
								+ " at "
								+ entry.tileX
								+ ", "
								+ entry.tileY
				);

				iterator.remove();
				continue;
			}

			Logging.logMessage(
					"Rain weathering damaged "
							+ entry.objectID
							+ " at "
							+ entry.tileX
							+ ", "
							+ entry.tileY
							+ " for "
							+ damage
			);

			entry.accumulatedRainExposure = 0L;
			entry.requiredRainExposure = rollExposureThreshold(object.isWall);
			entry.pendingDamageTime = 0L;
		}
	}

	private long rollDamageDelay() {
		return damageDelayMin
				+ (long)(GameRandom.globalRandom.nextDouble() * (damageDelayMax - damageDelayMin + 1L));
	}

	@Override
	public void addRegionSaveData(Region region, SaveData save) {
		for (WeatheringEntry entry : entries.values()) {
			if (!isInsideRegion(region, entry.tileX, entry.tileY)) {
				continue;
			}

			SaveData entrySave = new SaveData("ENTRY");
			entrySave.addInt("tileX", entry.tileX);
			entrySave.addInt("tileY", entry.tileY);
			entrySave.addInt("objectLayerID", entry.objectLayerID);
			entrySave.addUnsafeString("objectID", entry.objectID);
			entrySave.addLong("accumulatedRainExposure", entry.accumulatedRainExposure);
			entrySave.addLong("requiredRainExposure", entry.requiredRainExposure);
			entrySave.addLong("pendingDamageTime", entry.pendingDamageTime);
			save.addSaveData(entrySave);
		}
	}

	@Override
	public void loadRegionSaveData(Region region, LoadData save) {
		for (LoadData entryLoad : save.getLoadDataByName("ENTRY")) {
			int tileX = entryLoad.getInt("tileX");
			int tileY = entryLoad.getInt("tileY");
			int objectLayerID = entryLoad.getInt("objectLayerID", 0, false);
			String objectID = entryLoad.getUnsafeString("objectID", null, false);

			if (objectID == null) {
				continue;
			}

			WeatheringEntry entry = new WeatheringEntry(
					tileX,
					tileY,
					objectLayerID,
					objectID,
					entryLoad.getLong("accumulatedRainExposure", 0L, false),
					entryLoad.getLong("requiredRainExposure", 0L, false),
					entryLoad.getLong("pendingDamageTime", 0L, false)
			);

			if (entry.requiredRainExposure <= 0L) {
				GameObject object = level.getObject(objectLayerID, tileX, tileY);
				entry.requiredRainExposure = rollExposureThreshold(object != null && object.isWall);
			}

			entries.put(getEntryKey(objectLayerID, tileX, tileY), entry);
		}
	}

	@Override
	public void onUnloadedRegion(Region region) {
		entries.values().removeIf(entry -> isInsideRegion(region, entry.tileX, entry.tileY));
	}

	public void resetWeatheringAt(int tileX, int tileY) {
		for (WeatheringEntry entry : entries.values()) {
			if (entry.tileX != tileX || entry.tileY != tileY) {
				continue;
			}

			GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);
			entry.accumulatedRainExposure = 0L;
			entry.requiredRainExposure = rollExposureThreshold(object != null && object.isWall);
			entry.pendingDamageTime = 0L;

			Logging.logMessage(
					"Reset rain weathering for "
							+ entry.objectID
							+ " at "
							+ tileX
							+ ", "
							+ tileY
			);
		}
	}

	private void scanRegion(Region region) {
		for (int x = region.tileXOffset; x < region.tileXOffset + region.tileWidth; x++) {
			for (int y = region.tileYOffset; y < region.tileYOffset + region.tileHeight; y++) {
				for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
					GameObject object = level.getObject(layerID, x, y);
					registerObject(object, layerID, x, y, false);
				}
			}
		}
	}

	private void registerObject(GameObject object, int objectLayerID, int tileX, int tileY, boolean resetExisting) {
		String key = getEntryKey(objectLayerID, tileX, tileY);

		if (!isWeatherableWood(object)
				|| !object.isMultiTileMaster()
				|| !level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) {
			entries.remove(key);
			return;
		}

		if (!resetExisting && entries.containsKey(key)) {
			return;
		}

		entries.put(key,
				new WeatheringEntry(
						tileX,
						tileY,
						objectLayerID,
						object.getStringID(),
						0L,
						rollExposureThreshold(object.isWall),
						0L
				)
		);
	}

	private boolean isStillTrackedObject(GameObject object, WeatheringEntry entry) {
		return isWeatherableWood(object)
				&& object.isMultiTileMaster()
				&& object.getStringID().equals(entry.objectID)
				&& level.objectLayer.isPlayerPlaced(entry.objectLayerID, entry.tileX, entry.tileY);
	}

	private static final Set<String> woodFurnitureCategories = new HashSet<>(Arrays.asList(
			"oak",
			"spruce",
			"pine",
			"willow",
			"palm",
			"maple",
			"birch",
			"dryad",
			"bamboo",
			"deadwood"
	));

	private static final Set<String> woodWallPrefixes = new HashSet<>(Arrays.asList(
			"wood",
			"pine",
			"palm",
			"willow",
			"dryad",
			"bamboo"
	));

	private static final Set<String> explicitWoodObjects = new HashSet<>(Arrays.asList(
			"woodfence",
			"woodfencegate",
			"woodfencegateopen",
			"sprucelogbench",
			"willowlogbench",
			"dryadlogbench",
			"bamboologbench"
	));

	public static boolean isWeatherableFence(GameObject object) {
		return object != null
				&& object.isFence
				&& explicitWoodObjects.contains(object.getStringID());
	}

	private boolean isWeatherableWood(GameObject object) {
		if (object == null || object.getID() == 0 || object.objectHealth <= 0) {
			return false;
		}

		if (isWoodFurniture(object)) {
			return true;
		}

		String objectID = object.getStringID();

		if (explicitWoodObjects.contains(objectID)) {
			return true;
		}

		for (String prefix : woodWallPrefixes) {
			if (objectID.equals(prefix + "wall")
					|| objectID.equals(prefix + "door")
					|| objectID.equals(prefix + "dooropen")
					|| objectID.equals(prefix + "doorlocked")
					|| objectID.equals(prefix + "doorunlocked")
					|| objectID.equals(prefix + "window")) {
				return true;
			}
		}

		return false;
	}

	private boolean isWoodFurniture(GameObject object) {
		String[] category = object.itemCategoryTree;

		return category != null
				&& category.length >= 3
				&& "objects".equals(category[0])
				&& "furniture".equals(category[1])
				&& woodFurnitureCategories.contains(category[2]);
	}

	private boolean isExposedToRain(GameObject object, int objectLayerID, int tileX, int tileY) {
		Rectangle footprint = object
				.getMultiTile(level, objectLayerID, tileX, tileY)
				.getTileRectangle(tileX, tileY);

		if (object.isWall) {
			for (int x = footprint.x; x < footprint.x + footprint.width; x++) {
				for (int y = footprint.y; y < footprint.y + footprint.height; y++) {
					if (isOutsideOrBeyondLevel(x - 1, y)
							|| isOutsideOrBeyondLevel(x + 1, y)
							|| isOutsideOrBeyondLevel(x, y - 1)
							|| isOutsideOrBeyondLevel(x, y + 1)) {
						return true;
					}
				}
			}

			return false;
		}

		for (int x = footprint.x; x < footprint.x + footprint.width; x++) {
			for (int y = footprint.y; y < footprint.y + footprint.height; y++) {
				if (isOutsideOrBeyondLevel(x, y)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isOutsideOrBeyondLevel(int tileX, int tileY) {
		return !level.isTileWithinBounds(tileX, tileY) || level.isOutside(tileX, tileY);
	}

	private long rollExposureThreshold(boolean wall) {
		long min = wall ? wallMinExposure : normalMinExposure;
		long max = wall ? wallMaxExposure : normalMaxExposure;
		return min + (long)(GameRandom.globalRandom.nextDouble() * (max - min + 1L));
	}

	private static String getEntryKey(int objectLayerID, int tileX, int tileY) {
		return objectLayerID + ":" + tileX + ":" + tileY;
	}

	private static boolean isInsideRegion(Region region, int tileX, int tileY) {
		return tileX >= region.tileXOffset
				&& tileX < region.tileXOffset + region.tileWidth
				&& tileY >= region.tileYOffset
				&& tileY < region.tileYOffset + region.tileHeight;
	}

	private static class WeatheringEntry {
		private final int tileX;
		private final int tileY;
		private final int objectLayerID;
		private final String objectID;
		private long accumulatedRainExposure;
		private long requiredRainExposure;
		private long pendingDamageTime;

		private WeatheringEntry(
				int tileX,
				int tileY,
				int objectLayerID,
				String objectID,
				long accumulatedRainExposure,
				long requiredRainExposure,
				long pendingDamageTime
		) {
			this.tileX = tileX;
			this.tileY = tileY;
			this.objectLayerID = objectLayerID;
			this.objectID = objectID;
			this.accumulatedRainExposure = accumulatedRainExposure;
			this.requiredRainExposure = requiredRainExposure;
			this.pendingDamageTime = pendingDamageTime;
		}
	}
}
