package armor;

import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.ChestArmorItem;
import necesse.inventory.lootTable.presets.CosmeticArmorLootTable;

public class BuilderShirtArmorItem extends ChestArmorItem {
   public BuilderShirtArmorItem() {
      super(0, 0, Item.Rarity.COMMON, "buildershirt", "buildershirtarms", CosmeticArmorLootTable.cosmeticArmor);
   }
}
