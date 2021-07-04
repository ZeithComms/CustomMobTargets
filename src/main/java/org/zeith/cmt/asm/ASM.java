package org.zeith.cmt.asm;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.zeith.cmt.Config;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

public class ASM
{
	public static void patchEntityAI(EntityLiving entity)
	{
		EntityEntry entry = EntityRegistry.getEntry(entity.getClass());
		if(entry != null)
		{
			Config.AttackEntity ae = Config.get(entry.getRegistryName());
			if(ae != null)
			{
				for(Config.AttackTarget target : ae.targets)
				{
					EntityEntry attackEntry = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(target.entity));

					if(attackEntry != null)
					{
						Class<? extends Entity> aClass = attackEntry.getEntityClass();

						if(EntityLivingBase.class.isAssignableFrom(aClass))
							entity.targetTasks.addTask(
									target.priority,
									new LivingEntityAINearestAttackableTarget<>(
											entity,
											aClass.asSubclass(EntityLivingBase.class),
											target.check_sight
									)
							);
					}
				}
			}
		}
	}
}

abstract class EntityAITarget
		extends EntityAIBase
{
	/**
	 * The entity that this task belongs to
	 */
	protected final EntityLiving taskOwner;
	/**
	 * If true, EntityAI targets must be able to be seen (cannot be blocked by walls) to be suitable targets.
	 */
	protected boolean shouldCheckSight;
	/**
	 * When true, only entities that can be reached with minimal effort will be targetted.
	 */
	private final boolean nearbyOnly;
	/**
	 * When nearbyOnly is true: 0 -> No target, but OK to search; 1 -> Nearby target found; 2 -> Target too far.
	 */
	private int targetSearchStatus;
	/**
	 * When nearbyOnly is true, this throttles target searching to avoid excessive pathfinding.
	 */
	private int targetSearchDelay;
	/**
	 * If  @shouldCheckSight is true, the number of ticks before the interuption of this AITastk when the entity does't
	 * see the target
	 */
	private int targetUnseenTicks;
	protected EntityLivingBase target;
	protected int unseenMemoryTicks;

	public EntityAITarget(EntityLiving creature, boolean checkSight)
	{
		this(creature, checkSight, false);
	}

	public EntityAITarget(EntityLiving creature, boolean checkSight, boolean onlyNearby)
	{
		this.unseenMemoryTicks = 60;
		this.taskOwner = creature;
		this.shouldCheckSight = checkSight;
		this.nearbyOnly = onlyNearby;
	}

	/**
	 * Returns whether an in-progress EntityAIBase should continue executing
	 */
	@Override
	public boolean shouldContinueExecuting()
	{
		EntityLivingBase entitylivingbase = this.taskOwner.getAttackTarget();

		if(entitylivingbase == null)
		{
			entitylivingbase = this.target;
		}

		if(entitylivingbase == null)
		{
			return false;
		} else if(!entitylivingbase.isEntityAlive())
		{
			return false;
		} else
		{
			Team team = this.taskOwner.getTeam();
			Team team1 = entitylivingbase.getTeam();

			if(team != null && team1 == team)
			{
				return false;
			} else
			{
				double d0 = this.getTargetDistance();

				if(this.taskOwner.getDistanceSq(entitylivingbase) > d0 * d0)
				{
					return false;
				} else
				{
					if(this.shouldCheckSight)
					{
						if(this.taskOwner.getEntitySenses().canSee(entitylivingbase))
						{
							this.targetUnseenTicks = 0;
						} else if(++this.targetUnseenTicks > this.unseenMemoryTicks)
						{
							return false;
						}
					}

					if(entitylivingbase instanceof EntityPlayer && ((EntityPlayer) entitylivingbase).capabilities.disableDamage)
					{
						return false;
					} else
					{
						this.taskOwner.setAttackTarget(entitylivingbase);
						return true;
					}
				}
			}
		}
	}

	protected double getTargetDistance()
	{
		IAttributeInstance iattributeinstance = this.taskOwner.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE);
		return iattributeinstance == null ? 16.0D : iattributeinstance.getAttributeValue();
	}

	/**
	 * Execute a one shot task or start executing a continuous task
	 */
	public void startExecuting()
	{
		this.targetSearchStatus = 0;
		this.targetSearchDelay = 0;
		this.targetUnseenTicks = 0;
	}

	/**
	 * Reset the task's internal state. Called when this task is interrupted by another one
	 */
	public void resetTask()
	{
		this.taskOwner.setAttackTarget((EntityLivingBase) null);
		this.target = null;
	}

	/**
	 * A static method used to see if an entity is a suitable target through a number of checks.
	 */
	public static boolean isSuitableTarget(EntityLiving attacker, @Nullable EntityLivingBase target, boolean includeInvincibles, boolean checkSight)
	{
		if(target == null)
		{
			return false;
		} else if(target == attacker)
		{
			return false;
		} else if(!target.isEntityAlive())
		{
			return false;
		} else if(!attacker.canAttackClass(target.getClass()))
		{
			return false;
		} else if(attacker.isOnSameTeam(target))
		{
			return false;
		} else
		{
			if(attacker instanceof IEntityOwnable && ((IEntityOwnable) attacker).getOwnerId() != null)
			{
				if(target instanceof IEntityOwnable && ((IEntityOwnable) attacker).getOwnerId().equals(((IEntityOwnable) target).getOwnerId()))
				{
					return false;
				}

				if(target == ((IEntityOwnable) attacker).getOwner())
				{
					return false;
				}
			} else if(target instanceof EntityPlayer && !includeInvincibles && ((EntityPlayer) target).capabilities.disableDamage)
			{
				return false;
			}

			return !checkSight || attacker.getEntitySenses().canSee(target);
		}
	}

	/**
	 * A method used to see if an entity is a suitable target through a number of checks. Args : entity,
	 * canTargetInvinciblePlayer
	 */
	protected boolean isSuitableTarget(@Nullable EntityLivingBase target, boolean includeInvincibles)
	{
		if(!isSuitableTarget(this.taskOwner, target, includeInvincibles, this.shouldCheckSight))
			return false;
		else if(taskOwner instanceof EntityCreature && !((EntityCreature) taskOwner).isWithinHomeDistanceFromPosition(new BlockPos(target)))
			return false;
		else
		{
			if(this.nearbyOnly)
			{
				if(--this.targetSearchDelay <= 0)
					this.targetSearchStatus = 0;
				if(this.targetSearchStatus == 0)
					this.targetSearchStatus = this.canEasilyReach(target) ? 1 : 2;
				if(this.targetSearchStatus == 2)
					return false;
			}

			return true;
		}
	}

	/**
	 * Checks to see if this entity can find a short path to the given target.
	 */
	private boolean canEasilyReach(EntityLivingBase target)
	{
		this.targetSearchDelay = 10 + this.taskOwner.getRNG().nextInt(5);
		Path path = this.taskOwner.getNavigator().getPathToEntityLiving(target);

		if(path == null)
		{
			return false;
		} else
		{
			PathPoint pathpoint = path.getFinalPathPoint();

			if(pathpoint == null)
			{
				return false;
			} else
			{
				int i = pathpoint.x - MathHelper.floor(target.posX);
				int j = pathpoint.z - MathHelper.floor(target.posZ);
				return (double) (i * i + j * j) <= 2.25D;
			}
		}
	}

	public EntityAITarget setUnseenMemoryTicks(int p_190882_1_)
	{
		this.unseenMemoryTicks = p_190882_1_;
		return this;
	}
}

