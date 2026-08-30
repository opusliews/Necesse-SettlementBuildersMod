package opus.blueprint;

import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawables.SortedDrawable;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.WallObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;

import java.awt.*;
import java.util.List;
import java.util.WeakHashMap;

public final class BlueprintAreaHud {
	private static final WeakHashMap<Level, HudDrawElement> hudElements =
			new WeakHashMap<>();

	private static final int tileSize = 32;
	private static final float ghostAlpha = 0.3F;
	private static final Color areaColor =
			new Color(0, 0, 155);
	private static final float areaOpacity = 0.3F;

	private BlueprintAreaHud() {
	}

	public static void ensureAdded(Level level) {
		if (level == null || hudElements.containsKey(level)) {
			return;
		}

		HudDrawElement element = new HudDrawElement() {
			@Override
			public void addDrawables(
					List<SortedDrawable> list,
					GameCamera camera,
					PlayerMob perspective
			) {
				list.add(new SortedDrawable() {
					@Override
					public int getPriority() {
						return -2000000;
					}

					@Override
					public void draw(TickManager tickManager) {
						drawBlueprintAreas(
								level,
								camera,
								perspective
						);
					}
				});
			}
		};

		level.hudManager.addElement(element);

		hudElements.put(
				level,
				element
		);
	}

	private static void drawBlueprintAreas(
			Level level,
			GameCamera camera,
			PlayerMob player
	) {
		BlueprintAreaManager manager =
				BlueprintAreaManager.get(level);

		draw(
				level,
				camera,
				player,
				manager
		);
	}

	public static void draw(
			Level level,
			GameCamera camera,
			PlayerMob player,
			BlueprintAreaManager manager
	) {
		if (level == null
				|| camera == null
				|| player == null
				|| manager == null
		) {
			return;
		}

		// Areas first, so ghosts render on top.
		for (BlueprintArea area : manager.getAreas()) {
			drawAreaBackground(
					camera,
					area
			);
		}

		for (BlueprintArea area : manager.getAreas()) {
			drawAreaGhosts(
					level,
					camera,
					player,
					area
			);
		}
	}

	private static void drawAreaBackground(
			GameCamera camera,
			BlueprintArea area
	) {
		int drawX =
				camera.getTileDrawX(area.getOriginX());

		int drawY =
				camera.getTileDrawY(area.getOriginY());

		int drawWidth =
				area.getWidth() * tileSize;

		int drawHeight =
				area.getHeight() * tileSize;

		Renderer.initQuadDraw(
						drawWidth,
						drawHeight
				)
				.color(
						areaColor.getRed() / 255.0F,
						areaColor.getGreen() / 255.0F,
						areaColor.getBlue() / 255.0F,
						areaOpacity
				)
				.draw(
						drawX,
						drawY
				);
	}

	private static void drawAreaGhosts(
			Level level,
			GameCamera camera,
			PlayerMob player,
			BlueprintArea area
	) {
		BlueprintData blueprintData =
				area.getBlueprintData();

		if (blueprintData == null) {
			return;
		}

		int originX =
				area.getOriginX();

		int originY =
				area.getOriginY();

		for (
				BlueprintElement element :
				blueprintData.getElements()
		) {
			int tileX =
					originX + element.getX();

			int tileY =
					originY + element.getY();

			drawElement(
					level,
					camera,
					player,
					element,
					tileX,
					tileY
			);
		}
	}

	private static void drawElement(
			Level level,
			GameCamera camera,
			PlayerMob player,
			BlueprintElement element,
			int tileX,
			int tileY
	) {
		if (element.getTileID() != null) {
			GameTile tile =
					TileRegistry.getTile(
							element.getTileID()
					);

			if (tile != null) {
				tile.drawPreview(
						level,
						tileX,
						tileY,
						ghostAlpha,
						player,
						camera
				);
			}
		}

		if (element.getObjectID() != null) {
			GameObject object =
					ObjectRegistry.getObject(
							element.getObjectID()
					);

			if (object != null) {
				if (object instanceof WallObject) {
					drawWallPreview(
							(WallObject)object,
							level,
							tileX,
							tileY,
							player,
							camera
					);
				} else {
					object.drawMultiTilePreview(
							level,
							tileX,
							tileY,
							element.getRotation(),
							0.5f,
							player,
							camera
					);
				}
			}
		}
	}

	private static void drawWallPreview(
			WallObject wall,
			Level level,
			int tileX,
			int tileY,
			PlayerMob player,
			GameCamera camera
	) {
		SharedTextureDrawOptions options =
				new SharedTextureDrawOptions(
						WallObject.generatedWallTexture
				);

		boolean previousSmoothLighting =
				Settings.smoothLighting;

		try {
			Settings.smoothLighting = false;

			wall.addWallDrawOptions(
					options,
					level,
					tileX,
					tileY,
					level.lightManager.newLight(150.0F),
					null,
					camera,
					// Deliberately null:
					// blueprint ghosts should not fade based on player position.
					null
			);
		} finally {
			Settings.smoothLighting =
					previousSmoothLighting;
		}

		options.forEachDraw(draw -> {
			draw.alpha(0.5F);
		}).draw();
	}

}