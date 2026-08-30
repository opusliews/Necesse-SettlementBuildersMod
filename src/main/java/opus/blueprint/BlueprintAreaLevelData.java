package opus.blueprint;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opus.logging.Logging;

public class BlueprintAreaLevelData extends LevelData {
    public static final String managerKey =
            "opusblueprintareas";

    private final BlueprintAreaManager manager =
            new BlueprintAreaManager();

    public BlueprintAreaLevelData() {
    }

    public BlueprintAreaManager getManager() {
        return manager;
    }

    public static BlueprintAreaLevelData get(
            Level level,
            boolean createNewIfNull
    ) {
        if (level == null) {
            return null;
        }

        LevelData existing =
                level.getLevelData(managerKey);

        if (existing instanceof BlueprintAreaLevelData) {
            return (BlueprintAreaLevelData)existing;
        }

        if (!createNewIfNull) {
            return null;
        }

        BlueprintAreaLevelData data =
                new BlueprintAreaLevelData();

        level.addLevelData(
                managerKey,
                data
        );

        return data;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);

        if (manager.size() > 0) {
            save.addSaveData(
                    manager.getSaveData()
            );
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);

        LoadData areasSave = save.getFirstLoadDataByName("BLUEPRINT_AREAS");

        if (areasSave != null) {
            manager.applyLoadData(areasSave);
        }

        Logging.logMessage(
                "Loaded BlueprintAreaLevelData:"
                        + " client = " + isClient()
                        + " server = " + isServer()
                        + " areas = " + manager.size()
        );
    }
}