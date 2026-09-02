package opus.mobs;

import necesse.engine.expeditions.SettlerExpedition;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.SettlerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.util.GameRandom;
import necesse.engine.world.WorldFile;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.job.WorkInventory;
import necesse.inventory.InventoryItem;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

public class BuilderHumanMob extends HumanShop {
    public static final int maxWorkInventoryStacks = 5;
    private static final int buildersPerRecruitTier = 3;

    private static final String[][] recruitBarTiers = {
            {"copperbar", "ironbar", "goldbar"},
            {"goldbar", "demonicbar"},
            {"demonicbar", "glacialbar"},
            {"glacialbar", "tungstenbar"},
            {"tungstenbar", "ivybar"},
            {"ivybar", "myceliumbar"},
            {"myceliumbar", "ancientfossilbar"},
            {"myceliumbar", "ancientfossilbar"} // Repeated intentionaly for last tier
    };

    private static final String[][] recruitRockTiers = {
            {"stone"},
            {"stone", "sandstone"},
            {"sandstone", "deepsnowstone"},
            {"deepsnowstone", "deepstone"},
            {"deepstone", "swampstone"},
            {"swampstone", "deepswampstone"},
            {"deepswampstone", "deepsandstone"},
            {"deepswampstone", "deepsandstone"} // Repeated intentionaly for last tier
    };

    private static final String[] recruitCrystals = {
            "amethyst",
            "emerald",
            "ruby",
            "sapphire",
            "topaz"
    };

    public BuilderHumanMob() {
        super(500, 200, "builder");
        this.attackCooldown = 500;
        this.attackAnimTime = 500;
        this.setSwimSpeed(1.0F);
        this.jobTypeHandler.getPriority("construction").disabledBySettler = false;
        this.equipmentInventory.setItem(6, new InventoryItem("coppersword"));
        this.shop.addSellingItem("builderhat", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
        this.shop.addSellingItem("buildershirt", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
        this.shop.addSellingItem("builderboots", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
        this.shop.addSellingItem("blueprintItem", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 125, 20);
        this.shop.addSellingItem("projecteraser", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
    }

    @Override
    public void init() {
        super.init();

        this.jobTypeHandler.globalCooldown = 0L;
    }

    public int getWorkActionDelay() {
        int happiness = Math.max(0, Math.min(100, getSettlerHappiness()));
        return 5000 - happiness * 45;
    }

    @Override
    protected ArrayList<GameMessage> getMessages(ServerClient client) {
        return this.getLocalMessages("buildertalk", 7);
    }

    @Override
    public boolean canDoExpedition(SettlerExpedition expedition) {
        return false;
    }

    @Override
    public List getPossibleExpeditions() {
        return Collections.emptyList();
    }


    @Override
    public List<InventoryItem> getRecruitItems(ServerClient client) {
        int tier = getRecruitTier(client);

        GameRandom random = new GameRandom(
                (long)this.getSettlerSeed() * 227L
                        + tier * 7919L
        );

        String barID = getRecruitBarID(random, tier);
        String rockID = getRecruitRockID(random, tier);

        ArrayList<InventoryItem> items = new ArrayList<>();

        items.add(
                new InventoryItem(
                        barID,
                        random.getIntBetween(4, 10)
                )
        );

        items.add(
                new InventoryItem(
                        rockID,
                        random.getIntBetween(25, 50)
                )
        );

        if (tier == 7) {
            String crystalID = getRecruitCrystalID(random);

            items.add(
                    new InventoryItem(
                            crystalID,
                            random.getIntBetween(3, 7)
                    )
            );
        }

        return items;
    }

    private int getCombinedBuilderCount(ServerClient client) {
        SettlementsWorldData settlementsData =
                SettlementsWorldData.getSettlementsData(client.getServer());

        int builderCount = 0;

        for (Object object : settlementsData.collectCachedSettlements(
                cached -> ((CachedSettlementData)cached).hasAccess(client)
        )) {
            CachedSettlementData cached = (CachedSettlementData)object;

            ServerSettlementData settlement =
                    settlementsData.getServerData(cached.uniqueID);

            if (settlement != null) {
                builderCount += settlement.getSettlerCount(
                        SettlerRegistry.getSettler("builder")
                );
            } else {
                builderCount += getSavedBuilderCount(
                        client,
                        cached.uniqueID
                );
            }
        }

        return builderCount;
    }

    private int getSavedBuilderCount(
            ServerClient client,
            int settlementUniqueID
    ) {
        try {
            WorldFile file = client
                    .getServer()
                    .world
                    .fileSystem
                    .getSettlementFile(settlementUniqueID);

            if (!file.exists()) {
                return 0;
            }

            LoadData save = new LoadData(file);
            LoadData serverSave =
                    save.getFirstLoadDataByName("SERVER");

            if (serverSave == null) {
                return 0;
            }

            LoadData settlersSave =
                    serverSave.getFirstLoadDataByName("SETTLERS");

            if (settlersSave == null) {
                return 0;
            }

            int count = 0;

            for (Object object : settlersSave.getLoadDataByName("SETTLER")) {
                LoadData settlerLoad = (LoadData)object;

                if ("builder".equals(
                        settlerLoad.getUnsafeString(
                                "stringID",
                                null,
                                false
                        )
                )) {
                    count++;
                }
            }

            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private int getRecruitTier(ServerClient client) {
        int builderCount = getCombinedBuilderCount(client);

        return Math.min(
                builderCount / buildersPerRecruitTier,
                recruitBarTiers.length - 1
        );
    }

    private String getRecruitBarID(GameRandom random, int tier) {
        String[] options = recruitBarTiers[tier];
        return options[random.nextInt(options.length)];
    }

    private String getRecruitRockID(GameRandom random, int tier) {
        String[] options = recruitRockTiers[
                Math.min(tier, recruitRockTiers.length - 1)
                ];

        return options[random.nextInt(options.length)];
    }

    private String getRecruitCrystalID(GameRandom random) {
        return recruitCrystals[random.nextInt(recruitCrystals.length)];
    }

    // Inventory overrides, to remove broker value limits to builder inventory
    @Override
    public WorkInventory getWorkInventory() {
        WorkInventory parent = super.getWorkInventory();

        return new WorkInventory() {
            @Override
            public ListIterator listIterator() {
                return parent.listIterator();
            }

            @Override
            public Iterable items() {
                return parent.items();
            }

            @Override
            public Stream stream() {
                return parent.stream();
            }

            @Override
            public void markDirty() {
                parent.markDirty();
            }

            @Override
            public void add(InventoryItem item) {
                parent.add(item);
            }

            @Override
            public int getCanAddAmount(InventoryItem item) {
                if (getTotalItemStacks() >= maxWorkInventoryStacks) {
                    return 0;
                }

                return item.getAmount();
            }

            @Override
            public boolean isFull() {
                return getTotalItemStacks() >= maxWorkInventoryStacks;
            }

            @Override
            public int getTotalItemStacks() {
                return parent.getTotalItemStacks();
            }

            @Override
            public boolean isEmpty() {
                return parent.isEmpty();
            }
        };
    }
}
