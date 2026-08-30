package opus.jobs;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.SingleJobSequence;
import necesse.entity.mobs.job.activeJob.ActiveJob;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import opus.blueprint.BlueprintArea;
import opus.blueprint.BlueprintAreaManager;
import opus.logging.Logging;
import opus.mobs.BuilderHumanMob;

import java.awt.geom.Point2D;

public class ConstructionLevelJob extends TileLevelJob {
	private final String blueprintAreaUniqueID;

	public ConstructionLevelJob(int tileX, int tileY, String blueprintAreaUniqueID) {
		super(tileX, tileY);

		this.blueprintAreaUniqueID = blueprintAreaUniqueID;

		Logging.logMessage(
				"Created ConstructionLevelJob for blueprint "
						+ blueprintAreaUniqueID
						+ " at work tile "
						+ tileX
						+ ", "
						+ tileY
		);
	}

	public ConstructionLevelJob(LoadData save) {
		super(save);

		this.blueprintAreaUniqueID = save.getSafeString("blueprintAreaUniqueID");

		Logging.logMessage(
				"Loaded ConstructionLevelJob for blueprint "
						+ blueprintAreaUniqueID
						+ " at work tile "
						+ tileX
						+ ", "
						+ tileY
		);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addSafeString("blueprintAreaUniqueID", blueprintAreaUniqueID);
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid()) {
			return false;
		}

		boolean projectExists = BlueprintAreaManager
				.get(getLevel())
				.getArea(blueprintAreaUniqueID) != null;

		if (!projectExists) {
			Logging.logMessage(
					"ConstructionLevelJob became invalid because blueprint "
							+ blueprintAreaUniqueID
							+ " no longer exists"
			);
		}

		return projectExists;
	}

	public ActiveJob getActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		Logging.logMessage(
				"Creating ActiveJob for blueprint "
						+ blueprintAreaUniqueID
						+ " at work tile "
						+ tileX
						+ ", "
						+ tileY
		);

		return new TileActiveJob(worker, priority, tileX, tileY) {
			private boolean started;
			private boolean wasMoving;
			private boolean invalidLogged;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				Logging.logMessage(
						"Builder assigned movement target "
								+ tileX
								+ ", "
								+ tileY
								+ " for blueprint "
								+ blueprintAreaUniqueID
				);

				return new JobMoveToTile(tileX, tileY, false);
			}

			@Override
			public int getCompleteRange() {
				return 8;
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				ConstructionLevelJob.this.reservable.reserve(worker.getMobWorker());

				if (isMovingTo && !wasMoving) {
					wasMoving = true;

					Logging.logMessage(
							"Builder started moving toward work tile "
									+ tileX
									+ ", "
									+ tileY
									+ " for blueprint "
									+ blueprintAreaUniqueID
					);
				}
				else if (!isMovingTo && wasMoving) {
					wasMoving = false;

					Logging.logMessage(
							"Builder stopped moving for construction job at "
									+ tileX
									+ ", "
									+ tileY
									+ " for blueprint "
									+ blueprintAreaUniqueID
					);
				}
				else if (isCurrent && !isMovingTo) {
					BlueprintArea area = BlueprintAreaManager
							.get(getLevel())
							.getArea(blueprintAreaUniqueID);
					int blueprintCenterX = (area.getOriginX() * 32) + (area.getWidth() * 16);
					int blueprintCenterY = (area.getOriginY() * 32) + (area.getHeight() * 16);

					worker.showWorkAnimation(
							blueprintCenterX,
							blueprintCenterY,
							ItemRegistry.getItem("constructionhammer"),
							1000,
							true
					);
				}

			}

			@Override
			public boolean isValid(boolean isCurrent) {
				boolean removed = ConstructionLevelJob.this.isRemoved();
				boolean levelJobValid = ConstructionLevelJob.this.isValid();
				boolean reservationAvailable = ConstructionLevelJob.this.reservable.isAvailable(
						worker.getMobWorker()
				);

				boolean valid = !removed && levelJobValid && reservationAvailable;

				if (!valid && !invalidLogged) {
					invalidLogged = true;

					Logging.logMessage(
							"Construction ActiveJob became invalid for blueprint "
									+ blueprintAreaUniqueID
									+ " at work tile "
									+ tileX
									+ ", "
									+ tileY
									+ " [removed="
									+ removed
									+ ", levelJobValid="
									+ levelJobValid
									+ ", reservationAvailable="
									+ reservationAvailable
									+ "]"
					);
				}

				if (valid) {
					invalidLogged = false;
				}

				return valid;
			}

			@Override
			public ActiveJobResult perform() {
				if (!started) {
					started = true;

					Logging.logMessage(
							"Builder started construction for blueprint "
									+ blueprintAreaUniqueID
									+ " from work tile "
									+ tileX
									+ ", "
									+ tileY
					);
				}

				return ActiveJobResult.PERFORMING;
			}
		};
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		ConstructionLevelJob job = (ConstructionLevelJob)foundJob.job;

		Logging.logMessage(
				"Creating JobSequence for blueprint "
						+ job.blueprintAreaUniqueID
						+ " at work tile "
						+ job.tileX
						+ ", "
						+ job.tileY
		);

		return new SingleJobSequence(
				job.getActiveJob(worker, foundJob.priority),
				new LocalMessage("activities", "construction")
		).withPerformedLevelJob(foundJob.job);
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof BuilderHumanMob)) {
			return null;
		}

		BuilderHumanMob builder = (BuilderHumanMob)worker;

		return handler
				.setJobHandler(
						ConstructionLevelJob.class,
						foundJob -> getJobSequence(builder, foundJob)
				)
				.setPredicate(
						() ->
								!builder.isOnStrike()
										&& !builder.hasCompletedMission()
										&& (
										!builder.isSettler()
												|| builder.isSettlerWithinSettlement()
								)
				);
	}
}