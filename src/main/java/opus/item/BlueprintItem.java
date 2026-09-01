package opus.item;

import necesse.engine.GlobalData;
import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.state.MainGame;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameMath;
import necesse.engine.window.GameWindow;
import necesse.entity.mobs.PlayerInventoryItemAttackSlot;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlaceableItemInterface;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemInteractAction;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.WallObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import opus.forms.NewBlueprintForm;
import opus.logging.Logging;
import opus.network.PacketBlueprintUpdate;
import opus.network.PacketPlaceBlueprintArea;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class BlueprintItem extends Item implements ItemInteractAction, PlaceableItemInterface {
	private static final String blueprintNameKey = "blueprintName";
	private static final String blueprintDataKey = "blueprintData";

	private GameTexture emptyTexture;
	private GameTexture filledTexture;

	private static boolean pageUpDown;
	private static boolean pageDownDown;

	private InventoryItem cachedBlueprintItem;
	private BlueprintData cachedBlueprintData;

	public BlueprintItem() {
		super(1);
		this.stackSize = 1;
	}

	public boolean hasBlueprint(InventoryItem item) {
		String json = item.getGndData().getString(blueprintDataKey);

		return json != null && !json.isEmpty();
	}

	public BlueprintData getBlueprintData(InventoryItem item) {
		if (item != cachedBlueprintItem) {
			setCachedBlueprintItem(item);
		}

		return cachedBlueprintData;
	}

	private BlueprintData loadBlueprintData(InventoryItem item) {
		if (item == null) {
			return null;
		}

		String json = item.getGndData().getString(blueprintDataKey);

		if (json == null || json.isEmpty()) {
			return null;
		}

		return BlueprintData.fromJson(json);
	}

	private void setCachedBlueprintItem(InventoryItem item) {
		cachedBlueprintItem = item;
		cachedBlueprintData = loadBlueprintData(item);
	}

	private void setCachedBlueprintData(
			InventoryItem item,
			BlueprintData blueprintData
	) {
		cachedBlueprintItem = item;
		cachedBlueprintData = blueprintData;
	}

	public String getBlueprintName(InventoryItem item) {
		if (!hasBlueprint(item)) {
			return "Empty Blueprint";
		}

		String name = item.getGndData().getString(blueprintNameKey);

		return name == null || name.isEmpty()
			? "Empty Blueprint"
			: "Blueprint: " + name;
	}

	@Override
	public GameSprite getItemSprite(InventoryItem item, PlayerMob perspective) {
		return new GameSprite(
				hasBlueprint(item)
						? filledTexture
						: emptyTexture
		);
	}

	@Override
	protected void loadItemTextures() {
		emptyTexture = GameTexture.fromFile("items/blueprint_empty");
		filledTexture = GameTexture.fromFile("items/blueprint_full");
	}

	@Override
	protected ListGameTooltips getDisplayNameTooltips(
			InventoryItem item,
			PlayerMob perspective,
			GameBlackboard blackboard
	) {
		return new ListGameTooltips();
	}

	public final ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
		ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);

		tooltips.add(getBlueprintName(item));
		if (hasBlueprint(item)) {
			tooltips.add(Localization.translate(
					"itemtooltip",
					"blueprintnonemptytip"
			));
		} else {
			tooltips.add(Localization.translate(
					"itemtooltip",
					"blueprintemptytip"
			));
		}

		return tooltips;
	}

	public void setBlueprint(
		InventoryItem item,
		String name,
		BlueprintData blueprintData
	) {
		item.getGndData().setString(blueprintNameKey, name);
		item.getGndData().setString(
			blueprintDataKey,
			blueprintData.toJson()
		);
	}

	public void clearBlueprint(InventoryItem item) {
		item.getGndData().setString(blueprintNameKey, "");
		item.getGndData().setString(blueprintDataKey, "");
	}

	public static void frameTick(MainGame mainGame, TickManager tickManager, GameWindow gameWindow) {
		boolean currentPageUp =
				gameWindow.isKeyDown(GLFW.GLFW_KEY_PAGE_UP);

		boolean currentPageDown =
				gameWindow.isKeyDown(GLFW.GLFW_KEY_PAGE_DOWN);

		if (mainGame.getClient() != null
				&& mainGame.getClient().getPlayer() != null
		) {
			PlayerMob player = mainGame.getClient().getPlayer();
			InventoryItem item = player.getSelectedItem();

			if (item != null
					&& item.item instanceof BlueprintItem
			) {
				BlueprintItem blueprintItem =
						(BlueprintItem)item.item;

				if (item != blueprintItem.cachedBlueprintItem) {
					blueprintItem.setCachedBlueprintItem(item);
				}

				if (blueprintItem.cachedBlueprintData != null) {
					ItemAttackSlot attackSlot =
							player.getCurrentSelectedAttackSlot();

					if (attackSlot instanceof PlayerInventoryItemAttackSlot) {
						PlayerInventoryItemAttackSlot playerSlot =
								(PlayerInventoryItemAttackSlot)attackSlot;

						if (currentPageUp && !pageUpDown) {
							blueprintItem.rotate(mainGame, playerSlot, false);
						}

						if (currentPageDown && !pageDownDown) {
							blueprintItem.rotate(mainGame, playerSlot, true);
						}
					}
				}
			}
		}

		pageUpDown = currentPageUp;
		pageDownDown = currentPageDown;
	}

	private void rotate(
			MainGame mainGame,
			PlayerInventoryItemAttackSlot playerSlot,
			boolean clockwise
	) {
		InventoryItem item = playerSlot.getItem();

		if (cachedBlueprintData == null
				|| item == null
				|| item != cachedBlueprintItem
				|| item.item != this
		) {
			return;
		}

		if (clockwise)
			cachedBlueprintData = cachedBlueprintData.rotateClockwise();
		else
			cachedBlueprintData = cachedBlueprintData.rotateCounterClockwise();

		mainGame.getClient().network.sendPacket(
				new PacketBlueprintUpdate(
						playerSlot.slot.inventoryID,
						playerSlot.slot.slot,
						getRawBlueprintName(item),
						cachedBlueprintData
				)
		);
	}

	private String getRawBlueprintName(InventoryItem item) {
		String name = item.getGndData().getString(blueprintNameKey);

		return name == null ? "" : name;
	}

	@Override
	public void drawPlacePreview(
			Level level,
			int x,
			int y,
			GameCamera camera,
			PlayerMob player,
			InventoryItem item,
			PlayerInventorySlot slot
	) {
		if (item != cachedBlueprintItem
				|| cachedBlueprintData == null
		) {
			return;
		}

		BlueprintData blueprintData = cachedBlueprintData;

		int originX = GameMath.getTileCoordinate(x);
		int originY = GameMath.getTileCoordinate(y);

		for (BlueprintElement element : blueprintData.getElements()) {
			int tileX = originX + element.getX();
			int tileY = originY + element.getY();

			if (element.getTileID() != null) {
				GameTile tile = TileRegistry.getTile(
						element.getTileID()
				);

				if (tile != null) {
					tile.drawPreview(
							level,
							tileX,
							tileY,
							0.5F,
							player,
							camera
					);
				}
			}

			if (element.getObjectID() != null) {
				GameObject object = ObjectRegistry.getObject(
						element.getObjectID()
				);

				if (object != null && object.isMultiTileMaster()) {
					if (object instanceof WallObject) {
						drawWallPreview(
								(WallObject)object,
								level,
								tileX,
								tileY,
								player,
								camera
						);
					} else {
						object.drawMultiTilePreview(
								level,
								tileX,
								tileY,
								element.getRotation(),
								0.5f,
								player,
								camera
						);
					}
				}
			}
		}
	}

	private static void drawWallPreview(
			WallObject wall,
			Level level,
			int tileX,
			int tileY,
			PlayerMob player,
			GameCamera camera
	) {
		SharedTextureDrawOptions options =
				new SharedTextureDrawOptions(
						WallObject.generatedWallTexture
				);

		boolean previousSmoothLighting =
				Settings.smoothLighting;

		try {
			Settings.smoothLighting = false;

			wall.addWallDrawOptions(
					options,
					level,
					tileX,
					tileY,
					level.lightManager.newLight(150.0F),
					null,
					camera,
					// Deliberately null:
					// blueprint ghosts should not fade based on player position.
					null
			);
		} finally {
			Settings.smoothLighting =
					previousSmoothLighting;
		}

		options.forEachDraw(draw -> {
			draw.alpha(0.5F);
		}).draw();
	}

	@Override
	public boolean canLevelInteract(
			Level level,
			int x,
			int y,
			ItemAttackerMob attackerMob,
			InventoryItem item
	) {
		return true;
	}

	@Override
	public InventoryItem onAttack(
			Level level,
			int x,
			int y,
			ItemAttackerMob attackerMob,
			int attackHeight,
			InventoryItem item,
			ItemAttackSlot slot,
			int animAttack,
			int seed,
			GNDItemMap mapContent
	) {
		if (hasBlueprint(item)
				&& attackerMob.isPlayer
				&& level.isClient()
				&& GlobalData.getCurrentState() instanceof MainGame
		) {
			if (!(slot instanceof PlayerInventoryItemAttackSlot)) {
				return item;
			}

			PlayerInventoryItemAttackSlot playerSlot =
					(PlayerInventoryItemAttackSlot)slot;

			int originX = GameMath.getTileCoordinate(x);
			int originY = GameMath.getTileCoordinate(y);

			MainGame mainGame = (MainGame)GlobalData.getCurrentState();

			mainGame.getClient().network.sendPacket(
					new PacketPlaceBlueprintArea(
							originX,
							originY,
							playerSlot.slot.inventoryID,
							playerSlot.slot.slot
					)
			);

			Logging.logMessage(
					"CLIENT requested blueprint area at "
							+ originX
							+ ", "
							+ originY
			);
		}

		return item;
	}

	@Override
	public InventoryItem onLevelInteract(
			Level level,
			int x,
			int y,
			ItemAttackerMob attackerMob,
			int attackHeight,
			InventoryItem item,
			ItemAttackSlot slot,
			int seed,
			GNDItemMap mapContent
	) {
		if (!hasBlueprint(item)
				&& attackerMob.isPlayer
				&& level.isClient()
				&& GlobalData.getCurrentState() instanceof MainGame
		) {
			MainGame mainGame = (MainGame)GlobalData.getCurrentState();

			if (!(slot instanceof PlayerInventoryItemAttackSlot)) {
				return item;
			}

			PlayerInventoryItemAttackSlot playerSlot =
					(PlayerInventoryItemAttackSlot)slot;

			NewBlueprintForm.openBlueprintCreation(
					mainGame,
					(name, blueprintData) -> {
						if (!slot.isStillValid(attackerMob, item)) {
							mainGame.getClient().setMessage(
									"Blueprint item is no longer available.",
									Color.RED
							);
							return;
						}

						InventoryItem currentItem = slot.getItem();

						if (currentItem == null
								|| currentItem.item != this
								|| hasBlueprint(currentItem)
						) {
							mainGame.getClient().setMessage(
									"Blueprint item is no longer available.",
									Color.RED
							);
							return;
						}

						mainGame.getClient().network.sendPacket(
								new PacketBlueprintUpdate(
										playerSlot.slot.inventoryID,
										playerSlot.slot.slot,
										name,
										blueprintData
								)
						);

						setCachedBlueprintData(
								currentItem,
								blueprintData
						);
					}
			);
		}
		else if (
				hasBlueprint(item)
						&& attackerMob.isPlayer
						&& level.isClient()
						&& GlobalData.getCurrentState() instanceof MainGame
		) {
			if (!(slot instanceof PlayerInventoryItemAttackSlot)) {
				return item;
			}

			PlayerInventoryItemAttackSlot playerSlot =
					(PlayerInventoryItemAttackSlot)slot;

			MainGame mainGame =
					(MainGame)GlobalData.getCurrentState();

			mainGame.getClient().network.sendPacket(
					new PacketBlueprintUpdate(
							playerSlot.slot.inventoryID,
							playerSlot.slot.slot
					)
			);

			if (item == cachedBlueprintItem) {
				cachedBlueprintData = null;
			}
		}

		return item;
	}
}
