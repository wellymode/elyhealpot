package com.elyhealpot.mixin;

import com.elyhealpot.CooldownAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Map;

@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownManagerMixin implements CooldownAccessor {
    @Shadow @Final private Map<Identifier, ?> entries;
    @Shadow private int tick;
    @Shadow public abstract Identifier getGroup(ItemStack stack);

    private static Method endTickGetter = null;
    private static Field endTickField = null;

    @Override
    public float elyhealpot$getRemainingSeconds(ItemStack stack) {
        if (this.entries == null || stack == null || stack.isEmpty()) {
            return 0.0f;
        }
        Identifier id = this.getGroup(stack);
        if (id == null) {
            return 0.0f;
        }
        Object entry = this.entries.get(id);
        if (entry == null) {
            return 0.0f;
        }

        int endTick = getEndTick(entry);
        int remainingTicks = endTick - this.tick;
        if (remainingTicks > 0) {
            return remainingTicks / 20.0f;
        }
        return 0.0f;
    }

    private static int getEndTick(Object entry) {
        if (endTickGetter == null && endTickField == null) {
            try {
                RecordComponent[] components = entry.getClass().getRecordComponents();
                if (components != null && components.length >= 2) {
                    endTickGetter = components[1].getAccessor();
                    endTickGetter.setAccessible(true);
                }
            } catch (Throwable ignored) {
            }

            if (endTickGetter == null) {
                try {
                    Field[] fields = entry.getClass().getDeclaredFields();
                    for (int i = fields.length - 1; i >= 0; i--) {
                        if (fields[i].getType() == int.class && !Modifier.isStatic(fields[i].getModifiers())) {
                            endTickField = fields[i];
                            endTickField.setAccessible(true);
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            if (endTickGetter != null) {
                return (int) endTickGetter.invoke(entry);
            }
            if (endTickField != null) {
                return endTickField.getInt(entry);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
