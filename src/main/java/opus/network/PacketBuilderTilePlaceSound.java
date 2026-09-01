package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.registries.TileRegistry;

public class PacketBuilderTilePlaceSound extends Packet {
	private final int tileID;
	private final int tileX;
	private final int tileY;

	public PacketBuilderTilePlaceSound(int tileID, int tileX, int tileY) {
		this.tileID = tileID;
		this.tileX = tileX;
		this.tileY = tileY;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileID);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	public PacketBuilderTilePlaceSound(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		tileID = reader.getNextInt();
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		TileRegistry.getTile(tileID).playPlaceSound(tileX, tileY);
	}
}
