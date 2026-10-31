package cn.linmumua.buildingwand.task;

import cn.linmumua.buildingwand.compat.CraftEngineHook;
import cn.linmumua.buildingwand.compat.EntityOccupancy;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.item.WandKeys;
import cn.linmumua.buildingwand.item.WandTier;
import cn.linmumua.buildingwand.wand.Face;
import cn.linmumua.buildingwand.wand.Pos;
import cn.linmumua.buildingwand.wand.PosHash;
import cn.linmumua.buildingwand.wand.WandOptions;
import cn.linmumua.buildingwand.wand.WandService;
import cn.linmumua.buildingwand.wand.undo.UndoHistory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 发光方块预览。
 *
 * <p>手持之杖瞄准某个面时，把即将铺开的位置用 {@link BlockDisplay} 显示出来，
 * 只对本人可见。结果没有变化时跳过重建，避免每 4 tick 都销毁重造实体。</p>
 *
 * <p><b>Folia 线程模型</b>：所有 Bukkit 调度都必须落在正确的区域线程上。
 * 这里用全局区域调度器做「遍历玩家」这件事（它只读全局名单），
 * 然后把每个玩家的具体工作派发到他实体自己的调度器上；
 * 实体的创建与销毁同样回到实体所属区域执行。任何一步直接在主线程
 * 调 {@code world.spawn}/{@code remove} 都会在 Folia 上抛
 * "I/O from global region" 之类的异常。</p>
 */
public final class PreviewTask {

    private final Plugin plugin;
    private final Config config;
    private final WandKeys keys;
    private final WandService service;

    /** 每个玩家当前挂着的预览实体。只在实体所属区域线程里读写。 */
    private final Map<UUID, List<UUID>> spawned = Collections.synchronizedMap(new HashMap<>());
    /** 上次渲染时的状态签名，用于跳过无变化的重建。 */
    private final Map<UUID, String> signature = Collections.synchronizedMap(new HashMap<>());
    /**
     * 廉价签名：只由「命中位置 + 面 + 选项 + 目标方块」构成，不包含 BFS 结果。
     *
     * <p>这样能先比较、再决定要不要跑 BFS。原先的签名把 plan 也算进去，等于每 4 tick
     * 都必须跑完一次完整洪泛才有资格判断「变了没」——纯属白烧 CPU。</p>
     */
    private final Map<UUID, String> cheapKey = Collections.synchronizedMap(new HashMap<>());
    /** 距上次真正重算 BFS 已经过了多少个预览周期，用于强制刷新。 */
    private final Map<UUID, Integer> idleTicks = Collections.synchronizedMap(new HashMap<>());
    /** 上次给该玩家输出的诊断消息，仅在内容变化时才真正输出，避免刷屏。 */
    private final Map<UUID, String> lastDebugMessage = Collections.synchronizedMap(new HashMap<>());

    /**
     * 即使廉价签名没变，也每隔这么多个预览周期强制重算一次。
     *
     * <p>为什么需要：廉价签名看不到「周围方块被别的东西改了」这类变化（别人放/拆方块、
     * 水流动、漏斗变化……）。完全不重算会让预览停留在过期状态。1 秒一次的重算成本可以忽略，
     * 但足以让预览不会长期失真。</p>
     */
    private static final int FORCE_REFRESH_CYCLES = 20;

    private ScheduledTask task;

