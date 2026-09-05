package opus.blueprint;

import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.LogicGateRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.level.gameLogicGate.GameLogicGate;
import necesse.level.gameLogicGate.entities.LogicGateEntity;
import necesse.level.maps.Level;
import necesse.level.maps.presets.PresetRotation;
import necesse.level.maps.wireManager.WireManager;
import opus.logging.Logging;
import opus.tools.BlueprintElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class BlueprintInfrastructureSupport {
	private static final String wireItemID = "wire";
	private static final Map<BlueprintArea, Map<Long, String>> configuredLogicGates =
			Collections.synchronizedMap(new WeakHashMap<>());

	private BlueprintInfrastructureSupport() {
	}

	public static void captureElement(Level level, int tileX, int tileY, BlueprintElement element) {
		element.setWireMask(getWireMask(level, tileX, tileY));

		GameLogicGate gate = level.logicLayer.getLogicGate(tileX, tileY);
		LogicGateEntity entity = level.logicLayer.getEntity(tileX, tileY);

		if (gate == null || entity == null || !isObtainableLogicGate(gate.getStringID())) {
			return;
		}

		element.setLogicGateID(gate.getStringID());
		element.setLogicGateData(captureLogicGateData(entity));
		element.setLogicGateRotation(0);
	}

	public static BlueprintWireTarget findFirstWireTarget(BlueprintArea area, Level level) {
		for (int y = 0; y < area.getHeight(); y++) {
			for (int x = 0; x < area.getWidth(); x++) {
				BlueprintElement element = area.getBlueprintData().getElementAt(x, y);
				int desiredMask = element == null ? 0 : element.getWireMask();
				int worldX = area.getOriginX() + x;
				int worldY = area.getOriginY() + y;
				int currentMask = getWireMask(level, worldX, worldY);

				if (desiredMask != currentMask) {
					return new BlueprintWireTarget(worldX, worldY, desiredMask, currentMask);
				}
			}
		}

		return null;
	}

	public static BlueprintLogicGateTarget findFirstLogicGateTarget(BlueprintArea area, Level level) {
		for (int y = 0; y < area.getHeight(); y++) {
			for (int x = 0; x < area.getWidth(); x++) {
				BlueprintElement element = area.getBlueprintData().getElementAt(x, y);
				String desiredID = element == null ? null : element.getLogicGateID();
				int worldX = area.getOriginX() + x;
				int worldY = area.getOriginY() + y;
				GameLogicGate currentGate = level.logicLayer.getLogicGate(worldX, worldY);
				LogicGateEntity currentEntity = level.logicLayer.getEntity(worldX, worldY);
				String currentID = currentGate == null ? null : currentGate.getStringID();

				if (currentID != null && currentEntity == null) {
					return new BlueprintLogicGateTarget(worldX, worldY, desiredID,
							element == null ? null : element.getLogicGateData(),
							element == null ? 0 : element.getLogicGateRotation(),
							BlueprintLogicGateTarget.Action.REMOVE);
				}

				if (desiredID == null) {
					if (currentID != null) {
						return new BlueprintLogicGateTarget(worldX, worldY, null, null, 0,
								BlueprintLogicGateTarget.Action.REMOVE);
					}
					continue;
				}

				if (!isObtainableLogicGate(desiredID)) {
					continue;
				}

				if (currentID == null) {
					return new BlueprintLogicGateTarget(worldX, worldY, desiredID,
							element.getLogicGateData(), element.getLogicGateRotation(),
							BlueprintLogicGateTarget.Action.PLACE);
				}

				if (!desiredID.equals(currentID)) {
					return new BlueprintLogicGateTarget(worldX, worldY, desiredID,
							element.getLogicGateData(), element.getLogicGateRotation(),
							BlueprintLogicGateTarget.Action.REMOVE);
				}

				if (!isLogicGateConfigured(area, level, worldX, worldY)) {
					return new BlueprintLogicGateTarget(worldX, worldY, desiredID,
							element.getLogicGateData(), element.getLogicGateRotation(),
							BlueprintLogicGateTarget.Action.CONFIGURE);
				}
			}
		}

		return null;
	}

	public static void addRequiredMaterials(Map<String, Integer> required, BlueprintArea area, Level level) {
		for (int y = 0; y < area.getHeight(); y++) {
			for (int x = 0; x < area.getWidth(); x++) {
				BlueprintElement element = area.getBlueprintData().getElementAt(x, y);
				int desiredMask = element == null ? 0 : element.getWireMask();
				int worldX = area.getOriginX() + x;
				int worldY = area.getOriginY() + y;
				int currentMask = getWireMask(level, worldX, worldY);
				int missingWires = Integer.bitCount(desiredMask & ~currentMask);

				if (missingWires > 0) {
					required.merge(wireItemID, missingWires, Integer::sum);
				}
			}
		}

		for (int y = 0; y < area.getHeight(); y++) {
			for (int x = 0; x < area.getWidth(); x++) {
				BlueprintElement element = area.getBlueprintData().getElementAt(x, y);

				if (element == null || element.getLogicGateID() == null
						|| !isObtainableLogicGate(element.getLogicGateID())) {
					continue;
				}

				int worldX = area.getOriginX() + x;
				int worldY = area.getOriginY() + y;
				GameLogicGate currentGate = level.logicLayer.getLogicGate(worldX, worldY);

				if (currentGate == null || !element.getLogicGateID().equals(currentGate.getStringID())) {
					required.merge(element.getLogicGateID(), 1, Integer::sum);
				}
			}
		}
	}

	public static void addOrderedRemainingMaterialIDs(List<String> materials, BlueprintArea area, Level level) {
		for (int y = 0; y < area.getHeight(); y++) {
			for (int x = 0; x < area.getWidth(); x++) {
				BlueprintElement element = area.getBlueprintData().getElementAt(x, y);
				int desiredMask = element == null ? 0 : element.getWireMask();
				int worldX = area.getOriginX() + x;
				int worldY = area.getOriginY() + y;
				int missingWires = Integer.bitCount(desiredMask & ~getWireMask(level, worldX, worldY));

				for (int i = 0; i < missingWires; i++) {
					materials.add(wireItemID);
				}
			}
		}

		for (int y = 0; y < area.getHeight(); y++) {
			for (int x = 0; x < area.getWidth(); x++) {
				BlueprintElement element = area.getBlueprintData().getElementAt(x, y);

				if (element == null || element.getLogicGateID() == null
						|| !isObtainableLogicGate(element.getLogicGateID())) {
					continue;
				}

				int worldX = area.getOriginX() + x;
				int worldY = area.getOriginY() + y;
				GameLogicGate currentGate = level.logicLayer.getLogicGate(worldX, worldY);

				if (currentGate == null || !element.getLogicGateID().equals(currentGate.getStringID())) {
					materials.add(element.getLogicGateID());
				}
			}
		}
	}

	public static int countBuilderMaterial(BlueprintArea area, Level level, String itemID) {
		int count = 0;

		for (opus.mobs.BuilderHumanMob builder : area.getAssignedBuilders(level)) {
			for (InventoryItem item : builder.getWorkInventory().items()) {
				if (item != null && itemID.equals(item.item.getStringID())) {
					count += item.getAmount();
				}
			}
		}

		return count;
	}

	public static void performWireAction(Level level, BlueprintWireTarget target) {
		int removed = 0;

		for (int wireID = 0; wireID < WireManager.totalWires; wireID++) {
			boolean desired = (target.desiredMask & (1 << wireID)) != 0;
			boolean current = (target.currentMask & (1 << wireID)) != 0;

			if (desired == current) {
				continue;
			}

			level.wireManager.setWire(target.tileX, target.tileY, wireID, desired);
			if (!desired) {
				removed++;
			}
		}

		if (level.isServer()) {
			level.sendWireUpdatePacket(target.tileX, target.tileY);

			if (removed > 0) {
				level.entityManager.pickups.add(new InventoryItem(wireItemID, removed).getPickupEntity(
						level, target.tileX * 32 + 16, target.tileY * 32 + 16));
			}
		}
	}

	public static void performLogicGateAction(Level level, BlueprintArea area, BlueprintLogicGateTarget target) {
		if (target.action == BlueprintLogicGateTarget.Action.REMOVE) {
			GameLogicGate currentGate = level.logicLayer.getLogicGate(target.tileX, target.tileY);
			if (currentGate != null) {
				currentGate.removeGate(level, target.tileX, target.tileY);
				syncLogicGate(level, target.tileX, target.tileY);
			}
			clearConfiguredLogicGate(area, target.tileX, target.tileY);
			return;
		}

		GameLogicGate desiredGate = getLogicGate(target.logicGateID);
		if (desiredGate == null) {
			return;
		}

		if (target.action == BlueprintLogicGateTarget.Action.PLACE) {
			desiredGate.placeGate(level, target.tileX, target.tileY);
		}

		LogicGateEntity entity = level.logicLayer.getEntity(target.tileX, target.tileY);
		if (entity != null) {
			applyLogicGateData(entity, target.logicGateData, target.logicGateRotation);
			markLogicGateConfigured(area, entity);
		}

		syncLogicGate(level, target.tileX, target.tileY);
	}

	private static int getWireMask(Level level, int tileX, int tileY) {
		int mask = 0;
		for (int wireID = 0; wireID < WireManager.totalWires; wireID++) {
			if (level.wireManager.hasWire(tileX, tileY, wireID)) {
				mask |= 1 << wireID;
			}
		}
		return mask;
	}

	private static String captureLogicGateData(LogicGateEntity entity) {
		SaveData data = new SaveData("data");
		entity.addPresetSaveData(data);
		sanitizeLogicGateData(entity.getLogicGate().getStringID(), data);
		return data.getScript();
	}

	private static void applyLogicGateData(LogicGateEntity entity, String script, int rotation) {
		if (script == null || script.isEmpty()) {
			return;
		}

		try {
			LoadData data = new LoadData(script);
			entity.applyPresetLoadData(data, false, false, PresetRotation.toRotationAngle(rotation));
		} catch (Exception e) {
			Logging.logMessage("Could not apply blueprint logic gate data at " + entity.tileX + ", " + entity.tileY + ": " + e.getMessage());
		}
	}

	private static void sanitizeLogicGateData(String gateID, SaveData data) {
		replaceSmallBooleanArray(data, "outputs", new boolean[4]);

		switch (gateID) {
			case "buffergate":
				replaceBoolean(data, "active", false);
				data.removeSaveDataByName("changes");
				break;
			case "countergate":
				replaceInt(data, "currentValue", 0);
				break;
			case "delaygate":
				replaceInt(data, "ticksToFlip", 0);
				replaceBoolean(data, "active", false);
				break;
			case "srlatchgate":
				replaceBoolean(data, "active", false);
				break;
			case "soundgate":
				replaceBoolean(data, "active", false);
				break;
			case "tflipflopgate":
				replaceBoolean(data, "flipped", false);
				break;
			case "countdowngate":
				replaceBoolean(data, "currentlyActive", false);
				replaceInt(data, "currentCountdownTime", 0);
				break;
			case "countdownrelay":
				replaceBoolean(data, "isActive", false);
				break;
		}
	}

	private static void replaceBoolean(SaveData data, String name, boolean value) {
		data.removeFirstSaveDataByName(name);
		data.addBoolean(name, value);
	}

	private static void replaceInt(SaveData data, String name, int value) {
		data.removeFirstSaveDataByName(name);
		data.addInt(name, value);
	}

	private static void replaceSmallBooleanArray(SaveData data, String name, boolean[] value) {
		data.removeFirstSaveDataByName(name);
		data.addSmallBooleanArray(name, value);
	}

	private static boolean isObtainableLogicGate(String gateID) {
		int itemID = ItemRegistry.getItemID(gateID);
		return itemID >= 0 && ItemRegistry.isObtainable(itemID) && getLogicGate(gateID) != null;
	}

	private static GameLogicGate getLogicGate(String gateID) {
		int gateNumericID = LogicGateRegistry.getLogicGateID(gateID);
		return gateNumericID < 0 ? null : LogicGateRegistry.getLogicGate(gateNumericID);
	}

	private static void syncLogicGate(Level level, int tileX, int tileY) {
		if (level.isServer()) {
			level.getServer().network.sendToClientsWithTile(
					level.logicLayer.getUpdatePacket(tileX, tileY), level, tileX, tileY);
		}
	}

	private static boolean isLogicGateConfigured(BlueprintArea area, Level level, int tileX, int tileY) {
		Map<Long, String> configured = configuredLogicGates.get(area);
		if (configured == null) {
			return false;
		}

		String expectedData = configured.get(tileKey(tileX, tileY));
		LogicGateEntity entity = level.logicLayer.getEntity(tileX, tileY);
		if (expectedData == null || entity == null) {
			return false;
		}

		return expectedData.equals(captureLogicGateData(entity));
	}

	private static void markLogicGateConfigured(BlueprintArea area, LogicGateEntity entity) {
		configuredLogicGates
				.computeIfAbsent(area, ignored -> new HashMap<>())
				.put(tileKey(entity.tileX, entity.tileY), captureLogicGateData(entity));
	}

	private static void clearConfiguredLogicGate(BlueprintArea area, int tileX, int tileY) {
		Map<Long, String> configured = configuredLogicGates.get(area);
		if (configured != null) {
			configured.remove(tileKey(tileX, tileY));
		}
	}

	private static long tileKey(int tileX, int tileY) {
		return ((long)tileX << 32) ^ (tileY & 0xffffffffL);
	}

	public static class BlueprintWireTarget {
		public final int tileX;
		public final int tileY;
		public final int desiredMask;
		public final int currentMask;

		public BlueprintWireTarget(int tileX, int tileY, int desiredMask, int currentMask) {
			this.tileX = tileX;
			this.tileY = tileY;
			this.desiredMask = desiredMask;
			this.currentMask = currentMask;
		}

		public int getMissingWireCount() {
			return Integer.bitCount(desiredMask & ~currentMask);
		}
	}

	public static class BlueprintLogicGateTarget {
		public enum Action {
			PLACE,
			REMOVE,
			CONFIGURE
		}

		public final int tileX;
		public final int tileY;
		public final String logicGateID;
		public final String logicGateData;
		public final int logicGateRotation;
		public final Action action;

		public BlueprintLogicGateTarget(int tileX, int tileY, String logicGateID, String logicGateData,
				int logicGateRotation, Action action) {
			this.tileX = tileX;
			this.tileY = tileY;
			this.logicGateID = logicGateID;
			this.logicGateData = logicGateData;
			this.logicGateRotation = logicGateRotation;
			this.action = action;
		}

		public boolean requiresMaterial() {
			return action == Action.PLACE;
		}
	}
}
