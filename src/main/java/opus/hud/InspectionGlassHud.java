package opus.hud;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.SortedDrawable;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;
import opus.network.PacketRequestInspectionGlassData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class InspectionGlassHud {
	private static final Set<Level> addedLevels = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Level, Map<Long, Integer>> reinforcementByLevel = new WeakHashMap<>();
	private static final Map<Level, Long> nextRequestTimes = new WeakHashMap<>();
	private static final Map<Level, Boolean> activeByLevel = new WeakHashMap<>();
	private static final long requestInterval = 500L;
	private static final int inspectionRadius = 12;

	private InspectionGlassHud() {
	}


	public static void frameTick(MainGame mainGame) {
		if (mainGame == null || mainGame.getClient() == null) {
			return;
		}

		Client client = mainGame.getClient();
		Level level = client.getLevel();
		PlayerMob player = client.getPlayer();

		if (level == null || player == null) {
			return;
		}

		boolean active = isInspectionGlassSelected(player);
		activeByLevel.put(level, active);

		if (!active) {
			return;
		}

		requestServerData(level);
	}

	public static void ensureAdded(Level level) {
		if (level == null || addedLevels.contains(level)) {
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
						return -1500000;
					}

					@Override
					public void draw(TickManager tickManager) {
						drawInspectionHud(level, camera, perspective);
					}
				});
			}
		};

		level.hudManager.addElement(element);
		addedLevels.add(level);
	}

	public static void applyServerData(
			Level level,
			int startX,
			int startY,
			int endX,
			int endY,
			Map<Long, Integer> reinforcement
	) {
		Map<Long, Integer> cache = reinforcementByLevel.computeIfAbsent(level, key -> new HashMap<>());

		cache.entrySet().removeIf(entry -> {
			int x = GameMath.getXFromUniqueLongKey(entry.getKey());
			int y = GameMath.getYFromUniqueLongKey(entry.getKey());
			return x >= startX && x <= endX && y >= startY && y <= endY;
		});

		cache.putAll(reinforcement);
	}

	private static void drawInspectionHud(
			Level level,
			GameCamera camera,
			PlayerMob player
	) {
		if (!activeByLevel.getOrDefault(level, false)) {
			return;
		}

		int centerX = player.getTileX();
		int centerY = player.getTileY();
		int radiusSquared = inspectionRadius * inspectionRadius;

		Map<Long, Integer> reinforcement =
				reinforcementByLevel.getOrDefault(
						level,
						Collections.emptyMap()
				);

		for (Map.Entry<Long, Integer> entry : reinforcement.entrySet()) {
			int x = GameMath.getXFromUniqueLongKey(entry.getKey());
			int y = GameMath.getYFromUniqueLongKey(entry.getKey());

			int dx = x - centerX;
			int dy = y - centerY;

			if (dx * dx + dy * dy > radiusSquared) {
				continue;
			}

			drawIndicator(
					camera.getTileDrawX(x),
					camera.getTileDrawY(y),
					entry.getValue()
			);
		}
	}

	private static boolean isInspectionGlassSelected(PlayerMob player) {
		InventoryItem selected = player.getSelectedHotbarItem();
		return selected != null && "inspectionglass".equals(selected.item.getStringID());
	}

	private static void requestServerData(Level level) {
		if (!level.isClient()) {
			return;
		}

		long currentTime = level.getTime();
		long nextRequestTime = nextRequestTimes.getOrDefault(level, 0L);

		if (currentTime < nextRequestTime) {
			return;
		}

		nextRequestTimes.put(level, currentTime + requestInterval);

		level.getClient().network.sendPacket(
				new PacketRequestInspectionGlassData()
		);
	}

	private static void drawIndicator(int drawX, int drawY, int reinforcement) {
		int iconX = drawX + 1;
		int iconY = drawY + 13;

		drawQuad(iconX, iconY, 9, 18, 0, 0, 0);
		drawQuad(iconX + 1, iconY + 1, 7, 16, 209, 248, 255);

		int filled = Math.max(0, Math.min(4, reinforcement));

		for (int i = 0; i < 4; i++) {
			boolean reinforced = i < filled;
			int pipY = iconY + 14 - i * 4;

			if (reinforced) {
				drawQuad(iconX + 2, pipY, 5, 2, 34, 177, 76);
			} else {
				drawQuad(iconX + 2, pipY, 5, 2, 127, 127, 127);
			}
		}
	}

	private static void drawQuad(
			int x,
			int y,
			int width,
			int height,
			int red,
			int green,
			int blue
	) {
		Renderer.initQuadDraw(width, height)
				.color(red / 255.0F, green / 255.0F, blue / 255.0F, 1.0F)
				.draw(x, y);
	}
}