    public PreviewTask(Plugin plugin, Config config, WandKeys keys, WandService service) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.service = service;
    }

    public void start() {
        if (!config.previewEnabled()) {
            plugin.getLogger().info("[预览] 未启用（preview.enabled=false），不启动预览任务。");
            return;
        }
        long interval = config.previewIntervalTicks();
        // 全局区域只负责派发，真正的读写都在玩家的区域线程里完成
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> dispatch(), interval, interval);
        if (task == null) {
            plugin.getLogger().warning("[预览] 任务调度失败（runAtFixedRate 返回 null），预览不可用。");
        } else {
            plugin.getLogger().info("[预览] 预览任务已启动，每 " + interval + " tick 刷新一次。");
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID playerId : new ArrayList<>(spawned.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                clear(player);
            }
        }
        signature.clear();
        cheapKey.clear();
        idleTicks.clear();
        lastDebugMessage.clear();
    }

    // ---------------------------------------------------------------- 派发

    private void dispatch() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 玩家的实体调度器保证回调跑在他自己的区域线程上。
            // 【必须检查返回值】run() 在实体不在当前 tick（未加载/正在传送）时返回 null，
            // 且不会执行回调。之前丢弃返回值，导致「任务看似在跑但什么都没发生」，
            // 而且没有任何日志可查——预览不显示时根本无法区分是这里被跳过还是后面被跳过。
            var scheduled = player.getScheduler().run(plugin, t -> update(player), () -> { });
            if (scheduled == null && config.previewDebug()) {
                plugin.getLogger().info("[预览诊断] 无法调度玩家 " + player.getName()
                        + "（实体不在当前 tick），本周期跳过");
            }

        }
    }

    /**
     * 单个玩家的预览刷新。
     *
     * <p>整体包一层 catch：这是每 4 tick 跑一次的调度回调，里面任何一处抛异常
     * （CE API 未就绪、区块未加载、实体已失效……）如果被调度器静默吞掉，
     * 现象就是「预览完全不显示，且日志里什么都没有」，与「任务没启动」无法区分。
     * 捕获后按 debug 开关打出堆栈，把这类失败变成可见的。</p>
     */
    private void update(Player player) {
        try {
            updateChecked(player);
        } catch (Exception e) {
            if (config.previewDebug()) {
                plugin.getLogger().warning("[预览诊断] " + player.getName()
                        + " 刷新时抛异常：" + e);
                for (StackTraceElement frame : e.getStackTrace()) {
                    plugin.getLogger().warning("    在 " + frame);
                }
            }
            clear(player);
        }
    }

    private void updateChecked(Player player) {
        ItemStack wand = player.getInventory().getItemInMainHand();
        WandTier tier = CraftEngineHook.tierOf(wand);
        if (tier == null) {
            // 【这条分支曾经是唯一没有日志的静默返回】
            // 一旦 CraftEngine 物品识别失败（CE 未就绪、物品不是 CE 自定义物品、
            // 或手上拿的是别的物品），预览就会一直不显示，而日志里一条痕迹都没有——
            // 和「任务没启动」完全无法区分。必须让它出声。
            debug(player, "跳过：手上物品未识别为之杖（" + wand.getType() + "）");
            clear(player);
            return;
        }
        // 【潜行 = 撤销预览】
        // 撤销预览画的是「下一次撤销会回滚掉的方块」，与准星指向哪无关，
        // 所以必须在射线检测之前分流：站着时才走原来的铺面预览。
        if (player.isSneaking()) {
            updateUndoPreview(player);
            return;
        }
        // 【不要再用「背包类型 == CRAFTING」判断是否开着界面】
        // 这个判据在 MUCore/Folia 26.3 上不成立：玩家什么都没开时，
        // getOpenInventory().getType() 返回的不是 CRAFTING（实测日志被
        // 「跳过：正开着界面」刷屏，预览一次都不渲染）。
        // 而且开着界面时渲染预览本身无害——GUI 会盖住画面，玩家看不到也不影响操作。
        // 留着这种「猜枚举值」的守卫，代价是整功能静默失效，收益却接近于零，所以直接去掉。

        var hit = player.rayTraceBlocks(6);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            debug(player, "跳过：射线没打到方块");
            clear(player);
            return;
        }
        Block clicked = hit.getHitBlock();
        Face face = toFace(hit.getHitBlockFace());
        if (face == null) {
            debug(player, "跳过：命中面无法映射（" + hit.getHitBlockFace() + "）");
            clear(player);
            return;
        }

        WandOptions options = WandOptions.read(wand, keys, config);
        Pos hitPos = new Pos(clicked.getX(), clicked.getY(), clicked.getZ());
        BlockData data = clicked.getBlockData();

        UUID id = player.getUniqueId();
        String cheap = cheapKeyOf(hitPos, face, options, data);
        int idle = idleTicks.merge(id, 1, Integer::sum);
        // 瞄准目标与选项都没变，且没到强制刷新周期：直接沿用现有实体，不跑 BFS
        if (cheap.equals(cheapKey.get(id)) && idle < FORCE_REFRESH_CYCLES) {
            return;
        }
        idleTicks.put(id, 0);
        cheapKey.put(id, cheap);

        List<Pos> plan = service.planOnBlock(player, hitPos, face, options, tier);
        if (plan.isEmpty()) {
            debug(player, "跳过：计划为空（命中=" + clicked.getType() + " 面=" + face + "）");
            clear(player);
            return;
        }

        // 【过滤掉被实体占住的位置】落块侧（WandService#placeOne）会拒绝把方块塞进活着的实体里
        // ——CraftEngine 家具的碰撞箱就是隐形 shulker 实体。预览原先不看实体，于是会画出
        // 一批「右键必然一格都不落」的位置。这里改成与落块共用 EntityOccupancy，两边结论一致。
        //
        // 【必须排除自己 spawn 的预览实体】否则上一轮渲染出的预览方块会参与判据，
        // 只要是「原地刷新」就会被自己挡住，预览表现为整片消失或越刷新越少。
        Set<Pos> blocked = EntityOccupancy.blockedPositions(player.getWorld(), plan,
                spawned.getOrDefault(id, List.of()));
        if (!blocked.isEmpty()) {
            plan = new ArrayList<>(plan);
            plan.removeIf(blocked::contains);
            debug(player, "过滤 " + blocked.size() + " 个被实体占据的位置，剩 " + plan.size() + " 格");
        }
        if (plan.isEmpty()) {
            debug(player, "跳过：计划被实体占据而清空（命中=" + clicked.getType() + "）");
            clear(player);
            return;
        }

        // 上限在过滤之后才裁：否则被挡掉的格子会白占名额，玩家看到的位置比该有的更少
        if (plan.size() > config.previewMaxBlocks()) {
            plan = plan.subList(0, config.previewMaxBlocks());
        }

        String sig = cheap + '|' + plan.size() + '|' + PosHash.of(plan);
        if (sig.equals(signature.get(id))) {
            return;
        }
        signature.put(id, sig);
        // 把最终用于显示的外观一并打进诊断：预览「形状不对」（比如显示成音符盒）时，
        // 这一行能直接区分「材质解析失败回退到了占位方块」还是「解析正确但客户端资源包没更新」。
        BlockData displayData = displayData(data);
        debug(player, "渲染 " + plan.size() + " 个预览方块（外观="
                + displayData.getAsString() + "）");
        render(player, plan, displayData);
    }

    /**
     * 诊断日志。只在 preview.debug=true 时输出，用来定位「预览不出现」卡在哪一步。
     * 排查这类问题靠猜代价太高：可能卡在物品识别、射线、计划为空或实体渲染，
     * 四者的现象都是「什么都看不到」。
     */
    private void debug(Player player, String message) {
        if (!config.previewDebug()) {
            return;
        }
        // 【同一条消息只在状态变化时打一次】
        // 这个刷新每 4 tick 跑一次，无条件输出会把聊天栏和控制台冲爆
        // （实测刷屏到完全看不清别的内容），反而让真正的信息被埋掉。
        UUID id = player.getUniqueId();
        String previous = lastDebugMessage.put(id, message);
        if (message.equals(previous)) {
            return;
        }
        plugin.getLogger().info("[预览诊断] " + player.getName() + " " + message);
        if (player.hasPermission("buildingwand.debug")) {
            player.sendMessage(net.kyori.adventure.text.Component.text("[预览] " + message));
        }
    }

    // ---------------------------------------------------------------- 实体

    private void render(Player player, List<Pos> plan, BlockData displayData) {
        // 建造预览的发光由配置控制：留空或写 NONE 表示不加发光描边
        render(player, plan, displayData, parseGlow(config.previewBuildGlow()));
    }

    /**
     * 生成预览方块实体。
     *
     * @param glow 发光描边颜色；null 表示不加发光
     */
    private void render(Player player, List<Pos> plan, BlockData displayData, Color glow) {
        // 这里必须【同步】移除旧实体：本方法在玩家自己的区域线程上执行，而预览方块就在
        // 玩家附近（射线 6 格内），区块必然已加载，可以直接 remove。
        // 如果改成派发到实体的调度器上异步删，删除会推到下一 tick，期间新旧两批实体
        // 叠在同一坐标，视觉上就是闪烁/重影。
        removeNow(player.getUniqueId());

        World world = player.getWorld();
        List<UUID> ids = new ArrayList<>(plan.size());
        for (Pos pos : plan) {
            Location loc = new Location(world, pos.x(), pos.y(), pos.z());
            BlockDisplay display = world.spawn(loc, BlockDisplay.class, entity -> {
                // 【setVisibleByDefault 必须在加入世界之前调用】这里的 consumer 正是
                // Paper 提供的「实体创建后、加入世界并广播给客户端之前」的时机。
                // 放到 spawn 返回之后再设置就已经晚了：实体已经按默认可见广播过一次。
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setBlock(displayData);
                // 预览高度：只缩放 Y 轴，宽度保持一格；底面贴在方块底部不动
                float peek = (float) config.previewPeek();
                if (peek != 1.0f) {
                    entity.setTransformation(new Transformation(
                            new Vector3f(), new Quaternionf(),
                            new Vector3f(1.0f, peek, 1.0f), new Quaternionf()));
                }
                if (glow != null) {
                    entity.setGlowing(true);
                    entity.setGlowColorOverride(glow);
                }
            });
            // 只让持有之杖的本人看到
            player.showEntity(plugin, display);
            ids.add(display.getUniqueId());
        }
        spawned.put(player.getUniqueId(), ids);
    }

    /** 解析发光颜色；空值或 NONE 表示不发光。 */
    private static Color parseGlow(String hex) {
        if (hex == null || hex.isBlank() || "NONE".equalsIgnoreCase(hex)) {
            return null;
        }
        return parseColor(hex);
    }

    /**
     * 撤销预览：蹲下时用红色发光方块标出会被回滚的位置。
     *
     * <p>它在射线检测之前分流，因为撤销与准星指向哪无关——玩家要看的是
     * 「按 Shift+左键会拆掉哪些方块」，而不是眼前的这一面墙。</p>
     *
     * <p>用实体而不是粒子：粒子每帧都要重发，蹲着不动时也会持续刷屏；
     * 实体只要生一次就能一直挂着，玩家松开 Shift 或撤销后才清掉。</p>
     *
     * <p>每 4 tick 跑一次，所以先比对签名：撤销内容没变就不重建实体。</p>
     */
    private void updateUndoPreview(Player player) {
        UUID id = player.getUniqueId();

        UndoHistory.Operation operation = service.undoHistory().peekLatest(player);
        if (operation == null) {
            debug(player, "跳过：没有可撤销的操作");
            signature.remove(id);
            cheapKey.remove(id);
            removeNow(id);
            return;
        }
        List<Pos> plan = new ArrayList<>(operation.changes().size());
        for (UndoHistory.Change change : operation.changes()) {
            plan.add(change.pos());
        }
        if (plan.size() > config.previewMaxBlocks()) {
            plan = plan.subList(0, config.previewMaxBlocks());
        }

        String sig = "undo|" + plan.size() + '|' + PosHash.of(plan);
        if (sig.equals(signature.get(id))) {
            return;
        }
        signature.put(id, sig);
        // 撤销预览用独立颜色：它标的是「即将被拆掉」的位置，必须与建造预览一眼分得开，
        // 所以不吃 preview.build-glow（那个默认是不发光的）。
        BlockData displayData = displayData(player.getWorld().getBlockAt(
                plan.get(0).x(), plan.get(0).y(), plan.get(0).z()).getBlockData());
        Color glow = parseGlow(config.previewDestroyGlow());
        debug(player, "渲染 " + plan.size() + " 个撤销预览方块");
        render(player, plan, displayData, glow == null ? Color.fromRGB(0xFF4040) : glow);
    }

    /**
     * 决定预览实体显示成什么方块。
     *
     * <p>默认用本插件资源包里的 {@code buildingwand:preview}：64x64 贴图、
     * 四条边各 1px 纯白、中间全透明，据此做出「只描边框、中间能透过去看地形」的效果。
     * 配置值 {@code TARGET} 表示用被瞄准的那颗方块（不透明、所见即所得）；
     * 其它值按方块 id 解析，解析失败也回落到 TARGET，
     * 避免因为写错配置导致整个预览消失。</p>
     */
    private BlockData displayData(BlockData target) {
        String raw = config.previewBuildMaterial();
        if (raw == null || raw.isBlank() || "TARGET".equalsIgnoreCase(raw)) {
            return target;
        }
        // 先按 CE 方块解析：本插件注册的 buildingwand:preview_* 是专用的半透明预览方块，
        // 有自定义模型与空名称，比原版玻璃更干净。
        BlockData craftEngine = CraftEngineHook.blockData(raw);
        if (craftEngine != null) {
            return craftEngine;
        }
        // 回退到原版方块：CE 未就绪、包没加载、或配置里写的就是原版材质。
        Material material = Material.matchMaterial(raw);
        if (material != null && material.isBlock()) {
            return material.createBlockData();
        }
        // 最后回退到目标方块本身，保证「配置写错也不会让预览消失」
        return target;
    }

    /** 在当前线程立即移除该玩家的预览实体。 */
    private void removeNow(UUID playerId) {
        List<UUID> ids = spawned.remove(playerId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (UUID entityId : ids) {
            var entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    /**
     * 玩家退出时彻底丢弃他与本任务相关的全部状态。
     *
     * <p>与 {@link #clear(Player)} 分开：clear 会在每次渲染/跳过时被调用，
     * 不能顺手删诊断去重记录（否则每条日志都会重刷）；真正该回收的时机只有退出。</p>
     */
    public void forget(Player player) {
        UUID id = player.getUniqueId();
        lastDebugMessage.remove(id);
        clear(player);
    }

    /**
     * 清除某玩家的预览。
     * 调用点（预览 tick、交互监听、退出事件）都跑在玩家自己的区域线程上，
     * 预览实体又都在玩家附近，因此可以直接移除，不必再绕一次调度器。
     */
    public void clear(Player player) {
        UUID id = player.getUniqueId();
        signature.remove(id);
        cheapKey.remove(id);
        idleTicks.remove(id);
        // 【不要在这里清 lastDebugMessage】
        // debug() 靠「与上一条相同就不输出」来避免每 4 tick 刷屏，而大部分分支都是
        // 先 debug(...) 再 clear(...)。在 clear 里清掉记录等于把去重直接作废，
        // 结果就是同一条「跳过：手上物品未识别为之杖」每 4 tick 打一遍。
        removeNow(id);
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 廉价签名：只反映「瞄准了什么、什么选项、目标方块是什么」。
     * 不包含任何 BFS 结果，因此可以在跑洪泛之前就完成比较。
     */
    private static String cheapKeyOf(Pos hit, Face face,
                                     WandOptions options, BlockData data) {
        return new StringBuilder(48)
                .append(hit).append('|').append(face)
                .append('|').append(options.lock()).append('|').append(options.match())
                .append('|').append(options.replace()).append('|').append(options.random())
                .append('|').append(data.getAsString())
                .toString();
    }


    private static Color parseColor(String hex) {
        try {
            String raw = hex.startsWith("#") ? hex.substring(1) : hex;
            return Color.fromRGB(Integer.parseInt(raw, 16));
        } catch (NumberFormatException e) {
            return Color.fromRGB(0xFFFFFF);
        }
    }

    private static Face toFace(BlockFace face) {
        return switch (face) {
            case UP -> Face.UP;
            case DOWN -> Face.DOWN;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case EAST -> Face.EAST;
            case WEST -> Face.WEST;
            default -> null;
        };
    }
}
