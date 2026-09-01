package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.save.LoadData;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintAreaManager;

public class PacketSyncBlueprintAreas extends Packet {
	private final String saveData;

	public PacketSyncBlueprintAreas(
		BlueprintAreaManager manager
	) {
		this.saveData =
			manager.getSaveData().getScript();

		PacketWriter writer =
			new PacketWriter(this);

		writer.putNextStringLong(saveData);
	}

	public PacketSyncBlueprintAreas(byte[] data) {
		super(data);

		PacketReader reader =
			new PacketReader(this);

		this.saveData = reader.getNextStringLong();
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

		BlueprintAreaManager manager =
			BlueprintAreaManager.get(level);

		LoadData load =
			new LoadData(saveData);

		manager.applyLoadData(load);
	}
}
