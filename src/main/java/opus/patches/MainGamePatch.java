package opus.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;
import opus.forms.NewBlueprintForm;

@ModMethodPatch(target= MainGame.class, name="frameTick", arguments={TickManager.class, GameWindow.class})
public class MainGamePatch {
    @Advice.OnMethodExit
    static void onExit(@Advice.This MainGame mainGame, @Advice.Argument(value=0) TickManager tickManager, @Advice.Argument(value=1) GameWindow window) {
        NewBlueprintForm.frameTick(mainGame, tickManager, window);
    }
}
