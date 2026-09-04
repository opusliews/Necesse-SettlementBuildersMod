package opus.patches;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.mob.ShopContainerForm;
import necesse.inventory.container.mob.ShopContainer;
import net.bytebuddy.asm.Advice;
import opus.mobs.BuilderHumanMob;
import opus.network.PacketBuilderRoadRepairToggle;

@ModMethodPatch(
		target = ShopContainerForm.class,
		name = "addAdventurePartyDialogueOptions",
		arguments = {}
)
public class ShopContainerFormAdventurePartyPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static boolean onEnter(@Advice.This ShopContainerForm form) {
		return handleBuilderAdventurePartyOptions(form);
	}

	public static boolean handleBuilderAdventurePartyOptions(ShopContainerForm form) {
		ShopContainer container = (ShopContainer)form.getContainer();

		if (!(container.humanShop instanceof BuilderHumanMob)) {
			return false;
		}

		BuilderHumanMob builder = (BuilderHumanMob)container.humanShop;

		if (container.hasSettlerAccess
				&& container.canJoinAdventureParties
				&& !container.isInYourAdventureParty) {
			if (form.isCurrent(form.partyConfigForm)) {
				container.setIsInPartyConfig.runAndSend(false);
				form.makeCurrent(form.dialogueForm);
			}

			form.dialogueForm.addDialogueOption(
					new LocalMessage("ui", "settlerjoinparty"),
					() -> {
						container.joinAdventurePartyAction.runAndSend();
						form.waitingForPartyConfirm = true;
					}
			);

			if (container.isSettlerOutsideSettlement) {
				form.dialogueForm.addDialogueOption(
						new LocalMessage("ui", "settlerreturntosettlement"),
						container.returnToSettlementAction::runAndSend
				);
			}

			return true;
		}

		if (container.isInYourAdventureParty) {
			form.dialogueForm.addDialogueOption(
					new LocalMessage("ui", "confiureadventureparty"),
					() -> {
						container.setIsInPartyConfig.runAndSend(true);
						form.makeCurrent(form.partyConfigForm);
					}
			);

			form.dialogueForm.addDialogueOption(
					new LocalMessage(
							"ui",
							builder.isRepairOnRoad()
									? "builderstoproadrepairs"
									: "builderstartroadrepairs"
					),
					() -> {
						boolean enabled = !builder.isRepairOnRoad();

						builder.setRepairOnRoad(enabled);

						form.getClient().network.sendPacket(
								new PacketBuilderRoadRepairToggle(
										builder.getUniqueID(),
										enabled
								)
						);

						form.updateDialogue();
					}
			);

			form.dialogueForm.addDialogueOption(
					new LocalMessage("ui", "settlerleaveparty"),
					container.leaveAdventurePartyAction::runAndSend
			);
		}

		return true;
	}
}