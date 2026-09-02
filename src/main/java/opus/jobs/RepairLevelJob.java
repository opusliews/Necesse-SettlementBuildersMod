package opus.jobs;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import opus.damage.DamageRepairLevelData;
import opus.damage.HardcoreDamage;
import opus.logging.Logging;
import opus.mobs.BuilderHumanMob;

public class RepairLevelJob extends TileLevelJob {
	private long nextActionTime;

	public RepairLevelJob(int tileX, int tileY) {
		super(tileX, tileY);
	}

	public RepairLevelJob(LoadData save) {
		super(save);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		return super.isValid()
				&& HardcoreDamage.isEnabled(getLevel())
				&& DamageRepairLevelData.isRepairReady(getLevel(), tileX, tileY);
	}

	private TileActiveJob getActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
			private boolean started;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				return new JobMoveToTile(tileX, tileY, true);
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				RepairLevelJob.this.reservable.reserve(worker.getMobWorker());

				if (isCurrent && !isMovingTo) {
					worker.showWorkAnimation(
							tileX * 32 + 16,
							tileY * 32 + 16,
							ItemRegistry.getItem("constructionhammer"),
							1000,
							true
					);
				}
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				return !RepairLevelJob.this.isRemoved()
						&& RepairLevelJob.this.isValid()
						&& RepairLevelJob.this.reservable.isAvailable(worker.getMobWorker());
			}

			@Override
			public ActiveJobResult perform() {
				BuilderHumanMob builder = (BuilderHumanMob)worker.getMobWorker();
				long currentTime = getLevel().getTime();

				if (!started) {
					started = true;
					nextActionTime = currentTime + builder.getWorkActionDelay();
					return ActiveJobResult.PERFORMING;
				}

				if (currentTime < nextActionTime) {
					return ActiveJobResult.PERFORMING;
				}

				if (!DamageRepairLevelData.isRepairReady(getLevel(), tileX, tileY)) {
					return ActiveJobResult.FAILED;
				}

				DamageRepairLevelData.repairDamage(getLevel(), tileX, tileY);
				Logging.logMessage(
						"Builder " + builder.getUniqueID() + " repaired damage at " + tileX + ", " + tileY
				);
				return ActiveJobResult.FINISHED;
			}
		};
	}

	public static JobSequence getJobSequence(BuilderHumanMob worker, FoundJob foundJob) {
		RepairLevelJob job = (RepairLevelJob)foundJob.job;

		if (!job.isValid()) {
			return null;
		}

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "construction"),
				false
		);

		sequence.add(job.getActiveJob(worker, foundJob.priority));
		return sequence;
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof BuilderHumanMob)) {
			return null;
		}

		BuilderHumanMob builder = (BuilderHumanMob)worker;

		return handler
				.setJobHandler(RepairLevelJob.class, foundJob -> getJobSequence(builder, foundJob))
				.setPredicate(
						() -> !builder.isOnStrike()
								&& !builder.hasCompletedMission()
								&& (!builder.isSettler() || builder.isSettlerWithinSettlement())
				);
	}
}
