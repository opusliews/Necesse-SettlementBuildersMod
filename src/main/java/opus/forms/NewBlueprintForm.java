package opus.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.gameTool.GameToolManager;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import opus.logging.Logging;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;
import opus.tools.BlueprintSelectionTool;
import java.util.function.BiConsumer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static opus.tools.BlueprintElement.isBlueprintObject;
import static opus.tools.BlueprintElement.isBlueprintTile;

public class NewBlueprintForm extends Form {
	private static NewBlueprintForm builderForm;
	private static BlueprintSelectionTool blueprintTool;
	private final FormTextInput blueprintNameInput;

	private final BiConsumer<String, BlueprintData> onBlueprintCreated;

	private final MainGame mainGame;

	public NewBlueprintForm(MainGame mainGame, BiConsumer<String, BlueprintData> onBlueprintCreated) {
		super("newBlueprintForm", 380, 110);

		this.onBlueprintCreated = onBlueprintCreated;

		this.mainGame = mainGame;

		this.addComponent(
				new FormLocalLabel(
						new StaticMessage("Blueprint Name"),
						new FontOptions(16),
						0,
						72,
						18
				)
		);

		this.blueprintNameInput = this.addComponent(
				new FormTextInput(
						145,
						7,
						FormInputSize.SIZE_32,
						180,
						50
				)
		);

		this.addComponent(
				new FormContentIconButton(
						350,
						7,
						FormInputSize.SIZE_24,
						ButtonColor.BASE,
						this.getInterfaceStyle().button_help_20,
						new GameMessage[]{
								new StaticMessage(
										Localization.translate(
												"itemtooltip",
												"blueprinttoolhelp"
										)
								)
						}
				)
		);

		// Create button
		FormLocalTextButton createButton = this.addComponent(
				new FormLocalTextButton(
						new StaticMessage("Create Blueprint"),
						30,
						60,
						155,
						FormInputSize.SIZE_32,
						ButtonColor.BASE
				)
		);

		FormLocalTextButton cancelButton = this.addComponent(
				new FormLocalTextButton(
						new StaticMessage("Cancel"),
						195,
						60,
						155,
						FormInputSize.SIZE_32,
						ButtonColor.BASE
				)
		);

		createButton.onClicked(event -> {
			Rectangle selection = blueprintTool.getSelection();

			if (selection == null || selection.isEmpty()) {
				Client client = mainGame.getClient();
				client.setMessage(
						Localization.translate(
								"misc",
								"blueprintcreatedempty"
						),
						Color.RED
				);
				return;
			}

			String blueprintName = blueprintNameInput.getText().trim();

			if (blueprintName.isEmpty()) {
				Client client = mainGame.getClient();
				client.setMessage(
						Localization.translate(
								"misc",
								"blueprintcreatednameless"
						),
						Color.RED
				);
				return;
			}

			Level level = blueprintTool.getLevel();

			List<String> excludedObjectIDs = blueprintTool.getExcludedObjectIDs();
			List<String> excludedTileIDs = blueprintTool.getExcludedTileIDs();

			List<BlueprintElement> blueprintElements = new ArrayList<>();

			for (int x = selection.x; x < selection.x + selection.width; x++) {
				for (int y = selection.y; y < selection.y + selection.height; y++) {
					int relativeX = x - selection.x;
					int relativeY = y - selection.y;

					GameObject object = level.getObject(x, y);
					GameTile tile = level.getTile(x, y);

					BlueprintElement be = new BlueprintElement(relativeX, relativeY);

					if (isBlueprintObject(object) && !excludedObjectIDs.contains(object.getStringID())) {
						be.setObjectID(object.getStringID());
						be.setRotation(level.getObjectRotation(x, y));

						Logging.logMessage("Object found: " + object.getDisplayName());
					}
					
					if (isBlueprintTile(tile)
							&& isObtainableBlueprintTile(tile)
							&& !excludedTileIDs.contains(tile.getStringID())
					) {
						be.setTileID(tile.getStringID());
						Logging.logMessage("Tile found: " + tile.getDisplayName());
					}

					if (!be.isEmpty()) {
						blueprintElements.add(be);
					}
				}
			}
			if (blueprintElements.isEmpty()) {
				Client client = mainGame.getClient();
				client.setMessage(
						Localization.translate(
								"misc",
								"blueprintcreatedempty2"
						),
						Color.RED
				);
			}
			else {
				BlueprintData blueprintData = new BlueprintData(
						selection.width, selection.height, blueprintElements);

				if (onBlueprintCreated != null) {
					onBlueprintCreated.accept(
							blueprintName,
							blueprintData
					);
				}

				this.onCancel();
			}
		});

		cancelButton.onClicked(event -> {
			this.onCancel();
		});

		this.setPosition(10, 30);
	}

	private boolean isObtainableBlueprintTile(GameTile tile) {
		int itemID = ItemRegistry.getItemID(tile.getStringID());
		return ItemRegistry.isObtainable(itemID);
	}

	private void hideInventoryUI() {
		mainGame.formManager.toolbar.setHidden(true);
		mainGame.formManager.inventory.setHidden(true);
		mainGame.formManager.crafting.setHidden(true);
		mainGame.formManager.creative.setHidden(true);
	}

	public static boolean isBlueprintCreationOpen() {
		return builderForm != null;
	}

	public static void openBlueprintCreation(
			MainGame mainGame, BiConsumer<String, BlueprintData> onBlueprintCreated
	) {
		if (builderForm != null) {
			return;
		}

		builderForm = new NewBlueprintForm(mainGame, onBlueprintCreated);

		if (blueprintTool != null) {
			GameToolManager.clearGameTool(blueprintTool);
		}

		mainGame.formManager.addComponent(builderForm);

		blueprintTool = new BlueprintSelectionTool(
				mainGame.getClient().getLevel(),
				() -> {
					if (builderForm != null) {
						builderForm.onToolCancelled();
					}
				}
		);

		GameToolManager.setGameTool(
				blueprintTool,
				BlueprintSelectionTool.class
		);
	}

	public static void frameTick(MainGame mainGame, TickManager tickManager, GameWindow gameWindow) {
		if (mainGame.getClient() == null || mainGame.getClient().getPlayer() == null) {
			return;
		}
		if (!mainGame.formManager.pauseMenu.isHidden()) {
			if (builderForm != null) {
				builderForm.onCancel();
			}
			return;
		}

		if (builderForm != null) {
			builderForm.hideInventoryUI();
		}
	}

	// ESC Pressed handler
	private void onToolCancelled() {
		builderForm = null;
		blueprintTool = null;

		this.mainGame.formManager.removeComponent(this);
		this.dispose();

		this.mainGame.formManager.updateActive(true);
	}

	// Cancel Pressed handler
	protected void onCancel() {
		builderForm = null;

		this.mainGame.formManager.removeComponent(this);

		if (blueprintTool != null) {
			GameToolManager.clearGameTool(blueprintTool);
			blueprintTool = null;
		}

		this.dispose();

		this.mainGame.formManager.updateActive(true);
	}
}