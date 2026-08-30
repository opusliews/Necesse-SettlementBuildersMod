package opus.blueprint;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import opus.tools.BlueprintData;

import java.util.*;

public class BlueprintAreaManager {
	private final Map<String, BlueprintArea> areas =
			new LinkedHashMap<>();

	public static BlueprintAreaManager get(Level level) {
		BlueprintAreaLevelData levelData =
				BlueprintAreaLevelData.get(
						level,
						true
				);

		return levelData.getManager();

	}

	public BlueprintArea addArea(
		int originX,
		int originY,
		int width,
		int height,
		BlueprintData blueprintData
	) {
		if (blueprintData == null) {
			throw new IllegalArgumentException(
				"blueprintData cannot be null"
			);
		}

		BlueprintArea area =
			new BlueprintArea(
				originX,
				originY,
				width,
				height,
				blueprintData
			);

		areas.put(
			area.getUniqueID(),
			area
		);

		return area;
	}

	public void addArea(BlueprintArea area) {
		if (area == null) {
			throw new IllegalArgumentException(
				"area cannot be null"
			);
		}

		areas.put(
			area.getUniqueID(),
			area
		);
	}

	public BlueprintArea getArea(String uniqueID) {
		return areas.get(uniqueID);
	}

	public boolean removeArea(String uniqueID) {
		return areas.remove(uniqueID) != null;
	}

	public boolean containsArea(String uniqueID) {
		return areas.containsKey(uniqueID);
	}

	public Collection<BlueprintArea> getAreas() {
		return Collections.unmodifiableCollection(
			areas.values()
		);
	}

	public ArrayList<BlueprintArea> getAreasAtTile(
		int tileX,
		int tileY
	) {
		ArrayList<BlueprintArea> result =
			new ArrayList<>();

		for (BlueprintArea area : areas.values()) {
			if (area.containsTile(tileX, tileY)) {
				result.add(area);
			}
		}

		return result;
	}

	public void clear() {
		areas.clear();
	}

	public int size() {
		return areas.size();
	}

	public SaveData getSaveData() {
		SaveData save =
			new SaveData("BLUEPRINT_AREAS");

		for (BlueprintArea area : areas.values()) {
			save.addSaveData(
				area.getSaveData()
			);
		}

		return save;
	}

	public void applyLoadData(LoadData load) {
		areas.clear();

		for (
			LoadData areaLoad :
			load.getLoadDataByName("BLUEPRINT_AREA")
		) {
			BlueprintArea area =
				BlueprintArea.fromLoadData(
					areaLoad
				);

			areas.put(
				area.getUniqueID(),
				area
			);
		}
	}
}