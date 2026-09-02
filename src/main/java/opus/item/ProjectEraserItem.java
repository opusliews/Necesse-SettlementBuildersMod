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
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import opus.network.PacketEraseBlueprintProject;

public class ProjectEraserItem extends Item {
	public ProjectEraserItem() {
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
		tooltips.add(Localization.translate("itemtooltip", "projecterasertip"));
		return tooltips;
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
		if (!attackerMob.isPlayer
				|| !level.isClient()
				|| !(slot instanceof PlayerInventoryItemAttackSlot)
				|| !(GlobalData.getCurrentState() instanceof MainGame)) {
			return item;
		}

		PlayerInventoryItemAttackSlot playerSlot = (PlayerInventoryItemAttackSlot)slot;
		MainGame mainGame = (MainGame)GlobalData.getCurrentState();

		mainGame.getClient().network.sendPacket(
				new PacketEraseBlueprintProject(
						x / 32,
						y / 32,
						playerSlot.slot.inventoryID,
						playerSlot.slot.slot
				)
		);

		return item;
	}
}