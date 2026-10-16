package com.elyhealpot.mixin;

import com.elyhealpot.client.ElyhealpotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected ItemStack activeItemStack;

    @Inject(method = "consumeItem", at = @At("HEAD"))
    private void onConsumeItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getEntityWorld().isClient()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (self == client.player) {
                if (ElyhealpotClient.isHealing2Potion(this.activeItemStack)) {
                    ElyhealpotClient.triggerHealPotCooldown();
                }
            }
        }
    }
}
