package opus.mobs;

import necesse.engine.expeditions.SettlerExpedition;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.inventory.InventoryItem;
import necesse.inventory.lootTable.LootTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuilderHumanMob extends HumanShop {
    public BuilderHumanMob() {
        super(500, 200, "construction");
        this.attackCooldown = 500;
        this.attackAnimTime = 500;
        this.setSwimSpeed(1.0F);
        this.jobTypeHandler.getPriority("fishing").disabledBySettler = false;
        this.equipmentInventory.setItem(6, new InventoryItem("coppersword"));
        this.shop.addSellingItem("builderhat", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
        this.shop.addSellingItem("buildershirt", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
        this.shop.addSellingItem("builderboots", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
        this.shop.addSellingItem("blueprintitem", new SellingShopItem()).setStaticPriceBasedOnHappiness(100, 200, 20);
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
}
