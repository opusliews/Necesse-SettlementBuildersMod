package opus.tools;

import necesse.engine.GlobalData;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.gameTool.GameTool;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerEvent;
import necesse.engine.localization.Localization;
import necesse.engine.state.State;
import necesse.engine.util.Zoning;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.InputTooltip;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlueprintSelectionTool implements GameTool {
	private final Level level;

	private HudDrawElement hudElement;

	private Point mouseDownTile;
	private Point ctrlMouseDownTile;
	private GameTexture xIcon;

	private Rectangle selection;

	private final Set<Point> excludedTiles = new HashSet<>();

	private final Runnable onCancelled;

	public BlueprintSelectionTool(Level level, Runnable onCancelled) {
		this.level = level;
		this.onCancelled = onCancelled;
	}

	@Override
	public void init() {
		xIcon = GameTexture.fromFile("icons/x_icon");

		this.level.hudManager.addElement(
				this.hudElement = new HudDrawElement() {
					@Override
					public void addDrawables(
							List list,
							GameCamera camera,
							PlayerMob perspective
					) {
						addSelectionDrawable(list, camera);
						addExcludedTileDrawables(list, camera);
					}
				}
		);
	}

	private void addSelectionDrawable(List list, GameCamera camera) {
		Rectangle rectangle = getCurrentSelection(camera);

		if (rectangle == null) {
			return;
		}

		Rectangle drawRectangle = new Rectangle(
				rectangle.x * 32,
				rectangle.y * 32,
				rectangle.width * 32,
				rectangle.height * 32
		);

		Color edgeColor = new Color(0, 255, 255, 170);
		Color fillColor = new Color(0, 255, 255, 80);

		SharedTextureDrawOptions drawOptions =
				Zoning.getRectangleDrawOptions(
						drawRectangle,
						edgeColor,
						fillColor,
						camera
				);

		list.add(
				new SortedDrawable() {
					@Override
					public int getPriority() {
						return -2000000;
					}

					@Override
					public void draw(TickManager tickManager) {
						drawOptions.draw();
					}
				}
		);
	}

	private void addExcludedTileDrawables(List list, GameCamera camera) {
		for (Point excludedTile : excludedTiles) {
			Point tile = new Point(excludedTile);

			Rectangle drawRectangle = new Rectangle(
					tile.x * 32,
					tile.y * 32,
					32,
					32
			);

			Color edgeColor = new Color(255, 0, 0, 220);
			Color fillColor = new Color(0, 0, 0, 0);

			SharedTextureDrawOptions tileDrawOptions =
					Zoning.getRectangleDrawOptions(
							drawRectangle,
							edgeColor,
							fillColor,
							camera
					);

			list.add(
					new SortedDrawable() {
						@Override
						public int getPriority() {
							return -1999999;
						}

						@Override
						public void draw(TickManager tickManager) {
							tileDrawOptions.draw();

							drawExcludedX(camera, tile);
						}
					}
			);
		}
	}

	private void drawExcludedX(GameCamera camera, Point tile) {
		int drawX = camera.getDrawX(tile.x * 32);
		int drawY = camera.getDrawY(tile.y * 32);

		xIcon.initDraw()
				.size(32, 32)
				.pos(drawX, drawY)
				.draw();
	}

	private Rectangle getCurrentSelection(GameCamera camera) {
		if (mouseDownTile == null) {
			return selection;
		}

		int tileX = camera.getMouseLevelTilePosX();
		int tileY = camera.getMouseLevelTilePosY();

		return makeRectangle(
				mouseDownTile.x,
				mouseDownTile.y,
				tileX,
				tileY
		);
	}

	private Rectangle makeRectangle(
			int x1,
			int y1,
			int x2,
			int y2
	) {
		int startX = Math.min(x1, x2);
		int startY = Math.min(y1, y2);

		int endX = Math.max(x1, x2);
		int endY = Math.max(y1, y2);

		return new Rectangle(
				startX,
				startY,
				endX - startX + 1,
				endY - startY + 1
		);
	}

	private boolean isCtrlDown() {
		return WindowManager
				.getWindow()
				.getInput()
				.isKeyDown(341)
				|| WindowManager
				.getWindow()
				.getInput()
				.isKeyDown(345);
	}

	private void toggleExcludedTile(int tileX, int tileY) {
		if (selection == null) {
			return;
		}

		if (!selection.contains(tileX, tileY)) {
			return;
		}

		Point tile = new Point(tileX, tileY);

		if (excludedTiles.contains(tile)) {
			excludedTiles.remove(tile);
		} else {
			excludedTiles.add(tile);
		}
	}

	@Override
	public boolean inputEvent(InputEvent event) {
		State currentState = GlobalData.getCurrentState();

		if (currentState == null) {
			return false;
		}

		if (currentState.getFormManager() != null
				&& currentState.getFormManager().isMouseOver(event)) {
			return false;
		}

		int tileX = currentState
				.getCamera()
				.getMouseLevelTilePosX(event);

		int tileY = currentState
				.getCamera()
				.getMouseLevelTilePosY(event);

		// Left mouse pressed
		if (!event.isUsed()
				&& event.state
				&& event.getID() == -100) {

			if (isCtrlDown()) {
				/*
				 * Ctrl-click only works if a selection already exists
				 * and the click happens inside it.
				 */
				if (selection != null
						&& selection.contains(tileX, tileY)) {

					ctrlMouseDownTile = new Point(
							tileX,
							tileY
					);

					event.use();
					return true;
				}

				return false;
			}

			/*
			 * Normal drag starts a new rectangle.
			 */
			mouseDownTile = new Point(tileX, tileY);

			event.use();
			return true;
		}

		// Left mouse released
		if (!event.state && event.getID() == -100) {

			/*
			 * Finish Ctrl-click.
			 *
			 * It only counts as a click if the mouse is released
			 * on exactly the same tile where it was pressed.
			 */
			if (ctrlMouseDownTile != null) {
				if (ctrlMouseDownTile.x == tileX
						&& ctrlMouseDownTile.y == tileY) {

					toggleExcludedTile(tileX, tileY);
				}

				ctrlMouseDownTile = null;

				event.use();
				return true;
			}

			// Finish normal box drag.
			if (mouseDownTile != null) {
				selection = makeRectangle(
						mouseDownTile.x,
						mouseDownTile.y,
						tileX,
						tileY
				);

				mouseDownTile = null;

				/*
				 * A new/re-drawn rectangle invalidates all previous
				 * "X" tile exclusions.
				 */
				excludedTiles.clear();

				onSelectionChanged(selection);

				event.use();
				return true;
			}
		}

		return false;
	}

	protected void onSelectionChanged(Rectangle selection) {
		System.out.println(
				"Blueprint selection: "
						+ selection.x + ", "
						+ selection.y + " - "
						+ selection.width + "x"
						+ selection.height
		);
	}

	public Rectangle getSelection() {
		return selection == null
				? null
				: new Rectangle(selection);
	}

	public Set<Point> getExcludedTiles() {
		Set<Point> result = new HashSet<>();

		for (Point tile : excludedTiles) {
			result.add(new Point(tile));
		}

		return result;
	}

	public boolean isTileExcluded(int tileX, int tileY) {
		return excludedTiles.contains(
				new Point(tileX, tileY)
		);
	}

	public List<String> getExcludedObjectIDs() {
		List<String> objectIDs = new ArrayList<>();

		for (Point tile : excludedTiles) {
			GameObject object = level.getObject(tile.x, tile.y);

			if (object != null) {
				String objectID = object.getStringID();

				if (objectID != null && !objectIDs.contains(objectID)) {
					objectIDs.add(objectID);
				}
			}
		}

		return objectIDs;
	}

	public List<String> getExcludedTileIDs() {
		List<String> tileIDs = new ArrayList<>();

		for (Point tile : excludedTiles) {
			GameTile gameTile = level.getTile(tile.x, tile.y);

			if (gameTile != null) {
				String tileID = gameTile.getStringID();

				if (tileID != null && !tileIDs.contains(tileID)) {
					tileIDs.add(tileID);
				}
			}
		}

		return tileIDs;
	}

	public void clearExcludedTiles() {
		excludedTiles.clear();
	}

	public void clearSelection() {
		this.selection = null;
		this.mouseDownTile = null;
		this.ctrlMouseDownTile = null;
		this.excludedTiles.clear();
	}

	@Override
	public boolean controllerEvent(ControllerEvent event) {
		return false;
	}

	@Override
	public void isCancelled() {
		if (hudElement != null) {
			hudElement.remove();
		}

		if (onCancelled != null) {
			onCancelled.run();
		}
	}

	@Override
	public void isCleared() {
		if (hudElement != null) {
			hudElement.remove();
		}
	}

	public Level getLevel() {
		return this.level;
	}

	@Override
	public GameTooltips getTooltips() {
		ListGameTooltips tooltips = new ListGameTooltips();

		tooltips.add(
				new InputTooltip(
						-100,
						Localization.translate(
								"ui",
								"blueprintselectarea"
						)
				)
		);

		return tooltips;
	}
}