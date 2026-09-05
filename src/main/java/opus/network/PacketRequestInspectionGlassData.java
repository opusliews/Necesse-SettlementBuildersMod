package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import opus.damage.WeatheringLevelData;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class PacketRequestInspectionGlassData extends Packet {
	private static final int inspectionRadius = 12;
	private static final long requestCooldownNanos = 250L * 1000000L;
	private static final Map<ServerClient, Long> nextAllowedRequestTimes =
			Collections.synchronizedMap(new WeakHashMap<>());

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

		InventoryItem selected = client.playerMob.getSelectedHotbarItem();

		if (selected == null || !"inspectionglass".equals(selected.item.getStringID())) {
			return;
		}

		long currentTime = System.nanoTime();

		synchronized (nextAllowedRequestTimes) {
			long nextAllowedTime = nextAllowedRequestTimes.getOrDefault(client, 0L);

			if (currentTime < nextAllowedTime) {
				return;
			}

			nextAllowedRequestTimes.put(client, currentTime + requestCooldownNanos);
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
