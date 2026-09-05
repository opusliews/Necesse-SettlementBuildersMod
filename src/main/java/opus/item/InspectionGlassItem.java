package opus.item;

import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemInteractAction;
import necesse.level.maps.Level;
import opus.blueprint.BlueprintAreaHud;

public class InspectionGlassItem extends Item implements ItemInteractAction {
	public InspectionGlassItem() {
		super(1);
		this.stackSize = 1;
	}

	@Override
	public ListGameTooltips getTooltips(
			InventoryItem item,
			PlayerMob perspective,
			GameBlackboard blackboard
	) {
		ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
		String replacementValue = BlueprintAreaHud.isProjectGhostsVisible() ? "hide" : "show";
		tooltips.add(Localization.translate("itemtooltip", "inspectionglasstip", "toggle", replacementValue));
		return tooltips;
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
		if (attackerMob.isPlayer && level.isClient()) {
			BlueprintAreaHud.toggleProjectGhostsVisible();
		}

		return item;
	}
}
