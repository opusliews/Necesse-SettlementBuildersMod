package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opus.damage.DamageRepairLevelData;
import opus.damage.HardcoreDamage;
import opus.damage.WeatheringLevelData;
import opus.settler.BuilderRequestLevelData;

@ModMethodPatch(target = Level.class, name = "onLoadingComplete", arguments = {})
public class LevelLoadingCompletePatch {
	@Advice.OnMethodEnter
	static void onEnter(@Advice.This Level level) {
		if (!level.isServer()) {
			return;
		}

		BuilderRequestLevelData.get(level, true);

		if (HardcoreDamage.isServerEnabled()) {
			DamageRepairLevelData.get(level, true);
			WeatheringLevelData.get(level, true);
		}
	}
}
