package opus.damage;

import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.AbstractDamageResult;
import necesse.entity.DamagedObjectEntity;
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

public class WeatheringLevelData extends LevelData implements
		RegionLoadedListenerEntityComponent,
		RegionLevelDataComponent,
		ObjectPlacedListenerEntityComponent,
		TilePlacedListenerEntityComponent {
	public static final String managerKey = "opusweathering";

	private static final long checkInterval = 10000L;
	private static final long baseMinExposure = 15L * 60L * 1000L;
	private static final long baseMaxExposure = 25L * 60L * 1000L;
	private static final int wallTimeMultiplier = 3;
	private static final float damageFraction = 0.60F;
	private static final long damageDelayMin = 0L;
	private static final long damageDelayMax = 30000L;
	private static final int weatherproofTier = 4;
	private static final int wildernessReinforcement = 4;
	private static final double estimatedRainFraction = 330.0 / 1830.0;

	private final Map<String, WeatheringEntry> entries = new HashMap<>();
	private long nextCheckTime;
	private long lastWeatherCheckTime;

	public static WeatheringLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof WeatheringLevelData) {
			return (WeatheringLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		WeatheringLevelData data = new WeatheringLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	@Override
	public void onLoadingComplete() {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		level.regionManager.forEachLoadedRegions(this::scanRegion);
		normalizeSettlementReinforcement();
	}

	@Override
	public void onRegionLoaded(Region region) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		scanRegion(region);
		normalizeSettlementReinforcement(region);
		applyEstimatedUnloadedWeathering(region);
	}

	@Override
	public void onObjectPlaced(GameObject object, int objectLayerID, int tileX, int tileY, ServerClient client) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		Point masterTile = DamageRepairLevelData.getObjectMasterTile(level, objectLayerID, tileX, tileY);
		GameObject masterObject = level.getObject(objectLayerID, masterTile.x, masterTile.y);
		boolean markWildernessDamage = client != null && !isSettlementAt(masterTile.x, masterTile.y);
		registerObject(masterObject, objectLayerID, masterTile.x, masterTile.y, true, markWildernessDamage);
	}

	@Override
	public void onTilePlaced(GameTile tile, int tileX, int tileY, ServerClient client) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		boolean markWildernessDamage = client != null && !isSettlementAt(tileX, tileY);
		registerTile(level.getTile(tileX, tileY), tileX, tileY, true, markWildernessDamage);
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

		Iterator<WeatheringEntry> cleanupIterator = entries.values().iterator();
		while (cleanupIterator.hasNext()) {
			WeatheringEntry entry = cleanupIterator.next();

			if (!isStillTrackedEntry(entry)) {
				cleanupIterator.remove();
				continue;
			}

			normalizeSettlementReinforcement(entry);
		}

		if (entries.isEmpty() || !level.weatherLayer.isRaining()) {
			return;
		}

		for (WeatheringEntry entry : entries.values()) {
			if (entry.pendingDamageTime > 0L || !isEntryWeatherable(entry) || !isExposedToRain(entry)) {
				continue;
			}

			entry.accumulatedRainExposure += elapsed;

			if (entry.accumulatedRainExposure >= entry.requiredRainExposure) {
				entry.pendingDamageTime = currentTime + rollDamageDelay();
			}
		}
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

			normalizeSettlementReinforcement(entry);

			if (!isEntryWeatherable(entry)) {
				entry.pendingDamageTime = 0L;
				continue;
			}

			int health = getEntryHealth(entry);
			int damage = Math.max(1, (int)Math.ceil(health * damageFraction));
			AbstractDamageResult result;

			if (entry.isTile) {
				result = level.entityManager.doTileDamage(
						entry.tileX, entry.tileY, damage, Float.MAX_VALUE, null, null, true,
						entry.tileX * 32 + 16, entry.tileY * 32 + 16
				);
			} else {
				result = level.entityManager.doObjectDamage(
						entry.objectLayerID, entry.tileX, entry.tileY, damage, Float.MAX_VALUE, null, null, true,
						entry.tileX * 32 + 16, entry.tileY * 32 + 16
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
			entrySave.addLong("lastActiveWorldTime", level.getTime());
			entrySave.addInt("weatherDamage", entry.weatherDamage);
			entrySave.addInt("reinforced", entry.reinforced);
			entrySave.addBoolean("wildernessReinforced", entry.wildernessReinforced);
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

			entry.lastActiveWorldTime = entryLoad.getLong("lastActiveWorldTime", 0L, false);
			entry.weatherDamage = Math.max(0, entryLoad.getInt("weatherDamage", 0, false));
			entry.reinforced = Math.max(0, entryLoad.getInt("reinforced", 0, false));
			entry.wildernessReinforced = entryLoad.getBoolean("wildernessReinforced", false, false);

			if (entry.requiredRainExposure <= 0L) {
				entry.requiredRainExposure = rollExposureThreshold(entry);
			}

			entries.put(getEntryKey(entry), entry);
		}
	}

	private void applyEstimatedUnloadedWeathering(Region region) {
		long currentTime = level.getTime();
		Iterator<WeatheringEntry> iterator = entries.values().iterator();

		while (iterator.hasNext()) {
			WeatheringEntry entry = iterator.next();

			if (!isInsideRegion(region, entry.tileX, entry.tileY)) {
				continue;
			}

			long lastActiveWorldTime = entry.lastActiveWorldTime;
			entry.lastActiveWorldTime = 0L;

			if (lastActiveWorldTime <= 0L
					|| entry.pendingDamageTime > 0L
					|| !isStillTrackedEntry(entry)) {
				continue;
			}

			normalizeSettlementReinforcement(entry);

			if (!isEntryWeatherable(entry) || !isExposedToRain(entry)) {
				continue;
			}

			long unloadedTime = Math.max(0L, currentTime - lastActiveWorldTime);

			if (unloadedTime <= 0L) {
				continue;
			}

			entry.accumulatedRainExposure += (long)(unloadedTime * estimatedRainFraction);

			if (entry.accumulatedRainExposure < entry.requiredRainExposure) {
				continue;
			}

			if (applyUnloadedWeatheringStage(entry)) {
				iterator.remove();
			}
		}
	}

	private boolean applyUnloadedWeatheringStage(WeatheringEntry entry) {
		boolean alreadyDamaged = hasEntryDamage(entry);
		int damage = alreadyDamaged
				? getEntryHealth(entry)
				: Math.max(1, (int)Math.ceil(getEntryHealth(entry) * damageFraction));

		AbstractDamageResult result;

		if (entry.isTile) {
			result = level.entityManager.doTileDamage(
					entry.tileX, entry.tileY, damage, Float.MAX_VALUE, null, null, true,
					entry.tileX * 32 + 16, entry.tileY * 32 + 16
			);
		} else {
			result = level.entityManager.doObjectDamage(
					entry.objectLayerID, entry.tileX, entry.tileY, damage, Float.MAX_VALUE, null, null, true,
					entry.tileX * 32 + 16, entry.tileY * 32 + 16
			);
		}

		if (result != null && result.destroyed) {
			Logging.logMessage("Estimated unloaded rain weathering destroyed " + entry.materialID + " at " + entry.tileX + ", " + entry.tileY);
			return true;
		}

		Logging.logMessage("Estimated unloaded rain weathering damaged " + entry.materialID + " at " + entry.tileX + ", " + entry.tileY);
		entry.accumulatedRainExposure = 0L;
		entry.requiredRainExposure = rollExposureThreshold(entry);
		entry.pendingDamageTime = 0L;
		return false;
	}

	private boolean hasEntryDamage(WeatheringEntry entry) {
		DamagedObjectEntity damagedEntity = level.entityManager.getDamagedObjectEntity(entry.tileX, entry.tileY);

		if (damagedEntity == null || damagedEntity.removed() || damagedEntity.shouldRemove()) {
			return false;
		}

		return entry.isTile
				? damagedEntity.tileDamage > 0
				: damagedEntity.getObjectDamage(entry.objectLayerID) > 0;
	}

	@Override
	public void onUnloadedRegion(Region region) {
		entries.values().removeIf(entry -> isInsideRegion(region, entry.tileX, entry.tileY));
	}

	public boolean isSettlementAt(int tileX, int tileY) {
		return isServer()
				&& SettlementsWorldData.getSettlementsData(level.getServer()).hasSettlementAtTile(level, tileX, tileY);
	}

	public boolean hasRoadRepairTargetAt(int tileX, int tileY) {
		if (!isServer() || isSettlementAt(tileX, tileY)) {
			return false;
		}

		WeatheringEntry tileEntry = entries.get(getTileEntryKey(tileX, tileY));
		if (isRoadRepairTarget(tileEntry)) {
			return true;
		}

		for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
			WeatheringEntry objectEntry = entries.get(getObjectEntryKey(layerID, tileX, tileY));
			if (isRoadRepairTarget(objectEntry)) {
				return true;
			}
		}

		return false;
	}

	private boolean isRoadRepairTarget(WeatheringEntry entry) {
		if (entry == null || !isStillTrackedEntry(entry)) {
			return false;
		}

		normalizeSettlementReinforcement(entry);
		return entry.reinforced < wildernessReinforcement
				&& (entry.weatherDamage > 0 || hasEntryDamage(entry));
	}

	public void reinforceWildernessAt(int tileX, int tileY) {
		if (!isServer() || isSettlementAt(tileX, tileY)) {
			return;
		}

		reinforceWildernessEntry(entries.get(getTileEntryKey(tileX, tileY)));

		for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
			reinforceWildernessEntry(entries.get(getObjectEntryKey(layerID, tileX, tileY)));
		}
	}

	private void reinforceWildernessEntry(WeatheringEntry entry) {
		if (entry == null || !isStillTrackedEntry(entry)) {
			return;
		}

		entry.reinforced = wildernessReinforcement;
		entry.wildernessReinforced = true;
		entry.weatherDamage = 0;
		entry.accumulatedRainExposure = 0L;
		entry.requiredRainExposure = Long.MAX_VALUE;
		entry.pendingDamageTime = 0L;
	}

	public Map<Long, Integer> getReinforcementInCircle(
			int centerX,
			int centerY,
			int radius
	) {
		Map<Long, Integer> result = new HashMap<>();
		int radiusSquared = radius * radius;

		for (int x = centerX - radius; x <= centerX + radius; x++) {
			for (int y = centerY - radius; y <= centerY + radius; y++) {
				int dx = x - centerX;
				int dy = y - centerY;

				if (dx * dx + dy * dy > radiusSquared) {
					continue;
				}

				Map<Long, Integer> tileResult = getReinforcementInArea(x, y, x, y);
				result.putAll(tileResult);
			}
		}

		return result;
	}

	public Map<Long, Integer> getReinforcementInArea(
			int startX,
			int startY,
			int endX,
			int endY
	) {
		Map<Long, Integer> result = new HashMap<>();

		for (int x = startX; x <= endX; x++) {
			for (int y = startY; y <= endY; y++) {
				GameTile tile = level.getTile(x, y);
				WeatheringMaterialTier tileTier = MaterialWeatheringClassifier.getTileTier(tile);

				if (tileTier != null
						&& tileTier.isWeatherable()
						&& level.tileLayer.isPlayerPlaced(x, y)) {
					WeatheringEntry entry = entries.get(getTileEntryKey(x, y));
					int reinforced = getEntryReinforcement(entry);
					result.merge(GameMath.getUniqueLongKey(x, y), reinforced, Math::max);
				}

				for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
					GameObject object = level.getObject(layerID, x, y);
					WeatheringMaterialTier objectTier = MaterialWeatheringClassifier.getObjectTier(object);

					if (objectTier == null || !objectTier.isWeatherable()) {
						continue;
					}

					Point masterTile = DamageRepairLevelData.getObjectMasterTile(level, layerID, x, y);
					GameObject masterObject = level.getObject(layerID, masterTile.x, masterTile.y);

					if (masterObject == null
							|| masterObject.getID() == 0
							|| !masterObject.isMultiTileMaster()
							|| !level.objectLayer.isPlayerPlaced(layerID, masterTile.x, masterTile.y)) {
						continue;
					}

					WeatheringEntry entry = entries.get(getObjectEntryKey(layerID, masterTile.x, masterTile.y));
					int reinforced = getEntryReinforcement(entry);
					Rectangle footprint = masterObject
							.getMultiTile(level, layerID, masterTile.x, masterTile.y)
							.getTileRectangle(masterTile.x, masterTile.y);

					for (int footprintX = footprint.x; footprintX < footprint.x + footprint.width; footprintX++) {
						for (int footprintY = footprint.y; footprintY < footprint.y + footprint.height; footprintY++) {
							if (footprintX < startX || footprintX > endX || footprintY < startY || footprintY > endY) {
								continue;
							}

							result.merge(
									GameMath.getUniqueLongKey(footprintX, footprintY),
									reinforced,
									Math::max
							);
						}
					}
				}
			}
		}

		return result;
	}


	public int getReinforcementAt(int tileX, int tileY) {
		int reinforcement = getEntryReinforcement(entries.get(getTileEntryKey(tileX, tileY)));

		for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
			reinforcement = Math.max(
					reinforcement,
					getEntryReinforcement(entries.get(getObjectEntryKey(layerID, tileX, tileY)))
			);
		}

		return reinforcement;
	}

	private int getEntryReinforcement(WeatheringEntry entry) {
		if (entry == null || !isStillTrackedEntry(entry)) {
			return 0;
		}

		normalizeSettlementReinforcement(entry);
		return entry.reinforced;
	}

	public void addReinforcementAt(int tileX, int tileY, int amount) {
		if (amount <= 0) {
			return;
		}

		addEntryReinforcement(entries.get(getTileEntryKey(tileX, tileY)), amount);

		for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
			addEntryReinforcement(entries.get(getObjectEntryKey(layerID, tileX, tileY)), amount);
		}
	}

	private void addEntryReinforcement(WeatheringEntry entry, int amount) {
		if (entry == null || !isStillTrackedEntry(entry)) {
			return;
		}

		normalizeSettlementReinforcement(entry);
		entry.reinforced = Math.max(0, entry.reinforced + amount);
		entry.wildernessReinforced = false;
		entry.requiredRainExposure = rollExposureThreshold(entry);
		entry.pendingDamageTime = 0L;
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

	private void normalizeSettlementReinforcement() {
		for (WeatheringEntry entry : entries.values()) {
			normalizeSettlementReinforcement(entry);
		}
	}

	private void normalizeSettlementReinforcement(Region region) {
		for (WeatheringEntry entry : entries.values()) {
			if (isInsideRegion(region, entry.tileX, entry.tileY)) {
				normalizeSettlementReinforcement(entry);
			}
		}
	}

	private void normalizeSettlementReinforcement(WeatheringEntry entry) {
		if (!isSettlementAt(entry.tileX, entry.tileY)) {
			return;
		}

		entry.weatherDamage = 0;

		if (!entry.wildernessReinforced) {
			return;
		}

		entry.reinforced = 0;
		entry.wildernessReinforced = false;
		entry.accumulatedRainExposure = 0L;
		entry.requiredRainExposure = rollExposureThreshold(entry);
		entry.pendingDamageTime = 0L;
	}

	private void scanRegion(Region region) {
		for (int x = region.tileXOffset; x < region.tileXOffset + region.tileWidth; x++) {
			for (int y = region.tileYOffset; y < region.tileYOffset + region.tileHeight; y++) {
				registerTile(level.getTile(x, y), x, y, false, false);

				for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
					registerObject(level.getObject(layerID, x, y), layerID, x, y, false, false);
				}
			}
		}
	}

	private void registerObject(
			GameObject object,
			int objectLayerID,
			int tileX,
			int tileY,
			boolean resetExisting,
			boolean markWildernessDamage
	) {
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

		WeatheringEntry entry = new WeatheringEntry(tileX, tileY, objectLayerID, object.getStringID(), false, 0L, 0L, 0L);
		entry.weatherDamage = markWildernessDamage ? 1 : 0;
		entry.requiredRainExposure = rollExposureThreshold(entry);
		entries.put(key, entry);
	}

	private void registerTile(GameTile tile, int tileX, int tileY, boolean resetExisting, boolean markWildernessDamage) {
		String key = getTileEntryKey(tileX, tileY);
		WeatheringMaterialTier tier = MaterialWeatheringClassifier.getTileTier(tile);

		if (tier == null || !tier.isWeatherable() || !level.tileLayer.isPlayerPlaced(tileX, tileY)) {
			entries.remove(key);
			return;
		}

		if (!resetExisting && entries.containsKey(key)) {
			return;
		}

		WeatheringEntry entry = new WeatheringEntry(tileX, tileY, -1, tile.getStringID(), true, 0L, 0L, 0L);
		entry.weatherDamage = markWildernessDamage ? 1 : 0;
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

	private boolean isEntryWeatherable(WeatheringEntry entry) {
		WeatheringMaterialTier tier = getBaseTier(entry);
		return tier != null && tier.isWeatherable() && tier.getTier() + entry.reinforced < weatherproofTier;
	}

	private WeatheringMaterialTier getBaseTier(WeatheringEntry entry) {
		return entry.isTile
				? MaterialWeatheringClassifier.getTileTier(level.getTile(entry.tileX, entry.tileY))
				: MaterialWeatheringClassifier.getObjectTier(level.getObject(entry.objectLayerID, entry.tileX, entry.tileY));
	}

	public static boolean isWeatherableFence(GameObject object) {
		return MaterialWeatheringClassifier.isWeatherableFence(object);
	}

	private boolean isExposedToRain(WeatheringEntry entry) {
		if (entry.isTile) {
			return isExposedToPrecipitation(entry.tileX, entry.tileY);
		}

		GameObject object = level.getObject(entry.objectLayerID, entry.tileX, entry.tileY);
		Rectangle footprint = object
				.getMultiTile(level, entry.objectLayerID, entry.tileX, entry.tileY)
				.getTileRectangle(entry.tileX, entry.tileY);

		if (object.isWall) {
			for (int x = footprint.x; x < footprint.x + footprint.width; x++) {
				for (int y = footprint.y; y < footprint.y + footprint.height; y++) {
					if (isExposedToPrecipitation(x - 1, y)
							|| isExposedToPrecipitation(x + 1, y)
							|| isExposedToPrecipitation(x, y - 1)
							|| isExposedToPrecipitation(x, y + 1)) {
						return true;
					}
				}
			}

			return false;
		}

		for (int x = footprint.x; x < footprint.x + footprint.width; x++) {
			for (int y = footprint.y; y < footprint.y + footprint.height; y++) {
				if (isExposedToPrecipitation(x, y)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isExposedToPrecipitation(int tileX, int tileY) {
		if (!level.isTileWithinBounds(tileX, tileY)) {
			return true;
		}

		return level.isOutside(tileX, tileY) && level.getBiome(tileX, tileY).canRain(level);
	}

	private int getEntryHealth(WeatheringEntry entry) {
		return entry.isTile
				? level.getTile(entry.tileX, entry.tileY).tileHealth
				: level.getObject(entry.objectLayerID, entry.tileX, entry.tileY).objectHealth;
	}

	private long rollExposureThreshold(WeatheringEntry entry) {
		WeatheringMaterialTier tier = getBaseTier(entry);
		boolean wall = !entry.isTile && level.getObject(entry.objectLayerID, entry.tileX, entry.tileY).isWall;

		if (tier == null || !tier.isWeatherable()) {
			return Long.MAX_VALUE;
		}

		int effectiveTier = tier.getTier() + entry.reinforced;
		if (effectiveTier >= weatherproofTier) {
			return Long.MAX_VALUE;
		}

		long min = baseMinExposure * (effectiveTier + 1L);
		long max = baseMaxExposure * (effectiveTier + 1L);

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
		private long lastActiveWorldTime;
		private int weatherDamage;
		private int reinforced;
		private boolean wildernessReinforced;

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