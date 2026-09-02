package opus.object;

import necesse.entity.objectEntity.InventoryObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import opus.item.BlueprintItem;

public class BlueprintWorkstationObjectEntity extends InventoryObjectEntity {
	public BlueprintWorkstationObjectEntity(Level level, int x, int y) {
		super(level, x, y, 1);
	}

	@Override
	public boolean isItemValid(int slot, InventoryItem item) {
		return slot == 0 && (item == null || item.item instanceof BlueprintItem);
	}

	@Override
	public int getItemStackLimit(int slot, InventoryItem item) {
		return 1;
	}

	@Override
	public boolean canSetInventoryName() {
		return false;
	}
}
