package hex.wald.skyliplugin;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SkyliEffect {
    private static final String DEFAULT_SOUND = "minecraft:entity.wolf.death";
    private static final Particle.DustTransition BREATH = new Particle.DustTransition(
            Color.fromRGB(194, 212, 226), Color.fromRGB(235, 242, 247), 0.65f);

    enum Mode {
        NORMAL(3), QUIET(1), DEEP(5);

        private final int particles;

        Mode(int particles) {
            this.particles = particles;
        }

        static Mode named(String name) {
            return switch (name) {
                case "normal" -> NORMAL;
                case "quiet" -> QUIET;
                case "deep" -> DEEP;
                default -> null;
            };
        }
    }

    private final SkyliPlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();

    SkyliEffect(SkyliPlugin plugin) {
        this.plugin = plugin;
    }

    void play(Player source, Mode mode, YamlConfiguration settings) {
        boolean quiet = mode == Mode.QUIET;
        double scale = quiet ? number(settings, "quiet.radius-multiplier", 0.5, 1) : 1;
        double radius = number(settings, "radius", 10, 128);
        double soundRange = range(settings, "sound.radius", radius, scale);
        double chatRange = range(settings, "messages.radius", radius, scale);
        double breathRange = range(settings, "breath.radius", radius, scale);
        boolean showBreath = settings.getBoolean("breath.enabled", true);

        double volumeScale = quiet ? number(settings, "quiet.volume-multiplier", 0.35, 1) : 1;
        float volume = (float) (number(settings, "sound.volume", 1, 16) * volumeScale);
        float pitch = mode == Mode.DEEP
                ? (float) Math.max(0.5, number(settings, "sound.deep-pitch", 0.65, 2))
                : (float) Math.max(0.5, number(settings, "sound.pitch", 1, 2));
        NamespacedKey key = NamespacedKey.fromString(settings.getString("sound.key", DEFAULT_SOUND));
        String sound = key == null ? DEFAULT_SOUND : key.toString();

        String message = SkyliPlugin.message(settings, quiet ? "quiet" : "skyli")
                .replace("%player%", source.getName());
        Location origin = source.getLocation();
        List<Player> viewers = new ArrayList<>();
        stop(source);

        for (Player listener : source.getWorld().getPlayers()) {
            if (!plugin.canReceive(listener, source)) continue;

            boolean self = listener.equals(source);
            double distance = listener.getLocation().distanceSquared(origin);
            if (volume > 0 && (self || inRange(distance, soundRange))) {
                listener.playSound(source, sound, SoundCategory.PLAYERS, volume, pitch);
            }
            if (!self && inRange(distance, chatRange) && !message.isEmpty()) {
                listener.sendMessage(message);
            }
            if (showBreath && (self || inRange(distance, breathRange))) {
                viewers.add(listener);
            }
        }

        if (!viewers.isEmpty()) {
            showBreath(source, viewers, breathRange, mode.particles);
        }
    }

    private void showBreath(Player source, List<Player> viewers, double range, int particles) {
        World world = source.getWorld();
        BukkitTask task = new BukkitRunnable() {
            private int puffs = 3;

            @Override
            public void run() {
                if (!source.isOnline() || source.isDead() || !source.getWorld().equals(world)) {
                    stop(source);
                    return;
                }

                Location origin = source.getLocation();
                Location mouth = source.getEyeLocation();
                mouth.add(mouth.getDirection().multiply(0.37)).subtract(0, 0.18, 0);
                for (Player listener : viewers) {
                    if (!listener.isOnline() || !listener.getWorld().equals(world)
                            || !plugin.canReceive(listener, source)) {
                        continue;
                    }
                    if (listener.equals(source) || inRange(listener.getLocation().distanceSquared(origin), range)) {
                        listener.spawnParticle(Particle.DUST_COLOR_TRANSITION, mouth, particles,
                                0.06, 0.04, 0.06, 0, BREATH);
                    }
                }

                if (--puffs == 0) stop(source);
            }
        }.runTaskTimer(plugin, 0, 2);
        active.put(source.getUniqueId(), task);
    }

    void stop(Player player) {
        BukkitTask task = active.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    void stopAll() {
        active.values().forEach(BukkitTask::cancel);
        active.clear();
    }

    private static boolean inRange(double distance, double range) {
        return range > 0 && distance <= range;
    }

    private static double range(YamlConfiguration settings, String path, double fallback, double scale) {
        double radius = number(settings, path, fallback, 128) * scale;
        return radius * radius;
    }

    private static double number(YamlConfiguration settings, String path, double fallback, double max) {
        double value = settings.getDouble(path, fallback);
        return Double.isFinite(value) && value >= 0 ? Math.min(max, value) : fallback;
    }
}
