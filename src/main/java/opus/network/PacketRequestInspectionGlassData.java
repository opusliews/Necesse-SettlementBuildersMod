package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import opus.damage.WeatheringLevelData;

import java.util.Collections;
import java.util.Map;

public class PacketRequestInspectionGlassData extends Packet {
	private static final int inspectionRadius = 12;

	public PacketRequestInspectionGlassData() {
	}

	public PacketRequestInspectionGlassData(byte[] data) {
		super(data);
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		Level level = client.getLevel();

		if (level == null || client.playerMob == null) {
			return;
		}

		int centerX = client.playerMob.getTileX();
		int centerY = client.playerMob.getTileY();

		WeatheringLevelData weathering = WeatheringLevelData.get(level, false);

		Map<Long, Integer> reinforcement = weathering == null
				? Collections.emptyMap()
				: weathering.getReinforcementInCircle(
				centerX,
				centerY,
				inspectionRadius
		);

		client.sendPacket(
				new PacketInspectionGlassData(
						centerX - inspectionRadius,
						centerY - inspectionRadius,
						centerX + inspectionRadius,
						centerY + inspectionRadius,
						reinforcement
				)
		);
	}
}