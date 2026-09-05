package opus.container;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.customAction.StringCustomAction;
import necesse.inventory.container.object.OEInventoryContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.level.maps.Level;
import opus.item.BlueprintItem;
import opus.object.BlueprintWorkstationObjectEntity;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;

public class BlueprintWorkstationContainer extends OEInventoryContainer {
	public final BlueprintWorkstationObjectEntity workstation;
	public final StringCustomAction renameBlueprint;
	public final StringCustomAction pasteBlueprint;
	public final StringCustomAction removeElementType;

	public BlueprintWorkstationContainer(
			NetworkClient client,
			int uniqueSeed,
			SettlementDataEvent settlement,
			BlueprintWorkstationObjectEntity workstation,
			PacketReader reader
	) {
		super(client, uniqueSeed, settlement, workstation, reader);
		this.workstation = workstation;

		renameBlueprint = (StringCustomAction)registerAction(new StringCustomAction() {
			@Override
			protected void run(String value) {
				InventoryItem item = getBlueprintItem();
				String name = value == null ? "" : value.trim();

				if (item == null || name.isEmpty()) {
					return;
				}

				((BlueprintItem)item.item).setBlueprintName(item, name);
				markBlueprintChanged();
			}
		});

		pasteBlueprint = (StringCustomAction)registerAction(new StringCustomAction() {
			@Override
			protected void run(String json) {
				InventoryItem item = getBlueprintItem();
				BlueprintData data = parseBlueprintData(json);

				if (item == null || data == null) {
					return;
				}

				((BlueprintItem)item.item).setBlueprintData(item, data);
				markBlueprintChanged();
			}
		});

		removeElementType = (StringCustomAction)registerAction(new StringCustomAction() {
			@Override
			protected void run(String value) {
				InventoryItem item = getBlueprintItem();

				if (item == null || value == null) {
					return;
				}

				BlueprintItem blueprintItem = (BlueprintItem)item.item;
				BlueprintData data = parseBlueprintData(blueprintItem.getBlueprintJson(item));

				if (data == null) {
					return;
				}

				boolean removeTile = value.startsWith("tile:");
				boolean removeObject = value.startsWith("object:");
				boolean removeWire = value.equals("wire:all");
				boolean removeLogicGate = value.startsWith("logicgate:");

				if (!removeTile && !removeObject && !removeWire && !removeLogicGate) {
					return;
				}

				String id = value.substring(value.indexOf(':') + 1);
				boolean changed = false;
				java.util.List<BlueprintElement> elements = data.getElements();

				for (BlueprintElement element : elements) {
					if (removeTile && id.equals(element.getTileID())) {
						element.setTileID(null);
						changed = true;
					}

					if (removeObject && id.equals(element.getObjectID())) {
						element.setObjectID(null);
						changed = true;
					}

					if (removeWire && element.getWireMask() != 0) {
						element.setWireMask(0);
						changed = true;
					}

					if (removeLogicGate && id.equals(element.getLogicGateID())) {
						element.setLogicGateID(null);
						element.setLogicGateData(null);
						element.setLogicGateRotation(0);
						changed = true;
					}
				}

				if (!changed) {
					return;
				}

				elements.removeIf(BlueprintElement::isEmpty);

				blueprintItem.setBlueprintData(
						item,
						new BlueprintData(data.getWidth(), data.getHeight(), elements)
				);

				markBlueprintChanged();
			}
		});
	}

	public InventoryItem getBlueprintItem() {
		InventoryItem item = workstation.inventory.getItem(0);
		return item != null && item.item instanceof BlueprintItem ? item : null;
	}

	private BlueprintData parseBlueprintData(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}

		try {
			BlueprintData data = BlueprintData.fromJson(json);

			if (data.getWidth() <= 0 || data.getHeight() <= 0) {
				return null;
			}

			for (BlueprintElement element : data.getElements()) {
				if (element.getX() < 0
						|| element.getY() < 0
						|| element.getX() >= data.getWidth()
						|| element.getY() >= data.getHeight()) {
					return null;
				}
			}

			return data;
		} catch (Exception e) {
			return null;
		}
	}

	private void markBlueprintChanged() {
		workstation.inventory.markDirty(0);

		if (client.isServer()) {
			workstation.markDirty();
		}
	}

	@Override
	public boolean isValid(ServerClient client) {
		if (!super.isValid(client)) {
			return false;
		}

		Level level = client.getLevel();
		return !workstation.removed()
				&& level.getObject(workstation.tileX, workstation.tileY).isInInteractRange(
						level,
						workstation.tileX,
						workstation.tileY,
						client.playerMob
				);
	}

	public static void openAndSendContainer(
			int containerID,
			ServerClient client,
			Level level,
			int tileX,
			int tileY
	) {
		OEInventoryContainer.openAndSendContainer(containerID, client, level, tileX, tileY);
	}
}
