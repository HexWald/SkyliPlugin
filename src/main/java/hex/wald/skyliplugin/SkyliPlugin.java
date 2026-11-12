package hex.wald.skyliplugin;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class SkyliPlugin extends JavaPlugin implements Listener {
    private static final String USE = "skyli.use";
    private static final String RELOAD = "skyli.reload";
    private static final String DOUBLE_SNEAK = "double-sneak";
    private static final String SNEAK_SWAP = "sneak-swap";
    private static final long DOUBLE_SNEAK_WINDOW = TimeUnit.MILLISECONDS.toNanos(350);

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private NamespacedKey bindKey;
    private NamespacedKey modeKey;
    private NamespacedKey muteKey;
    private YamlConfiguration settings;
    private SkyliEffect effect;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = readSettings();
        } catch (IOException | InvalidConfigurationException error) {
            getLogger().severe("Could not load config.yml: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bindKey = new NamespacedKey(this, "bind");
        modeKey = new NamespacedKey(this, "bind-mode");
        muteKey = new NamespacedKey(this, "muted");
        effect = new SkyliEffect(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.nanoTime();
            cooldowns.values().removeIf(end -> end - now <= 0);
            lastSneak.values().removeIf(start -> now - start > DOUBLE_SNEAK_WINDOW);
        }, 1200, 1200);
    }

    @Override
    public void onDisable() {
        if (effect != null) {
            effect.stopAll();
        }
        cooldowns.clear();
        lastSneak.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "normal" : args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("reload") && args.length == 1) {
            reloadSettings(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эта команда доступна только игрокам.");
            return true;
        }
        if (!player.hasPermission(USE)) {
            player.sendMessage("Нет права " + USE);
            return true;
        }
        if (subcommand.equals("bind")) {
            handleBind(player, args);
            return true;
        }
        if (args.length == 1 && subcommand.equals("mute")) {
            toggleMute(player);
            return true;
        }

        SkyliEffect.Mode mode = SkyliEffect.Mode.named(subcommand);
        if (mode == null || args.length > 1) {
            return false;
        }
        whine(player, mode);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean canUse = sender instanceof Player && sender.hasPermission(USE);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (canUse) options.addAll(List.of("quiet", "deep", "mute", "bind"));
            if (sender.hasPermission(RELOAD)) options.add("reload");
        } else if (canUse && args.length >= 2 && args[0].equalsIgnoreCase("bind")) {
            if (args.length == 2) options.addAll(List.of(DOUBLE_SNEAK, SNEAK_SWAP, "off"));
            if (args.length == 3 && isGesture(args[1])) options.addAll(List.of("normal", "quiet", "deep"));
        }

        if (options.isEmpty()) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !player.hasPermission(USE) || !bind(player).equals(DOUBLE_SNEAK)) {
            return;
        }

        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        Long previous = lastSneak.put(id, now);
        if (previous != null && now - previous <= DOUBLE_SNEAK_WINDOW) {
            lastSneak.remove(id);
            whine(player, boundMode(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && player.hasPermission(USE) && bind(player).equals(SNEAK_SWAP)) {
            event.setCancelled(true);
            whine(player, boundMode(player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resetGesture(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        resetGesture(event.getPlayer());
    }

    private void resetGesture(Player player) {
        lastSneak.remove(player.getUniqueId());
        effect.stop(player);
    }

    private void whine(Player player, SkyliEffect.Mode mode) {
        if (player.isDead()) return;

        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        Long end = cooldowns.get(id);
        if (end != null && end - now > 0) {
            long seconds = 1 + TimeUnit.NANOSECONDS.toSeconds(end - now - 1);
            player.sendMessage(message(settings, "cooldown").replace("%time%", Long.toString(seconds)));
            return;
        }

        int seconds = Math.max(0, settings.getInt("cooldown", 5));
        if (seconds > 0) {
            cooldowns.put(id, now + TimeUnit.SECONDS.toNanos(seconds));
        }
        effect.play(player, mode, settings);
    }

    private String bind(Player player) {
        return player.getPersistentDataContainer().getOrDefault(bindKey, PersistentDataType.STRING, "off");
    }

    private SkyliEffect.Mode boundMode(Player player) {
        String name = player.getPersistentDataContainer().getOrDefault(modeKey, PersistentDataType.STRING, "normal");
        SkyliEffect.Mode mode = SkyliEffect.Mode.named(name);
        return mode == null ? SkyliEffect.Mode.NORMAL : mode;
    }

    private boolean isGesture(String name) {
        return name.equalsIgnoreCase(DOUBLE_SNEAK) || name.equalsIgnoreCase(SNEAK_SWAP);
    }

    private void handleBind(Player player, String[] args) {
        if (args.length == 1) {
            String gesture = bind(player);
            String mode = isGesture(gesture) ? " " + boundMode(player).name().toLowerCase(Locale.ROOT) : "";
            player.sendMessage("Текущий бинд: " + gesture + mode);
            return;
        }

        String gesture = args[1].toLowerCase(Locale.ROOT);
        SkyliEffect.Mode mode = args.length == 3
                ? SkyliEffect.Mode.named(args[2].toLowerCase(Locale.ROOT))
                : SkyliEffect.Mode.NORMAL;
        var data = player.getPersistentDataContainer();
        if (gesture.equals("off") && args.length == 2) {
            data.remove(bindKey);
            data.remove(modeKey);
            player.sendMessage("Бинд выключен.");
        } else if (isGesture(gesture) && args.length <= 3 && mode != null) {
            String modeName = mode.name().toLowerCase(Locale.ROOT);
            data.set(bindKey, PersistentDataType.STRING, gesture);
            data.set(modeKey, PersistentDataType.STRING, modeName);
            player.sendMessage("Бинд включён: " + gesture + " " + modeName);
        } else {
            player.sendMessage("/skyli bind <double-sneak|sneak-swap> [normal|quiet|deep] или /skyli bind off");
            return;
        }
        lastSneak.remove(player.getUniqueId());
    }

    private void toggleMute(Player player) {
        var data = player.getPersistentDataContainer();
        if (data.has(muteKey, PersistentDataType.BYTE)) {
            data.remove(muteKey);
            player.sendMessage(message(settings, "unmuted"));
        } else {
            data.set(muteKey, PersistentDataType.BYTE, (byte) 1);
            player.sendMessage(message(settings, "muted"));
        }
    }

    boolean canReceive(Player listener, Player source) {
        return listener.equals(source) || (listener.canSee(source)
                && !listener.getPersistentDataContainer().has(muteKey, PersistentDataType.BYTE));
    }

    private YamlConfiguration readSettings() throws IOException, InvalidConfigurationException {
        YamlConfiguration updated = new YamlConfiguration();
        updated.load(new File(getDataFolder(), "config.yml"));
        try (var defaults = new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8)) {
            updated.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        }
        return updated;
    }

    private void reloadSettings(CommandSender sender) {
        if (!sender.hasPermission(RELOAD)) {
            sender.sendMessage("Нет права " + RELOAD);
            return;
        }
        try {
            settings = readSettings();
        } catch (IOException | InvalidConfigurationException error) {
            getLogger().warning("Could not reload config.yml: " + error.getMessage());
            sender.sendMessage("Не удалось загрузить config.yml. Проверь журнал сервера.");
            return;
        }
        effect.stopAll();
        if (settings.getInt("cooldown", 5) <= 0) cooldowns.clear();
        sender.sendMessage("Конфиг Skyli обновлён.");
    }

    static String message(YamlConfiguration settings, String key) {
        String text = settings.getString("messages." + key);
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
