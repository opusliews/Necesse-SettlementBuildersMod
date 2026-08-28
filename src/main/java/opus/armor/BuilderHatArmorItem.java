package opus.armor;

import necesse.entity.mobs.gameDamageType.DamageType;
import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.armorItem.SetHelmetArmorItem;
import necesse.inventory.lootTable.presets.CosmeticArmorLootTable;
import necesse.inventory.lootTable.presets.CosmeticSetArmorLootTable;

public class BuilderHatArmorItem extends SetHelmetArmorItem {
   public BuilderHatArmorItem() {
      super(0, (DamageType)null, 0, CosmeticArmorLootTable.cosmeticArmor, CosmeticSetArmorLootTable.cosmeticSetArmor, Item.Rarity.COMMON, "builderhat", "buildershirt", "builderboots", (String)null);
      this.facialFeatureDrawOptions = ArmorItem.FacialFeatureDrawMode.OVER_FACIAL_FEATURE;
      this.hairDrawOptions = ArmorItem.HairDrawMode.OVER_HAIR;
      this.hairMaskTextureName = "safarihat_hardhat_minerhat_hairmask";
   }
}
