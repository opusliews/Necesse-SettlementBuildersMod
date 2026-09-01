package opus.mobs;

import necesse.engine.expeditions.SettlerExpedition;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.entity.mobs.job.WorkInventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.lootTable.LootTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

public class BuilderHumanMob extends HumanShop {
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
        this.shop.addSellingItem("blueprintItem", new SellingShopItem()).setStaticPriceBasedOnHappiness(100, 200, 20);
    }

    @Override
    public void init() {
        super.init();

        this.jobTypeHandler.globalCooldown = 0L;
    }

    public LootTable getLootTable() {
        return super.getLootTable();
    }

    protected ArrayList<GameMessage> getMessages(ServerClient client) {
        ArrayList<GameMessage> out = this.getLocalMessages("buildertalk", 7);

        return out;
    }

    public boolean canDoExpedition(SettlerExpedition expedition) {
        return false;
    }

    public List getPossibleExpeditions() {
        return Collections.emptyList();
    }



    public List<InventoryItem> getRecruitItems(ServerClient client) {
        if (this.isTrapped()) {
            return Collections.emptyList();
        } else {
            GameRandom random = new GameRandom((long)this.getSettlerSeed() * 227L);
            if (this.isVisitor()) {
                return Collections.singletonList(new InventoryItem("coin", random.getIntBetween(250, 400)));
            } else {
                return Collections.emptyList();
            }
        }
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
                if (getTotalItemStacks() > 4) {
                    return 0;
                }

                return item.getAmount();
            }

            @Override
            public boolean isFull() {
                return getTotalItemStacks() > 4;
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
