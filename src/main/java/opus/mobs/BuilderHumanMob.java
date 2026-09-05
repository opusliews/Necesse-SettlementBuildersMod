package opus.mobs;

import necesse.engine.expeditions.SettlerExpedition;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.SettlerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.engine.world.WorldFile;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.friendly.human.MoveToTile;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.job.WorkInventory;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opus.damage.DamageRepairLevelData;
import opus.damage.WeatheringLevelData;
import opus.logging.Logging;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

public class BuilderHumanMob extends HumanShop {
	public static final int maxWorkInventoryStacks = 5;
	private static final int buildersPerRecruitTier = 3;
	private static final int roadRepairRadius = 6;
	private static final long roadRepairCheckInterval = 2000L;

	private boolean repairOnRoad;
	private final ArrayDeque<Point> roadRepairQueue = new ArrayDeque<>();
	private Point activeRoadRepairTarget;
	private Point activeRoadRepairWorkTile;
	private boolean atRoadRepairWorkTile;
	private long activeRoadRepairCompleteTime;
	private long nextRoadRepairCheckTime;

	private static final String[][] recruitBarTiers = {
			{"copperbar", "ironbar", "goldbar"},
			{"goldbar", "demonicbar"},
			{"demonicbar", "ivybar"},
			{"ivybar", "tungstenbar"},
			{"tungstenbar", "glacialbar"},
			{"glacialbar", "myceliumbar"},
			{"myceliumbar", "ancientfossilbar"},
			{"myceliumbar", "ancientfossilbar"} // Repeated intentionaly for last tier
	};

	private static final String[][] recruitRockTiers = {
			{"stone"},
			{"stone", "swampstone"},
			{"swampstone", "sandstone"},
			{"sandstone", "deepstone"},
			{"deepstone", "deepsnowstone"},
			{"deepsnowstone", "deepswampstone"},
			{"deepswampstone", "deepsandstone"} // Repeated intentionaly for last tier
	};

	private static final String[] recruitCrystals = {
			"amethyst",
			"emerald",
			"ruby",
			"sapphire",
			"topaz"
	};

