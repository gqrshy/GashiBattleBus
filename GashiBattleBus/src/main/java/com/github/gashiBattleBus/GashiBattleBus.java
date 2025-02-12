package com.github.gashiBattleBus;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.Sound;

public class GashiBattleBus extends JavaPlugin implements Listener {

    // 試合に参加しているプレイヤーの UUID を管理
    private final Set<UUID> activePlayers = new HashSet<>();

    // ファーストキルがすでに発生しているかどうかのフラグ（初期状態は false）
    private boolean firstKillAwarded = false;

    private Scoreboard scoreboard;
    private Objective objective;

    @Override
    public void onEnable() {
        // /startbr コマンドの登録
        Objects.requireNonNull(this.getCommand("startbr")).setExecutor(new StartBRCommand());
        // イベントリスナーの登録
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("BattleRoyalePlugin が有効になりました");
    }

    @Override
    public void onDisable() {
        getLogger().info("BattleRoyalePlugin が無効になりました");
    }

    // /startbr コマンドの処理（内部クラス）
    public class StartBRCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            // コマンド実行者がプレイヤーでなければ中断
            if (!(sender instanceof Player player)) {
                sender.sendMessage("このコマンドはプレイヤーのみが実行できます。");
                return true;
            }
            final World world = player.getWorld();

            // 現在そのワールドにいる全プレイヤーを試合参加リストに追加
            activePlayers.clear();
            for (Player p : world.getPlayers()) {
                activePlayers.add(p.getUniqueId());
            }

            // 試合開始時にファーストキルフラグをリセット
            firstKillAwarded = false;

            // スコアボードの初期化
            setupScoreboard();

            // カウントダウンの開始（5秒～）
            new BukkitRunnable() {
                int countdown = 5;

                @Override
                public void run() {
                    if (countdown > 0) {
                        // ワールド内の全プレイヤーにタイトル表示とサウンド再生
                        for (Player p : world.getPlayers()) {
                            p.sendTitle(ChatColor.RED + "" + countdown, "", 10, 20, 10);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                        countdown--;
                    } else {
                        // カウントダウン終了時に「GO!」を表示とサウンド再生
                        for (Player p : world.getPlayers()) {
                            p.sendTitle(ChatColor.GREEN + "GO!", "", 10, 20, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        }
                        // 試合開始処理
                        startBattleRoyale(world);
                        cancel();
                    }
                }
            }.runTaskTimer(GashiBattleBus.this, 0, 20);

            return true;
        }
    }

    /**
     * スコアボードのセットアップ
     */
    private void setupScoreboard() {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        scoreboard = scoreboardManager.getNewScoreboard();

        objective = scoreboard.registerNewObjective("br", "dummy", ChatColor.AQUA + "残り人数");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();

        // 試合参加プレイヤー全員にスコアボードを設定
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setScoreboard(scoreboard);
            }
        }
    }

    /**
     * スコアボードの更新（残り参加人数を表示）
     */
    private void updateScoreboard() {
        // 既存のエントリをクリア
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        int count = activePlayers.size();
        Score score = objective.getScore(ChatColor.YELLOW + "プレイヤー数: " + count);
        score.setScore(1);
    }

    /**
     * 試合開始処理
     */
    private void startBattleRoyale(World world) {
        Location spawn = world.getSpawnLocation();
        world.getWorldBorder().setCenter(spawn);
        world.getWorldBorder().setSize(1800);

        for (Player player : world.getPlayers()) {
            Location dropLocation = new Location(world, spawn.getX(), 200, spawn.getZ());
            player.teleport(dropLocation);

            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));

            player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));

            player.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET, 5));

            monitorPlayerFlight(player);
        }
    }

    /**
     * プレイヤーの着地を監視し、着地したらエリトラと花火を除去するタスク
     */
    private void monitorPlayerFlight(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnGround()) {
                    if (player.getInventory().getChestplate() != null &&
                            player.getInventory().getChestplate().getType() == Material.ELYTRA) {
                        player.getInventory().setChestplate(new ItemStack(Material.AIR));
                    }
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() == Material.FIREWORK_ROCKET) {
                            player.getInventory().remove(item);
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(this, 0, 10);
    }

    /**
     * プレイヤー死亡時の処理
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (activePlayers.contains(dead.getUniqueId())) {
            activePlayers.remove(dead.getUniqueId());
            updateScoreboard();
            Bukkit.broadcastMessage(ChatColor.RED
                    + dead.getName()
                    + " が排除された！ 残り "
                    + activePlayers.size()
                    + " 名が生存している。");
        }

        // ファーストキルがまだ発生していなければ、キラーに特典を付与
        Player killer = dead.getKiller();
        if (!firstKillAwarded && killer != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + killer.getName() + " minecraft:diamond_sword 1");
            firstKillAwarded = true;
            Bukkit.broadcastMessage(ChatColor.GOLD + killer.getName() + " がファーストキルを取り、ボーナスアイテムを獲得しました！");
        }

        // 試合終了判定（残り1人または0人の場合）
        if (activePlayers.size() == 1) {
            UUID winnerId = activePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " が優勝しました！");
            }
            // 試合終了後にスコアボードをリセットする
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        } else if (activePlayers.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "試合が終了しました！");
            // 試合終了後にスコアボードをリセットする
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }
}