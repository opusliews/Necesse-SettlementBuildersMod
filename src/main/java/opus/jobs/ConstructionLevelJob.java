package opus.jobs;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.packet.PacketPlaceObject;
import necesse.engine.network.packet.PacketPlaceTile;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.AbstractDamageResult;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.job.*;
import necesse.entity.mobs.job.activeJob.*;
import necesse.entity.pickup.ItemPickupEntity;
import necesse.gfx.GameColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.gameObject.AirObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import opus.blueprint.*;
import opus.logging.Logging;
import opus.mobs.BuilderHumanMob;
import opus.network.PacketBlueprintBlockedState;
import opus.network.PacketBuilderObjectPlaceSound;
import opus.network.PacketBuilderTilePlaceSound;
import opus.network.PacketSyncBlueprintAreas;

import java.awt.*;
import java.util.List;
import java.util.*;

public class ConstructionLevelJob extends TileLevelJob {
	private final String blueprintAreaUniqueID;
	private long nextActionTime;

	public ConstructionLevelJob(int tileX, int tileY, String blueprintAreaUniqueID) {
		super(tileX, tileY);

		this.blueprintAreaUniqueID = blueprintAreaUniqueID;

		Logging.logMessage(
				"Created ConstructionLevelJob for blueprint "
						+ blueprintAreaUniqueID
						+ " at work tile "
						+ tileX
						+ ", "
						+ tileY
		);
	}

	public ConstructionLevelJob(LoadData save) {
		super(save);

		this.blueprintAreaUniqueID = save.getSafeString("blueprintAreaUniqueID");

		Logging.logMessage(
				"Loaded ConstructionLevelJob for blueprint "
						+ blueprintAreaUniqueID
						+ " at work tile "
						+ tileX
						+ ", "
						+ tileY
		);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addSafeString("blueprintAreaUniqueID", blueprintAreaUniqueID);
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid()) {
			return false;
		}

		boolean projectExists = BlueprintAreaManager
				.get(getLevel())
				.getArea(blueprintAreaUniqueID) != null;

		if (!projectExists) {
			Logging.logMessage(
					"ConstructionLevelJob became invalid because blueprint "
							+ blueprintAreaUniqueID
							+ " no longer exists"
			);
		}

