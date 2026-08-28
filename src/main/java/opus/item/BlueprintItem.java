package opus.item;

import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.state.MainGame;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerInventoryItemAttackSlot;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemInteractAction;
import necesse.level.maps.Level;
import opus.forms.NewBlueprintForm;
import opus.network.PacketBlueprintUpdate;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;

import java.awt.*;
import java.util.ArrayList;

public class BlueprintItem extends Item implements ItemInteractAction {
	private static final String blueprintNameKey = "blueprintName";
	private static final String blueprintDataKey = "blueprintData";

	private GameTexture emptyTexture;
	private GameTexture filledTexture;

	public BlueprintItem() {
		super(1);
		this.stackSize = 1;
	}

	public boolean hasBlueprint(InventoryItem item) {
		String json = item.getGndData().getString(blueprintDataKey);

		return json != null && !json.isEmpty();
	}

	public BlueprintData getBlueprintData(InventoryItem item) {
		if (!hasBlueprint(item)) {
			return null;
		}

		String json = item.getGndData().getString(blueprintDataKey);

		return BlueprintData.fromJson(json);
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
		if (
				!hasBlueprint(item)
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
		}

		return item;
	}
}