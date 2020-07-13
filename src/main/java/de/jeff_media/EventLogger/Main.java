package de.jeff_media.EventLogger;

import de.jeff_media.EventLogger.CancellationDetector;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private CancellationDetector<PlayerInteractEvent> cancellationDetector = new CancellationDetector<PlayerInteractEvent>(PlayerInteractEvent.class);


    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        cancellationDetector.addListener(new CancellationDetector.CancelListener<PlayerInteractEvent>() {
            @Override
            public void onCancelled(Plugin plugin, PlayerInteractEvent event) {
                getLogger().info("============== EVENT CANCELLED ==============");
                getLogger().info(String.format("Event: %s",event.getEventName()));
                getLogger().info(String.format("Class: %s",event.getClass().getName()));
                getLogger().info("has been cancelled by");
                getLogger().info(String.format("Plugin: %s",plugin.getName()));
                getLogger().info(String.format("Class: %s",plugin.getClass().getName()));
                getLogger().info("=============================================");
                int i = 0;
            }
        });
    }

    @Override
    public void onDisable() {
        //blockEventCancellationDetector.close();
        cancellationDetector.close();
    }

}
