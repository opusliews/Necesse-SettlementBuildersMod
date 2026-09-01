package opus.blueprint;

import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.AirObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.multiTile.MultiTile;
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
	private String constructionBlockedReason;

	private boolean constructionComplete;

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
				false,
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
		boolean constructionStarted,
		boolean constructionComplete
	) {
		this.uniqueID = uniqueID;
		this.settlementUniqueID = settlementUniqueID;
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height = height;
		this.blueprintData = blueprintData;
		this.constructionStarted = constructionStarted;
		this.constructionComplete = constructionComplete;
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


	public BlueprintData getBlueprintData() {
		return blueprintData;
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

	public BlueprintTileTarget findFirstTileTarget(Level level) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null || element.getTileID() == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;
				GameTile wantedTile = TileRegistry.getTile(element.getTileID());

				if (level.getTileID(worldX, worldY) != wantedTile.getID()) {
					return new BlueprintTileTarget(worldX, worldY, element.getTileID());
				}
			}
		}

		return null;
	}

	public BlueprintObjectTarget findFirstObjectTarget(Level level) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (!isObjectPlacementElement(element)) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				if (!isObjectComplete(level, element, worldX, worldY)) {
					return new BlueprintObjectTarget(
							worldX,
							worldY,
							element.getObjectID(),
							element.getRotation()
					);
				}
			}
		}

		return null;
	}

	private boolean isBlueprintObjectComplete(
			Level level,
			BlueprintElement element,
			int worldX,
			int worldY
	) {
		if (element == null || element.getObjectID() == null) {
			return true;
		}

		GameObject wantedObject = ObjectRegistry.getObject(element.getObjectID());

		if (wantedObject == null) {
			return false;
		}

		if (wantedObject.isMultiTileMaster()) {
			return isObjectComplete(level, element, worldX, worldY);
		}

		Point masterPos = (Point)wantedObject
				.getMultiTile(element.getRotation())
				.getMasterTilePos(worldX, worldY)
				.orElse(null);

		if (masterPos == null) {
			return false;
		}

		int localMasterX = masterPos.x - originX;
		int localMasterY = masterPos.y - originY;

		BlueprintElement masterElement =
				blueprintData.getElementAt(localMasterX, localMasterY);

		return masterElement != null
				&& isObjectComplete(
				level,
				masterElement,
				masterPos.x,
				masterPos.y
		);
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

					if (!isBlueprintObjectComplete(level, element, worldX, worldY)) {
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


	public boolean setConstructionBlockedReason(String reason) {
		if (Objects.equals(constructionBlockedReason, reason)) {
			return false;
		}

		constructionBlockedReason = reason;
		return true;
	}

	public void clearConstructionBlockedReason() {
		constructionBlockedReason = null;
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
		save.addBoolean("constructionComplete", constructionComplete);

		if (!assignedBuilderIDs.isEmpty()) {
			SaveData buildersSave = new SaveData("BUILDERS");

			for (int builderUniqueID : assignedBuilderIDs) {
				SaveData builderSave = new SaveData("BUILDER");
				builderSave.addInt("uniqueID", builderUniqueID);

				Map<String, Integer> allocation = builderMaterialAllocations.get(builderUniqueID);

				if (allocation != null && !allocation.isEmpty()) {
					SaveData allocationSave = new SaveData("ALLOCATION");

					for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
						SaveData itemSave = new SaveData("ITEM");
						itemSave.addUnsafeString("itemID", entry.getKey());
						itemSave.addInt("amount", entry.getValue());
						allocationSave.addSaveData(itemSave);
					}

					builderSave.addSaveData(allocationSave);
				}

				buildersSave.addSaveData(builderSave);
			}

			save.addSaveData(buildersSave);
		}

		return save;
	}

	private void consumeBuilderMaterialAllocation(
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


	public BuilderHumanMob consumeBuilderMaterial(Level level, String itemID) {
		for (BuilderHumanMob builder : getAssignedBuilders(level)) {
			ListIterator<InventoryItem> iterator = builder.getWorkInventory().listIterator();

			while (iterator.hasNext()) {
				InventoryItem item = iterator.next();

				if (!item.item.getStringID().equals(itemID) || item.getAmount() <= 0) {
					continue;
				}

				item.setAmount(item.getAmount() - 1);

				if (item.getAmount() <= 0) {
					iterator.remove();
				}

				builder.getWorkInventory().markDirty();
				consumeBuilderMaterialAllocation(builder.getUniqueID(), itemID, 1);
				return builder;
			}
		}

		return null;
	}

	public int getAllocatedMaterialAmount(String itemID) {
		int amount = 0;

		for (Map<String, Integer> allocation : builderMaterialAllocations.values()) {
			amount += allocation.getOrDefault(itemID, 0);
		}

		return amount;
	}

	private boolean isObjectComplete(Level level, BlueprintElement element, int worldX, int worldY) {
		if (element == null || element.getObjectID() == null) {
			return true;
		}

		GameObject wantedObject = ObjectRegistry.getObject(element.getObjectID());

		if (wantedObject == null
				|| level.getObjectID(worldX, worldY) != wantedObject.getID()
				|| level.getObjectRotation(worldX, worldY) != element.getRotation()
		) {
			return false;
		}

		if (!wantedObject.isMultiTileMaster()) {
			return true;
		}

		for (Object valueObject : wantedObject.getMultiTile(element.getRotation()).getIDs(worldX, worldY)) {
			MultiTile.CoordinateValue value = (MultiTile.CoordinateValue)valueObject;

			if (level.getObjectID(value.tileX, value.tileY) != (Integer)value.value
					|| level.getObjectRotation(value.tileX, value.tileY) != element.getRotation()
			) {
				return false;
			}
		}

		return true;
	}

	private boolean isObjectPlacementElement(BlueprintElement element) {
		if (element == null || element.getObjectID() == null) {
			return false;
		}

		GameObject object = ObjectRegistry.getObject(element.getObjectID());
		return object != null && object.isMultiTileMaster();
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
					GameObject wantedObject = ObjectRegistry.getObject(wantedObjectID);

					if (wantedObject != null
							&& wantedObject.isMultiTileMaster()
							&& !isObjectComplete(level, element, worldX, worldY)
					) {
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

				if (!isObjectPlacementElement(element)) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				if (!isObjectComplete(level, element, worldX, worldY)) {
					materials.add(element.getObjectID());
				}
			}
		}

		return materials;
	}

	public boolean isConstructionComplete() {
		return constructionComplete;
	}

	public void setConstructionComplete(boolean constructionComplete) {
		this.constructionComplete = constructionComplete;
	}

	public boolean hasAssignedBuilders() {
		return !assignedBuilderIDs.isEmpty();
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
		boolean constructionComplete = load.getBoolean("constructionComplete", false, false);

		BlueprintArea area = new BlueprintArea(
				uniqueID,
				settlementUniqueID,
				originX,
				originY,
				width,
				height,
				blueprintData,
				constructionStarted,
				constructionComplete
		);

		LoadData buildersSave = load.getFirstLoadDataByName("BUILDERS");

		if (buildersSave != null) {
			for (Object builderObject : buildersSave.getLoadDataByName("BUILDER")) {
				LoadData builderLoad = (LoadData)builderObject;
				int builderUniqueID = builderLoad.getInt("uniqueID");

				area.assignedBuilderIDs.add(builderUniqueID);

				LoadData allocationSave = builderLoad.getFirstLoadDataByName("ALLOCATION");

				if (allocationSave != null) {
					Map<String, Integer> allocation = new LinkedHashMap<>();

					for (Object itemObject : allocationSave.getLoadDataByName("ITEM")) {
						LoadData itemLoad = (LoadData)itemObject;

						String itemID = itemLoad.getUnsafeString("itemID");
						int amount = itemLoad.getInt("amount");

						if (amount > 0) {
							allocation.put(itemID, amount);
						}
					}

					if (!allocation.isEmpty()) {
						area.builderMaterialAllocations.put(builderUniqueID, allocation);
					}
				}
			}
		}

		return area;
	}
}
