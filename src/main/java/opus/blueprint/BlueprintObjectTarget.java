package opus.blueprint;

public class BlueprintObjectTarget {
	public final int tileX;
	public final int tileY;
	public final String objectID;
	public final int rotation;

	public BlueprintObjectTarget(int tileX, int tileY, String objectID, int rotation) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.objectID = objectID;
		this.rotation = rotation;
	}
}