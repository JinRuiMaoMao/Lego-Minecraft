package com.legominecraft.entity;

import com.legominecraft.block.LegoColor;
import com.legominecraft.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class LegoMinifigEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> COLOR_ID = SynchedEntityData.defineId(
		LegoMinifigEntity.class, EntityDataSerializers.INT
	);

	public LegoMinifigEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 12.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COLOR_ID, LegoColor.YELLOW.ordinal());
	}

	public LegoColor getLegoColor() {
		int ordinal = this.entityData.get(COLOR_ID);
		LegoColor[] colors = LegoColor.values();
		if (ordinal < 0 || ordinal >= colors.length) {
			return LegoColor.YELLOW;
		}
		return colors[ordinal];
	}

	public void setLegoColor(LegoColor color) {
		this.entityData.set(COLOR_ID, color.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel serverLevel, DamageSource damageSource, boolean recentlyHit) {
		super.dropCustomDeathLoot(serverLevel, damageSource, recentlyHit);
		this.spawnAtLocation(new ItemStack(ModItems.getMinifigSpawn(this.getLegoColor())));
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("LegoColor", this.getLegoColor().getId());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("LegoColor")) {
			this.setLegoColor(LegoColor.fromId(tag.getString("LegoColor")));
		}
	}
}
