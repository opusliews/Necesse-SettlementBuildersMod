package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ObjectRegistry;

public class PacketBuilderObjectPlaceSound extends Packet {
	private final int objectID;
	private final int tileX;
	private final int tileY;

	public PacketBuilderObjectPlaceSound(int objectID, int tileX, int tileY) {
		this.objectID = objectID;
		this.tileX = tileX;
		this.tileY = tileY;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(objectID);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	public PacketBuilderObjectPlaceSound(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		objectID = reader.getNextInt();
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		ObjectRegistry.getObject(objectID).playPlaceSound(tileX, tileY);
	}
}
