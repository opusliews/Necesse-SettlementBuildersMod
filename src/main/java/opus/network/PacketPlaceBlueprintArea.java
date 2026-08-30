package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintArea;
import opus.blueprint.BlueprintAreaManager;
import opus.item.BlueprintItem;
import opus.logging.Logging;
import opus.tools.BlueprintData;

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

		BlueprintAreaManager manager = BlueprintAreaManager.get(level);

		BlueprintArea area =
				manager.addArea(
						originX,
						originY,
						blueprintData.getWidth(),
						blueprintData.getHeight(),
						blueprintData
				);

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