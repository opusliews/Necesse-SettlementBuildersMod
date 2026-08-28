package opus.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.gameTool.GameToolManager;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.ui.ButtonColor;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import opus.SettlementBuilders;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;
import opus.tools.BlueprintSelectionTool;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static opus.tools.BlueprintElement.isBlueprintObject;
import static opus.tools.BlueprintElement.isBlueprintTile;

public class NewBlueprintForm extends Form {
	private static NewBlueprintForm builderForm;
	private static BlueprintSelectionTool blueprintTool;
	private final MainGame mainGame;

	public NewBlueprintForm(MainGame mainGame) {
		super("newBlueprintForm", 220, 120);

		this.mainGame = mainGame;

		FormLocalTextButton CreateButton = this.addComponent(
				new FormLocalTextButton(
						new StaticMessage("Create Blueprint"),
						20,
						20,
						180,
						FormInputSize.SIZE_32,
						ButtonColor.BASE
				)
		);

		FormLocalTextButton CancelButton = this.addComponent(
				new FormLocalTextButton(
						new StaticMessage("Cancel"),
						20,
						68,
						180,
						FormInputSize.SIZE_32,
						ButtonColor.BASE
				)
		);

		CreateButton.onClicked(event -> {
			if (blueprintTool.getSelection().isEmpty()) {
				// TODO Handle Empty Selection
				return;
			}
			Rectangle selection = blueprintTool.getSelection();
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

						System.out.println("Poop: Object found: " + object.getDisplayName());
					}
					if (isBlueprintTile(tile) && !excludedTileIDs.contains(tile.getStringID())) {
						be.setTileID(tile.getStringID());
						System.out.println("Poop: Tile found: " + tile.getDisplayName());
					}

					if (!be.isEmpty()) {
						blueprintElements.add(be);
					}
				}
			}
			if (!blueprintElements.isEmpty()) {
				BlueprintData blueprintData = new BlueprintData(
						selection.width, selection.height, blueprintElements);
				System.out.println("Poop: Blueprint JSON:" + blueprintData.toJson());
			}
		});

		CancelButton.onClicked(event -> {
			this.onCancel();
		});

		this.setPosition(10,30
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

		if (SettlementBuilders.openBuilderFormControl.isPressed()) {
			if (builderForm == null) {
				builderForm = new NewBlueprintForm(mainGame);

				mainGame.formManager.addComponent(
						(FormComponent)builderForm
				);

				if (blueprintTool != null) {
					GameToolManager.clearGameTool(blueprintTool);
				}
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
		}
	}

	// ESC Pressed handler
	private void onToolCancelled() {
		builderForm = null;
		blueprintTool = null;

		this.mainGame.formManager.removeComponent(this);
		this.dispose();
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
	}
}