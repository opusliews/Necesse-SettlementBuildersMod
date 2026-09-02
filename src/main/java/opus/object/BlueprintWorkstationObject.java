package opus.object;

import necesse.engine.localization.Localization;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.container.InventoryObject;
import necesse.level.maps.Level;
import opus.SettlementBuilders;
import opus.container.BlueprintWorkstationContainer;

import java.awt.*;

public class BlueprintWorkstationObject extends InventoryObject {
	public BlueprintWorkstationObject() {
		super(
				"blueprintworkstation",
				1,
				new Rectangle(4, 4, 24, 24),
				ToolType.ALL,
				new Color(126, 84, 55)
		);
		this.objectHealth = 100;
		this.setItemCategory(new String[]{"objects", "misc"});
		this.setCraftingCategory(new String[]{"objects", "misc"});
	}

	@Override
	public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
		return Localization.translate("controls", "opentip");
	}

	@Override
	public void interact(Level level, int x, int y, PlayerMob player) {
		if (level.isClient()) {
			playInteractSound(level, player, x, y, false, false);
		}

		if (level.isServer()) {
			BlueprintWorkstationContainer.openAndSendContainer(
					SettlementBuilders.blueprintWorkstationContainerID,
					player.getServerClient(),
					level,
					x,
					y
			);
		}
	}

	@Override
	public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
		return new BlueprintWorkstationObjectEntity(level, x, y);
	}
}
