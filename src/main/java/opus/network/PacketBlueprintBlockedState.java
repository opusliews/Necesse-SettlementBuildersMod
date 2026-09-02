package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintArea;
import opus.blueprint.BlueprintAreaManager;

public class PacketBlueprintBlockedState extends Packet {
	private final String uniqueID;
	private final String blockedReason;

	public PacketBlueprintBlockedState(String uniqueID, String blockedReason) {
		this.uniqueID = uniqueID;
		this.blockedReason = blockedReason;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextString(uniqueID);
		writer.putNextBoolean(blockedReason != null);

		if (blockedReason != null) {
			writer.putNextString(blockedReason);
		}
	}

	public PacketBlueprintBlockedState(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		uniqueID = reader.getNextString();

		if (reader.getNextBoolean()) {
			blockedReason = reader.getNextString();
		} else {
			blockedReason = null;
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();

		if (level == null) {
			return;
		}

		BlueprintArea area = BlueprintAreaManager.get(level).getArea(uniqueID);

		if (area != null) {
			area.setConstructionBlockedReason(blockedReason);
		}
	}
}