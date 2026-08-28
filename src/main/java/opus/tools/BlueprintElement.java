package opus.tools;

import necesse.level.gameObject.*;
import necesse.level.gameTile.*;

public class BlueprintElement {
	private final int x;
	private final int y;

	private String tileID;
	private String objectID;

	private int rotation;

	private boolean empty;

	public boolean isEmpty() {
		return empty;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public String getTileID() {
		return tileID;
	}

	public String getObjectID() {
		return objectID;
	}


	public void setTileID(String tileID) {
		this.tileID = tileID;
		this.empty = false;
	}

	public void setObjectID(String objectID) {
		this.objectID = objectID;
		this.empty = false;
	}

	public int getRotation() {
		return rotation;
	}

	public void setRotation(int rotation) {
		this.rotation = rotation;
	}

	public BlueprintElement(int x, int y) {
		this.x = x;
		this.y = y;
		this.empty = true;
	}

	public static boolean isBlueprintObject(GameObject gameObject) {
		if (gameObject == null) {
			return false;
		}

		if (gameObject instanceof AirObject) {
			return false;
		}

		if (gameObject instanceof InvisibleTriggerObject) {
			return false;
		}

		if (gameObject instanceof DungeonEntranceObject
				|| gameObject instanceof DungeonExitObject
				|| gameObject instanceof TrialEntranceObject) {
			return false;
		}

		return true;
	}

	public static boolean isBlueprintTile(GameTile gameTile) {
		if (gameTile == null) {
			return false;
		}

		if (gameTile instanceof EmptyTile) {
			return false;
		}

		// Water/lava/etc. are not normal tile placement.
		if (gameTile instanceof LiquidTile) {
			return false;
		}

		// What the fuck even is this
		if (gameTile instanceof ChromaKeyTile) {
			return false;
		}

		return true;
	}
}