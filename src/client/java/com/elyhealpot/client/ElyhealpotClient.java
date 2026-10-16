package com.elyhealpot.client;

import com.elyhealpot.CooldownAccessor;
import com.elyhealpot.ElyhealpotConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class ElyhealpotClient implements ClientModInitializer {
    private static long cooldownStartTimeMs = 0L;
    private static long cooldownDurationMs = 0L;
    private static long cooldownEndTimeMs = 0L;

    // Ticking and drink tracking state
    private static boolean wasUsingHeal2 = false;
    private static int lastUseTimeLeft = 0;
    private static int initialHeal2Count = 0;
    private static int drinkSlot = -1;
    private static boolean drinkOffhand = false;

    // Rollback detection state (fresh drink)
    private static boolean watchingRollback = false;
    private static long rollbackWatchExpiry = 0L;
    private static int targetRollbackCount = 0;

    // Secondary drink attempt while already cooling down
    private static boolean watchingSecondaryDrink = false;
    private static long secondaryWatchExpiry = 0L;
    private static int secondaryTargetCount = 0;

    @Override
    public void onInitializeClient() {
        ElyhealpotConfig.getInstance();

        // Register client command /elyhealpot
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("elyhealpot")
                .executes(context -> {
                    sendInfo(context.getSource());
                    return 1;
                })
                .then(ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.literal("funtime").executes(context -> {
                        ElyhealpotConfig.getInstance().setMode(ElyhealpotConfig.ServerMode.FUNTIME);
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fРежим установлен на: §eFunTime §7(КД: 20 сек)"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("spacetimes").executes(context -> {
                        ElyhealpotConfig.getInstance().setMode(ElyhealpotConfig.ServerMode.SPACETIMES);
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fРежим установлен на: §bSpaceTimes §7(КД: 18.4 сек)"));
                        return 1;
                    }))
                )
                .then(ClientCommandManager.literal("vanillacd")
                    .executes(context -> {
                        ElyhealpotConfig cfg = ElyhealpotConfig.getInstance();
                        cfg.showVanillaCooldownNumbers = !cfg.showVanillaCooldownNumbers;
                        cfg.save();
                        String state = cfg.showVanillaCooldownNumbers ? "§aВключены" : "§cВыключены";
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fЧисла ванильного майнкрафтовского КД: " + state));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("on").executes(context -> {
                        ElyhealpotConfig.getInstance().showVanillaCooldownNumbers = true;
                        ElyhealpotConfig.getInstance().save();
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fЧисла ванильного майнкрафтовского КД: §aВключены"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(context -> {
                        ElyhealpotConfig.getInstance().showVanillaCooldownNumbers = false;
                        ElyhealpotConfig.getInstance().save();
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fЧисла ванильного майнкрафтовского КД: §cВыключены"));
                        return 1;
                    }))
                )
                .then(ClientCommandManager.literal("decimals")
                    .then(ClientCommandManager.literal("low").executes(context -> {
                        ElyhealpotConfig.getInstance().decimalDisplay = ElyhealpotConfig.DecimalDisplay.LOW;
                        ElyhealpotConfig.getInstance().save();
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fДесятичные доли: §eТолько когда < 10 сек"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("always").executes(context -> {
                        ElyhealpotConfig.getInstance().decimalDisplay = ElyhealpotConfig.DecimalDisplay.ALWAYS;
                        ElyhealpotConfig.getInstance().save();
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fДесятичные доли: §eВсегда"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("none").executes(context -> {
                        ElyhealpotConfig.getInstance().decimalDisplay = ElyhealpotConfig.DecimalDisplay.NONE;
                        ElyhealpotConfig.getInstance().save();
                        context.getSource().sendFeedback(Text.literal("§a[ElyHealPot] §fДесятичные доли: §cОтключены (только целые числа)"));
                        return 1;
                    }))
                )
            );
        });

        // Tick event to track drinking and watch for server rollback
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                wasUsingHeal2 = false;
                watchingRollback = false;
                return;
            }

            boolean isUsing = player.isUsingItem();
            ItemStack active = player.getActiveItem();

            if (isUsing && isHealing2Potion(active)) {
                if (!wasUsingHeal2) {
                    wasUsingHeal2 = true;
                    initialHeal2Count = countHealing2Potions(player);
                    drinkSlot = player.getInventory().getSelectedSlot();
                    drinkOffhand = (player.getActiveHand() == Hand.OFF_HAND);
                }
                lastUseTimeLeft = player.getItemUseTimeLeft();
            } else {
                if (wasUsingHeal2) {
                    // Player finished the drink animation (within 5 ticks of completion)
                    if (lastUseTimeLeft <= 5) {
                        onDrinkFinished();
                    }
                    wasUsingHeal2 = false;
                }
            }

            // Watch for server rollback: if server restores the potion, cancel cooldown immediately
            if (watchingRollback) {
                if (System.currentTimeMillis() > rollbackWatchExpiry) {
                    watchingRollback = false;
                } else {
                    int currentCount = countHealing2Potions(player);
                    if (currentCount >= targetRollbackCount) {
                        // The potion returned! Server cancelled the drink. Cancel timer!
                        resetCooldown();
                        watchingRollback = false;
                    }
                }
            }

            // Watch for drink attempt while already cooling down:
            if (watchingSecondaryDrink) {
                if (System.currentTimeMillis() > secondaryWatchExpiry) {
                    watchingSecondaryDrink = false;
                } else {
                    int currentCount = countHealing2Potions(player);
                    if (currentCount < secondaryTargetCount) {
                        // Count actually decreased! Server allowed the drink! Restart cooldown!
                        triggerHealPotCooldown();
                        watchingSecondaryDrink = false;
                    }
                    // If count didn't decrease (server rejected), do NOTHING: existing timer continues smoothly!
                }
            }
        });
    }

    public static void onDrinkFinished() {
        long now = System.currentTimeMillis();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        int count = player != null ? countHealing2Potions(player) : 0;

        if (initialHeal2Count <= 0) {
            initialHeal2Count = count + 1;
        }

        // Case 1: If cooldown is ALREADY active, do NOT reset or overwrite it!
        if (isHealPotCooldownActive()) {
            // Player tried to drink while on cooldown.
            // Server will reject this. We watch if count actually decreases.
            // Only if count decreases (server actually allowed it) do we restart the cooldown.
            watchingSecondaryDrink = true;
            secondaryWatchExpiry = now + 900L;
            secondaryTargetCount = count;
            return;
        }

        // Case 2: Cooldown was NOT active: start it immediately!
        triggerHealPotCooldown();
        watchingRollback = true;
        rollbackWatchExpiry = now + 900L;
        targetRollbackCount = initialHeal2Count;
    }

    private static void sendInfo(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        ElyhealpotConfig cfg = ElyhealpotConfig.getInstance();
        String currentMode = cfg.mode == ElyhealpotConfig.ServerMode.SPACETIMES ? "§bSpaceTimes (18.4 сек)" : "§eFunTime (20 сек)";
        String vanillaState = cfg.showVanillaCooldownNumbers ? "§aВкл" : "§cВыкл";
        source.sendFeedback(Text.literal("§6=== §eElyHealPot §6==="));
        source.sendFeedback(Text.literal("§7Текущий режим: " + currentMode));
        source.sendFeedback(Text.literal("§7Числа на майнкрафтовском КД: " + vanillaState));
        source.sendFeedback(Text.literal("§7Команды:"));
        source.sendFeedback(Text.literal("§f/elyhealpot mode funtime §7— режим FunTime (КД 20 сек)"));
        source.sendFeedback(Text.literal("§f/elyhealpot mode spacetimes §7— режим SpaceTimes (КД 18.4 сек)"));
        source.sendFeedback(Text.literal("§f/elyhealpot vanillacd <on|off> §7— включить/выключить числа на майнкрафтовском КД"));
        source.sendFeedback(Text.literal("§f/elyhealpot decimals <low|always|none> §7— режим десятых долей"));
    }

    public static int countHealing2Potions(ClientPlayerEntity player) {
        if (player == null) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isHealing2Potion(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean isHealing2Potion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents != null) {
            if (contents.matches(Potions.STRONG_HEALING)) {
                return true;
            }

            for (StatusEffectInstance effect : contents.getEffects()) {
                if (effect.getEffectType().equals(StatusEffects.INSTANT_HEALTH) && effect.getAmplifier() >= 1) {
                    return true;
                }
            }

            if (contents.potion().isPresent()) {
                String id = contents.potion().get().getIdAsString();
                if ("minecraft:strong_healing".equals(id) || "strong_healing".equals(id)) {
                    return true;
                }
            }
        }

        // Fallback for custom named potions on servers
        if (stack.isOf(Items.POTION)) {
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            if ((name.contains("исцелен") || name.contains("лечен") || name.contains("healing"))
                    && (name.contains("ii") || name.contains("2"))) {
                return true;
            }
        }

        return false;
    }

    public static synchronized void triggerHealPotCooldown() {
        long now = System.currentTimeMillis();
        cooldownStartTimeMs = now;
        cooldownDurationMs = (long) (ElyhealpotConfig.getInstance().getCooldownSeconds() * 1000.0f);
        cooldownEndTimeMs = now + cooldownDurationMs;
    }

    public static synchronized void resetCooldown() {
        cooldownStartTimeMs = 0L;
        cooldownDurationMs = 0L;
        cooldownEndTimeMs = 0L;
    }

    public static boolean isHealPotCooldownActive() {
        return System.currentTimeMillis() < cooldownEndTimeMs;
    }

    public static float getHealPotRemainingSeconds() {
        long remainingMs = cooldownEndTimeMs - System.currentTimeMillis();
        return Math.max(0.0f, remainingMs / 1000.0f);
    }

    public static float getHealPotCooldownProgress() {
        if (cooldownDurationMs <= 0L) return 0.0f;
        long remainingMs = cooldownEndTimeMs - System.currentTimeMillis();
        if (remainingMs <= 0L) return 0.0f;
        return MathHelper.clamp((float) remainingMs / (float) cooldownDurationMs, 0.0f, 1.0f);
    }

    public static float getVanillaCooldownSeconds(ItemCooldownManager manager, ItemStack stack) {
        if (manager instanceof CooldownAccessor accessor) {
            return accessor.elyhealpot$getRemainingSeconds(stack);
        }
        return 0.0f;
    }

    public static void renderCooldown(DrawContext context, TextRenderer textRenderer, ItemStack stack, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        boolean isHealing2 = isHealing2Potion(stack);
        boolean isVanillaCoolingDown = player.getItemCooldownManager().isCoolingDown(stack);

        // 1. Draw simulated cooldown sweep (empty over time) only if server does NOT have vanilla cooldown on this item
        if (isHealing2 && isHealPotCooldownActive() && !isVanillaCoolingDown) {
            float progress = getHealPotCooldownProgress();
            if (progress > 0.0f) {
                int k = y + MathHelper.floor(16.0f * (1.0f - progress));
                int l = k + MathHelper.ceil(16.0f * progress);
                context.fill(RenderPipelines.GUI, x, k, x + 16, l, 0x7FFFFFFF);
            }
        }

        // 2. Cooldown numbers
        float remainingSeconds = 0.0f;
        if (isVanillaCoolingDown) {
            if (ElyhealpotConfig.getInstance().showVanillaCooldownNumbers) {
                remainingSeconds = getVanillaCooldownSeconds(player.getItemCooldownManager(), stack);
            }
        } else if (isHealing2 && isHealPotCooldownActive()) {
            remainingSeconds = getHealPotRemainingSeconds();
        }

        if (remainingSeconds > 0.0f) {
            renderCooldownNumber(context, textRenderer, remainingSeconds, x, y);
        }
    }

    public static void renderCooldownNumber(DrawContext context, TextRenderer textRenderer, float remaining, int x, int y) {
        ElyhealpotConfig config = ElyhealpotConfig.getInstance();
        String text;
        if (remaining >= 60.0f) {
            int min = (int) (remaining / 60.0f);
            text = min + "m";
        } else if (config.decimalDisplay == ElyhealpotConfig.DecimalDisplay.ALWAYS) {
            text = String.format(Locale.ROOT, "%.1f", remaining);
        } else if (config.decimalDisplay == ElyhealpotConfig.DecimalDisplay.NONE) {
            text = String.valueOf((int) Math.ceil(remaining));
        } else { // LOW (default)
            if (remaining < 10.0f) {
                text = String.format(Locale.ROOT, "%.1f", remaining);
            } else {
                text = String.valueOf((int) Math.ceil(remaining));
            }
        }

        // Scale larger, slightly adjust for 4-character strings like "18.4"
        float scale = text.length() > 3 ? 0.82f : 1.0f;

        context.getMatrices().pushMatrix();
        // Top-left corner of the slot
        context.getMatrices().translate(x + 1.0f, y + 1.0f);
        context.getMatrices().scale(scale, scale);

        context.drawTextWithShadow(textRenderer, text, 0, 0, 0xFFFFFFFF);
        context.getMatrices().popMatrix();
    }
}
