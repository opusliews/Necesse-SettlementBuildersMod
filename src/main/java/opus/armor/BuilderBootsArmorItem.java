package opus.armor;

import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.BootsArmorItem;
import necesse.inventory.lootTable.presets.CosmeticArmorLootTable;

public class BuilderBootsArmorItem extends BootsArmorItem {
   public BuilderBootsArmorItem() {
      super(0, 0, Item.Rarity.COMMON, "builderboots", CosmeticArmorLootTable.cosmeticArmor);
   }
}
