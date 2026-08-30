package opus.blueprint;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import opus.tools.BlueprintData;

import java.awt.*;
import java.util.UUID;

public class BlueprintArea {
	private final String uniqueID;

	private final int originX;
	private final int originY;

	private final int width;
	private final int height;

	private final BlueprintData blueprintData;

	private final int settlementUniqueID;

	public BlueprintArea(
			int settlementUniqueID,
			int originX,
			int originY,
			int width,
			int height,
			BlueprintData blueprintData
	) {
		this(
				UUID.randomUUID().toString(),
				settlementUniqueID,
				originX,
				originY,
				width,
				height,
				blueprintData
		);
	}

	private BlueprintArea(
		String uniqueID,
		int settlementUniqueID,
		int originX,
		int originY,
		int width,
		int height,
		BlueprintData blueprintData
	) {
		this.uniqueID = uniqueID;
		this.settlementUniqueID = settlementUniqueID;
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height = height;
		this.blueprintData = blueprintData;
	}

	public String getUniqueID() {
		return uniqueID;
	}

	public int getOriginX() {
		return originX;
	}

	public int getOriginY() {
		return originY;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getSettlementUniqueID() {
		return settlementUniqueID;
	}

	public int getEndX() {
		return originX + width - 1;
	}

	public int getEndY() {
		return originY + height - 1;
	}

	public BlueprintData getBlueprintData() {
		return blueprintData;
	}

	public boolean containsTile(int tileX, int tileY) {
		return tileX >= originX
			&& tileY >= originY
			&& tileX < originX + width
			&& tileY < originY + height;
	}

	public Rectangle getTileBounds() {
		return new Rectangle(
			originX,
			originY,
			width,
			height
		);
	}

	public SaveData getSaveData() {
		SaveData save = new SaveData("BLUEPRINT_AREA");

		save.addUnsafeString("uniqueID", uniqueID);
		save.addInt("settlementUniqueID", settlementUniqueID);

		save.addInt("originX", originX);
		save.addInt("originY", originY);

		save.addInt("width", width);
		save.addInt("height", height);

		save.addSafeString(
				"blueprintData",
				blueprintData.toJson()
		);

		return save;
	}

	public static BlueprintArea fromLoadData(LoadData load) {
		String uniqueID = load.getUnsafeString("uniqueID");
		int settlementUniqueID =
				load.getInt("settlementUniqueID",0,false);

		int originX = load.getInt("originX");
		int originY = load.getInt("originY");

		int width = load.getInt("width");
		int height = load.getInt("height");

		String json = load.getSafeString("blueprintData", null, false);

		if (json == null) {
			throw new IllegalStateException("Blueprint area is missing blueprintData");
		}

		BlueprintData blueprintData = BlueprintData.fromJson(json);

		return new BlueprintArea(
				uniqueID,
				settlementUniqueID,
				originX,
				originY,
				width,
				height,
				blueprintData
		);
	}
}