package opus.blueprint;

import necesse.engine.network.client.Client;
import necesse.level.maps.Level;
import opus.network.PacketRequestBlueprintAreas;

public class BlueprintAreaSync {
	private static Level lastLevel;

	public static void frameTick(Client client) {
		if (client == null) {
			lastLevel = null;
			return;
		}

		Level level = client.getLevel();

		if (level == null) {
			lastLevel = null;
			return;
		}

		if (level == lastLevel) {
			return;
		}

		lastLevel = level;

		client.network.sendPacket(
			new PacketRequestBlueprintAreas()
		);

	}
}
