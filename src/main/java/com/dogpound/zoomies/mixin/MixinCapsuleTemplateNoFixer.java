package com.dogpound.zoomies.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.IFixType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Capsule fills loot chests with capsules holding a random reward structure, and the first time each structure is used
 * it is read and pushed through the whole data fixer (every mod's walkers — Forge's CompoundDataFixer, as with
 * Treasure2). With its big reward pool that happens again and again while new chunks fill their chests: 38% of the
 * server thread in a fresh area (profiled 2026-10-02). Templates already saved as current 1.12.2 (DataVersion 1343+)
 * skip the fixer; older ones still go through it.
 */
@Mixin(targets = "capsule.structure.CapsuleTemplateManager", remap = false)
public abstract class MixinCapsuleTemplateNoFixer {
    @Redirect(method = "readTemplateFromStream", require = 0,
              at = @At(value = "INVOKE", target = "Lnet/minecraft/util/datafix/DataFixer;func_188257_a(Lnet/minecraft/util/datafix/IFixType;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;"))
    private NBTTagCompound zoomies$onlyIfOld(DataFixer fixer, IFixType type, NBTTagCompound nbt) {
        if (nbt.hasKey("DataVersion", 99) && nbt.getInteger("DataVersion") >= 1343) return nbt;
        return fixer.process(type, nbt);
    }
}
