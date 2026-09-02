package opus.object;

import necesse.inventory.item.Item;
import necesse.level.gameObject.PaintingObject;
import necesse.level.maps.Level;

public class BuilderJobRequestBulletinObject extends PaintingObject {
	public static final String stringID = "builderjobrequestbulletin";

	public BuilderJobRequestBulletinObject() {
		super(Item.Rarity.COMMON);
		this.texturePath = "builderjobrequestbulletin";
		this.setItemCategory("objects", "misc");
		this.setCraftingCategory("objects", "misc");
	}

	@Override
	public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
		if (rotation != 2) {
			return "tilecovered";
		}

		return super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
	}
}