	public BuilderHumanMob() {
		super(500, 200, "builder");
		this.attackCooldown = 500;
		this.attackAnimTime = 500;
		this.setSwimSpeed(1.0F);
		this.jobTypeHandler.getPriority("construction").disabledBySettler = false;
		this.equipmentInventory.setItem(6, new InventoryItem("coppersword"));
		this.shop.addSellingItem("builderhat", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
		this.shop.addSellingItem("buildershirt", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
		this.shop.addSellingItem("builderboots", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
		this.shop.addSellingItem("blueprintItem", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 125, 20);
		this.shop.addSellingItem("projecteraser", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
	}

	@Override
	public void init() {
		super.init();
		this.jobTypeHandler.globalCooldown = 0L;
	}

	@Override
	public void serverTick() {
		super.serverTick();
		tickRoadRepairs();
	}

	public int getWorkActionDelay() {
		int happiness = Math.max(0, Math.min(100, getSettlerHappiness()));
		return 5000 - happiness * 45;
	}

	private void tickRoadRepairs() {
		if (!repairOnRoad || !adventureParty.isInAdventureParty()) {
			clearRoadRepairState();
			return;
		}

		Level level = getLevel();
		WeatheringLevelData weathering = WeatheringLevelData.get(level, false);

		if (weathering == null || weathering.isSettlementAt(getTileX(), getTileY())) {
			clearRoadRepairState();
			return;
		}

		long currentTime = level.getTime();

		if (activeRoadRepairTarget == null && currentTime >= nextRoadRepairCheckTime) {
			nextRoadRepairCheckTime = currentTime + roadRepairCheckInterval;
			refreshRoadRepairQueue(weathering);
			startNextRoadRepair(weathering);
		}

		if (activeRoadRepairTarget == null) {
			return;
		}

		if (!isRoadRepairTargetStillValid(weathering, activeRoadRepairTarget)) {
			finishCurrentRoadRepairTarget();
			startNextRoadRepair(weathering);
			return;
		}

		if (!atRoadRepairWorkTile) {
			if (activeRoadRepairWorkTile != null
					&& getTileX() == activeRoadRepairWorkTile.x
					&& getTileY() == activeRoadRepairWorkTile.y) {
				atRoadRepairWorkTile = true;
				activeRoadRepairCompleteTime = currentTime + getWorkActionDelay();
			} else {
				return;
			}
		}

		showWorkAnimation(
				activeRoadRepairTarget.x * 32 + 16,
				activeRoadRepairTarget.y * 32 + 16,
				ItemRegistry.getItem("constructionhammer"),
				1000,
				true
		);

		if (currentTime < activeRoadRepairCompleteTime) {
			return;
		}

		Point repaired = activeRoadRepairTarget;
		DamageRepairLevelData.repairDamage(level, repaired.x, repaired.y);
		weathering.reinforceWildernessAt(repaired.x, repaired.y);

		Logging.logMessage(
				"Builder " + getUniqueID() + " reinforced wilderness construction at " + repaired.x + ", " + repaired.y
		);

		finishCurrentRoadRepairTarget();
		startNextRoadRepair(weathering);
	}

	private void refreshRoadRepairQueue(WeatheringLevelData weathering) {
		roadRepairQueue.clear();

		int centerX = getTileX();
		int centerY = getTileY();
		ArrayList<Point> targets = new ArrayList<>();

		for (int x = centerX - roadRepairRadius; x <= centerX + roadRepairRadius; x++) {
			for (int y = centerY - roadRepairRadius; y <= centerY + roadRepairRadius; y++) {
				if (weathering.hasRoadRepairTargetAt(x, y)) {
					targets.add(new Point(x, y));
				}
			}
		}

		targets.sort((a, b) -> Integer.compare(
				distanceSquaredToTile(a.x, a.y),
				distanceSquaredToTile(b.x, b.y)
		));

		roadRepairQueue.addAll(targets);
	}

	private void startNextRoadRepair(WeatheringLevelData weathering) {
		while (!roadRepairQueue.isEmpty()) {
			Point target = getNearestQueuedRoadRepairTarget(weathering);

			if (target == null) {
				roadRepairQueue.clear();
				return;
			}

			roadRepairQueue.remove(target);
			Point workTile = findRoadRepairWorkTile(target);

			if (workTile == null) {
				continue;
			}

			activeRoadRepairTarget = target;
			activeRoadRepairWorkTile = workTile;
			atRoadRepairWorkTile = getTileX() == workTile.x && getTileY() == workTile.y;
			activeRoadRepairCompleteTime = atRoadRepairWorkTile
					? getLevel().getTime() + getWorkActionDelay()
					: 0L;
			return;
		}
	}

	private Point getNearestQueuedRoadRepairTarget(WeatheringLevelData weathering) {
		Point nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (Point target : roadRepairQueue) {
			if (!isRoadRepairTargetStillValid(weathering, target)) {
				continue;
			}

			int distance = distanceSquaredToTile(target.x, target.y);

			if (distance < nearestDistance) {
				nearest = target;
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	private Point findRoadRepairWorkTile(Point target) {
		ArrayList<Point> candidates = new ArrayList<>();

		for (int range = 1; range <= 2; range++) {
			candidates.clear();

			for (int x = target.x - range; x <= target.x + range; x++) {
				for (int y = target.y - range; y <= target.y + range; y++) {
					if (Math.max(Math.abs(x - target.x), Math.abs(y - target.y)) != range) {
						continue;
					}

					if (!getLevel().isTileWithinBounds(x, y)) {
						continue;
					}

					candidates.add(new Point(x, y));
				}
			}

			candidates.sort((a, b) -> Integer.compare(
					distanceSquaredToTile(a.x, a.y),
					distanceSquaredToTile(b.x, b.y)
			));

			for (Point candidate : candidates) {
				if (getTileX() == candidate.x && getTileY() == candidate.y) {
					return candidate;
				}

				if (estimateCanMoveTo(candidate.x, candidate.y, false)) {
					return candidate;
				}
			}
		}

		return null;
	}

	private int distanceSquaredToTile(int tileX, int tileY) {
		int dx = tileX - getTileX();
		int dy = tileY - getTileY();
		return dx * dx + dy * dy;
	}

	private boolean isRoadRepairTargetStillValid(WeatheringLevelData weathering, Point target) {
		return Math.abs(target.x - getTileX()) <= roadRepairRadius
				&& Math.abs(target.y - getTileY()) <= roadRepairRadius
				&& !weathering.isSettlementAt(target.x, target.y)
				&& weathering.hasRoadRepairTargetAt(target.x, target.y);
	}

	private void finishCurrentRoadRepairTarget() {
		activeRoadRepairTarget = null;
		activeRoadRepairWorkTile = null;
		atRoadRepairWorkTile = false;
		activeRoadRepairCompleteTime = 0L;
	}

	private void clearRoadRepairState() {
		roadRepairQueue.clear();
		finishCurrentRoadRepairTarget();
		nextRoadRepairCheckTime = 0L;
	}

	@Override
	public MoveToTile getMoveToPoint() {
		if (activeRoadRepairTarget != null
				&& activeRoadRepairWorkTile != null
				&& !atRoadRepairWorkTile) {
			Point workTile = activeRoadRepairWorkTile;

			return new MoveToTile(workTile, false) {
				@Override
				public boolean moveIfPathFailed(float tileDistance) {
					return false;
				}

				@Override
				public boolean isAtLocation(float tileDistance, boolean foundPath) {
					return foundPath && tileDistance < 0.75F;
				}

				@Override
				public void onArrivedAtLocation() {
					atRoadRepairWorkTile = true;
					activeRoadRepairCompleteTime = getLevel().getTime() + getWorkActionDelay();
				}
			};
		}

		return super.getMoveToPoint();
	}

	@Override
	protected ArrayList<GameMessage> getMessages(ServerClient client) {
		return this.getLocalMessages("buildertalk", 7);
	}

	@Override
	public boolean canDoExpedition(SettlerExpedition expedition) {
		return false;
	}

	@Override
	public List getPossibleExpeditions() {
		return Collections.emptyList();
	}

	@Override
	public List<InventoryItem> getRecruitItems(ServerClient client) {
		int tier = getRecruitTier(client);

		GameRandom random = new GameRandom((long)this.getSettlerSeed() * 227L + tier * 7919L);
		String barID = getRecruitBarID(random, tier);
		String rockID = getRecruitRockID(random, tier);
		ArrayList<InventoryItem> items = new ArrayList<>();

		items.add(new InventoryItem(barID, random.getIntBetween(4, 10)));
		items.add(new InventoryItem(rockID, random.getIntBetween(25, 50)));

		if (tier == 7) {
			String crystalID = getRecruitCrystalID(random);
			items.add(new InventoryItem(crystalID, random.getIntBetween(3, 7)));
		}

		return items;
	}

	private int getCombinedBuilderCount(ServerClient client) {
		SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(client.getServer());
		int builderCount = 0;

		for (Object object : settlementsData.collectCachedSettlements(cached -> ((CachedSettlementData)cached).hasAccess(client))) {
			CachedSettlementData cached = (CachedSettlementData)object;
			ServerSettlementData settlement = settlementsData.getServerData(cached.uniqueID);

			if (settlement != null) {
				builderCount += settlement.getSettlerCount(SettlerRegistry.getSettler("builder"));
			} else {
				builderCount += getSavedBuilderCount(client, cached.uniqueID);
			}
		}

		return builderCount;
	}

	private int getSavedBuilderCount(ServerClient client, int settlementUniqueID) {
		try {
			WorldFile file = client.getServer().world.fileSystem.getSettlementFile(settlementUniqueID);

			if (!file.exists()) {
				return 0;
			}

			LoadData save = new LoadData(file);
			LoadData serverSave = save.getFirstLoadDataByName("SERVER");

			if (serverSave == null) {
				return 0;
			}

			LoadData settlersSave = serverSave.getFirstLoadDataByName("SETTLERS");

			if (settlersSave == null) {
				return 0;
			}

			int count = 0;

			for (Object object : settlersSave.getLoadDataByName("SETTLER")) {
				LoadData settlerLoad = (LoadData)object;

				if ("builder".equals(settlerLoad.getUnsafeString("stringID", null, false))) {
					count++;
				}
			}

			return count;
		} catch (Exception e) {
			return 0;
		}
	}

	private int getRecruitTier(ServerClient client) {
		int builderCount = getCombinedBuilderCount(client);
		return Math.min(builderCount / buildersPerRecruitTier, recruitBarTiers.length - 1);
	}

	private String getRecruitBarID(GameRandom random, int tier) {
		String[] options = recruitBarTiers[tier];
		return options[random.nextInt(options.length)];
	}

	private String getRecruitRockID(GameRandom random, int tier) {
		String[] options = recruitRockTiers[Math.min(tier, recruitRockTiers.length - 1)];
		return options[random.nextInt(options.length)];
	}

	private String getRecruitCrystalID(GameRandom random) {
		return recruitCrystals[random.nextInt(recruitCrystals.length)];
	}

	public boolean isRepairOnRoad() {
		return repairOnRoad;
	}

	public void setRepairOnRoad(boolean repairOnRoad) {
		this.repairOnRoad = repairOnRoad;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addBoolean("repairOnRoad", repairOnRoad);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		repairOnRoad = save.getBoolean("repairOnRoad", false, false);
	}

	@Override
	public void setupSpawnPacket(PacketWriter writer) {
		super.setupSpawnPacket(writer);
		writer.putNextBoolean(repairOnRoad);
	}

	@Override
	public void applySpawnPacket(PacketReader reader) {
		super.applySpawnPacket(reader);
		repairOnRoad = reader.getNextBoolean();
	}

	// Inventory overrides, to remove broker value limits to builder inventory
	@Override
	public WorkInventory getWorkInventory() {
		WorkInventory parent = super.getWorkInventory();

		return new WorkInventory() {
			@Override
			public ListIterator listIterator() {
				return parent.listIterator();
			}

			@Override
			public Iterable items() {
				return parent.items();
			}

			@Override
			public Stream stream() {
				return parent.stream();
			}

			@Override
			public void markDirty() {
				parent.markDirty();
			}

			@Override
			public void add(InventoryItem item) {
				parent.add(item);
			}

			@Override
			public int getCanAddAmount(InventoryItem item) {
				if (getTotalItemStacks() >= maxWorkInventoryStacks) {
					return 0;
				}

				return item.getAmount();
			}

			@Override
			public boolean isFull() {
				return getTotalItemStacks() >= maxWorkInventoryStacks;
			}

			@Override
			public int getTotalItemStacks() {
				return parent.getTotalItemStacks();
			}

			@Override
			public boolean isEmpty() {
				return parent.isEmpty();
			}
		};
	}
}
