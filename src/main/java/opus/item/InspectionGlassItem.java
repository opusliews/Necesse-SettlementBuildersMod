package opus.item;

import necesse.engine.localization.Localization;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

public class InspectionGlassItem extends Item {
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
		tooltips.add(Localization.translate("itemtooltip", "inspectionglasstip"));
		return tooltips;
	}
}
