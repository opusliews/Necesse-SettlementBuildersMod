package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.util.GameMath;
import opus.hud.InspectionGlassHud;

import java.util.HashMap;
import java.util.Map;

public class PacketInspectionGlassData extends Packet {
	private final int startX;
	private final int startY;
	private final int endX;
	private final int endY;
	private final Map<Long, Integer> reinforcement;

	public PacketInspectionGlassData(
			int startX,
			int startY,
			int endX,
			int endY,
			Map<Long, Integer> reinforcement
	) {
		this.startX = startX;
		this.startY = startY;
		this.endX = endX;
		this.endY = endY;
		this.reinforcement = new HashMap<>(reinforcement);

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(startX);
		writer.putNextInt(startY);
		writer.putNextInt(endX);
		writer.putNextInt(endY);
		writer.putNextInt(this.reinforcement.size());

		for (Map.Entry<Long, Integer> entry : this.reinforcement.entrySet()) {
			writer.putNextInt(GameMath.getXFromUniqueLongKey(entry.getKey()));
			writer.putNextInt(GameMath.getYFromUniqueLongKey(entry.getKey()));
			writer.putNextByteUnsigned(Math.min(255, Math.max(0, entry.getValue())));
		}
	}

	public PacketInspectionGlassData(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		startX = reader.getNextInt();
		startY = reader.getNextInt();
		endX = reader.getNextInt();
		endY = reader.getNextInt();

		int count = reader.getNextInt();
		reinforcement = new HashMap<>();

		for (int i = 0; i < count; i++) {
			int tileX = reader.getNextInt();
			int tileY = reader.getNextInt();
			int value = reader.getNextByteUnsigned();
			reinforcement.put(GameMath.getUniqueLongKey(tileX, tileY), value);
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		if (client.getLevel() == null) {
			return;
		}

		InspectionGlassHud.applyServerData(
				client.getLevel(),
				startX,
				startY,
				endX,
				endY,
				reinforcement
		);
	}
}
