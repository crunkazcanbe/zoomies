package com.dogpound.zoomies.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.IFixType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Treasure2 runs every structure template through the whole data fixer on every world load. In a 700-mod pack that
 * fixer is huge (Forge's CompoundDataFixer runs every mod's walkers because template files never carry
 * ForgeDataVersion), and it was 100% of Treasure2's time — most of a brand-new world's 13-minute setup
 * (profiled 2026-10-02). All 45 of its templates are DataVersion 1343 = current 1.12.2: nothing to fix.
 * Up-to-date templates skip the fixer; anything older still goes through it.
 */
@Mixin(targets = "com.someguyssoftware.treasure2.world.gen.structure.TreasureTemplateManager", remap = false)
public abstract class MixinTreasureTemplateNoFixer {
    @Redirect(method = "readTemplateFromStream", require = 0,
              at = @At(value = "INVOKE", target = "Lnet/minecraft/util/datafix/DataFixer;func_188257_a(Lnet/minecraft/util/datafix/IFixType;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;"))
    private NBTTagCompound zoomies$onlyIfOld(DataFixer fixer, IFixType type, NBTTagCompound nbt) {
        if (nbt.hasKey("DataVersion", 99) && nbt.getInteger("DataVersion") >= 1343) return nbt;
        return fixer.process(type, nbt);
    }
}
