package opus;

import necesse.engine.registries.PacketRegistry;
import opus.armor.BuilderBootsArmorItem;
import opus.armor.BuilderHatArmorItem;
import opus.armor.BuilderShirtArmorItem;
import opus.item.BlueprintItem;
import opus.mobs.BuilderHumanMob;
import necesse.engine.input.Control;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.MobRegistry;
import necesse.engine.registries.SettlerRegistry;
import opus.network.PacketBlueprintUpdate;
import opus.settler.BuilderSettler;

@ModEntry
public class SettlementBuilders {
    public void init() {
        System.out.println("Poop: Mod is running!");

        // Registrations
        SettlerRegistry.registerSettler("builder", new BuilderSettler());
        MobRegistry.registerMob("builderhuman", BuilderHumanMob.class, true);
        ItemRegistry.registerItem("builderhat", new BuilderHatArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("buildershirt", new BuilderShirtArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("builderboots", new BuilderBootsArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("blueprintItem", new BlueprintItem(), 10.0F, true);

        PacketRegistry.registerPacket(PacketBlueprintUpdate.class);
    }
}
