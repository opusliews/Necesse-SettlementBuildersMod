package opus.damage;

import necesse.level.maps.Level;
import opus.SettlementBuilders;

public final class HardcoreDamage {
	private static boolean clientEnabled;

	private HardcoreDamage() {
	}

	public static boolean isServerEnabled() {
		return SettlementBuilders.settings.hardcoreDamage;
	}

	public static boolean isEnabled(Level level) {
		if (level == null) {
			return false;
		}

		if (level.isServer()) {
			return isServerEnabled();
		}

		return level.isClient() && clientEnabled;
	}

	public static void setClientEnabled(boolean enabled) {
		clientEnabled = enabled;
	}
}
