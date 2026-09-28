package id.menki.cdrmoonfishing.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Migrates only untouched English defaults to Indonesian.
 * Custom administrator text is preserved because values are replaced only
 * when they exactly match the previous bundled defaults.
 */
public final class IndonesianLocalizationMigration {
    private IndonesianLocalizationMigration() { }

    public static void apply(JavaPlugin plugin) {
        migrateConfig(plugin);
        migrateFile(plugin, "bait.yml", baitReplacements());
        migrateFile(plugin, "rod.yml", rodReplacements());
        migrateFile(plugin, "contracts.yml", contractReplacements());
        migrateFile(plugin, "milestones.yml", milestoneReplacements());
        migrateFile(plugin, "fish.yml", fishReplacements());
    }

    private static void migrateConfig(JavaPlugin plugin) {
        boolean changed = false;
        changed |= replaceConfig(plugin, "messages.bite-common", "Something is biting...", "Ada ikan yang menyambar...");
        changed |= replaceConfig(plugin, "messages.bite-unusual", "Something unusual is biting...", "Ada sesuatu yang tidak biasa menyambar...");
        changed |= replaceConfig(plugin, "messages.bite-legendary", "Something HUGE took the bait...", "SESUATU YANG BESAR memakan umpannya...");
        changed |= replaceConfig(plugin, "messages.encounter-start", "FISH ON! Keep the line under control.", "IKAN TERSANGKUT! Jaga tarikan senar.");
        changed |= replaceConfig(plugin, "messages.escaped", "The fish escaped.", "Ikannya lepas.");
        changed |= replaceConfig(plugin, "messages.market-duplicate-blocked", "This catch identity has already been redeemed.", "Identitas tangkapan ini sudah pernah dijual.");
        if (plugin.getConfig().getInt("config-version", 1) < 2) {
            plugin.getConfig().set("config-version", 2);
            changed = true;
        }
        if (changed) plugin.saveConfig();
    }

    private static boolean replaceConfig(JavaPlugin plugin, String path, String oldValue, String newValue) {
        String current = plugin.getConfig().getString(path);
        if (!oldValue.equals(current)) return false;
        plugin.getConfig().set(path, newValue);
        return true;
    }