		return projectExists;
	}

	public String getBlueprintAreaUniqueID() {
		return blueprintAreaUniqueID;
	}

	public ActiveJob getActiveJob(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			LinkedListJobSequence sequence
	) {
		Logging.logMessage(
				"Creating ActiveJob for blueprint "
						+ blueprintAreaUniqueID
						+ " at work tile "
						+ tileX
						+ ", "
						+ tileY
		);

		return new TileActiveJob(worker, priority, tileX, tileY) {
			private boolean started;
			private boolean invalidLogged;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				return new JobMoveToTile(tileX, tileY, false);
			}

			@Override
			public int getCompleteRange() {
				return 8;
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);

				BlueprintArea area = BlueprintAreaManager.get(getLevel()).getArea(blueprintAreaUniqueID);

				if (area != null) {
					int builderUniqueID = worker.getMobWorker().getUniqueID();

					if (area.isConstructionComplete() && !worker.getWorkInventory().isEmpty()) {
						Logging.logMessage(
								"Builder "
										+ builderUniqueID
										+ " interrupted during completed blueprint cleanup and remains assigned"
						);
					} else {
						area.releaseBuilder(builderUniqueID);

						Logging.logMessage(
								"Builder "
										+ builderUniqueID
										+ " left construction project "
										+ blueprintAreaUniqueID
						);

						if (area.isConstructionComplete() && !area.hasAssignedBuilders()) {
							finishBlueprintCleanup(getLevel(), area);
						}
					}
				}
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				ConstructionLevelJob.this.reservable.reserve(worker.getMobWorker());

				if (isCurrent && !isMovingTo) {
					BlueprintArea area = BlueprintAreaManager.get(getLevel()).getArea(blueprintAreaUniqueID);

					if (area == null) {
						return;
					}

					int blueprintCenterX = (area.getOriginX() * 32) + (area.getWidth() * 16);
					int blueprintCenterY = (area.getOriginY() * 32) + (area.getHeight() * 16);

					worker.showWorkAnimation(
							blueprintCenterX,
							blueprintCenterY,
							ItemRegistry.getItem("constructionhammer"),
							1000,
							true
					);
				}
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				boolean removed = ConstructionLevelJob.this.isRemoved();
				boolean levelJobValid = ConstructionLevelJob.this.isValid();
				boolean reservationAvailable = ConstructionLevelJob.this.reservable.isAvailable(
						worker.getMobWorker()
				);

				boolean valid = !removed && levelJobValid && reservationAvailable;

				if (!valid && !invalidLogged) {
					invalidLogged = true;

					Logging.logMessage(
							"Construction ActiveJob became invalid for blueprint "
									+ blueprintAreaUniqueID
									+ " at work tile "
									+ tileX
									+ ", "
									+ tileY
									+ " [removed="
									+ removed
									+ ", levelJobValid="
									+ levelJobValid
									+ ", reservationAvailable="
									+ reservationAvailable
									+ "]"
					);
				}

				if (valid) {
					invalidLogged = false;
				}

				return valid;
			}

			@Override
			public ActiveJobResult perform() {
				int actionDelay = ((BuilderHumanMob)worker.getMobWorker()).getWorkActionDelay();
				long currentTime = getLevel().getTime();

				BlueprintArea area = BlueprintAreaManager
						.get(getLevel())
						.getArea(blueprintAreaUniqueID);

				if (area == null) {
					return ActiveJobResult.FAILED;
				}

				if (area.isConstructionComplete()) {
					if (currentTime < nextActionTime) {
						return ActiveJobResult.PERFORMING;
					}

					if (queueCompletionCleanupJobs(worker, priority, area, sequence)) {
						return ActiveJobResult.FINISHED;
					}

					nextActionTime = currentTime + actionDelay;
					return ActiveJobResult.PERFORMING;
				}

				if (!started) {
					started = true;
					nextActionTime = getLevel().getTime() + actionDelay;

					Logging.logMessage(
							"Builder started construction for blueprint "
									+ blueprintAreaUniqueID
									+ " from work tile "
									+ tileX
									+ ", "
									+ tileY
					);

					return ActiveJobResult.PERFORMING;
				}

				if (currentTime < nextActionTime) {
					return ActiveJobResult.PERFORMING;
				}

				BlueprintClearTarget clearTarget = area.findFirstClearTarget(getLevel());

				if (clearTarget != null) {
					performClearAction(getLevel(), worker.getMobWorker(), area, clearTarget);
					nextActionTime = currentTime + actionDelay;
					return ActiveJobResult.PERFORMING;
				}

				BlueprintTileTarget tileTarget = area.findFirstTileTarget(getLevel());

				if (tileTarget != null) {
					BuilderHumanMob materialBuilder = area.consumeBuilderMaterial(getLevel(), tileTarget.tileID);

					if (materialBuilder != null) {
						performTilePlaceAction(getLevel(), tileTarget);
						nextActionTime = currentTime + actionDelay;

						Logging.logMessage(
								"Builder "
										+ worker.getMobWorker().getUniqueID()
										+ " placed tile "
										+ tileTarget.tileID
										+ " at "
										+ tileTarget.tileX
										+ ", "
										+ tileTarget.tileY
										+ " using material from Builder "
										+ materialBuilder.getUniqueID()
						);

						return ActiveJobResult.PERFORMING;
					}

					if (queueRefillJobs(worker, priority, area, sequence)) {
						Logging.logMessage(
								"Builder "
										+ worker.getMobWorker().getUniqueID()
										+ " queued another construction material batch for blueprint "
										+ blueprintAreaUniqueID
						);

						return ActiveJobResult.FINISHED;
					}

					nextActionTime = currentTime + actionDelay;
					return ActiveJobResult.PERFORMING;
				}

				BlueprintObjectTarget objectTarget =
						area.findFirstObjectTarget(getLevel());

				if (objectTarget != null) {
					GameObject object = ObjectRegistry.getObject(objectTarget.objectID);

					if (object == null) {
						nextActionTime = currentTime + actionDelay;
						return ActiveJobResult.PERFORMING;
					}

					String placeError = object.canPlace(
							getLevel(),
							0,
							objectTarget.tileX,
							objectTarget.tileY,
							objectTarget.rotation,
							true,
							false
					);

					if (placeError != null) {
						nextActionTime = currentTime + actionDelay;
						return ActiveJobResult.PERFORMING;
					}

					BuilderHumanMob materialBuilder =
							area.consumeBuilderMaterial(
									getLevel(),
									objectTarget.objectID
							);

					if (materialBuilder != null) {
						performObjectPlaceAction(getLevel(), objectTarget);

						nextActionTime = currentTime + actionDelay;

						Logging.logMessage(
								"Builder "
										+ worker.getMobWorker().getUniqueID()
										+ " placed object "
										+ objectTarget.objectID
										+ " at "
										+ objectTarget.tileX
										+ ", "
										+ objectTarget.tileY
										+ " using material from Builder "
										+ materialBuilder.getUniqueID()
						);

						return ActiveJobResult.PERFORMING;
					}

					if (queueRefillJobs(worker, priority, area, sequence)) {
						return ActiveJobResult.FINISHED;
					}

					nextActionTime = currentTime + actionDelay;
					return ActiveJobResult.PERFORMING;
				}

				beginBlueprintCompletion(getLevel(), area);

				if (queueCompletionCleanupJobs(worker, priority, area, sequence)) {
					return ActiveJobResult.FINISHED;
				}

				nextActionTime = currentTime + actionDelay;
				return ActiveJobResult.PERFORMING;
			}
		};
	}

	private boolean queueRefillJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			BlueprintArea area,
			LinkedListJobSequence sequence
	) {
		BuilderHumanMob builder = (BuilderHumanMob)worker.getMobWorker();

		ServerSettlementData settlement = SettlementsWorldData
				.getSettlementsData(getLevel().getServer())
				.getServerData(area.getSettlementUniqueID());

		if (settlement == null) {
			Logging.logMessage(
					"Could not continue blueprint "
							+ area.getUniqueID()
							+ " because settlement "
							+ area.getSettlementUniqueID()
							+ " could not be found"
			);

			return false;
		}

		Map<String, Integer> missing = getMissingProjectMaterials(settlement, area, getLevel(), builder);

		if (!missing.isEmpty()) {
			sendMissingMaterialsMessage(getLevel(), area, settlement, missing);

			Logging.logMessage(
					"Blueprint "
							+ area.getUniqueID()
							+ " blocked during refill because required materials are missing: "
							+ missing
			);

			return false;
		}



		List<ActiveJob> dumpJobs = new ArrayList<>();

		if (!addCurrentInventoryDropOffJobs(worker, priority, dumpJobs)) {
			Logging.logMessage(
					"Builder "
							+ worker.getMobWorker().getUniqueID()
							+ " could not currently schedule a settlement storage drop-off; construction will retry"
			);

			return false;
		}

		int builderUniqueID = builder.getUniqueID();

		Map<String, Integer> allocation =
				getBuilderMaterialAllocation(getLevel(), area, builderUniqueID);

		if (allocation.isEmpty()) {
			cancelPlannedJobs(dumpJobs);
			return false;
		}

		List<SettlementStoragePickupSlot> pickupSlots =
				reserveBuilderMaterials(worker, allocation);

		if (pickupSlots == null) {
			cancelPlannedJobs(dumpJobs);

			Map<String, Integer> missingAfterReservation =
					getMissingProjectMaterials(settlement, area, getLevel(), builder);

			if (!missingAfterReservation.isEmpty()) {
				sendMissingMaterialsMessage(getLevel(), area, settlement, missingAfterReservation);

				Logging.logMessage(
						"Blueprint "
								+ area.getUniqueID()
								+ " became blocked while reserving refill materials: "
								+ missingAfterReservation
				);
			} else {
				sendConstructionMessage(
						getLevel(),
						area,
						settlement,
						"constructionmaterialsunavailable"
				);

				Logging.logMessage(
						"Blueprint "
								+ area.getUniqueID()
								+ " could not reserve refill materials"
				);
			}

			return false;
		}

		if (area.clearConstructionBlockedReason()) {
			syncConstructionBlockedReason(getLevel(), area);
		}

		area.setBuilderMaterialAllocation(builderUniqueID, allocation);

		sequence.addAll(dumpJobs);

		for (SettlementStoragePickupSlot slot : pickupSlots) {
			sequence.add(slot.toPickupJob(worker, priority));
		}

		sequence.add(getActiveJob(worker, priority, sequence));

		return true;
	}

	private void performTilePlaceAction(Level level, BlueprintTileTarget target) {
		GameTile tile = TileRegistry.getTile(target.tileID);

		tile.placeTile(level, target.tileX, target.tileY, true);
		level.onTilePlaced(tile, target.tileX, target.tileY, null);
		level.tileLayer.setIsPlayerPlaced(target.tileX, target.tileY, true);

		if (level.isServer()) {
			level.getServer().network.sendToClientsWithTile(
					new PacketPlaceTile(level, null, tile.getID(), target.tileX, target.tileY),
					level,
					target.tileX,
					target.tileY
			);
		}

		level.getServer().network.sendToClientsWithTile(
				new PacketBuilderTilePlaceSound(
						tile.getID(),
						target.tileX,
						target.tileY
				),
				level,
				target.tileX,
				target.tileY
		);

		level.getLevelTile(target.tileX, target.tileY).checkAround();

		for (Integer layerID : ObjectLayerRegistry.getLayerIDs()) {
			GameObject object = level.getObject(layerID, target.tileX, target.tileY);

			if (object.getID() != 0) {
				object.checkIsValid(level, layerID, target.tileX, target.tileY);
			}
		}

		level.getLevelObject(target.tileX, target.tileY).checkAround();
	}

	private void performObjectPlaceAction(Level level, BlueprintObjectTarget target) {
		GameObject object = ObjectRegistry.getObject(target.objectID);
		int layerID = 0;

		object.placeObject(
				level,
				layerID,
				target.tileX,
				target.tileY,
				target.rotation,
				true
		);

		if (level.isServer()) {
			level.onObjectPlaced(
					object,
					layerID,
					target.tileX,
					target.tileY,
					null
			);

			level.getServer().network.sendToClientsWithTile(
					new PacketPlaceObject(
							level,
							null,
							layerID,
							target.tileX,
							target.tileY,
							object.getID(),
							target.rotation,
							true,
							false
					),
					level,
					target.tileX,
					target.tileY
			);
		}

		level.getServer().network.sendToClientsWithTile(
				new PacketBuilderObjectPlaceSound(
						object.getID(),
						target.tileX,
						target.tileY
				),
				level,
				target.tileX,
				target.tileY
		);

		level.getTile(target.tileX, target.tileY)
				.checkAround(level, target.tileX, target.tileY);

		level.getObject(target.tileX, target.tileY)
				.checkAround(level, target.tileX, target.tileY);
	}

	private void performClearAction(
			Level level, Mob worker, BlueprintArea area, BlueprintClearTarget target) {
		AbstractDamageResult result;

		if (target.type == BlueprintClearTarget.Type.OBJECT) {
			GameObject object = level.getObject(target.tileX, target.tileY);

			result = level.entityManager.doObjectDamage(
					0,
					target.tileX,
					target.tileY,
					object.objectHealth,
					Float.MAX_VALUE,
					worker,
					null,
					true,
					target.tileX * 32 + 16,
					target.tileY * 32 + 16
			);
		}
		else {
			GameTile tile = level.getTile(target.tileX, target.tileY);

			result = level.entityManager.doTileDamage(
					target.tileX,
					target.tileY,
					tile.tileHealth,
					Float.MAX_VALUE,
					worker,
					null,
					true,
					target.tileX * 32 + 16,
					target.tileY * 32 + 16
			);
		}

		redirectClearDrops(level, area, target, result);
	}

	private void redirectClearDrops(
			Level level,
			BlueprintArea area,
			BlueprintClearTarget target,
			AbstractDamageResult result
	) {
		if (result == null || !result.destroyed || result.itemsDropped.isEmpty()) {
			return;
		}

		Point dropTile = findClearDropTile(level, area, target);

		if (dropTile == null) {
			return;
		}

		float dropX = dropTile.x * 32 + 16 + getRandomNumber(-14, 15);
		float dropY = dropTile.y * 32 + 16 + getRandomNumber(-14, 15);

		for (ItemPickupEntity pickup : result.itemsDropped) {
			pickup.x = dropX;
			pickup.y = dropY;
			pickup.dx = 0.0F;
			pickup.dy = 0.0F;

			pickup.sendTargetUpdatePacket();
		}
	}

	private static Map<String, Integer> getMissingProjectMaterials(
			ServerSettlementData settlement,
			BlueprintArea area,
			Level level,
			BuilderHumanMob candidateBuilder
	) {
		Map<String, Integer> required = area.getRequiredMaterials(level);
		Map<String, Integer> available = new LinkedHashMap<>();

		for (SettlementInventory storage : settlement.storageManager.getStorage()) {
			InventoryRange range = storage.getInventoryRange();

			if (range == null) {
				continue;
			}

			for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
				InventoryItem item = range.inventory.getItem(slot);

				if (item == null) {
					continue;
				}

				String itemID = item.item.getStringID();

				if (required.containsKey(itemID)) {
					available.merge(itemID, item.getAmount(), Integer::sum);
				}
			}
		}

		for (BuilderHumanMob builder : area.getAssignedBuilders(level)) {
			for (InventoryItem item : builder.getWorkInventory().items()) {
				if (item == null) {
					continue;
				}

				String itemID = item.item.getStringID();

				if (required.containsKey(itemID)) {
					available.merge(itemID, item.getAmount(), Integer::sum);
				}
			}
		}

		if (candidateBuilder != null && !area.isBuilderAssigned(candidateBuilder.getUniqueID())) {
			for (InventoryItem item : candidateBuilder.getWorkInventory().items()) {
				if (item == null) {
					continue;
				}

				String itemID = item.item.getStringID();

				if (required.containsKey(itemID)) {
					available.merge(itemID, item.getAmount(), Integer::sum);
				}
			}
		}

		Map<String, Integer> missing = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> entry : required.entrySet()) {
			int missingAmount = entry.getValue() - available.getOrDefault(entry.getKey(), 0);

			if (missingAmount > 0) {
				missing.put(entry.getKey(), missingAmount);
			}
		}

		return missing;
	}

	private static void sendMissingMaterialsMessage(
			Level level,
			BlueprintArea area,
			ServerSettlementData settlement,
			Map<String, Integer> missing
	) {
		StringBuilder materials = new StringBuilder();
		boolean first = true;

		for (Map.Entry<String, Integer> entry : missing.entrySet()) {
			if (!first) {
				materials.append(", ");
			}

			first = false;

			String displayName = ItemRegistry.getDisplayName(ItemRegistry.getItemID(entry.getKey()));

			materials
					.append(entry.getValue())
					.append("x ")
					.append(displayName);
		}

		sendConstructionMessage(
				level,
				area,
				settlement,
				"constructionmissingmaterials",
				"materials",
				materials.toString()
		);
	}

	private static void sendConstructionMessage(
			Level level,
			BlueprintArea area,
			ServerSettlementData settlement,
			String translationKey
	) {
		if (!area.setConstructionBlockedReason(translationKey)) {
			return;
		}

		syncConstructionBlockedReason(level, area);

		String message = Localization.translate("jobs", translationKey);

		settlement.networkData.streamTeamMembers().forEach(
				client -> client.sendChatMessage(message)
		);
	}

	private static void sendConstructionMessage(
			Level level,
			BlueprintArea area,
			ServerSettlementData settlement,
			String translationKey,
			String replacementKey,
			String replacementValue
	) {
		if (!area.setConstructionBlockedReason(translationKey)) {
			return;
		}

		syncConstructionBlockedReason(level, area);

		String message =
				GameColor.RED.getColorCode()
						+ Localization.translate(
						"jobs",
						translationKey,
						replacementKey,
						replacementValue
				);

		settlement.networkData.streamTeamMembers().forEach(
				client -> client.sendChatMessage(message)
		);
	}

	private static void syncBlueprintAreas(Level level) {
		if (!level.isServer()) {
			return;
		}

		BlueprintAreaManager manager = BlueprintAreaManager.get(level);

		level.getServer().network.sendToClientsAtEntireLevel(
				new PacketSyncBlueprintAreas(manager),
				level
		);
	}

	private void beginBlueprintCompletion(Level level, BlueprintArea area) {
		if (area.isConstructionComplete()) {
			return;
		}

		area.setConstructionComplete(true);
		if (area.clearConstructionBlockedReason()) {
			syncConstructionBlockedReason(level, area);
		}

		syncBlueprintAreas(level);

		Logging.logMessage(
				"Blueprint "
						+ area.getUniqueID()
						+ " construction completed; waiting for Builders to return surplus materials"
		);
	}

	private boolean queueCompletionCleanupJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			BlueprintArea area,
			LinkedListJobSequence sequence
	) {
		ServerSettlementData settlement = SettlementsWorldData
				.getSettlementsData(getLevel().getServer())
				.getServerData(area.getSettlementUniqueID());

		if (settlement == null) {
			Logging.logMessage(
					"Could not clean up completed blueprint "
							+ area.getUniqueID()
							+ " because settlement "
							+ area.getSettlementUniqueID()
							+ " could not be found"
			);

			return false;
		}

		List<ActiveJob> dumpJobs = new ArrayList<>();

		if (!addCurrentInventoryDropOffJobs(worker, priority, dumpJobs)) {
			Logging.logMessage(
					"Builder "
							+ worker.getMobWorker().getUniqueID()
							+ " could not currently schedule a settlement storage drop-off; construction will retry"
			);

			return false;
		}

		if (area.clearConstructionBlockedReason()) {
			syncConstructionBlockedReason(getLevel(), area);
		}

		sequence.addAll(dumpJobs);
		sequence.add(getCompletionCleanupActiveJob(worker, priority));

		return true;
	}

	private ActiveJob getCompletionCleanupActiveJob(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority
	) {
		return new SimplePerformActiveJob(worker, priority) {
			@Override
			public ActiveJobResult perform() {
				BlueprintArea area = BlueprintAreaManager
						.get(getLevel())
						.getArea(blueprintAreaUniqueID);

				if (area == null) {
					return ActiveJobResult.FINISHED;
				}

				int builderUniqueID = worker.getMobWorker().getUniqueID();

				area.releaseBuilder(builderUniqueID);

				Logging.logMessage(
						"Builder "
								+ builderUniqueID
								+ " finished surplus material cleanup for blueprint "
								+ blueprintAreaUniqueID
				);

				if (!area.hasAssignedBuilders()) {
					finishBlueprintCleanup(getLevel(), area);
				}

				return ActiveJobResult.FINISHED;
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);

				BlueprintArea area = BlueprintAreaManager
						.get(getLevel())
						.getArea(blueprintAreaUniqueID);

				if (area == null) {
					return;
				}

				int builderUniqueID = worker.getMobWorker().getUniqueID();

				if (!worker.getWorkInventory().isEmpty()) {
					Logging.logMessage(
							"Builder "
									+ builderUniqueID
									+ " interrupted during completed blueprint cleanup and remains assigned"
					);

					return;
				}

				area.releaseBuilder(builderUniqueID);

				Logging.logMessage(
						"Builder "
								+ builderUniqueID
								+ " left completed blueprint cleanup "
								+ blueprintAreaUniqueID
				);

				if (!area.hasAssignedBuilders()) {
					finishBlueprintCleanup(getLevel(), area);
				}
			}
		};
	}

	private void finishBlueprintCleanup(Level level, BlueprintArea area) {
		String uniqueID = area.getUniqueID();

		BlueprintAreaManager manager = BlueprintAreaManager.get(level);
		manager.removeArea(uniqueID);

		syncBlueprintAreas(level);

		Logging.logMessage(
				"Blueprint "
						+ uniqueID
						+ " fully completed and removed after Builder material cleanup"
		);
	}

	private int getRandomNumber(int min, int max) {
		return (int) ((Math.random() * (max - min)) + min);
	}

	private Point findClearDropTile(
			Level level,
			BlueprintArea area,
			BlueprintClearTarget target
	) {
		Point bestTile = null;
		int bestDistanceSquared = Integer.MAX_VALUE;

		for (Point tile : area.getOutsideBorderTiles()) {
			if (!level.isTileWithinBounds(tile.x, tile.y)) {
				continue;
			}

			if (!(level.getObject(tile.x, tile.y) instanceof AirObject)) {
				continue;
			}

			if (level.getTile(tile.x, tile.y).isLiquid) {
				continue;
			}

			int dx = tile.x - target.tileX;
			int dy = tile.y - target.tileY;
			int distanceSquared = dx * dx + dy * dy;

			if (distanceSquared < bestDistanceSquared) {
				bestTile = tile;
				bestDistanceSquared = distanceSquared;
			}
		}

		return bestTile;
	}

	public static JobSequence getJobSequence(BuilderHumanMob worker, FoundJob foundJob) {
		ConstructionLevelJob job = (ConstructionLevelJob)foundJob.job;

		BlueprintArea area = BlueprintAreaManager.get(job.getLevel()).getArea(job.blueprintAreaUniqueID);

		if (area == null) {
			return null;
		}

		ServerSettlementData settlement = SettlementsWorldData
				.getSettlementsData(job.getLevel().getServer())
				.getServerData(area.getSettlementUniqueID());

		if (settlement == null) {
			Logging.logMessage(
					"Could not continue blueprint "
							+ area.getUniqueID()
							+ " because settlement "
							+ area.getSettlementUniqueID()
							+ " could not be found"
			);

			return null;
		}

		if (area.isConstructionComplete()) {
			if (!area.isBuilderAssigned(worker.getUniqueID())) {
				return null;
			}

			LinkedListJobSequence sequence = new LinkedListJobSequence(
					new LocalMessage("activities", "construction"),
					false
			);

			if (!job.queueCompletionCleanupJobs(
					worker,
					foundJob.priority,
					area,
					sequence
			)) {
				return null;
			}

			return sequence;
		}

		Map<String, Integer> missing = getMissingProjectMaterials(settlement, area, job.getLevel(), worker);

		if (area.findFirstClearTarget(job.getLevel()) == null
				&& area.findFirstTileTarget(job.getLevel()) == null
				&& area.findFirstObjectTarget(job.getLevel()) == null) {
			job.beginBlueprintCompletion(job.getLevel(), area);

			String message =
					GameColor.GREEN.getColorCode()
							+ Localization.translate("jobs", "constructionalreadycomplete");

			settlement.networkData.streamTeamMembers().forEach(
					client -> client.sendChatMessage(message)
			);

			if (!area.hasAssignedBuilders()) {
				job.finishBlueprintCleanup(job.getLevel(), area);
			}

			return null;
		}

		if (!missing.isEmpty()) {
			sendMissingMaterialsMessage(job.getLevel(), area, settlement, missing);

			Logging.logMessage(
					"Blueprint "
							+ area.getUniqueID()
							+ " blocked because required materials are missing: "
							+ missing
			);

			return null;
		}



		if (!area.hasConstructionStarted()) {
			area.setConstructionStarted(true);

			Logging.logMessage(
					"Blueprint "
							+ area.getUniqueID()
							+ " passed full material check and can begin construction"
			);
		}

		int builderUniqueID = worker.getUniqueID();

		if (area.isBuilderAssigned(builderUniqueID)) {
			LinkedListJobSequence sequence = new LinkedListJobSequence(
					new LocalMessage("activities", "construction"),
					false
			);

			sequence.add(job.getActiveJob(worker, foundJob.priority, sequence));

			return sequence;
		}

		clearPreviousBuilderAssignment(job.getLevel(), builderUniqueID);

		List<ActiveJob> dumpJobs = new ArrayList<>();

		if (!addCurrentInventoryDropOffJobs(worker, foundJob.priority, dumpJobs)) {
			Logging.logMessage(
					"Builder "
							+ builderUniqueID
							+ " could not currently find enough usable settlement storage "
							+ "for carried items; construction job will retry"
			);

			return null;
		}

		Map<String, Integer> allocation = getBuilderMaterialAllocation(job.getLevel(), area, builderUniqueID);

		if (allocation.isEmpty()) {
			area.assignBuilder(worker);

			Logging.logMessage(
					"Builder "
							+ builderUniqueID
							+ " joined blueprint "
							+ area.getUniqueID()
							+ " without material allocation; using shared Builder materials"
			);

			LinkedListJobSequence sequence = new LinkedListJobSequence(
					new LocalMessage("activities", "construction"),
					false
			);

			sequence.addAll(dumpJobs);
			sequence.add(job.getActiveJob(worker, foundJob.priority, sequence));

			return sequence;
		}

		List<SettlementStoragePickupSlot> pickupSlots = reserveBuilderMaterials(worker, allocation);
		if (pickupSlots == null) {
			cancelPlannedJobs(dumpJobs);

			Map<String, Integer> missingAfterReservation =
					getMissingProjectMaterials(settlement, area, job.getLevel(), worker);

			if (!missingAfterReservation.isEmpty()) {
				sendMissingMaterialsMessage(job.getLevel(), area, settlement, missingAfterReservation);

				Logging.logMessage(
						"Blueprint "
								+ area.getUniqueID()
								+ " became blocked while reserving materials: "
								+ missingAfterReservation
				);
			} else {
				sendConstructionMessage(
						job.getLevel(),
						area,
						settlement,
						"constructionmaterialsunavailable"
				);

				Logging.logMessage(
						"Blueprint "
								+ area.getUniqueID()
								+ " could not reserve required construction materials"
				);
			}

			return null;
		}

		if (area.clearConstructionBlockedReason()) {
			syncConstructionBlockedReason(job.getLevel(), area);
		}

		area.assignBuilder(worker);
		area.setBuilderMaterialAllocation(builderUniqueID, allocation);

		Logging.logMessage(
				"Builder "
						+ builderUniqueID
						+ " joined blueprint "
						+ area.getUniqueID()
						+ " with material allocation "
						+ allocation
		);

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "construction"),
				false
		);

		sequence.addAll(dumpJobs);

		for (SettlementStoragePickupSlot slot : pickupSlots) {
			sequence.add(slot.toPickupJob(worker, foundJob.priority));
		}

		sequence.add(job.getActiveJob(worker, foundJob.priority, sequence));

		return sequence;
	}


	private static Map<String, Integer> getBuilderMaterialAllocation(
			Level level, BlueprintArea area, int builderUniqueID
	) {
		List<String> orderedMaterials = area.getOrderedRemainingMaterialIDs(level);
		Map<String, Integer> alreadyAllocated = area.getAllocatedMaterialsExcept(builderUniqueID);

		List<InventoryItem> simulatedInventory = new ArrayList<>();
		Map<String, Integer> allocation = new LinkedHashMap<>();

		for (String itemID : orderedMaterials) {
			int allocatedAmount = alreadyAllocated.getOrDefault(itemID, 0);

			if (allocatedAmount > 0) {
				alreadyAllocated.put(itemID, allocatedAmount - 1);
				continue;
			}

			if (simulatedInventory.size() >= BuilderHumanMob.maxWorkInventoryStacks) {
				break;
			}

			InventoryItem item = new InventoryItem(itemID, 1);

			item.combineOrAddToList(level, null, simulatedInventory, "construction");

			allocation.merge(itemID, 1, Integer::sum);
		}

		return allocation;
	}

	private static boolean addCurrentInventoryDropOffJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			List<ActiveJob> jobs
	) {
		List<InventoryItem> currentItems = new ArrayList<>();

		for (InventoryItem item : worker.getWorkInventory().items()) {
			if (item != null && item.getAmount() > 0) {
				currentItems.add(item.copy());
			}
		}

		for (InventoryItem item : currentItems) {
			ArrayList<HasStorageLevelJob.DropOffFind> dropOffLocations =
					HasStorageLevelJob.findDropOffLocation(worker, item);

			int dropOffCapacity = 0;

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				dropOffCapacity += location.item.getAmount();
			}


			if (dropOffCapacity < item.getAmount()) {
				cancelPlannedJobs(jobs);
				return false;
			}

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				jobs.add(location.getActiveJob(worker, priority, null, false));
			}
		}

		return true;
	}

	private static void cancelPlannedJobs(List<ActiveJob> jobs) {
		for (ActiveJob job : jobs) {
			job.onCancelled(true, false, false);
		}

		jobs.clear();
	}

	private static List<SettlementStoragePickupSlot> reserveBuilderMaterials(
			EntityJobWorker worker,
			Map<String, Integer> allocation
	) {
		SettlementStorageRecords records = PickupSettlementStorageActiveJob.getStorageRecords(worker);

		if (records == null) {
			return null;
		}

		SettlementStorageItemIDIndex itemIndex = records.getIndex(SettlementStorageItemIDIndex.class);

		List<SettlementStoragePickupSlot> reserved = new ArrayList<>();

		for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
			int amount = entry.getValue();

			LinkedList<SettlementStoragePickupSlot> slots = itemIndex.findPickupSlots(
					entry.getKey(),
					worker,
					null,
					amount,
					amount
			);

			if (slots == null) {
				for (SettlementStoragePickupSlot slot : reserved) {
					if (!slot.isRemoved()) {
						slot.remove();
					}
				}

				return null;
			}

            reserved.addAll(slots);
		}

		return reserved;
	}

	private static void clearPreviousBuilderAssignment(Level level, int builderUniqueID) {
		for (BlueprintArea area : BlueprintAreaManager.get(level).getAreas()) {
			if (area.isBuilderAssigned(builderUniqueID)) {
				area.releaseBuilder(builderUniqueID);
			}
		}
	}

	private static void syncConstructionBlockedReason(
			Level level,
			BlueprintArea area
	) {
		if (!level.isServer()) {
			return;
		}

		level.getServer().network.sendToClientsAtEntireLevel(
				new PacketBlueprintBlockedState(
						area.getUniqueID(),
						area.getConstructionBlockedReason()
				),
				level
		);
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof BuilderHumanMob)) {
			return null;
		}

		BuilderHumanMob builder = (BuilderHumanMob)worker;

		return handler
				.setJobHandler(
						ConstructionLevelJob.class,
						foundJob -> getJobSequence(builder, foundJob)
				)
				.setPredicate(
						() ->
								!builder.isOnStrike()
										&& !builder.hasCompletedMission()
										&& (
										!builder.isSettler()
												|| builder.isSettlerWithinSettlement()
								)
				);
	}
}
