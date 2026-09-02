package opus.config;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

public class SettlementBuildersSettings extends ModSettings {
	public boolean hardcoreDamage = true;

	@Override
	public void addSaveData(SaveData save) {
		save.addBoolean(
				"hardcoreDamage",
				hardcoreDamage,
				"If true, damaged tiles and objects do not recover naturally, Builders repair them, and exposed wooden objects weather in rain"
		);
	}

	@Override
	public void applyLoadData(LoadData save) {
		hardcoreDamage = save.getBoolean("hardcoreDamage", true, false);
	}
}