    private static void migrateFile(JavaPlugin plugin, String fileName, Map<String, TextReplacement> replacements) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (Map.Entry<String, TextReplacement> entry : replacements.entrySet()) {
            String current = yaml.getString(entry.getKey());
            TextReplacement replacement = entry.getValue();
            if (replacement.oldValue().equals(current)) {
                yaml.set(entry.getKey(), replacement.newValue());
                changed = true;
            }
        }
        if (!changed) return;
        try {
            yaml.save(file);
            plugin.getLogger().info("Localized untouched defaults in " + fileName + " to Bahasa Indonesia.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save Indonesian localization migration for " + fileName + ": " + ex.getMessage());
        }
    }

    private static Map<String, TextReplacement> baitReplacements() {
        Map<String, TextReplacement> map = new LinkedHashMap<>();
        add(map, "bait.worm.display-name", "Worm", "Cacing");
        add(map, "bait.shrimp.display-name", "Shrimp Bait", "Umpan Udang");
        add(map, "bait.glow_worm.display-name", "Glow Worm", "Cacing Bercahaya");
        add(map, "bait.moon_worm.display-name", "Moon Worm", "Cacing Bulan");
        add(map, "bait.ancient_bait.display-name", "Ancient Bait", "Umpan Kuno");
        return map;
    }

    private static Map<String, TextReplacement> rodReplacements() {
        Map<String, TextReplacement> map = new LinkedHashMap<>();
        add(map, "tiers.driftwood.display-name", "Driftwood Rod", "Joran Kayu Apung");
        add(map, "tiers.reinforced.display-name", "Reinforced Rod", "Joran Diperkuat");
        add(map, "tiers.oceanic.display-name", "Oceanic Rod", "Joran Samudra");
        add(map, "tiers.abyssal.display-name", "Abyssal Rod", "Joran Abyssal");
        add(map, "tiers.lunar.display-name", "Lunar Rod", "Joran Lunar");
        return map;
    }

    private static Map<String, TextReplacement> contractReplacements() {
        Map<String, TextReplacement> map = new LinkedHashMap<>();
        add(map, "contracts.rare_hunter.display-name", "Rare Hunter", "Pemburu Ikan Langka");
        add(map, "contracts.rare_hunter.description", "Catch 5 RARE-or-better fish.", "Tangkap 5 ikan LANGKA atau lebih tinggi.");
        add(map, "contracts.heavy_haul.display-name", "Heavy Haul", "Tangkapan Berat");
        add(map, "contracts.heavy_haul.description", "Catch a total of 30 kg of fish.", "Tangkap ikan dengan total berat 30 kg.");
        add(map, "contracts.moon_koi_night.display-name", "Moonlit Koi", "Koi di Bawah Bulan");
        add(map, "contracts.moon_koi_night.description", "Catch 1 Moon Koi at night.", "Tangkap 1 Koi Bulan pada malam hari.");
        add(map, "contracts.shrimp_specialist.display-name", "Shrimp Specialist", "Spesialis Umpan Udang");
        add(map, "contracts.shrimp_specialist.description", "Catch 3 fish using Shrimp bait.", "Tangkap 3 ikan menggunakan Umpan Udang.");
        add(map, "contracts.deep_water_hunt.display-name", "Deep Water Hunt", "Perburuan Laut Dalam");
        add(map, "contracts.deep_water_hunt.description", "Catch 3 fish from depth 20 or deeper.", "Tangkap 3 ikan dari kedalaman 20 blok atau lebih.");
        add(map, "contracts.storm_fisher.display-name", "Storm Fisher", "Pemancing Badai");
        add(map, "contracts.storm_fisher.description", "Catch 2 fish during a thunderstorm.", "Tangkap 2 ikan saat badai petir.");
        return map;
    }

    private static Map<String, TextReplacement> milestoneReplacements() {
        Map<String, TextReplacement> map = new LinkedHashMap<>();
        add(map, "milestones.collection_25.display-name", "FishDex Explorer", "Penjelajah FishDex");
        add(map, "milestones.collection_50.display-name", "FishDex Collector", "Kolektor FishDex");
        add(map, "milestones.collection_75.display-name", "FishDex Hunter", "Pemburu FishDex");
        add(map, "milestones.collection_100.display-name", "Master of the FishDex", "Master FishDex");
        add(map, "milestones.first_legendary.display-name", "Legendary Discovery", "Penemuan Legendaris");
        return map;
    }

    private static Map<String, TextReplacement> fishReplacements() {
        Map<String, TextReplacement> map = new LinkedHashMap<>();
        add(map, "fish.river_carp.display-name", "River Carp", "Ikan Mas Sungai");
        add(map, "fish.silver_salmon.display-name", "Silver Salmon", "Salmon Perak");
        add(map, "fish.moon_koi.display-name", "Moon Koi", "Koi Bulan");
        add(map, "fish.moon_koi.phases.moon_dance.display-name", "Moon Dance", "Tarian Bulan");
        add(map, "fish.moon_koi.phases.moon_dance.title", "§d§lMOON KOI", "§d§lKOI BULAN");
        add(map, "fish.moon_koi.phases.moon_dance.subtitle", "§7Follow its strange rhythm...", "§7Ikuti ritmenya yang aneh...");
        add(map, "fish.moon_koi.phases.final_dance.display-name", "Final Dance", "Tarian Terakhir");
        add(map, "fish.moon_koi.phases.final_dance.title", "§d§lFINAL DANCE", "§d§lTARIAN TERAKHIR");
        add(map, "fish.moon_koi.phases.final_dance.subtitle", "§fThe Moon Koi fights back!", "§fKoi Bulan mulai melawan!");
        add(map, "fish.abyss_eel.display-name", "Abyss Eel", "Belut Jurang");
        add(map, "fish.abyss_eel.phases.deep_stalk.display-name", "Deep Stalk", "Mengintai di Kedalaman");
        add(map, "fish.abyss_eel.phases.deep_stalk.title", "§5§lABYSS EEL", "§5§lBELUT JURANG");
        add(map, "fish.abyss_eel.phases.deep_stalk.subtitle", "§7Something circles beneath you...", "§7Sesuatu berputar di bawahmu...");
        add(map, "fish.abyss_eel.phases.abyssal_dive.display-name", "Abyssal Dive", "Terjun ke Jurang");
        add(map, "fish.abyss_eel.phases.abyssal_dive.title", "§5§lABYSSAL DIVE", "§5§lTERJUN KE JURANG");
        add(map, "fish.abyss_eel.phases.abyssal_dive.subtitle", "§dIt dives into the darkness!", "§dIa menyelam ke dalam kegelapan!");
        add(map, "fish.abyss_eel.phases.desperate_rush.display-name", "Desperate Rush", "Serbuan Terakhir");
        add(map, "fish.abyss_eel.phases.desperate_rush.title", "§c§lDESPERATE RUSH", "§c§lSERBUAN TERAKHIR");
        add(map, "fish.abyss_eel.phases.desperate_rush.subtitle", "§7Don't let the line snap!", "§7Jangan sampai senarnya putus!");
        add(map, "fish.lunar_leviathan.display-name", "Lunar Leviathan", "Leviathan Lunar");
        add(map, "fish.lunar_leviathan.phases.awakening.display-name", "Phase I - Awakening", "Fase I - Kebangkitan");
        add(map, "fish.lunar_leviathan.phases.awakening.title", "§6§lLUNAR LEVIATHAN", "§6§lLEVIATHAN LUNAR");
        add(map, "fish.lunar_leviathan.phases.awakening.subtitle", "§ePHASE I §7- The ancient beast awakens", "§eFASE I §7- Makhluk purba itu terbangun");
        add(map, "fish.lunar_leviathan.phases.abyssal_dive.display-name", "Phase II - Abyssal Dive", "Fase II - Terjun Abyssal");
        add(map, "fish.lunar_leviathan.phases.abyssal_dive.title", "§5§lABYSSAL DIVE", "§5§lTERJUN ABYSSAL");
        add(map, "fish.lunar_leviathan.phases.abyssal_dive.subtitle", "§dPHASE II §7- It drags the line into the abyss!", "§dFASE II §7- Ia menyeret senar ke kedalaman!");
        add(map, "fish.lunar_leviathan.phases.final_struggle.display-name", "Phase III - Final Struggle", "Fase III - Perlawanan Terakhir");
        add(map, "fish.lunar_leviathan.phases.final_struggle.title", "§4§lFINAL STRUGGLE", "§4§lPERLAWANAN TERAKHIR");
        add(map, "fish.lunar_leviathan.phases.final_struggle.subtitle", "§cPHASE III §7- Survive the last assault!", "§cFASE III §7- Bertahan dari serangan terakhir!");
        return map;
    }

    private static void add(Map<String, TextReplacement> map, String path, String oldValue, String newValue) {
        map.put(path, new TextReplacement(oldValue, newValue));
    }

    private record TextReplacement(String oldValue, String newValue) { }
}
