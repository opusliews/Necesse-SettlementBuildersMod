package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.ObjectDamageResult;
import necesse.entity.mobs.Attacker;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opus.damage.DamageRepairLevelData;
import opus.damage.HardcoreDamage;

@ModMethodPatch(
		target = Level.class,
		name = "onObjectDamaged",
		arguments = {GameObject.class, int.class, int.class, int.class, Attacker.class, ServerClient.class, ObjectDamageResult.class}
)
public class LevelObjectDamagedPatch {
	@Advice.OnMethodEnter
	static void onEnter(
			@Advice.This Level level,
			@Advice.Argument(1) int objectLayerID,
			@Advice.Argument(6) ObjectDamageResult result
	) {
		if (!level.isServer() || !HardcoreDamage.isServerEnabled() || result == null) {
			return;
		}

		DamageRepairLevelData data = DamageRepairLevelData.get(level, true);
		data.recordDamage(result, objectLayerID);
	}
}
