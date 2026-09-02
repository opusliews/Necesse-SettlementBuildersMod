package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintAreaManager;
import opus.damage.HardcoreDamage;

public class PacketRequestBlueprintAreas extends Packet {
	public PacketRequestBlueprintAreas() {
	}

	public PacketRequestBlueprintAreas(byte[] data) {
		super(data);
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

		BlueprintAreaManager manager = BlueprintAreaManager.get(level);

		// TODO: Move this from PacketRequestBlueprintAreas into a more appropriate location
		client.sendPacket(new PacketHardcoreDamageSetting(HardcoreDamage.isServerEnabled()));
		client.sendPacket(
			new PacketSyncBlueprintAreas(manager)
		);
	}
}
