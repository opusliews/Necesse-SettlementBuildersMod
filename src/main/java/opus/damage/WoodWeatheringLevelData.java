package opus.damage;

import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.entity.AbstractDamageResult;
import necesse.entity.manager.ObjectPlacedListenerEntityComponent;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.entity.manager.TilePlacedListenerEntityComponent;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.RegionLevelDataComponent;
import necesse.level.maps.regionSystem.Region;
import opus.logging.Logging;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class WoodWeatheringLevelData extends LevelData implements
		RegionLoadedListenerEntityComponent,
		RegionLevelDataComponent,
		ObjectPlacedListenerEntityComponent,
		TilePlacedListenerEntityComponent {
	public static final String managerKey = "opuswoodweathering";

	private static final long checkInterval = 10000L;
	private static final long baseMinExposure = 15L * 60L * 1000L;
	 private static final long baseMaxExposure = 25L * 60L * 1000L;
	private static final int wallTimeMultiplier = 3;
	private static final float damageFraction = 0.60F;
	private static final long damageDelayMin = 0L;
	private static final long damageDelayMax = 30000L;

	private final Map<String, WeatheringEntry> entries = new HashMap<>();
	private long nextCheckTime;
	private long lastWeatherCheckTime;

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
	public void onTilePlaced(GameTile tile, int tileX, int tileY, ServerClient client) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		registerTile(level.getTile(tileX, tileY), tileX, tileY, true);
	}

	@Override
	public void tick() {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		long currentTime = level.getTime();

		if (!entries.isEmpty()) {
			processPendingDamage(currentTime);
		}

		if (currentTime < nextCheckTime) {
			return;
		}

		long elapsed = lastWeatherCheckTime == 0L ? 0L : currentTime - lastWeatherCheckTime;
		lastWeatherCheckTime = currentTime;
		nextCheckTime = currentTime + checkInterval;

		if (entries.isEmpty()) {
			return;
		}

		if (!level.weatherLayer.isRaining()) {
			return;
		}

		for (WeatheringEntry entry : entries.values()) {
			if (!isStillTrackedEntry(entry) || entry.pendingDamageTime > 0L || !isExposedToRain(entry)) {
				continue;
			}

			entry.accumulatedRainExposure += elapsed;

			if (entry.accumulatedRainExposure >= entry.requiredRainExposure) {
				entry.pendingDamageTime = currentTime + rollDamageDelay();
			}
		}

		entries.values().removeIf(entry -> !isStillTrackedEntry(entry));
	}

	private void processPendingDamage(long currentTime) {
		Iterator<WeatheringEntry> iterator = entries.values().iterator();

		while (iterator.hasNext()) {
			WeatheringEntry entry = iterator.next();
			if (entry.pendingDamageTime <= 0L || currentTime < entry.pendingDamageTime) {
				continue;
			}

			if (!isStillTrackedEntry(entry)) {
				iterator.remove();
				continue;
			}

			int health = getEntryHealth(entry);
			int damage = Math.max(1, (int)Math.ceil(health * damageFraction));
			AbstractDamageResult result;

			if (entry.isTile) {
				result = level.entityManager.doTileDamage(
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
			} else {
				result = level.entityManager.doObjectDamage(
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
			}

			if (result != null && result.destroyed) {
				Logging.logMessage("Rain weathering destroyed " + entry.materialID + " at " + entry.tileX + ", " + entry.tileY);
				iterator.remove();
				continue;
			}

			Logging.logMessage("Rain weathering damaged " + entry.materialID + " at " + entry.tileX + ", " + entry.tileY + " for " + damage);
			entry.accumulatedRainExposure = 0L;
			entry.requiredRainExposure = rollExposureThreshold(entry);
			entry.pendingDamageTime = 0L;
		}
	}

	private long rollDamageDelay() {
		return damageDelayMin + (long)(GameRandom.globalRandom.nextDouble() * (damageDelayMax - damageDelayMin + 1L));
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
			entrySave.addUnsafeString("objectID", entry.materialID);
			entrySave.addBoolean("isTile", entry.isTile);
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
			String materialID = entryLoad.getUnsafeString("objectID", null, false);
			boolean isTile = entryLoad.getBoolean("isTile", false, false);

			if (materialID == null) {
				continue;
			}

			WeatheringEntry entry = new WeatheringEntry(
					tileX,
					tileY,
					objectLayerID,
					materialID,
					isTile,
					entryLoad.getLong("accumulatedRainExposure", 0L, false),
					entryLoad.getLong("requiredRainExposure", 0L, false),
					entryLoad.getLong("pendingDamageTime", 0L, false)
			);

			// DEBUG: reroll/reset on load while testing accelerated timings.
			entry.requiredRainExposure = rollExposureThreshold(entry);
			entry.accumulatedRainExposure = 0L;
			entry.pendingDamageTime = 0L;

			entries.put(getEntryKey(entry), entry);
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

			entry.accumulatedRainExposure = 0L;
			entry.requiredRainExposure = rollExposureThreshold(entry);
			entry.pendingDamageTime = 0L;
			Logging.logMessage("Reset rain weathering for " + entry.materialID + " at " + tileX + ", " + tileY);
		}
	}

	private void scanRegion(Region region) {
		for (int x = region.tileXOffset; x < region.tileXOffset + region.tileWidth; x++) {
			for (int y = region.tileYOffset; y < region.tileYOffset + region.tileHeight; y++) {
				registerTile(level.getTile(x, y), x, y, false);

				for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
					registerObject(level.getObject(layerID, x, y), layerID, x, y, false);
				}
			}
		}
	}

	private void registerObject(GameObject object, int objectLayerID, int tileX, int tileY, boolean resetExisting) {
		String key = getObjectEntryKey(objectLayerID, tileX, tileY);
		WeatheringMaterialTier tier = MaterialWeatheringClassifier.getObjectTier(object);

		if (tier == null || !tier.isWeatherable()
				|| !object.isMultiTileMaster()
				|| !level.objectLayer.isPlayerPlaced(objectLayerID, tileX, tileY)) {
			entries.remove(key);
			return;
		}

		if (!resetExisting && entries.containsKey(key)) {
			return;
		}

		WeatheringEntry entry = new WeatheringEntry(
				tileX,
				tileY,
				objectLayerID,
				object.getStringID(),
				false,
				0L,
				0L,
				0L
		);

		entry.requiredRainExposure = rollExposureThreshold(entry);
		entries.put(key, entry);
	}

	private void registerTile(GameTile tile, int tileX, int tileY, boolean resetExisting) {
		String key = getTileEntryKey(tileX, tileY);
		WeatheringMaterialTier tier = MaterialWeatheringClassifier.getTileTier(tile);

		if (tier == null || !tier.isWeatherable() || !level.tileLayer.isPlayerPlaced(tileX, tileY)) {
			entries.remove(key);
			return;
		}

		if (!resetExisting && entries.containsKey(key)) {
			return;
		}

		WeatheringEntry entry = new WeatheringEntry(
				tileX,
				tileY,
				-1,
				tile.getStringID(),
				true,
				0L,
				0L,
				0L
		);

		entry.requiredRainExposure = rollExposureThreshold(entry);
		entries.put(key, entry);
	}

	private boolean isStillTrackedEntry(WeatheringEntry entry) {
		if (entry.isTile) {
			GameTile tile = level.getTile(entry.tileX, entry.tileY);
			WeatheringMaterialTier tier = MaterialWeatheringClassifier.getTileTier(tile);

			return tier != null
					&& tier.isWeatherable()
					&& tile.getStringID().equals(entry.materialID)
					&& level.tileLayer.isPlayerPlaced(entry.tileX, entry.tileY);
		}

		GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);
		WeatheringMaterialTier tier = MaterialWeatheringClassifier.getObjectTier(object);

		return tier != null
				&& tier.isWeatherable()
				&& object.isMultiTileMaster()
				&& object.getStringID().equals(entry.materialID)
				&& level.objectLayer.isPlayerPlaced(entry.objectLayerID, entry.tileX, entry.tileY);
	}

	public static boolean isWeatherableFence(GameObject object) {
		return MaterialWeatheringClassifier.isWeatherableFence(object);
	}

	private boolean isExposedToRain(WeatheringEntry entry) {
		if (entry.isTile) {
			return isOutsideOrBeyondLevel(entry.tileX, entry.tileY);
		}

		GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);
		Rectangle footprint = object
				.getMultiTile(level, entry.objectLayerID, entry.tileX, entry.tileY)
				.getTileRectangle(entry.tileX, entry.tileY);

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

	private int getEntryHealth(WeatheringEntry entry) {
		return entry.isTile
				? level.getTile(entry.tileX, entry.tileY).tileHealth
				: level.getObject(entry.objectLayerID, entry.tileX, entry.tileY).objectHealth;
	}

	private long rollExposureThreshold(WeatheringEntry entry) {
		WeatheringMaterialTier tier;
		boolean wall = false;

		if (entry.isTile) {
			tier = MaterialWeatheringClassifier.getTileTier(level.getTile(entry.tileX, entry.tileY));
		} else {
			GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);
			tier = MaterialWeatheringClassifier.getObjectTier(object);
			wall = object != null && object.isWall;
		}

		if (tier == null || !tier.isWeatherable()) {
			return Long.MAX_VALUE;
		}

		long min = baseMinExposure * tier.getTimeMultiplier();
		long max = baseMaxExposure * tier.getTimeMultiplier();

		if (wall) {
			min *= wallTimeMultiplier;
			max *= wallTimeMultiplier;
		}

		return min + (long)(GameRandom.globalRandom.nextDouble() * (max - min + 1L));
	}

	private static String getEntryKey(WeatheringEntry entry) {
		return entry.isTile
				? getTileEntryKey(entry.tileX, entry.tileY)
				: getObjectEntryKey(entry.objectLayerID, entry.tileX, entry.tileY);
	}

	private static String getObjectEntryKey(int objectLayerID, int tileX, int tileY) {
		return objectLayerID + ":" + tileX + ":" + tileY;
	}

	private static String getTileEntryKey(int tileX, int tileY) {
		return "tile:" + tileX + ":" + tileY;
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
		private final String materialID;
		private final boolean isTile;
		private long accumulatedRainExposure;
		private long requiredRainExposure;
		private long pendingDamageTime;

		private WeatheringEntry(
				int tileX,
				int tileY,
				int objectLayerID,
				String materialID,
				boolean isTile,
				long accumulatedRainExposure,
				long requiredRainExposure,
				long pendingDamageTime
		) {
			this.tileX = tileX;
			this.tileY = tileY;
			this.objectLayerID = objectLayerID;
			this.materialID = materialID;
			this.isTile = isTile;
			this.accumulatedRainExposure = accumulatedRainExposure;
			this.requiredRainExposure = requiredRainExposure;
			this.pendingDamageTime = pendingDamageTime;
		}
	}
}