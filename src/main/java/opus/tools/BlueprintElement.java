package opus.tools;

import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.*;
import necesse.level.gameTile.*;

public class BlueprintElement {
	private final int x;
	private final int y;

	private String tileID;
	private String objectID;

	private int rotation;


	public boolean isEmpty() {
		return tileID == null && objectID == null;
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
	}

	public void setObjectID(String objectID) {
		this.objectID = objectID;
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
	}

	public static boolean isBlueprintObject(GameObject gameObject) {
		if (gameObject == null) {
			return false;
		}
		if (gameObject instanceof AirObject) {
			return false;
		}
		if (gameObject.toolType == ToolType.UNBREAKABLE) {
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
