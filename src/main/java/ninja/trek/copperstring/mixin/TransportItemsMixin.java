package ninja.trek.copperstring.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.phys.AABB;
import ninja.trek.copperstring.ItemFilterCache;
import ninja.trek.copperstring.ItemFilterCache.FilterResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(TransportItemsBetweenContainers.class)
public class TransportItemsMixin {

    @Shadow private TransportItemsBetweenContainers.TransportItemTarget target;
    @Shadow private TransportItemsBetweenContainers.TransportItemState state;
    @Shadow private int ticksSinceReachingTarget;

    @Unique private static final Map<UUID, BlockPos> wildcardPositions = new ConcurrentHashMap<>();
    @Unique private static final Set<UUID> forceFallbackDeposit = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Wraps the pickupItemFromContainer call inside pickUpItems to only pick up
     * items matching the copper golem's custom name filter.
     * Unnamed golems use vanilla behavior.
     */
    @WrapOperation(
        method = "pickUpItems",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;pickupItemFromContainer(Lnet/minecraft/world/Container;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack filterPickup(Container container, Operation<ItemStack> original,
                                   @Local(argsOnly = true) PathfinderMob mob) {
        if (mob instanceof CopperGolem copperGolem && copperGolem.hasCustomName()) {
            String name = copperGolem.getCustomName().getString();
            FilterResult filter = ItemFilterCache.getFilterResult(name);
            return filteredPickupFromContainer(container, filter);
        }
        return original.call(container);
    }

    private static ItemStack filteredPickupFromContainer(Container container, FilterResult filter) {
        int i = 0;
        for (ItemStack itemStack : container) {
            if (!itemStack.isEmpty() && ItemFilterCache.stackMatchesFilter(filter, itemStack)) {
                int j = Math.min(itemStack.getCount(), 16);
                return container.removeItem(i, j);
            }
            i++;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Filters pickup target selection:
     * Skips source chests with no items matching the copper golem's name filter.
     * Unnamed golems use vanilla behavior.
     */
    @Inject(
        method = "isTargetValidToPick",
        at = @At("RETURN"),
        cancellable = true
    )
    private void filterTarget(PathfinderMob mob, Level level, BlockEntity blockEntity,
                              Set<GlobalPos> set, Set<GlobalPos> set2, AABB aabb,
                              CallbackInfoReturnable<TransportItemsBetweenContainers.TransportItemTarget> cir) {
        TransportItemsBetweenContainers.TransportItemTarget result = cir.getReturnValue();
        if (result == null) return;

        boolean isPickingUp = mob.getMainHandItem().isEmpty();

        if (isPickingUp) {
            if (mob instanceof CopperGolem cg && cg.hasCustomName()) {
                String name = cg.getCustomName().getString();
                FilterResult filter = ItemFilterCache.getFilterResult(name);
                boolean hasMatch = false;
                for (ItemStack stack : result.container()) {
                    if (!stack.isEmpty() && ItemFilterCache.stackMatchesFilter(filter, stack)) {
                        hasMatch = true;
                        break;
                    }
                }
                if (!hasMatch) {
                    cir.setReturnValue(null);
                }
            }
        }
    }

    /**
     * Wraps the matchesLeavingItemsRequirement check during deposit.
     * For named chests, the chest name filter replaces vanilla's item-matching check,
     * so an empty named chest won't accept items that don't match its filter.
     * Wildcard (*) chests record their position for fallback, or accept if in fallback mode.
     * Unnamed chests use vanilla behavior.
     */
    @WrapOperation(
        method = "doReachedTargetInteraction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;matchesLeavingItemsRequirement(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/Container;)Z"
        )
    )
    private boolean filterDeposit(PathfinderMob mob, Container container, Operation<Boolean> original) {
        String chestName = getContainerCustomName(container);
        ItemStack heldStack = mob.getMainHandItem();
        ItemStack effectiveStack = ItemFilterCache.getEffectiveStack(heldStack);

        if (chestName != null) {
            if ("*".equals(chestName) && mob instanceof CopperGolem cg) {
                UUID golemId = cg.getUUID();
                if (forceFallbackDeposit.contains(golemId)) {
                    return true;
                }
                // Use the target's own pos (the half getTransportTarget selected
                // and the golem pathed to) rather than getContainerBlockPos which
                // may return the other half of a double chest.
                if (this.target != null) {
                    wildcardPositions.put(golemId, this.target.pos());
                }
            }
            FilterResult chestFilter = ItemFilterCache.getFilterResult(chestName);
            return ItemFilterCache.stackMatchesFilter(chestFilter, effectiveStack);
        }

        // Unnamed chest: if effective stack differs, use custom matching
        if (effectiveStack != heldStack) {
            return hasEffectiveItemMatch(container, effectiveStack);
        }
        return original.call(mob, container);
    }

    /**
     * Checks if the container already has an item matching the effective stack.
     * Mirrors vanilla's hasItemMatchingHandItem but uses the resolved effective item.
     */
    @Unique
    private static boolean hasEffectiveItemMatch(Container container, ItemStack effectiveStack) {
        boolean isEmpty = true;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack containerStack = container.getItem(i);
            if (!containerStack.isEmpty()) {
                isEmpty = false;
                if (ItemStack.isSameItem(containerStack, effectiveStack)) {
                    return true;
                }
            }
        }
        return isEmpty;
    }

    /**
     * Intercepts cooldown to redirect golems holding unmatched items to a wildcard (*) chest.
     * Catches all three call sites: visited > 10, no targets found, unreachable > 50.
     */
    @Inject(method = "enterCooldownAfterNoMatchingTargetFound", at = @At("HEAD"), cancellable = true)
    private void interceptCooldown(PathfinderMob pathfinderMob, CallbackInfo ci) {
        if (!(pathfinderMob instanceof CopperGolem cg) || pathfinderMob.getMainHandItem().isEmpty()) {
            return;
        }

        UUID golemId = cg.getUUID();
        BlockPos wildcardPos = wildcardPositions.remove(golemId);
        if (wildcardPos == null) {
            return;
        }

        Level level = pathfinderMob.level();
        TransportItemsBetweenContainers.TransportItemTarget wildcardTarget =
            TransportItemsBetweenContainers.TransportItemTarget.tryCreatePossibleTarget(wildcardPos, level);
        if (wildcardTarget == null) {
            return;
        }

        forceFallbackDeposit.add(golemId);
        this.target = wildcardTarget;
        this.state = TransportItemsBetweenContainers.TransportItemState.TRAVELLING;
        this.ticksSinceReachingTarget = 0;
        // Clear stale navigation so hasValidTravellingPath creates a fresh path
        // to the wildcard target instead of using the old path to the previous target.
        pathfinderMob.getNavigation().stop();
        pathfinderMob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pathfinderMob.getBrain().eraseMemory(MemoryModuleType.VISITED_BLOCK_POSITIONS);
        pathfinderMob.getBrain().eraseMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS);
        ci.cancel();
    }

    /**
     * Cleans up wildcard state after a successful pickup/deposit cycle.
     */
    @Inject(method = "clearMemoriesAfterMatchingTargetFound", at = @At("HEAD"))
    private void onClearMemories(PathfinderMob pathfinderMob, CallbackInfo ci) {
        if (pathfinderMob instanceof CopperGolem cg) {
            UUID golemId = cg.getUUID();
            wildcardPositions.remove(golemId);
            forceFallbackDeposit.remove(golemId);
        }
    }

    /**
     * Cleans up wildcard state when the behavior ends entirely.
     */
    @Inject(method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;J)V", at = @At("HEAD"))
    private void onStop(ServerLevel serverLevel, PathfinderMob pathfinderMob, long l, CallbackInfo ci) {
        if (pathfinderMob instanceof CopperGolem cg) {
            UUID golemId = cg.getUUID();
            wildcardPositions.remove(golemId);
            forceFallbackDeposit.remove(golemId);
        }
    }

    /**
     * Gets the custom name from a container, handling both single chests
     * (BaseContainerBlockEntity) and double chests (CompoundContainer).
     * Returns null if the container has no custom name.
     */
    private static String getContainerCustomName(Container container) {
        if (container instanceof BaseContainerBlockEntity bcbe && bcbe.hasCustomName()) {
            return bcbe.getCustomName().getString();
        }
        if (container instanceof CompoundContainer cc) {
            CompoundContainerAccessor accessor = (CompoundContainerAccessor) cc;
            Container c1 = accessor.getContainer1();
            Container c2 = accessor.getContainer2();
            if (c1 instanceof BaseContainerBlockEntity bcbe1 && bcbe1.hasCustomName()) {
                return bcbe1.getCustomName().getString();
            }
            if (c2 instanceof BaseContainerBlockEntity bcbe2 && bcbe2.hasCustomName()) {
                return bcbe2.getCustomName().getString();
            }
        }
        return null;
    }

}
