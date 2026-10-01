package dev.afunk.surfcraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Cobwebs, berry bushes and powder snow scale the next move: the controller hands those ticks to vanilla. */
@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("stuckSpeedMultiplier")
	Vec3 surfcraft$stuckSpeedMultiplier();
}
