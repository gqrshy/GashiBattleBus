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

public class GashiBattleBus extends JavaPlugin implements Listener {

    // 試合に参加しているプレイヤーの UUID を管理
    private final Set<UUID> activePlayers = new HashSet<>();

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

            // スコアボードの初期化
            setupScoreboard();

            // カウントダウンの開始（5秒～）
            new BukkitRunnable() {
                int countdown = 5;

                @Override
                public void run() {
                    if (countdown > 0) {
                        // ワールド内の全プレイヤーにタイトル表示
                        for (Player p : world.getPlayers()) {
                            p.sendTitle(ChatColor.RED + "" + countdown, "", 10, 20, 10);
                        }
                        countdown--;
                    } else {
                        // カウントダウン終了時に「GO!」を表示
                        for (Player p : world.getPlayers()) {
                            p.sendTitle(ChatColor.GREEN + "GO!", "", 10, 20, 10);
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
        // スコアボード関連
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        scoreboard = scoreboardManager.getNewScoreboard();

        // objective の登録。表示名は「残り人数」
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
     * ・ワールドボーダーの中心をスポーン地点に設定
     * ・全プレイヤーを「降下開始地点」にテレポート（ここでは y=200 としています）
     * ・エリトラ、ロケット花火、スローフォーリング効果を付与
     * ・降下中を監視するタスクを開始
     *
     * @param world コマンド実行時のワールド
     */
    private void startBattleRoyale(World world) {
        Location spawn = world.getSpawnLocation();
        // ワールドボーダーの中心とサイズを設定（例：サイズ 1000）
        world.getWorldBorder().setCenter(spawn);
        world.getWorldBorder().setSize(1000);

        // 試合参加プレイヤーに対して降下開始の設定
        for (Player player : world.getPlayers()) {
            // 降下開始位置。スポーンの x,z を利用し、y は 200（必要に応じて調整可）
            Location dropLocation = new Location(world, spawn.getX(), 200, spawn.getZ());
            player.teleport(dropLocation);

            // スローフォーリング効果（300 tick = 15秒）
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));

            // エリトラを装備
            player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));

            // ロケット花火を 5 個追加
            player.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET, 5));

            // 降下完了（地面に着地）を監視
            monitorPlayerFlight(player);
        }
    }

    /**
     * プレイヤーの着地を監視し、着地したらエリトラと花火を除去するタスク
     *
     * @param player 降下中のプレイヤー
     */
    private void monitorPlayerFlight(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // プレイヤーが地面に着地していれば
                if (player.isOnGround()) {
                    // エリトラを除去（胸部スロットがエリトラの場合）
                    if (player.getInventory().getChestplate() != null &&
                            player.getInventory().getChestplate().getType() == Material.ELYTRA) {
                        player.getInventory().setChestplate(new ItemStack(Material.AIR));
                    }
                    // 所持しているロケット花火をすべて除去
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
     * ・試合参加プレイヤーリストから削除
     * ・スコアボード更新
     * ・倒された旨のメッセージ放送
     * ・残りプレイヤーが 1 人になれば優勝者を発表
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (activePlayers.contains(dead.getUniqueId())) {
            activePlayers.remove(dead.getUniqueId());
            updateScoreboard();
            Bukkit.broadcastMessage(ChatColor.RED + dead.getName() + " が倒されました！");
        }
        // 試合終了判定（残り 1 人または 0 人の場合）
        if (activePlayers.size() <= 1 && activePlayers.size() > 0) {
            UUID winnerId = activePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " が優勝しました！");
            }
        } else if (activePlayers.size() == 0) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "試合が終了しました！");
        }
    }
}
