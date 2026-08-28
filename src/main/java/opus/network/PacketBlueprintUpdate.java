package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import opus.item.BlueprintItem;
import opus.tools.BlueprintData;

public class PacketBlueprintUpdate extends Packet {
	private final int inventoryID;
	private final int slotIndex;
	private final boolean clear;
	private final String blueprintName;
	private final String blueprintJson;

	public PacketBlueprintUpdate(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);

		this.inventoryID = reader.getNextInt();
		this.slotIndex = reader.getNextInt();
		this.clear = reader.getNextBoolean();

		if (clear) {
			this.blueprintName = "";
			this.blueprintJson = "";
		} else {
			this.blueprintName = reader.getNextString();
			this.blueprintJson = reader.getNextString();
		}
	}

	public PacketBlueprintUpdate(
		int inventoryID,
		int slotIndex,
		String blueprintName,
		BlueprintData blueprintData
	) {
		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;
		this.clear = false;
		this.blueprintName = blueprintName;
		this.blueprintJson = blueprintData.toJson();

		PacketWriter writer = new PacketWriter(this);

		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
		writer.putNextBoolean(false);
		writer.putNextString(blueprintName);
		writer.putNextString(this.blueprintJson);
	}

	public PacketBlueprintUpdate(
		int inventoryID,
		int slotIndex
	) {
		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;
		this.clear = true;
		this.blueprintName = "";
		this.blueprintJson = "";

		PacketWriter writer = new PacketWriter(this);

		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
		writer.putNextBoolean(true);
	}

	@Override
	public void processServer(
		NetworkPacket packet,
		Server server,
		ServerClient client
	) {
		PlayerInventorySlot playerSlot = new PlayerInventorySlot(
			inventoryID,
			slotIndex
		);

		InventoryItem item = playerSlot.getItem(
			client.playerMob.getInv()
		);

		if (item == null || !(item.item instanceof BlueprintItem)) {
			return;
		}

		BlueprintItem blueprintItem = (BlueprintItem)item.item;

		if (clear) {
			blueprintItem.clearBlueprint(item);
		} else {
			BlueprintData blueprintData;

			try {
				blueprintData = BlueprintData.fromJson(blueprintJson);
			} catch (Exception e) {
				return;
			}

			blueprintItem.setBlueprint(
				item,
				blueprintName,
				blueprintData
			);
		}

		playerSlot.setItem(
			client.playerMob.getInv(),
			item
		);

		playerSlot.markDirty(
			client.playerMob.getInv()
		);
	}
}