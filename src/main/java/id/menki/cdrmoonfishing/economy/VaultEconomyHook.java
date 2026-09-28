package id.menki.cdrmoonfishing.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

public final class VaultEconomyHook {
    private final JavaPlugin plugin;
    private Object provider;
    private Method depositMethod;
    private Method formatMethod;

    public VaultEconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean refresh() {
        boolean ready = connect(true);
        plugin.getLogger().info("Vault economy provider: " + (ready ? "READY" : "OFFLINE"));
        return ready;
    }

    public boolean ensureReady() {
        return isReady() || connect(false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean connect(boolean logErrors) {
        provider = null;
        depositMethod = null;
        formatMethod = null;

        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return false;
        }

        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) {
                return false;
            }

            provider = registration.getProvider();
            depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            formatMethod = economyClass.getMethod("format", double.class);
            return true;
        } catch (ReflectiveOperationException ex) {
            if (logErrors) {
                plugin.getLogger().warning("Could not hook Vault economy: " + ex.getMessage());
            }
            return false;
        }
    }

    public boolean isReady() {
        return provider != null && depositMethod != null;
    }

    public DepositResult deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0) {
            return new DepositResult(false, "Invalid transaction amount.");
        }
        if (!ensureReady()) {
            return new DepositResult(false, "Vault economy provider is not available.");
        }

        try {
            Object response = depositMethod.invoke(provider, player, amount);
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            boolean success = Boolean.TRUE.equals(successMethod.invoke(response));
            if (success) {
                return new DepositResult(true, null);
            }

            String error = "Economy provider rejected the transaction.";
            try {
                Object raw = response.getClass().getField("errorMessage").get(response);
                if (raw != null && !raw.toString().isBlank()) error = raw.toString();
            } catch (ReflectiveOperationException ignored) {
                // Vault EconomyResponse exposes this field, but keep a safe fallback.
            }
            return new DepositResult(false, error);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Vault deposit failed: " + ex.getMessage());
            return new DepositResult(false, "Economy transaction failed.");
        }
    }

    public String format(double amount) {
        if (ensureReady() && formatMethod != null) {
            try {
                Object value = formatMethod.invoke(provider, amount);
                if (value != null) return value.toString();
            } catch (ReflectiveOperationException ignored) {
                // Fall through to numeric formatting.
            }
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    public record DepositResult(boolean success, String error) {}
}
