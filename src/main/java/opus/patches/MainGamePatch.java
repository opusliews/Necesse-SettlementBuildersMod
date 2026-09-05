package opus.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;
import opus.blueprint.BlueprintAreaHud;
import opus.blueprint.BlueprintAreaSync;
import opus.forms.NewBlueprintForm;
import opus.hud.InspectionGlassHud;
import opus.item.BlueprintItem;

@ModMethodPatch(target= MainGame.class, name="frameTick", arguments={TickManager.class, GameWindow.class})
public class MainGamePatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This MainGame mainGame, @Advice.Argument(value=0) TickManager tickManager, @Advice.Argument(value=1) GameWindow window) {
		NewBlueprintForm.frameTick(mainGame, tickManager, window);
		BlueprintItem.frameTick(mainGame, tickManager, window);
		InspectionGlassHud.frameTick(mainGame);

		if (mainGame.getClient() != null) {
			BlueprintAreaSync.frameTick(mainGame.getClient());

			if (mainGame.getClient().getLevel() != null) {
				BlueprintAreaHud.ensureAdded(mainGame.getClient().getLevel());
				InspectionGlassHud.ensureAdded(mainGame.getClient().getLevel());
			}
		}
	}
}
