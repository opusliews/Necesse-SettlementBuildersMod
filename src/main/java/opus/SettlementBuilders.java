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
import opus.jobs.ConstructionLevelJob;
import opus.mobs.BuilderHumanMob;
import opus.network.*;
import opus.settler.BuilderSettler;

@ModEntry
public class SettlementBuilders {
    public void init() {
        // Registrations
        SettlerRegistry.registerSettler("builder", new BuilderSettler());
        MobRegistry.registerMob("builderhuman", BuilderHumanMob.class, true);
        ItemRegistry.registerItem("builderhat", new BuilderHatArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("buildershirt", new BuilderShirtArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("builderboots", new BuilderBootsArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("blueprintItem", new BlueprintItem(), 10.0F, true);

        LevelDataRegistry.registerLevelData(BlueprintAreaLevelData.managerKey, BlueprintAreaLevelData.class);

        JobTypeRegistry.registerType(
                "construction",
                new JobType(
                        true,
                        true,
                        new LocalMessage("jobs", "constructionname"),
                        new LocalMessage("jobs", "constructiontip")
                )
        );

        LevelJobRegistry.registerJob(
                "construction",
                ConstructionLevelJob.class,
                ConstructionLevelJob::handler,
                "construction"
        );

        PacketRegistry.registerPacket(PacketBlueprintUpdate.class);
        PacketRegistry.registerPacket(PacketPlaceBlueprintArea.class);
        PacketRegistry.registerPacket(PacketRequestBlueprintAreas.class);
        PacketRegistry.registerPacket(PacketSyncBlueprintAreas.class);
        PacketRegistry.registerPacket(PacketAddBlueprintArea.class);
        PacketRegistry.registerPacket(PacketRemoveBlueprintArea.class);
        PacketRegistry.registerPacket(PacketBuilderTilePlaceSound.class);
        PacketRegistry.registerPacket(PacketBuilderObjectPlaceSound.class);
    }
}
