package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opus.blueprint.BlueprintAreaManager;

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
		BlueprintAreaManager manager =
			BlueprintAreaManager.get(
				client.getLevel()
			);

		client.sendPacket(
			new PacketSyncBlueprintAreas(manager)
		);
	}
}