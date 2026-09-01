package opus.settler;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.HumanLook;
import necesse.gfx.drawOptions.human.HumanDrawOptions;
import necesse.inventory.InventoryItem;
import necesse.level.maps.levelData.settlementData.settler.Settler;

public class BuilderSettler extends Settler {
    public BuilderSettler() {
        super("builderhuman");
    }

    @Override
    public GameMessage getAcquireTip() {
        return new LocalMessage("settlement", "buildertip");
    }

    @Override
    public void setDefaultArmor(HumanDrawOptions drawOptions, int settlerSeed, HumanLook look, boolean customLook) {
        drawOptions.helmet(new InventoryItem("builderhat"));
        drawOptions.chestplate(new InventoryItem("buildershirt"));
        drawOptions.boots(new InventoryItem("builderboots"));
    }

}
