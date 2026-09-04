package opus.damage;

public enum WeatheringMaterialTier {
	WOOD(0, true),
	SURFACE_MASONRY(1, true),
	REINFORCED(2, true),
	ADVANCED_BIOME_STONE(3, true),
	LATE_GAME_MASONRY(4, false);

	private final int tier;
	private final boolean weatherable;

	WeatheringMaterialTier(int tier, boolean weatherable) {
		this.tier = tier;
		this.weatherable = weatherable;
	}

	public int getTier() {
		return tier;
	}

	public int getTimeMultiplier() {
		return tier + 1;
	}

	public boolean isWeatherable() {
		return weatherable;
	}
}
