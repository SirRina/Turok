package com.oldturok.turok.module.modules.movement;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import com.oldturok.turok.module.Module;
import net.minecraftforge.client.event.InputUpdateEvent;

@Module.Info(name = "NoSlowDown", category = Module.Category.TUROK_MOVEMENT)
public class NoSlowDown extends Module {

    @EventHandler
    private Listener<InputUpdateEvent> eventListener = new Listener<>(event -> {
        if (mc.player.isHandActive() && !mc.player.isRiding()) {
            event.getMovementInput().moveStrafe *= 5;
            event.getMovementInput().moveForward *= 5;
        }
    });
}
