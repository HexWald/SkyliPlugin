package hex.wald.skyliplugin;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SkyliPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Skyli плагин включен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Skyli плагин выключен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("skyli")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage("Только игрок может использовать эту команду!");
                return true;
            }

            Player player = (Player) sender;

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_WOLF_DEATH,
                    1.0f,
                    1.0f
            );

            for (Player nearby : Bukkit.getOnlinePlayers()) {

                if (nearby.equals(player)) continue;

                if (nearby.getWorld().equals(player.getWorld())
                        && nearby.getLocation().distance(player.getLocation()) <= 10) {

                    nearby.sendMessage("&7Игрок &e" + player.getName() + " &7поскулил (/skyli)");
                }
            }

            return true;
        }

        return false;
    }
}