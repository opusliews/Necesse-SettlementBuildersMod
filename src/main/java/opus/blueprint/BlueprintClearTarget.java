package opus.blueprint;

public class BlueprintClearTarget {
	public enum Type {
		TILE,
		OBJECT
	}

	public final Type type;
	public final int tileX;
	public final int tileY;

	public BlueprintClearTarget(Type type, int tileX, int tileY) {
		this.type = type;
		this.tileX = tileX;
		this.tileY = tileY;
	}
}
