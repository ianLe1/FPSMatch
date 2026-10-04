package net.ptcrys.fpsmatch.common.effect;

import net.neoforged.fml.common.EventBusSubscriber;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.packet.effect.FlashBombAddonS2CPacket;
import net.ptcrys.fpsmatch.util.RenderUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = FPSMatch.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FlashBlindnessMobEffect extends MobEffect {

    private int fullBlindnessTime = 0;
    private int totalBlindnessTime = 0;
    private int ticker = 0;

    public FlashBlindnessMobEffect(MobEffectCategory pCategory) {
        super(pCategory, RenderUtil.color(255, 255, 255));
    }

    public int getFullBlindnessTime() {
        return fullBlindnessTime;
    }

    public void setFullBlindnessTime(int fullBlindnessTime) {
        this.fullBlindnessTime = fullBlindnessTime;
    }

    public int getTotalBlindnessTime() {
        return totalBlindnessTime;
    }

    public void setTotalBlindnessTime(int totalBlindnessTime) {
        this.totalBlindnessTime = totalBlindnessTime;
    }

    public void setTicker(int ticker) {
        this.ticker = ticker;
    }

    public int getTicker() {
        return ticker;
    }

    public void setTotalAndTicker(int totalBlindnessTime) {
        this.totalBlindnessTime = totalBlindnessTime;
        this.ticker = totalBlindnessTime;
    }

    @SubscribeEvent
    public static void onServerTickEvent(PlayerTickEvent.Post event) {
        {
            if (!event.getEntity().level().isClientSide && event.getEntity().hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
                MobEffectInstance effectInstance = event.getEntity().getEffect(FPSMEffectRegister.FLASH_BLINDNESS);
                if (effectInstance != null && effectInstance.getEffect() instanceof FlashBlindnessMobEffect flashBlindnessMobEffect) {
                    int fullBlindnessTime = flashBlindnessMobEffect.getFullBlindnessTime();
                    if (fullBlindnessTime > 0) {
                        flashBlindnessMobEffect.setFullBlindnessTime(fullBlindnessTime - 1);
                        FPSMatch.sendToPlayer((ServerPlayer) event.getEntity(), new FlashBombAddonS2CPacket(flashBlindnessMobEffect.getFullBlindnessTime(), flashBlindnessMobEffect.getTotalBlindnessTime(), flashBlindnessMobEffect.getTicker()));
                    } else {
                        int ticker = flashBlindnessMobEffect.getTicker();
                        if (ticker >= 1) {
                            flashBlindnessMobEffect.setTicker(ticker - 1);
                        }
                        FPSMatch.sendToPlayer((ServerPlayer) event.getEntity(), new FlashBombAddonS2CPacket(flashBlindnessMobEffect.getFullBlindnessTime(), flashBlindnessMobEffect.getTotalBlindnessTime(), flashBlindnessMobEffect.getTicker()));
                        if (flashBlindnessMobEffect.getTicker() == 0) {
                            event.getEntity().removeEffect(FPSMEffectRegister.FLASH_BLINDNESS);
                        }
                    }
                }
            }
        }
    }
}
