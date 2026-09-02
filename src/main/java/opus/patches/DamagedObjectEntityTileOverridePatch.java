package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import necesse.entity.TileDamageResult;
import net.bytebuddy.asm.Advice;
import opus.damage.DamageRepairLevelData;
import opus.damage.HardcoreDamage;

@ModMethodPatch(target = DamagedObjectEntity.class, name = "doTileDamageOverride", arguments = {int.class})
public class DamagedObjectEntityTileOverridePatch {
	@Advice.OnMethodExit
	static void onExit(
			@Advice.This DamagedObjectEntity entity,
			@Advice.Return TileDamageResult result
	) {
		if (!entity.isServer() || !HardcoreDamage.isServerEnabled() || result == null) {
			return;
		}

		DamageRepairLevelData data = DamageRepairLevelData.get(entity.getLevel(), true);
		data.recordDamage(result, -1);
	}
}
