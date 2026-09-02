package opus.settler;

import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.SettlerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.manager.ObjectPlacedListenerEntityComponent;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementVisitorSpawner;
import necesse.level.maps.levelData.settlementData.settler.Settler;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import necesse.level.maps.regionSystem.Region;
import opus.logging.Logging;
import opus.object.BuilderJobRequestBulletinObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BuilderRequestLevelData extends LevelData implements
		RegionLoadedListenerEntityComponent,
		ObjectPlacedListenerEntityComponent {

	public static final String managerKey = "opusbuilderrequests";

	private static final long checkInterval = 1000L;
	private static final long minSpawnDelay = 5L * 60L * 1000L / 60;
	private static final long maxSpawnDelay = 10L * 60L * 1000L / 60;
	private static final long failedSpawnRetryDelay = 30000L;

	private final Set<Long> bulletinTiles = new HashSet<>();
	private final Map<Integer, Long> nextSpawnTimes = new HashMap<>();

	private long nextCheckTime;

	public static BuilderRequestLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);

		if (existing instanceof BuilderRequestLevelData) {
			return (BuilderRequestLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		BuilderRequestLevelData data = new BuilderRequestLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	@Override
	public void onLoadingComplete() {
		if (!isServer()) {
			return;
		}

		level.regionManager.forEachLoadedRegions(this::scanRegion);
	}

	@Override
	public void onRegionLoaded(Region region) {
		if (!isServer()) {
			return;
		}

		scanRegion(region);
	}

	@Override
	public void onObjectPlaced(
			GameObject object,
			int objectLayerID,
			int tileX,
			int tileY,
			ServerClient client
	) {
		if (!isServer() || !isBulletinObject(object)) {
			return;
		}

		bulletinTiles.add(GameMath.getUniqueLongKey(tileX, tileY));
	}

	@Override
	public void tick() {
		if (!isServer()) {
			return;
		}

		long currentTime = level.getTime();

		if (currentTime < nextCheckTime) {
			return;
		}

		nextCheckTime = currentTime + checkInterval;

		bulletinTiles.removeIf(key -> {
			int tileX = GameMath.getXFromUniqueLongKey(key);
			int tileY = GameMath.getYFromUniqueLongKey(key);
			return !isBulletinAt(tileX, tileY);
		});

		Map<Integer, ServerSettlementData> activeSettlements = new HashMap<>();

		for (long key : bulletinTiles) {
			int tileX = GameMath.getXFromUniqueLongKey(key);
			int tileY = GameMath.getYFromUniqueLongKey(key);

			ServerSettlementData settlement = SettlementsWorldData
					.getSettlementsData(level)
					.getServerDataAtTile(level.getIdentifier(), tileX, tileY);

			if (settlement != null) {
				activeSettlements.put(settlement.uniqueID, settlement);
			}
		}

		nextSpawnTimes.keySet().removeIf(id -> !activeSettlements.containsKey(id));

		for (Map.Entry<Integer, ServerSettlementData> active : activeSettlements.entrySet()) {
			int settlementUniqueID = active.getKey();
			ServerSettlementData settlement = active.getValue();

			long nextSpawnTime = nextSpawnTimes.computeIfAbsent(
					settlementUniqueID,
					id -> currentTime + rollSpawnDelay()
			);

			if (currentTime < nextSpawnTime) {
				continue;
			}

			if (spawnBuilderVisitor(settlement)) {
				nextSpawnTimes.put(settlementUniqueID, currentTime + rollSpawnDelay());
			} else {
				nextSpawnTimes.put(settlementUniqueID, currentTime + failedSpawnRetryDelay);
			}
		}
	}

	private void scanRegion(Region region) {
		for (int tileX = region.tileXOffset; tileX < region.tileXOffset + region.tileWidth; tileX++) {
			for (int tileY = region.tileYOffset; tileY < region.tileYOffset + region.tileHeight; tileY++) {
				if (isBulletinAt(tileX, tileY)) {
					bulletinTiles.add(GameMath.getUniqueLongKey(tileX, tileY));
				}
			}
		}
	}

	private boolean isBulletinAt(int tileX, int tileY) {
		for (int layerID = 0; layerID < ObjectLayerRegistry.getTotalLayers(); layerID++) {
			if (isBulletinObject(level.getObject(layerID, tileX, tileY))) {
				return true;
			}
		}

		return false;
	}

	private static boolean isBulletinObject(GameObject object) {
		return object != null
				&& object.getID() != 0
				&& BuilderJobRequestBulletinObject.stringID.equals(object.getStringID());
	}

	private boolean spawnBuilderVisitor(ServerSettlementData settlement) {
		Settler settler = SettlerRegistry.getSettler("builder");

		if (settler == null) {
			Logging.logMessage("Could not spawn Builder visitor: Builder settler is not registered");
			return false;
		}

		SettlerMob mob = settler.getNewSettlerMob(settlement);

		if (mob == null || !(mob.getMob() instanceof HumanMob)) {
			Logging.logMessage("Could not spawn Builder visitor: failed to create Builder mob");
			return false;
		}

		mob.setSettlerSeed(GameRandom.globalRandom.nextInt(), true);

		boolean spawned = settlement.spawnVisitor(
				new SettlementVisitorSpawner(
						ServerSettlementData.visitorRecruitsOdds,
						(HumanMob)mob.getMob()
				)
		);

		if (spawned) {
			Logging.logMessage(
					"Builder Job Request Bulletin spawned a Builder visitor for settlement "
							+ settlement.uniqueID
			);
		}

		return spawned;
	}

	private long rollSpawnDelay() {
		return (long)(GameRandom.globalRandom.nextDouble() * (maxSpawnDelay - minSpawnDelay)) + minSpawnDelay;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);

		if (nextSpawnTimes.isEmpty()) {
			return;
		}

		SaveData timersSave = new SaveData("BUILDER_REQUEST_TIMERS");

		for (Map.Entry<Integer, Long> entry : nextSpawnTimes.entrySet()) {
			SaveData timerSave = new SaveData("TIMER");
			timerSave.addInt("settlementUniqueID", entry.getKey());
			timerSave.addLong("nextSpawnTime", entry.getValue());
			timersSave.addSaveData(timerSave);
		}

		save.addSaveData(timersSave);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);

		nextSpawnTimes.clear();

		LoadData timersSave = save.getFirstLoadDataByName("BUILDER_REQUEST_TIMERS");

		if (timersSave == null) {
			return;
		}

		for (LoadData timerLoad : timersSave.getLoadData()) {
			int settlementUniqueID = timerLoad.getInt("settlementUniqueID");
			long nextSpawnTime = timerLoad.getLong("nextSpawnTime", 0L, false);
			nextSpawnTimes.put(settlementUniqueID, nextSpawnTime);
		}
	}
}
