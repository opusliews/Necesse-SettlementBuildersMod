package opus.blueprint;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.regionSystem.Region;
import opus.jobs.ConstructionLevelJob;
import opus.logging.Logging;

import java.awt.*;

public class BlueprintAreaLevelData extends LevelData implements RegionLoadedListenerEntityComponent {
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
    public void onRegionLoaded(Region region) {
        if (!isServer()) {
            return;
        }

        for (BlueprintArea area : manager.getAreas()) {
            if (area.isConstructionComplete()) {
                continue;
            }

            for (Point workTile : area.getOutsideBorderTiles()) {
                if (workTile.x < region.tileXOffset
                        || workTile.y < region.tileYOffset
                        || workTile.x >= region.tileXOffset + region.tileWidth
                        || workTile.y >= region.tileYOffset + region.tileHeight) {
                    continue;
                }

                if (level.isSolidTile(workTile.x, workTile.y)) {
                    continue;
                }

                ConstructionLevelJob job = new ConstructionLevelJob(
                        workTile.x,
                        workTile.y,
                        area.getUniqueID()
                );

                if (level.jobsLayer.addJob(job) != null) {
                    Logging.logMessage(
                            "Recreated ConstructionLevelJob after region load at "
                                    + workTile.x + ", " + workTile.y
                    );
                }
            }
        }
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