package opus.network;

import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketStatusMessage;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opus.blueprint.BlueprintArea;
import opus.blueprint.BlueprintAreaManager;
import opus.item.ProjectEraserItem;
import opus.jobs.ConstructionLevelJob;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class PacketEraseBlueprintProject extends Packet {
	private final int tileX;
	private final int tileY;
	private final int inventoryID;
	private final int slotIndex;

	public PacketEraseBlueprintProject(
			int tileX,
			int tileY,
			int inventoryID,
			int slotIndex
	) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
	}

	public PacketEraseBlueprintProject(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		inventoryID = reader.getNextInt();
		slotIndex = reader.getNextInt();
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

		PlayerInventorySlot playerSlot = new PlayerInventorySlot(
				inventoryID,
				slotIndex
		);

		InventoryItem item = playerSlot.getItem(client.playerMob.getInv());

		if (item == null || !(item.item instanceof ProjectEraserItem)) {
			return;
		}

		BlueprintAreaManager manager = BlueprintAreaManager.get(level);
		BlueprintArea area = manager.getAreaAtTile(tileX, tileY);

		if (area == null) {
			return;
		}

		ServerSettlementData settlement = SettlementsWorldData
				.getSettlementsData(server)
				.getServerData(area.getSettlementUniqueID());

		if (settlement == null || !settlement.networkData.isClientPartOf(client)) {
			client.sendPacket(
					new PacketStatusMessage(
							Localization.translate(
									"misc",
									"projecteraseothersettlement"
							),
							Color.RED,
							5
					)
			);

			return;
		}

		removeConstructionJobs(level, area);

		String uniqueID = area.getUniqueID();
		manager.removeArea(uniqueID);

		server.network.sendToClientsAtEntireLevel(
				new PacketRemoveBlueprintArea(uniqueID),
				level
		);

		client.sendPacket(
				new PacketStatusMessage(
						Localization.translate(
								"misc",
								"projecterased"
						),
						Color.GREEN,
						3
				)
		);
	}

	private void removeConstructionJobs(
			Level level,
			BlueprintArea area
	) {
		for (Point workTile : area.getOutsideBorderTiles()) {
			List<ConstructionLevelJob> jobs = level.jobsLayer
					.streamJobsInTile(workTile.x, workTile.y)
					.filter(job -> job instanceof ConstructionLevelJob)
					.map(job -> (ConstructionLevelJob)job)
					.filter(job -> area.getUniqueID().equals(job.getBlueprintAreaUniqueID()))
					.collect(Collectors.toList());

			for (ConstructionLevelJob job : jobs) {
				job.remove();
			}
		}
	}
}