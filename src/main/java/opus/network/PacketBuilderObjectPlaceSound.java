package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameRandom;
import necesse.entity.particle.Particle;
import necesse.gfx.GameResources;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;

import java.awt.Color;

public class PacketBuilderObjectPlaceSound extends Packet {
	private final int objectID;
	private final int tileX;
	private final int tileY;
	private final boolean repairEffect;

	public PacketBuilderObjectPlaceSound(int objectID, int tileX, int tileY) {
		this.objectID = objectID;
		this.tileX = tileX;
		this.tileY = tileY;
		this.repairEffect = false;
		writePacket();
	}

	public PacketBuilderObjectPlaceSound(int tileX, int tileY) {
		this.objectID = -1;
		this.tileX = tileX;
		this.tileY = tileY;
		this.repairEffect = true;
		writePacket();
	}

	public PacketBuilderObjectPlaceSound(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		objectID = reader.getNextInt();
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		repairEffect = reader.getNextBoolean();
	}

	private void writePacket() {
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(objectID);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextBoolean(repairEffect);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		if (!repairEffect) {
			ObjectRegistry.getObject(objectID).playPlaceSound(tileX, tileY);
			return;
		}

		Level level = client.getLevel();

		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			return;
		}

		SoundManager.playSound(
				GameResources.tap,
				SoundEffect.effect(tileX * 32 + 16, tileY * 32 + 16)
		);

		GameObject object = level.getObject(tileX, tileY);
		GameTile tile = level.getTile(tileX, tileY);
		Color debrisColor = object != null && object.getID() != 0
				? object.getDebrisColor(level, tileX, tileY)
				: tile.getDebrisColor(level, tileX, tileY);

		for (int i = 0; i < 30; i++) {
			float x = tileX * 32 + 16 + GameRandom.globalRandom.getFloatBetween(-8.0F, 8.0F);
			float y = tileY * 32 + 16 + GameRandom.globalRandom.getFloatBetween(-6.0F, 6.0F);
			float dx = GameRandom.globalRandom.getFloatBetween(-25.0F, 25.0F);
			float dy = GameRandom.globalRandom.getFloatBetween(-15.0F, 15.0F);

			level.entityManager.addParticle(x, y, Particle.GType.COSMETIC)
					.sprite(GameResources.debrisParticles.sprite(GameRandom.globalRandom.nextInt(6), 0, 20))
					.color(debrisColor)
					.sizeFadesInAndOut(7, 11, 0, 120)
					.movesFriction(dx, dy, 0.8F)
					.heightMoves(8.0F, 20.0F)
					.lifeTime(500);
		}
	}
}
