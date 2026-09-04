package opus;

import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.events.ServerClientConnectedEvent;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.*;
import necesse.entity.mobs.job.JobType;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import opus.armor.BuilderBootsArmorItem;
import opus.armor.BuilderHatArmorItem;
import opus.armor.BuilderShirtArmorItem;
import opus.blueprint.BlueprintAreaLevelData;
import opus.config.SettlementBuildersSettings;
import opus.container.BlueprintWorkstationContainer;
import opus.damage.DamageRepairLevelData;
import opus.damage.HardcoreDamage;
import opus.damage.WoodWeatheringLevelData;
import opus.forms.BlueprintWorkstationContainerForm;
import opus.item.BlueprintItem;
import opus.item.ProjectEraserItem;
import opus.jobs.ConstructionLevelJob;
import opus.jobs.RepairLevelJob;
import opus.mobs.BuilderHumanMob;
import opus.network.*;
import opus.object.BlueprintWorkstationObject;
import opus.object.BlueprintWorkstationObjectEntity;
import opus.object.BuilderJobRequestBulletinObject;
import opus.settler.BuilderRequestLevelData;
import opus.settler.BuilderSettler;

@ModEntry
public class SettlementBuilders {
    public static final SettlementBuildersSettings settings = new SettlementBuildersSettings();
    public static int blueprintWorkstationContainerID;

    public ModSettings initSettings() {
        return settings;
    }

    public void init() {
        // Registrations
        SettlerRegistry.registerSettler("builder", new BuilderSettler());
        MobRegistry.registerMob("builderhuman",
                BuilderHumanMob.class, true);
        ItemRegistry.registerItem("builderhat",
                new BuilderHatArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("buildershirt",
                new BuilderShirtArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("builderboots",
                new BuilderBootsArmorItem(), 50.0F, true);
        ItemRegistry.registerItem("blueprintItem",
                new BlueprintItem(), 25.0F, true);
        ItemRegistry.registerItem("projecteraser",
                new ProjectEraserItem(), 30.0F, true);
        ObjectRegistry.registerObject("blueprintworkstation",
                new BlueprintWorkstationObject(), 100.0F, true);
        ObjectRegistry.registerObject(
                BuilderJobRequestBulletinObject.stringID,
                new BuilderJobRequestBulletinObject(), 25.0F, true);


        blueprintWorkstationContainerID = ContainerRegistry.registerSettlementDependantOEContainer(
                (client, uniqueSeed, settlement, oe, content) -> new BlueprintWorkstationContainerForm(
                        client,
                        new BlueprintWorkstationContainer(
                                client.getClient(),
                                uniqueSeed,
                                settlement,
                                (BlueprintWorkstationObjectEntity)oe,
                                new PacketReader(content)
                        )
                ),
                (client, uniqueSeed, settlement, oe, content, serverObject) -> new BlueprintWorkstationContainer(
                        client,
                        uniqueSeed,
                        settlement,
                        (BlueprintWorkstationObjectEntity)oe,
                        new PacketReader(content)
                )
        );

        LevelDataRegistry.registerLevelData(BlueprintAreaLevelData.managerKey, BlueprintAreaLevelData.class);
        LevelDataRegistry.registerLevelData(DamageRepairLevelData.managerKey, DamageRepairLevelData.class);
        LevelDataRegistry.registerLevelData(WoodWeatheringLevelData.managerKey, WoodWeatheringLevelData.class);
        LevelDataRegistry.registerLevelData(BuilderRequestLevelData.managerKey, BuilderRequestLevelData.class);

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

        LevelJobRegistry.registerJob(
                "repair",
                RepairLevelJob.class,
                RepairLevelJob::handler,
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
        PacketRegistry.registerPacket(PacketHardcoreDamageSetting.class);
        PacketRegistry.registerPacket(PacketEraseBlueprintProject.class);
        PacketRegistry.registerPacket(PacketBlueprintBlockedState.class);
        PacketRegistry.registerPacket(PacketBuilderRoadRepairToggle.class);

        GameEvents.addListener(
                ServerClientConnectedEvent.class,
                new GameEventListener<ServerClientConnectedEvent>() {
                    @Override
                    public void onEvent(ServerClientConnectedEvent event) {
                        event.client.sendPacket(
                                new PacketHardcoreDamageSetting(
                                        HardcoreDamage.isServerEnabled()
                                )
                        );
                    }
                }
        );
    }

    public void postInit() {
        Recipes.registerModRecipe(new Recipe(
                "blueprintItem",
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("stackofpaper", 1),
                        new Ingredient("quillandparchment", 1)
                }
        ));

        Recipes.registerModRecipe(new Recipe(
                "blueprintworkstation",
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("anylog", 15),
                        new Ingredient("tungstenbar", 3),
                        new Ingredient("stackofpaper", 1)
                }
        ));

        Recipes.registerModRecipe(new Recipe(
                "builderhat",
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("wool", 12),
                        new Ingredient("ironbar", 1)
                }
        ));

        Recipes.registerModRecipe(new Recipe(
                "buildershirt",
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("wool", 16)
                }
        ));

        Recipes.registerModRecipe(new Recipe(
                "builderboots",
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("wool", 8),
                        new Ingredient("leather", 1)
                }
        ));

        Recipes.registerModRecipe(new Recipe(
                "projecteraser",
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("quillandparchment", 1),
                        new Ingredient("ironbar", 1)
                }
        ));

        Recipes.registerModRecipe(new Recipe(
                BuilderJobRequestBulletinObject.stringID,
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("stackofpaper", 1),
                        new Ingredient("quillandparchment", 1)
                }
        ));
    }
}
