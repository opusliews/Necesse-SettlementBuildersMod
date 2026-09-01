package opus.blueprint;

import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.AirObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import opus.mobs.BuilderHumanMob;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;

import java.awt.*;
import java.util.List;
import java.util.*;

public class BlueprintArea {
	private final String uniqueID;

	private final int originX;
	private final int originY;

	private final int width;
	private final int height;

	private final BlueprintData blueprintData;

	private final int settlementUniqueID;

	private boolean constructionStarted;
	private boolean materialsBlocked;

	private final Map<Integer, Map<String, Integer>> builderMaterialAllocations = new HashMap<>();

	private final Set<Integer> assignedBuilderIDs = new HashSet<>();

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
				blueprintData,
				false
		);
	}

	private BlueprintArea(
		String uniqueID,
		int settlementUniqueID,
		int originX,
		int originY,
		int width,
		int height,
		BlueprintData blueprintData,
		boolean constructionStarted
	) {
		this.uniqueID = uniqueID;
		this.settlementUniqueID = settlementUniqueID;
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height = height;
		this.blueprintData = blueprintData;
		this.constructionStarted = constructionStarted;
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

	public void assignBuilder(BuilderHumanMob builder) {
		assignedBuilderIDs.add(builder.getUniqueID());
	}

	public void releaseBuilder(int builderUniqueID) {
		assignedBuilderIDs.remove(builderUniqueID);
		clearBuilderMaterialAllocation(builderUniqueID);
	}

	public boolean isBuilderAssigned(int builderUniqueID) {
		return assignedBuilderIDs.contains(builderUniqueID);
	}

	public List<BuilderHumanMob> getAssignedBuilders(Level level) {
		List<BuilderHumanMob> builders = new ArrayList<>();

		for (int uniqueID : assignedBuilderIDs) {
			BuilderHumanMob mob = (BuilderHumanMob)level.entityManager.mobs.get(uniqueID, false);
			if (mob != null)
				builders.add(mob);
		}

		return builders;
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

	public List<Point> getOutsideBorderTiles() {
		List<Point> tiles = new ArrayList<>();

		int left = originX - 1;
		int right = originX + width;
		int top = originY - 1;
		int bottom = originY + height;

		for (int x = left; x <= right; x++) {
			tiles.add(new Point(x, top));
			tiles.add(new Point(x, bottom));
		}

		for (int y = originY; y < originY + height; y++) {
			tiles.add(new Point(left, y));

			tiles.add(new Point(right, y));
		}

		return tiles;
	}

	public BlueprintClearTarget findFirstClearTarget(Level level) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int worldX = originX + x;
				int worldY = originY + y;

				BlueprintElement element = blueprintData.getElementAt(x, y);

				String wantedTileID = element == null ? null : element.getTileID();

				if (wantedTileID != null) {
					GameTile currentTile = level.getTile(worldX, worldY);
					GameTile wantedTile = TileRegistry.getTile(wantedTileID);

					if (currentTile.getID() != wantedTile.getID() && !wantedTile.canReplace(level, worldX, worldY)) {
						return new BlueprintClearTarget(BlueprintClearTarget.Type.TILE, worldX, worldY);
					}
				}

				GameObject currentObject = level.getObject(worldX, worldY);

				if (!(currentObject instanceof AirObject)) {
					String wantedObjectID = element == null ? null : element.getObjectID();

					if (wantedObjectID == null) {
						return new BlueprintClearTarget(BlueprintClearTarget.Type.OBJECT, worldX, worldY);
					}

					if (!currentObject.getStringID().equals(wantedObjectID)) {
						return new BlueprintClearTarget(BlueprintClearTarget.Type.OBJECT, worldX, worldY);
					}

					if (level.getObjectRotation(worldX, worldY) != element.getRotation()) {
						return new BlueprintClearTarget(BlueprintClearTarget.Type.OBJECT, worldX, worldY);
					}
				}
			}
		}

		return null;
	}

	public boolean hasConstructionStarted() {
		return constructionStarted;
	}

	public void setConstructionStarted(boolean constructionStarted) {
		this.constructionStarted = constructionStarted;
	}

	public boolean isMaterialsBlocked() {
		return materialsBlocked;
	}

	public void setMaterialsBlocked(boolean materialsBlocked) {
		this.materialsBlocked = materialsBlocked;
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

		save.addBoolean("constructionStarted", constructionStarted);

		return save;
	}

	public void consumeBuilderMaterialAllocation(
			int builderUniqueID,
			String itemID,
			int amount
	) {
		Map<String, Integer> allocation = builderMaterialAllocations.get(builderUniqueID);

		if (allocation == null) {
			return;
		}

		int remaining = allocation.getOrDefault(itemID, 0) - amount;

		if (remaining > 0) {
			allocation.put(itemID, remaining);
		} else {
			allocation.remove(itemID);
		}

		if (allocation.isEmpty()) {
			builderMaterialAllocations.remove(builderUniqueID);
		}
	}


	public BuilderMaterialSource findBuilderMaterial(Level level, String itemID) {
		for (BuilderHumanMob builder : getAssignedBuilders(level)) {
			ListIterator<InventoryItem> iterator = builder.getWorkInventory().listIterator();

			while (iterator.hasNext()) {
				InventoryItem item = iterator.next();

				if (item.item.getStringID().equals(itemID) && item.getAmount() > 0) {
					return new BuilderMaterialSource(builder, item);
				}
			}
		}

		return null;
	}

	public Map<String, Integer> getRequiredMaterials(Level level) {
		Map<String, Integer> required = new LinkedHashMap<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				String wantedTileID = element.getTileID();

				if (wantedTileID != null) {
					GameTile currentTile = level.getTile(worldX, worldY);
					GameTile wantedTile = TileRegistry.getTile(wantedTileID);

					if (currentTile.getID() != wantedTile.getID()) {
						required.merge(wantedTileID, 1, Integer::sum);
					}
				}

				String wantedObjectID = element.getObjectID();

				if (wantedObjectID != null) {
					GameObject currentObject = level.getObject(worldX, worldY);

					boolean correctObject =
							currentObject.getStringID().equals(wantedObjectID)
									&& level.getObjectRotation(worldX, worldY) == element.getRotation();

					if (!correctObject) {
						required.merge(wantedObjectID, 1, Integer::sum);
					}
				}
			}
		}

		return required;
	}

	public void setBuilderMaterialAllocation(int builderUniqueID, Map<String, Integer> allocation) {
		if (allocation.isEmpty()) {
			clearBuilderMaterialAllocation(builderUniqueID);
		} else {
			builderMaterialAllocations.put(builderUniqueID, new LinkedHashMap<>(allocation));
		}
	}

	public void clearBuilderMaterialAllocation(int builderUniqueID) {
		builderMaterialAllocations.remove(builderUniqueID);
	}

	public Map<String, Integer> getAllocatedMaterialsExcept(int builderUniqueID) {
		Map<String, Integer> allocated = new HashMap<>();

		for (Map.Entry<Integer, Map<String, Integer>> builderEntry : builderMaterialAllocations.entrySet()) {
			if (builderEntry.getKey() == builderUniqueID) {
				continue;
			}

			for (Map.Entry<String, Integer> materialEntry : builderEntry.getValue().entrySet()) {
				allocated.merge(materialEntry.getKey(), materialEntry.getValue(), Integer::sum);
			}
		}

		return allocated;
	}

	public List<String> getOrderedRemainingMaterialIDs(Level level) {
		List<String> materials = new ArrayList<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null || element.getTileID() == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				GameTile currentTile = level.getTile(worldX, worldY);
				GameTile wantedTile = TileRegistry.getTile(element.getTileID());

				if (currentTile.getID() != wantedTile.getID()) {
					materials.add(element.getTileID());
				}
			}
		}

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null || element.getObjectID() == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				GameObject currentObject = level.getObject(worldX, worldY);

				boolean correct =
						currentObject.getStringID().equals(element.getObjectID())
								&& level.getObjectRotation(worldX, worldY) == element.getRotation();

				if (!correct) {
					materials.add(element.getObjectID());
				}
			}
		}

		return materials;
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

		boolean constructionStarted = load.getBoolean("constructionStarted", false, false);

		return new BlueprintArea(
				uniqueID,
				settlementUniqueID,
				originX,
				originY,
				width,
				height,
				blueprintData,
				constructionStarted
		);
	}
}