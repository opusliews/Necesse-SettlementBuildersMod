package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.Mob;
import necesse.level.maps.Level;
import opus.mobs.BuilderHumanMob;

public class PacketBuilderRoadRepairToggle extends Packet {
	private final int builderUniqueID;
	private final boolean enabled;

	public PacketBuilderRoadRepairToggle(int builderUniqueID, boolean enabled) {
		this.builderUniqueID = builderUniqueID;
		this.enabled = enabled;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(builderUniqueID);
		writer.putNextBoolean(enabled);
	}

	public PacketBuilderRoadRepairToggle(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		builderUniqueID = reader.getNextInt();
		enabled = reader.getNextBoolean();
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

		Mob mob = GameUtils.getLevelMob(builderUniqueID, level);

		if (!(mob instanceof BuilderHumanMob)) {
			return;
		}

		BuilderHumanMob builder = (BuilderHumanMob)mob;

		if (builder.adventureParty.getServerClient() != client) {
			return;
		}

		builder.setRepairOnRoad(enabled);
	}
}