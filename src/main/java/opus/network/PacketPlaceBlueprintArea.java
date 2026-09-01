package opus.network;

import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketStatusMessage;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opus.blueprint.BlueprintArea;
import opus.blueprint.BlueprintAreaManager;
import opus.item.BlueprintItem;
import opus.jobs.ConstructionLevelJob;
import opus.logging.Logging;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;

import java.awt.*;

public class PacketPlaceBlueprintArea extends Packet {
	private final int originX;
	private final int originY;

	private final int inventoryID;
	private final int slotIndex;

	public PacketPlaceBlueprintArea(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);

		this.originX = reader.getNextInt();
		this.originY = reader.getNextInt();

		this.inventoryID = reader.getNextInt();
		this.slotIndex = reader.getNextInt();
	}

	public PacketPlaceBlueprintArea(
			int originX,
			int originY,
			int inventoryID,
			int slotIndex
	) {
		this.originX = originX;
		this.originY = originY;

		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;

		PacketWriter writer = new PacketWriter(this);

		writer.putNextInt(originX);
		writer.putNextInt(originY);

		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
	}

	private boolean isEntirelyInsideSettlement(
			ServerSettlementData settlement,
			int originX,
			int originY,
			int width,
			int height
	) {
		int endX = originX + width - 1;
		int endY = originY + height - 1;

		return settlement.boundsManager.isTileWithinBounds(
				originX,
				originY
		)
				&& settlement.boundsManager.isTileWithinBounds(
				endX,
				originY
		)
				&& settlement.boundsManager.isTileWithinBounds(
				originX,
				endY
		)
				&& settlement.boundsManager.isTileWithinBounds(
				endX,
				endY
		);
	}

	private boolean overlapsExistingArea(
			BlueprintAreaManager manager,
			int originX,
			int originY,
			int width,
			int height
	) {
		Rectangle bounds = new Rectangle(
				originX,
				originY,
				width,
				height
		);

		for (BlueprintArea area : manager.getAreas()) {
			if (area.isConstructionComplete()) {
				continue;
			}

			if (bounds.intersects(area.getTileBounds())) {
				return true;
			}
		}

		return false;
	}

	private boolean hasInvalidFloatingObjects(
			Level level,
			int originX,
			int originY,
			BlueprintData blueprintData
	) {
		for (BlueprintElement element : blueprintData.getElements()) {
			String objectID = element.getObjectID();

			if (objectID == null) {
				continue;
			}

			GameObject object = ObjectRegistry.getObject(objectID);

			if (object == null) {
				continue;
			}

			int tileX = originX + element.getX();
			int tileY = originY + element.getY();

			// The object itself is allowed on liquid.
			if (object.canPlaceOnLiquid) {
				continue;
			}

			/*
			 * If this blueprint cell also contains a tile,
			 * that tile will be built before the object.
			 *
			 * Blueprint tiles currently cannot be liquid tiles,
			 * so this provides the required solid surface.
			 */
			if (element.getTileID() != null) {
				continue;
			}

			/*
			 * Mirror GameObject.canPlace's liquid rule.
			 */
			if (level.isLiquidTile(tileX, tileY)
					&& !level.getTile(tileX, tileY).overridesCannotPlaceOnLiquid
			) {
				return true;
			}
		}

		return false;
	}

	private boolean hasUnbreakableObject(Level level, int originX, int originY, BlueprintData blueprintData) {
		for (int y = 0; y < blueprintData.getHeight(); y++) {
			for (int x = 0; x < blueprintData.getWidth(); x++) {
				int tileX = originX + x;
				int tileY = originY + y;

				for (int layerID : ObjectLayerRegistry.getLayerIDs()) {
					GameObject object = level.getObject(layerID, tileX, tileY);

					if (object.toolType == ToolType.UNBREAKABLE) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private void clientErrorMessage(ServerClient client, String category, String translationKey, int seconds) {
		client.sendPacket(
				new PacketStatusMessage(
						Localization.translate(category,translationKey), Color.RED,seconds
				)
		);
	}


	@Override
	public void processServer(
			NetworkPacket packet,
			Server server,
			ServerClient client
	) {
		Level level = client.getLevel();

		if (level == null) {
			return;
		}

		PlayerInventorySlot playerSlot =
				new PlayerInventorySlot(
						inventoryID,
						slotIndex
				);

		InventoryItem item = playerSlot.getItem(client.playerMob.getInv());

		if (item == null || !(item.item instanceof BlueprintItem)) {
			return;
		}

		BlueprintItem blueprintItem = (BlueprintItem)item.item;

		if (!blueprintItem.hasBlueprint(item)) {
			return;
		}

		BlueprintData blueprintData;

		try {
			blueprintData = blueprintItem.getBlueprintData(item);
		} catch (Exception e) {
			Logging.logMessage(
					"SERVER rejected blueprint placement: "
							+ "could not read blueprint data"
			);

			return;
		}

		if (blueprintData == null) {
			return;
		}

		SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(server);
		ServerSettlementData settlement =
				settlementsData.getOrLoadServerDataAtTile(
						level.getIdentifier(),
						originX,
						originY
				);

		if (settlement == null) {
			clientErrorMessage(client, "misc", "blueprintplaceoutsidesettlement1", 5);
			return;
		}

		if (!settlement.networkData.isClientPartOf(client)) {
			clientErrorMessage(client, "misc", "blueprintplaceothersettlement", 5);

			return;
		}

		if (!isEntirelyInsideSettlement(
				settlement, originX, originY, blueprintData.getWidth(), blueprintData.getHeight())) {
			clientErrorMessage(client, "misc", "blueprintplaceoutsidesettlement2", 5);
			return;
		}

		if (hasUnbreakableObject(
				level, originX, originY,blueprintData)) {
			clientErrorMessage(client, "misc", "blueprintplaceunbreakable", 5);
			return;
		}

		if (hasInvalidFloatingObjects(level, originX, originY, blueprintData)) {
			clientErrorMessage(client, "misc", "blueprintplacefluid", 20);
			return;
		}

		BlueprintAreaManager manager = BlueprintAreaManager.get(level);

		if (overlapsExistingArea(
				manager, originX, originY, blueprintData.getWidth(), blueprintData.getHeight())) {
			clientErrorMessage(client, "misc", "blueprintplaceoverlap", 5);
			return;
		}

		BlueprintArea area =
				manager.addArea(
						settlement.uniqueID,
						originX,
						originY,
						blueprintData.getWidth(),
						blueprintData.getHeight(),
						blueprintData
				);

		for (Point workTile : area.getOutsideBorderTiles()) {
			if (level.isSolidTile(workTile.x, workTile.y)) {
				continue;
			}

			level.jobsLayer.addJob(
					new ConstructionLevelJob(workTile.x, workTile.y, area.getUniqueID()));
		}

		Logging.logMessage(
				"SERVER placed blueprint area "
						+ area.getUniqueID()
						+ " at "
						+ originX
						+ ", "
						+ originY
						+ " areas="
						+ manager.size()
		);

		server.network.sendToClientsAtEntireLevel(
				new PacketAddBlueprintArea(area),
				level
		);
	}
}
