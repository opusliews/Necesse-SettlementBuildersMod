package opus.blueprint;

import necesse.inventory.InventoryItem;
import opus.mobs.BuilderHumanMob;

public class BuilderMaterialSource {
	public final BuilderHumanMob builder;
	public final InventoryItem item;

	public BuilderMaterialSource(BuilderHumanMob builder, InventoryItem item) {
		this.builder = builder;
		this.item = item;
	}
}