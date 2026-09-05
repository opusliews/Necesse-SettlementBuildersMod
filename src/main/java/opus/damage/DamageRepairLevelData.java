package opus.damage;

import necesse.engine.network.packet.PacketDamagedTileEntity;
import necesse.engine.network.packet.PacketDamagedTileRemoved;
import necesse.engine.util.GameMath;
import necesse.entity.AbstractDamageResult;
import necesse.entity.DamagedObjectEntity;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.regionSystem.Region;
import opus.jobs.RepairLevelJob;
import opus.logging.Logging;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DamageRepairLevelData extends LevelData implements RegionLoadedListenerEntityComponent {
	public static final String managerKey = "opushardcoredamage";
	public static final long repairDebounceTime = 10000L;
	private static final long pendingCheckInterval = 1000L;
	private static final int fenceRepairBatchSize = 20;

	private final Set<Long> pendingRepairs = new HashSet<>();
	private long nextPendingCheckTime;

	public static DamageRepairLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);

		if (existing instanceof DamageRepairLevelData) {
			return (DamageRepairLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		DamageRepairLevelData data = new DamageRepairLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public void recordDamage(AbstractDamageResult result, int objectLayerID) {
		if (!isServer() || !HardcoreDamage.isEnabled(level) || result == null || result.destroyed) {
			return;
		}

		DamagedObjectEntity damagedEntity = result.damagedObjectEntity;

		if (damagedEntity == null || damagedEntity.removed() || damagedEntity.shouldRemove()) {
			return;
		}

		damagedEntity.shouldSave = true;

		Point repairTile = objectLayerID >= 0
				? getObjectMasterTile(level, objectLayerID, damagedEntity.tileX, damagedEntity.tileY)
				: new Point(damagedEntity.tileX, damagedEntity.tileY);

		pendingRepairs.add(GameMath.getUniqueLongKey(repairTile.x, repairTile.y));
	}

	@Override
	public void onLoadingComplete() {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		level.entityManager.damagedObjects.stream().forEach(entity -> registerLoadedDamage((DamagedObjectEntity)entity));
	}

	@Override
	public void onRegionLoaded(Region region) {
		if (!isServer() || !HardcoreDamage.isEnabled(level)) {
			return;
		}

		for (Object object : level.entityManager.damagedObjects.getInRegion(region.regionX, region.regionY)) {
			registerLoadedDamage((DamagedObjectEntity)object);
		}
	}

	@Override
	public void tick() {
		if (!isServer() || !HardcoreDamage.isEnabled(level) || pendingRepairs.isEmpty()) {
			return;
		}

		long currentTime = level.getTime();

		if (currentTime < nextPendingCheckTime) {
			return;
		}

		nextPendingCheckTime = currentTime + pendingCheckInterval;

		Iterator<Long> iterator = pendingRepairs.iterator();

		while (iterator.hasNext()) {
			long key = iterator.next();
			int tileX = GameMath.getXFromUniqueLongKey(key);
			int tileY = GameMath.getYFromUniqueLongKey(key);

			if (!hasRepairableDamage(level, tileX, tileY)) {
				iterator.remove();
				continue;
			}

			if (!isRepairReady(level, tileX, tileY)) {
				continue;
			}

			if (level.jobsLayer.addJob(new RepairLevelJob(tileX, tileY)) != null) {
				Logging.logMessage("Created Builder repair job at " + tileX + ", " + tileY);
				iterator.remove();
			}
		}
	}

	private void registerLoadedDamage(DamagedObjectEntity damagedEntity) {
		if (damagedEntity == null || damagedEntity.removed() || damagedEntity.shouldRemove()) {
			return;
		}

		damagedEntity.shouldSave = true;

		if (damagedEntity.tileDamage > 0) {
			pendingRepairs.add(GameMath.getUniqueLongKey(damagedEntity.tileX, damagedEntity.tileY));
		}

		if (damagedEntity.hasAnyObjectDamage()) {
			for (int layerID = 0; layerID < damagedEntity.objectDamage.length; layerID++) {
				if (damagedEntity.objectDamage[layerID] <= 0) {
					continue;
				}

				Point masterTile = getObjectMasterTile(level, layerID, damagedEntity.tileX, damagedEntity.tileY);
				pendingRepairs.add(GameMath.getUniqueLongKey(masterTile.x, masterTile.y));
			}
		}
	}

	public static boolean hasRepairableDamage(Level level, int tileX, int tileY) {
		DamagedObjectEntity damagedEntity = level.entityManager.getDamagedObjectEntity(tileX, tileY);
		return damagedEntity != null && !damagedEntity.removed() && !damagedEntity.shouldRemove();
	}

	public static boolean isRepairReady(Level level, int tileX, int tileY) {
		DamagedObjectEntity damagedEntity = level.entityManager.getDamagedObjectEntity(tileX, tileY);
		return damagedEntity != null
				&& !damagedEntity.removed()
				&& !damagedEntity.shouldRemove()
				&& damagedEntity.getTimeSinceLastDamage() >= repairDebounceTime;
	}

	public static Point getObjectMasterTile(Level level, int objectLayerID, int tileX, int tileY) {
		GameObject object = level.getObject(objectLayerID, tileX, tileY);

		if (object == null || object.getID() == 0 || object.isMultiTileMaster()) {
			return new Point(tileX, tileY);
		}

		return (Point)object
				.getMultiTile(level, objectLayerID, tileX, tileY)
				.getMasterTilePos(tileX, tileY)
				.orElse(new Point(tileX, tileY));
	}

	public static void repairDamage(Level level, int tileX, int tileY) {
		if (hasWeatherableFenceDamage(level, tileX, tileY)) {
			repairFenceBatch(level, tileX, tileY);
			return;
		}

		repairSingleDamage(level, tileX, tileY);
	}

	private static void repairFenceBatch(Level level, int startX, int startY) {
		ArrayDeque<Point> open = new ArrayDeque<>();
		Set<Long> visited = new HashSet<>();
		List<Point> damagedFences = new ArrayList<>();

		open.add(new Point(startX, startY));

		while (!open.isEmpty()) {
			Point current = open.removeFirst();
			long currentKey = GameMath.getUniqueLongKey(current.x, current.y);

			if (!visited.add(currentKey)) {
				continue;
			}

			if (!hasWeatherableFence(level, current.x, current.y)) {
				continue;
			}

			if (hasWeatherableFenceDamage(level, current.x, current.y)) {
				damagedFences.add(current);

				if (damagedFences.size() >= fenceRepairBatchSize) {
					break;
				}
			}

			addConnectedFence(level, open, visited, current.x - 1, current.y);
			addConnectedFence(level, open, visited, current.x + 1, current.y);
			addConnectedFence(level, open, visited, current.x, current.y - 1);
			addConnectedFence(level, open, visited, current.x, current.y + 1);
		}

		for (Point fence : damagedFences) {
			repairSingleDamage(level, fence.x, fence.y);
			removeRepairTracking(level, fence.x, fence.y);
		}

		Logging.logMessage(
				"Builder fence repair batch repaired "
						+ damagedFences.size()
						+ " fences starting at "
						+ startX
						+ ", "
						+ startY
		);
	}

	private static void addConnectedFence(
			Level level,
			ArrayDeque<Point> open,
			Set<Long> visited,
			int tileX,
			int tileY
	) {
		long key = GameMath.getUniqueLongKey(tileX, tileY);

		if (visited.contains(key) || !hasWeatherableFence(level, tileX, tileY)) {
			return;
		}

		open.addLast(new Point(tileX, tileY));
	}

	private static boolean hasWeatherableFence(Level level, int tileX, int tileY) {
		for (int layerID = 0; layerID < necesse.engine.registries.ObjectLayerRegistry.getTotalLayers(); layerID++) {
			GameObject object = level.getObject(layerID, tileX, tileY);

			if (WeatheringLevelData.isWeatherableFence(object)) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasWeatherableFenceDamage(Level level, int tileX, int tileY) {
		DamagedObjectEntity damagedEntity = level.entityManager.getDamagedObjectEntity(tileX, tileY);

		if (damagedEntity == null || damagedEntity.removed() || damagedEntity.shouldRemove()) {
			return false;
		}

		int layers = Math.min(
				damagedEntity.objectDamage.length,
				necesse.engine.registries.ObjectLayerRegistry.getTotalLayers()
		);

		for (int layerID = 0; layerID < layers; layerID++) {
			if (damagedEntity.objectDamage[layerID] <= 0) {
				continue;
			}

			GameObject object = level.getObject(layerID, tileX, tileY);

			if (WeatheringLevelData.isWeatherableFence(object)) {
				return true;
			}
		}

		return false;
	}

	private static void removeRepairTracking(Level level, int tileX, int tileY) {
		DamageRepairLevelData data = get(level, false);

		if (data != null) {
			data.pendingRepairs.remove(GameMath.getUniqueLongKey(tileX, tileY));
		}

		List<RepairLevelJob> jobs = level.jobsLayer
				.streamJobsInTile(tileX, tileY)
				.filter(job -> job instanceof RepairLevelJob)
				.map(job -> (RepairLevelJob)job)
				.collect(Collectors.toList());

		for (RepairLevelJob job : jobs) {
			job.remove();
		}
	}

	private static void repairSingleDamage(Level level, int tileX, int tileY) {
		Set<Long> affectedTiles = new HashSet<>();
		boolean repairedObjectDamage = false;
		affectedTiles.add(GameMath.getUniqueLongKey(tileX, tileY));

		for (int layerID = 0; layerID < necesse.engine.registries.ObjectLayerRegistry.getTotalLayers(); layerID++) {
			GameObject object = level.getObject(layerID, tileX, tileY);

			if (object == null || object.getID() == 0) {
				continue;
			}

			LevelObject master = (LevelObject)object
					.getMultiTile(level, layerID, tileX, tileY)
					.getMasterLevelObject(level, layerID, tileX, tileY)
					.orElse(null);

			if (master == null) {
				continue;
			}

			Rectangle rectangle = master.object
					.getMultiTile(level, layerID, master.tileX, master.tileY)
					.getTileRectangle(master.tileX, master.tileY);

			for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					affectedTiles.add(GameMath.getUniqueLongKey(x, y));
				}
			}
		}

		for (long key : affectedTiles) {
			int currentX = GameMath.getXFromUniqueLongKey(key);
			int currentY = GameMath.getYFromUniqueLongKey(key);
			DamagedObjectEntity damagedEntity = level.entityManager.getDamagedObjectEntity(currentX, currentY);

			if (damagedEntity == null || damagedEntity.removed()) {
				continue;
			}

			if (currentX == tileX && currentY == tileY) {
				damagedEntity.tileDamage = 0;
			}

			for (int layerID = 0; layerID < damagedEntity.objectDamage.length; layerID++) {
				if (damagedEntity.objectDamage[layerID] > 0) {
					repairedObjectDamage = true;
				}

				damagedEntity.objectDamage[layerID] = 0;
			}

			damagedEntity.damageRecoverBuffer = 0.0F;

			if (damagedEntity.shouldRemove()) {
				damagedEntity.remove();
				level.getServer().network.sendToClientsWithTile(
						new PacketDamagedTileRemoved(currentX, currentY),
						level,
						currentX,
						currentY
				);
			} else {
				damagedEntity.shouldSave = true;
				level.getServer().network.sendToClientsWithTile(
						new PacketDamagedTileEntity(damagedEntity),
						level,
						currentX,
						currentY
				);
			}
		}

		if (repairedObjectDamage) {
			WeatheringLevelData weathering = WeatheringLevelData.get(level, false);

			if (weathering != null) {
				weathering.resetWeatheringAt(tileX, tileY);
			}
		}
	}
}
