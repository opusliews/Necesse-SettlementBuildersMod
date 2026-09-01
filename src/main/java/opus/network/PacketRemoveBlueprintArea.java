package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintAreaManager;

public class PacketRemoveBlueprintArea extends Packet {
	private final String uniqueID;

	public PacketRemoveBlueprintArea(String uniqueID) {
		this.uniqueID = uniqueID;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextString(uniqueID);
	}

	public PacketRemoveBlueprintArea(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		uniqueID = reader.getNextString();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();

		if (level == null) {
			return;
		}

		BlueprintAreaManager.get(level).removeArea(uniqueID);
	}
}
