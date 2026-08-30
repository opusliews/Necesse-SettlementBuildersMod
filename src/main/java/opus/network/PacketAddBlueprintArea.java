package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.save.LoadData;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintArea;
import opus.blueprint.BlueprintAreaManager;
import opus.logging.Logging;

public class PacketAddBlueprintArea extends Packet {
	private final String areaSaveData;

	public PacketAddBlueprintArea(BlueprintArea area) {
		this.areaSaveData =
			area.getSaveData().getScript();

		PacketWriter writer =
			new PacketWriter(this);

		writer.putNextStringLong(areaSaveData);
	}

	public PacketAddBlueprintArea(byte[] data) {
		super(data);

		PacketReader reader =
			new PacketReader(this);

		this.areaSaveData = reader.getNextStringLong();
	}

	@Override
	public void processClient(
		NetworkPacket packet,
		Client client
	) {
		Level level = client.getLevel();

		if (level == null) {
			return;
		}

		LoadData load =
			new LoadData(areaSaveData);

		BlueprintArea area =
			BlueprintArea.fromLoadData(load);

		BlueprintAreaManager manager =
			BlueprintAreaManager.get(level);

		manager.addArea(area);

		Logging.logMessage(
			"CLIENT received blueprint area "
				+ area.getUniqueID()
				+ " at "
				+ area.getOriginX()
				+ ", "
				+ area.getOriginY()
				+ " areas="
				+ manager.size()
		);
	}
}