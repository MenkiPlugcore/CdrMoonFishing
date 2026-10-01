package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.event.FishingBuffEventManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FishEventCommand implements CommandExecutor, TabCompleter {
    private static final long MAX_DURATION_MS = 24L * 60L * 60L * 1000L;
    private final FishingBuffEventManager manager;

    public FishEventCommand(FishingBuffEventManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("Kamu tidak memiliki izin.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Penggunaan: /" + label + " start <2x|4x|8x> <durasi>", NamedTextColor.RED));
                    sender.sendMessage(Component.text("Contoh: /" + label + " start 4x 20m", NamedTextColor.GRAY));
                    return true;
                }

                Integer multiplier = parseMultiplier(args[1]);
                if (multiplier == null) {
                    sender.sendMessage(Component.text("Multiplier hanya boleh 2x, 4x, atau 8x.", NamedTextColor.RED));
                    return true;
                }

                Long duration = parseDuration(args[2]);
                if (duration == null) {
                    sender.sendMessage(Component.text("Durasi tidak valid. Gunakan contoh: 30m, 2h, atau 90s.", NamedTextColor.RED));
                    return true;
                }

                FishingBuffEventManager.StartResult result = manager.start(multiplier, duration);
                sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }
            case "stop", "end" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("Kamu tidak memiliki izin.", NamedTextColor.RED));
                    return true;
                }
                if (!manager.stop(true)) {
                    sender.sendMessage(Component.text("Tidak ada Fishing Fever yang aktif.", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Fishing Fever dihentikan.", NamedTextColor.GREEN));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Subcommand tidak dikenal. Gunakan status, start, atau stop.", NamedTextColor.RED));
                return true;
            }
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("━━━━ FISHING FEVER ━━━━", NamedTextColor.GOLD));
        if (!manager.isActive()) {
            sender.sendMessage(Component.text("Status: TIDAK AKTIF", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Status: AKTIF", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Buff: x" + manager.multiplier(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Sisa waktu: " + manager.formatDuration(manager.remainingMillis()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Efek: rarity luck + big-fish luck global", NamedTextColor.GRAY));
    }

    private Integer parseMultiplier(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("x", "");
        try {
            int value = Integer.parseInt(normalized);
            return value == 2 || value == 4 || value == 8 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier = 60_000L; // angka tanpa suffix = menit
        String number = normalized;

        if (normalized.endsWith("ms")) return null;
        if (normalized.endsWith("s")) {
            multiplier = 1_000L;
            number = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 60_000L;
            number = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("h")) {
            multiplier = 3_600_000L;
            number = normalized.substring(0, normalized.length() - 1);
        }

        try {
            long value = Long.parseLong(number);
            if (value <= 0L) return null;
            long duration = Math.multiplyExact(value, multiplier);
            if (duration < 60_000L || duration > MAX_DURATION_MS) return null;
            return duration;
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("cdrmoonfishing.admin")) options.addAll(List.of("start", "stop"));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("cdrmoonfishing.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("2x", "4x", "8x").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start") && sender.hasPermission("cdrmoonfishing.admin")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("10m", "15m", "20m", "30m", "1h").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
