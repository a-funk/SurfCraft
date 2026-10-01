package dev.afunk.surfcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.afunk.surfcraft.movement.RampCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * b1: players near ramps collide with the exact slopes (both sides), and the controller's own moves return the movement
 * it already traced. {@code collide} is the single funnel for every move (vanilla travel, the server's per-tick
 * simulation and its re-simulation of move packets, pistons) and wraps the step-up too.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 surfcraft$exactCollide(Entity self, Vec3 movement, Operation<Vec3> original, @Local(argsOnly = true) MoverType type) {
		if (self instanceof Player player) {
			Vec3 exact = RampCollision.collide(player, movement, type);
			if (exact != null) return exact;
		}
		return original.call(self, movement);
	}
}
