package opus.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.registries.LogicGateRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameBackground;
import necesse.gfx.forms.ContainerComponent;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.presets.containerComponent.ContainerFormSwitcher;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.level.gameLogicGate.GameLogicGate;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import opus.container.BlueprintWorkstationContainer;
import opus.item.BlueprintItem;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlueprintWorkstationContainerForm extends ContainerFormSwitcher {
	private final BlueprintWorkstationContainer workstationContainer;
	private final Form mainForm;
	private final Form renameForm;
	private final FormContentBox elementsBox;
	private final FormTextButton renameButton;
	private final FormContentIconButton copyButton;
	private final FormContentIconButton pasteButton;
	private FormTextInput renameInput;
	private String lastBlueprintSignature;

	public BlueprintWorkstationContainerForm(Client client, BlueprintWorkstationContainer container) {
		super(client, container);
		this.workstationContainer = container;

		mainForm = (Form)addComponent(new Form("blueprintWorkstation", 400, 320));
		renameForm = (Form)addComponent(new Form("renameBlueprint", 400, 120));

		mainForm.addComponent(new FormLabel(
				"Blueprint Workstation",
				new FontOptions(20),
				FormLabel.ALIGN_MID,
				mainForm.getWidth() / 2,
				8
		));

		mainForm.addComponent(new FormContainerSlot(
				client,
				container,
				container.INVENTORY_START,
				16,
				42
		));

		renameButton = (FormTextButton)mainForm.addComponent(new FormTextButton(
				"Rename",
				64,
				46,
				108,
				FormInputSize.SIZE_32,
				ButtonColor.BASE
		));
		renameButton.onClicked(event -> openRenameForm());

		copyButton = (FormContentIconButton)mainForm.addComponent(new FormContentIconButton(
				184,
				50,
				FormInputSize.SIZE_24,
				ButtonColor.BASE,
				getInterfaceStyle().copy_button,
				new GameMessage[]{new StaticMessage("Copy blueprint")}
		));
		copyButton.onClicked(event -> copyBlueprint());

		pasteButton = (FormContentIconButton)mainForm.addComponent(new FormContentIconButton(
				214,
				50,
				FormInputSize.SIZE_24,
				ButtonColor.BASE,
				getInterfaceStyle().paste_button,
				new GameMessage[]{new StaticMessage("Paste blueprint")}
		));
		pasteButton.onClicked(event -> pasteBlueprint());

		elementsBox = (FormContentBox)mainForm.addComponent(new FormContentBox(
				16,
				88,
				368,
				216,
				GameBackground.textBox
		));
		elementsBox.alwaysShowVerticalScrollBar = true;

		setupRenameForm();
		refreshBlueprint(true);
		makeCurrent(mainForm);
	}

	private void setupRenameForm() {
		renameForm.addComponent(new FormLabel(
				"Blueprint Name",
				new FontOptions(16),
				FormLabel.ALIGN_LEFT,
				16,
				18
		));

		renameInput = (FormTextInput)renameForm.addComponent(new FormTextInput(
				172,
				7,
				FormInputSize.SIZE_32,
				210,
				80
		));

		FormTextButton saveButton = (FormTextButton)renameForm.addComponent(new FormTextButton(
				"Rename",
				16,
				64,
				156,
				FormInputSize.SIZE_32,
				ButtonColor.BASE
		));
		saveButton.onClicked(event -> {
			String name = renameInput.getText().trim();

			if (name.isEmpty()) {
				client.setMessage("Blueprint name cannot be empty", Color.RED);
				return;
			}

			workstationContainer.renameBlueprint.runAndSend(name);
			makeCurrent(mainForm);
			refreshBlueprint(true);
		});

		FormTextButton cancelButton = (FormTextButton)renameForm.addComponent(new FormTextButton(
				"Cancel",
				188,
				64,
				156,
				FormInputSize.SIZE_32,
				ButtonColor.BASE
		));
		cancelButton.onClicked(event -> makeCurrent(mainForm));
	}

	private void openRenameForm() {
		InventoryItem item = workstationContainer.getBlueprintItem();

		if (item == null || !((BlueprintItem)item.item).hasBlueprint(item)) {
			return;
		}

		renameInput.setText(((BlueprintItem)item.item).getRawBlueprintName(item));
		makeCurrent(renameForm);
	}

	private void copyBlueprint() {
		InventoryItem item = workstationContainer.getBlueprintItem();

		if (item == null) {
			return;
		}

		BlueprintItem blueprintItem = (BlueprintItem)item.item;
		String json = blueprintItem.getBlueprintJson(item);

		if (json != null && !json.isEmpty()) {
			WindowManager.getWindow().putClipboard(json);
		}
	}

	private void pasteBlueprint() {
		if (workstationContainer.getBlueprintItem() == null) {
			return;
		}

		String json = WindowManager.getWindow().getClipboard();

		try {
			BlueprintData data = BlueprintData.fromJson(json);
			if (data.getWidth() <= 0 || data.getHeight() <= 0) {
				throw new IllegalArgumentException();
			}
		} catch (Exception e) {
			client.setMessage("Clipboard does not contain valid blueprint JSON", Color.RED);
			return;
		}

		workstationContainer.pasteBlueprint.runAndSend(json);
		refreshBlueprint(true);
	}

	private void refreshBlueprint(boolean force) {
		InventoryItem item = workstationContainer.getBlueprintItem();
		String signature = "";

		if (item != null) {
			BlueprintItem blueprintItem = (BlueprintItem)item.item;
			signature = blueprintItem.getRawBlueprintName(item) + "\n" + blueprintItem.getBlueprintJson(item);
		}

		if (!force && signature.equals(lastBlueprintSignature)) {
			return;
		}

		lastBlueprintSignature = signature;
		elementsBox.clearComponents();

		boolean hasBlueprint = item != null && ((BlueprintItem)item.item).hasBlueprint(item);
		renameButton.setActive(hasBlueprint);
		copyButton.setActive(hasBlueprint);
		pasteButton.setActive(item != null);

		if (!hasBlueprint) {
			elementsBox.addComponent(new FormLabel(
					"Place a blueprint in the slot above.",
					new FontOptions(16),
					FormLabel.ALIGN_LEFT,
					8,
					8
			));
			elementsBox.setContentBox(new Rectangle(0, 0, 340, 32));
			return;
		}

		BlueprintItem blueprintItem = (BlueprintItem)item.item;
		BlueprintData data;

		try {
			data = BlueprintData.fromJson(blueprintItem.getBlueprintJson(item));
		} catch (Exception e) {
			elementsBox.addComponent(new FormLabel(
					"Invalid blueprint data",
					new FontOptions(16),
					FormLabel.ALIGN_LEFT,
					8,
					8
			));
			elementsBox.setContentBox(new Rectangle(0, 0, 340, 32));
			return;
		}

		List<ElementGroup> groups = getElementGroups(data);
		int rowY = 4;

		if (groups.isEmpty()) {
			elementsBox.addComponent(new FormLabel(
					"Blueprint has no elements.",
					new FontOptions(16),
					FormLabel.ALIGN_LEFT,
					8,
					8
			));
			rowY = 32;
		} else {
			for (ElementGroup group : groups) {
				elementsBox.addComponent(new FormLabel(
						group.count + " x " + group.name,
						new FontOptions(14),
						FormLabel.ALIGN_LEFT,
						8,
						rowY + 6,
						292
				));

				FormTextButton removeButton = (FormTextButton)elementsBox.addComponent(new FormTextButton(
						"X",
						310,
						rowY,
						28,
						FormInputSize.SIZE_24,
						ButtonColor.RED
				));

				removeButton.onClicked(event -> {
					workstationContainer.removeElementType.runAndSend(group.key);
					refreshBlueprint(true);
				});

				rowY += 30;
			}
		}

		elementsBox.setContentBox(new Rectangle(0, 0, 344, Math.max(32, rowY + 4)));
	}

	private List<ElementGroup> getElementGroups(BlueprintData data) {
		Map<String, ElementGroup> groups = new LinkedHashMap<>();

		for (BlueprintElement element : data.getElements()) {
			if (element.getTileID() != null) {
				String tileID = element.getTileID();
				String key = "tile:" + tileID;
				ElementGroup group = groups.get(key);

				if (group == null) {
					GameTile tile = TileRegistry.getTile(tileID);
					String name = tile == null ? tileID : tile.getDisplayName();
					group = new ElementGroup(key, name);
					groups.put(key, group);
				}
				group.count++;
			}

			if (element.getObjectID() != null) {
				String objectID = element.getObjectID();
				String key = "object:" + objectID;
				ElementGroup group = groups.get(key);

				if (group == null) {
					GameObject object = ObjectRegistry.getObject(objectID);
					String name = object == null ? objectID : object.getDisplayName();
					group = new ElementGroup(key, name);
					groups.put(key, group);
				}
				group.count++;
			}

			if (element.getWireMask() != 0) {
				String key = "wire:all";
				ElementGroup group = groups.get(key);
				if (group == null) {
					group = new ElementGroup(key, "Wire");
					groups.put(key, group);
				}
				group.count += Integer.bitCount(element.getWireMask());
			}

			if (element.getLogicGateID() != null) {
				String gateID = element.getLogicGateID();
				String key = "logicgate:" + gateID;
				ElementGroup group = groups.get(key);

				if (group == null) {
					int gateNumericID = LogicGateRegistry.getLogicGateID(gateID);
					GameLogicGate gate = gateNumericID < 0 ? null : LogicGateRegistry.getLogicGate(gateNumericID);
					String name = gate == null ? gateID : gate.getDisplayName();
					group = new ElementGroup(key, name);
					groups.put(key, group);
				}
				group.count++;
			}
		}

		return new ArrayList<>(groups.values());
	}

	private static class ElementGroup {
		private final String key;
		private final String name;
		private int count;

		private ElementGroup(String key, String name) {
			this.key = key;
			this.name = name;
		}
	}

	@Override
	public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
		refreshBlueprint(false);
		super.draw(tickManager, perspective, renderBox);
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		ContainerComponent.setPosFocus(mainForm);
		ContainerComponent.setPosFocus(renameForm);
	}

	@Override
	public boolean shouldOpenInventory() {
		return true;
	}
}
