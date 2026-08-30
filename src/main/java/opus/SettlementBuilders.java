package opus;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.*;
import necesse.entity.mobs.job.JobType;
import opus.armor.BuilderBootsArmorItem;
import opus.armor.BuilderHatArmorItem;
import opus.armor.BuilderShirtArmorItem;
import opus.blueprint.BlueprintAreaLevelData;
import opus.item.BlueprintItem;
import opus.logging.Logging;
import opus.mobs.BuilderHumanMob;
import opus.network.*;
import opus.settler.BuilderSettler;

@ModEntry
public class SettlementBuilders {
    public static int constructionJobTypeID;

    public void init() {
        Logging.logMessage("Mod is running!");

        // Registrations
        SettlerRegistry.registerSettler("builder", new BuilderSettler());
        MobRegistry.registerMob("builderhuman", BuilderHumanMob.class, true);
        ItemRegistry.registerItem("builderhat", new BuilderHatArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("buildershirt", new BuilderShirtArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("builderboots", new BuilderBootsArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("blueprintItem", new BlueprintItem(), 10.0F, true);

        LevelDataRegistry.registerLevelData("opusblueprintareas", BlueprintAreaLevelData.class);

        constructionJobTypeID = JobTypeRegistry.registerType(
                "construction",
                new JobType(
                        true,
                        true,
                        new LocalMessage("jobs", "constructionname"),
                        new LocalMessage("jobs", "constructiontip")
                )
        );

        PacketRegistry.registerPacket(PacketBlueprintUpdate.class);
        PacketRegistry.registerPacket(PacketPlaceBlueprintArea.class);
        PacketRegistry.registerPacket(PacketRequestBlueprintAreas.class);
        PacketRegistry.registerPacket(PacketSyncBlueprintAreas.class);
        PacketRegistry.registerPacket(PacketAddBlueprintArea.class);
    }
}