class LivingEntityAINearestAttackableTarget<T extends EntityLivingBase>
		extends EntityAITarget
{
	protected final Class<T> targetClass;
	private final int targetChance;
	/**
	 * Instance of EntityAINearestAttackableTargetSorter.
	 */
	protected final net.minecraft.entity.ai.EntityAINearestAttackableTarget.Sorter sorter;
	protected final Predicate<? super T> targetEntitySelector;
	protected T targetEntity;

	public LivingEntityAINearestAttackableTarget(EntityLiving creature, Class<T> classTarget, boolean checkSight)
	{
		this(creature, classTarget, checkSight, false);
	}

	public LivingEntityAINearestAttackableTarget(EntityLiving creature, Class<T> classTarget, boolean checkSight, boolean onlyNearby)
	{
		this(creature, classTarget, 10, checkSight, onlyNearby, (Predicate) null);
	}

	public LivingEntityAINearestAttackableTarget(EntityLiving creature, Class<T> classTarget, int chance, boolean checkSight, boolean onlyNearby, @Nullable final Predicate<? super T> targetSelector)
	{
		super(creature, checkSight, onlyNearby);
		this.targetClass = classTarget;
		this.targetChance = chance;
		this.sorter = new net.minecraft.entity.ai.EntityAINearestAttackableTarget.Sorter(creature);
		this.setMutexBits(1);
		this.targetEntitySelector = target ->
		{
			if(target == null)
				return false;
			else if(targetSelector != null && !targetSelector.apply(target))
				return false;
			else
				return EntitySelectors.NOT_SPECTATING.apply(target) && LivingEntityAINearestAttackableTarget.this.isSuitableTarget(target, false);
		};
	}

	/**
	 * Returns whether the EntityAIBase should begin execution.
	 */
	public boolean shouldExecute()
	{
		if(this.targetChance > 0 && this.taskOwner.getRNG().nextInt(this.targetChance) != 0)
		{
			return false;
		} else if(this.targetClass != EntityPlayer.class && this.targetClass != EntityPlayerMP.class)
		{
			List<T> list = this.taskOwner.world.<T> getEntitiesWithinAABB(this.targetClass, this.getTargetableArea(this.getTargetDistance()), this.targetEntitySelector);

			if(list.isEmpty())
			{
				return false;
			} else
			{
				list.sort(this.sorter);
				this.targetEntity = list.get(0);
				return true;
			}
		} else
		{
			this.targetEntity = (T) this.taskOwner.world.getNearestAttackablePlayer(this.taskOwner.posX, this.taskOwner.posY + (double) this.taskOwner.getEyeHeight(), this.taskOwner.posZ, this.getTargetDistance(), this.getTargetDistance(), new Function<EntityPlayer, Double>()
			{
				@Nullable
				public Double apply(@Nullable EntityPlayer p_apply_1_)
				{
					ItemStack itemstack = p_apply_1_.getItemStackFromSlot(EntityEquipmentSlot.HEAD);

					if(itemstack.getItem() == Items.SKULL)
					{
						int i = itemstack.getItemDamage();
						boolean flag = LivingEntityAINearestAttackableTarget.this.taskOwner instanceof EntitySkeleton && i == 0;
						boolean flag1 = LivingEntityAINearestAttackableTarget.this.taskOwner instanceof EntityZombie && i == 2;
						boolean flag2 = LivingEntityAINearestAttackableTarget.this.taskOwner instanceof EntityCreeper && i == 4;

						if(flag || flag1 || flag2)
						{
							return 0.5D;
						}
					}

					return 1.0D;
				}
			}, (Predicate<EntityPlayer>) this.targetEntitySelector);
			return this.targetEntity != null;
		}
	}

	protected AxisAlignedBB getTargetableArea(double targetDistance)
	{
		return this.taskOwner.getEntityBoundingBox().grow(targetDistance, 4.0D, targetDistance);
	}

	/**
	 * Execute a one shot task or start executing a continuous task
	 */
	public void startExecuting()
	{
		this.taskOwner.setAttackTarget(this.targetEntity);
		super.startExecuting();
	}

	public static class Sorter
			implements Comparator<Entity>
	{
		private final Entity entity;

		public Sorter(Entity entityIn)
		{
			this.entity = entityIn;
		}

		public int compare(Entity p_compare_1_, Entity p_compare_2_)
		{
			double d0 = this.entity.getDistanceSq(p_compare_1_);
			double d1 = this.entity.getDistanceSq(p_compare_2_);

			if(d0 < d1)
			{
				return -1;
			} else
			{
				return d0 > d1 ? 1 : 0;
			}
		}
	}
}