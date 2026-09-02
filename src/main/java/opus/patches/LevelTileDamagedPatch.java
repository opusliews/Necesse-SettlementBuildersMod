package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.TileDamageResult;
import necesse.entity.mobs.Attacker;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opus.damage.DamageRepairLevelData;
import opus.damage.HardcoreDamage;

@ModMethodPatch(
		target = Level.class,
		name = "onTileDamaged",
		arguments = {GameTile.class, int.class, int.class, Attacker.class, ServerClient.class, TileDamageResult.class}
)
public class LevelTileDamagedPatch {
	@Advice.OnMethodEnter
	static void onEnter(
			@Advice.This Level level,
			@Advice.Argument(5) TileDamageResult result
	) {
		if (!level.isServer() || !HardcoreDamage.isServerEnabled() || result == null) {
			return;
		}

		DamageRepairLevelData data = DamageRepairLevelData.get(level, true);
		data.recordDamage(result, -1);
	}
}
