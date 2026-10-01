package dev.afunk.surfcraft.karambit;

import dev.afunk.surfcraft.SurfCraft;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Karambit: builds surf wall a module at a time. Right-click places its module (or extends the clicked ramp),
 * sneak + right-click on a ramp copies that ramp as the module, sneak + right-click in the air undoes. The server does
 * the work ({@link ModulePlacer}); the client only swings.
 */
public final class KarambitItem extends Item {
	public static final DataComponentType<SurfModule> MODULE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SurfCraft.id("module"),
			DataComponentType.<SurfModule>builder().persistent(SurfModule.CODEC).cacheEncoding().build());
	public static final KarambitItem KARAMBIT = item();
	private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xFF3030, 1.5F);

	private KarambitItem(Properties properties) {
		super(properties);
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.insertAfter(SurfBlocks.STEEP_SURF_RAMP, KARAMBIT));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ModulePlacer.HISTORY.clear());
	}

	private static KarambitItem item() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SurfCraft.id("karambit"));
		return Registry.register(BuiltInRegistries.ITEM, key, new KarambitItem(new Properties().setId(key).stacksTo(1)));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		// Always consume the click on the client: a pass would fall through to use(), which undoes when sneaking.
		if (!(context.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.SUCCESS;
		BlockPos pos = context.getClickedPos();
		ModulePlacer.Result result = !context.isSecondaryUseActive() ? ModulePlacer.place(level, player, context.getItemInHand(), pos, context.getClickedFace())
				: SurfRampBlock.isRamp(level.getBlockState(pos)) ? ModulePlacer.copy(level, player, context.getItemInHand(), pos)
				: ModulePlacer.Result.fail("surfcraft.karambit.copy_hint");
		feedback(level, player, result);
		return result.ok() ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
		if (level instanceof ServerLevel server) feedback(server, player, ModulePlacer.undo(server, player));
		return InteractionResult.SUCCESS;
	}

	/** Action bar message; a slash on success, red dust on the cells in the way on refusal. */
	private static void feedback(ServerLevel level, Player player, ModulePlacer.Result result) {
		player.sendOverlayMessage(result.message());
		if (result.ok()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
		} else if (player instanceof ServerPlayer serverPlayer) {
			for (BlockPos p : result.blocked().subList(0, Math.min(64, result.blocked().size()))) {
				level.sendParticles(serverPlayer, BLOCKED, true, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0);
			}
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		SurfModule module = ModulePlacer.module(stack);
		builder.accept(Component.translatable(stack.has(MODULE) ? "item.surfcraft.karambit.module" : "item.surfcraft.karambit.default",
				module.width(), module.height(), module.length(), module.blocks()).withStyle(ChatFormatting.LIGHT_PURPLE));
		for (String control : List.of("place", "extend", "copy", "undo")) builder.accept(Component.translatable("item.surfcraft.karambit." + control).withStyle(ChatFormatting.GRAY));
	}
}
