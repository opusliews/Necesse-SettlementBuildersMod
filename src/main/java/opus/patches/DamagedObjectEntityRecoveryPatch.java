package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import net.bytebuddy.asm.Advice;
import opus.damage.HardcoreDamage;

@ModMethodPatch(target = DamagedObjectEntity.class, name = "tickDamageRecovery", arguments = {})
public class DamagedObjectEntityRecoveryPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static boolean onEnter(@Advice.This DamagedObjectEntity entity) {
		return HardcoreDamage.isEnabled(entity.getLevel());
	}
}
