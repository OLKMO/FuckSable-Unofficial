<div align="center">
  <img src="https://raw.githubusercontent.com/XSY-HYH/fuck-sable/main/src/main/resources/icon.jpg" alt="FuckSable" width="128" height="128">
</div>

# FuckSable

> 一个专门修 Sable 的 mod / A mod that fixes Sable
> 因为 Sable 的作者挖坑不填，我来填 / Because Sable's author left holes, I'm filling them

Sable 是个让 Minecraft 方块物理化的 mod，想法很酷。但 Rust + Java 的缝合架构嘛……你懂的。这个 mod 的存在就是为了补那些能让服务器血压直接拉满的洞。

Sable is a Minecraft mod that adds physics to blocks — a cool concept. But the Rust + Java hybrid architecture... you know the drill. This mod exists to patch the holes that'll send your server's blood pressure through the roof.

---

## 前置 / Dependency

- **Sable** — 不是可选，毕竟我存在的意义就是擦它的屁股 / Not optional, after all, my whole reason for existing is to clean up its mess

## 社区 / Community

- [Discord](https://discord.gg/6cZAm2zYNn)

---

## 修复项 / Fixes

默认全开，可通过配置文件单独关闭。依赖其他 mod 的修复项，缺 mod 会自动禁用。

Enabled by default, can be disabled individually via config. Fixes that depend on other mods auto-disable if the mod is missing.

### Sable 核心修复 / Core Fixes

| 修复项 / Fix | 描述 / Description |
|--------------|---------------------|
| `async-save` | SubLevel 保存操作重定向到异步 I/O 线程，防止保存时服务器冻结。Sable 的 Rust 原生层在主线程做同步 I/O —— 稍微大点的物理结构就能把整个服务器卡死。/ Redirects SubLevel saving to async I/O thread, preventing server freeze during save. Sable's Rust native layer does sync I/O on main thread — one slightly large physics structure can freeze the entire server. |
| `panic-guard` | 调用 Rust 原生代码前加安全检查，防止 Rust 侧 panic 把整个 JVM 带走。是的，Rust panic 会直接弄死 Java 进程，没有任何优雅处理。/ Adds safety checks before calling Rust native code, preventing Rust-side panic from taking down the entire JVM. Yes, Rust panic will kill the Java process directly, no graceful handling whatsoever. |
| `write-flush` | 更新存储文件头之前先刷盘，防止崩溃时数据变垃圾。Sable 的存储格式在异常退出后基本就是不可读的废数据。/ Ensures data is flushed to disk before updating storage file header, preventing data corruption on crashes. Sable's storage format becomes unreadable garbage after abnormal exit. |
| `corrupted-cleanup` | 清理 holding chunk 中损坏的 SubLevel 指针，防止反复加载报错。/ Removes corrupted sub-level pointers from holding chunks to prevent repeated load errors. |
| `plot-holder-guard` | plot 区块的 holder 不存在时安全跳过方块更新，而不是直接崩溃。竹子长在物理结构旁边？服务器炸。/ Safely skips block updates when plot chunk's holder doesn't exist, instead of crashing. Bamboo growing next to a physics structure? Server dies. |
| `sublevel-entity-guard` | 防止 `SubLevelInclusiveLevelEntityGetter` 遍历异常 AABB 时服务器卡死。`EntitySectionStorage` 内部数据被并发修改损坏后，实体碰撞检测可能陷入无限循环 —— 一只蝙蝠的碰撞查询就能卡死服务器 60 秒以上。/ Prevents server freeze when `SubLevelInclusiveLevelEntityGetter` iterates over abnormal AABBs. After `EntitySectionStorage` internal data gets corrupted by concurrent modification, entity collision checks can enter infinite loops — a single bat's collision query can lock up the server for 60+ seconds. |
| `command-block-sublevel-fix` | 禁止命令方块（及其变体）被放到物理化结构上。原版只禁止直接物理化命令方块，但你可以先放方块再物理化 —— 显然不是预期行为。/ Prevents command blocks (and variants) from being placed on physics-enabled structures. Vanilla only prevents directly physics-enabling command blocks, but you can place first then enable — clearly not intended. |
| `player-position-guard` | 玩家坐标超出世界边界时自动拉回，防止 SubLevel 物理把玩家扔出世界导致崩溃。Y 轴上限扩展至原版 +1000 格。/ Clamps player position to world border when coordinates exceed boundaries, preventing server crashes from SubLevel physics. Y-axis limit extended to vanilla +1000. |
| `light-engine-bounds-guard` | 防止 SubLevel 区段超出世界高度限制时光照引擎崩溃。/ Prevents light engine crashes when SubLevel sections exceed world height limits during light propagation. |
| `physics-ticket-guard` | 防止 PhysicsChunkTicketManager 导致 DistanceManager 内部状态损坏（ArrayIndexOutOfBoundsException）。/ Prevents server crash when PhysicsChunkTicketManager triggers DistanceManager internal state corruption. |
| `sublevel-volume-limit` | 限制单个物理结构最大方块数量（8192，约 16x16x32 体积），防止过大的碰撞体导致 Rapier 原生崩溃。/ Limits maximum block count of a single physics structure (8192, approx 16x16x32), preventing Rapier native crashes from oversized collision bodies. |

### 兼容性修复 / Compatibility Fixes

| 修复项 / Fix | 依赖 / Dependency | 描述 / Description |
|--------------|-------------------|---------------------|
| `carryon-compat` | CarryOn | 修 CarryOn 把玩家放到物理化 sub-level 时传送到错误维度并崩溃。Sable 的射线检测返回局部坐标，CarryOn 当全局坐标用，玩家直接被扔进虚空。/ Fixes CarryOn teleporting players to wrong dimension and crashing when placing onto physics sub-level. Sable's raycasting returns local coordinates, CarryOn treats them as global — players get thrown into the void. |
| `typewriter-server-fix` | Simulated | 修 Simulated 打字机在专用服务器上崩溃。服务端代码里出现了 `net.minecraft.client.gui.screens.Screen` —— 这玩意儿在专用服务器上根本不存在。客户端/服务端不分，离谱。/ Fixes Simulated typewriter crash on dedicated servers. Server code references `net.minecraft.client.gui.screens.Screen` — a class that doesn't exist on dedicated servers. Basic client/server separation failure, truly baffling. |
| `aeronautics-server-fix` | Aeronautics | 修航空学 SteamVentBlockEntity 在专用服务器上崩溃。又是客户端类引用，这次是 `Minecraft.getInstance()`。同一个坑踩两次，真有你的。/ Fixes Aeronautics SteamVentBlockEntity crash on dedicated servers. Another client class reference, this time `Minecraft.getInstance()`. Falling into the same pit twice, nice one. |
| `aeronautics-slime-bearfix` | Aeronautics | 修粘液球粘连结构时可以粘连旋转轴承导致其分离并产生穿模效应。**默认关闭** —— 因为很多玩法基于这个 bug。/ Fixes slime blocks sticking to bearing structures causing them to separate and clip through blocks. **Disabled by default** — because many builds rely on this bug. |
| `physics-staff-drag-clipfix` | Simulated | 修物理手杖高速拖动物理结构时穿模和飞出世界边界。在 `updatePose` 中钳制 SubLevel 位置到世界边界内，超出则清零速度。Y 轴上限扩展至原版 +1000 格。**默认开启**。/ Fixes physics structures clipping through blocks and flying out of world bounds when dragged at high speed. Clamps SubLevel position to world bounds in `updatePose`, zeroes velocity if out of bounds. Y-axis limit extended to vanilla +1000. **Enabled by default**. |
| `copycats-lift-compat` | Sable, Copycats | 修 Copycats 方块缺少 facing 属性时触发 `sable$getNormal` 导致服务器崩溃。/ Prevents server crash when Copycats blocks with missing facing property trigger `sable$getNormal` in onBlockChange. |
| `ctt-concurrent-fix` | CreateThreadedTrains | 修 CTT 和机械动力的并发问题。CTT 把火车 tick 移到工作线程，但只把 `manageEntities` 调度回主线程，`updateContraptionAnchors` 还在工作线程跑 —— 主线程读到不一致状态，`EntitySectionStorage` 里的 AVL 树被搞坏，轻则 NPE，重则整个服务器死循环卡死。/ Fixes concurrency issue between CTT and Create. CTT moves train ticking to worker threads but only schedules `manageEntities` back to main thread — `updateContraptionAnchors` still runs on workers. Main thread reads inconsistent state, corrupts AVL tree in `EntitySectionStorage`, resulting in NPE or entire server locking up in infinite loop. |
| `ctt-log-spam-fix` | CreateThreadedTrains | 抑制 CTT 列车计算失败时的重复 WARN 日志刷屏，每种异常类型只输出一次。/ Suppresses repeated warning logs from CTT when train calculation fails, only logs once per error type. |
| `effortless-particle-fix` | Effortless, Sable | 修 Effortless 对着 Sable 物理结构操作时客户端崩溃。Sable 射线检测返回 Plot 存储区域的远端坐标，Effortless 用该坐标生成粒子时客户端未加载该区块导致崩溃。修复方式：跳过未加载区块的粒子生成。/ Fixes Effortless client crash when interacting with Sable physics structures. Sable raycasting returns Plot storage area coordinates (distant chunks), Effortless uses these to generate particles but the client hasn't loaded those chunks. Fix: skip particle generation for unloaded chunks. |
| `vista-camera-chunk-fix` | Vista, Sable | 修 Vista 摄像头区块加载与 Sable 物理结构不兼容：ViewFinder 在物理结构上时，其坐标是 SubLevel 内部坐标，Vista 用该坐标在主世界 force-load 区块导致 TPS 掉 0 和无限加载循环。修复方式：force-load 前将坐标投影到主世界坐标。/ Fixes Vista camera chunk loading incompatibility with Sable physics structures: when ViewFinder is on a physics structure, its coordinates are SubLevel-internal, Vista force-loads wrong chunks in the overworld causing TPS drop and infinite loading loops. Fix: project coordinates to world coordinates before force-loading. |

---

## 调试命令 / Debug Commands

| 命令 / Command | 作用 / Purpose |
|----------------|----------------|
| `/fucksable` | 查看所有修复项状态 / View status of all fixes |
| `/fucksable <修复项/fix> on/off` | 启用/禁用某个修复项 / Enable/disable a fix |
| `/fucksable all on/off` | 启用/禁用全部修复项 / Enable/disable all fixes |
| `/fucksable default` | 恢复所有修复项为默认配置 / Reset all fixes to defaults |

---

## 国际化 / Internationalization (i18n)

FuckSable 支持自定义语言包。所有可打印信息（命令反馈、日志等）都可以通过语言文件覆盖。

FuckSable supports custom language packs. All printable messages (command feedback, logs, etc.) can be overridden via language files.

### 使用方法 / Usage

```
/fucksablelang <语言文件名>
```

- `<语言文件名>`: 语言文件的名称（不含路径，不含扩展名）/ Language file name (without path, without extension)

语言文件位于 `.minecraft/config/fucksable/lang/`（服务端则为 `config/fucksable/lang/`）。

Language files are located in `.minecraft/config/fucksable/lang/` (or `config/fucksable/lang/` for servers).

### 默认语言包 / Default Language Packs

| 文件名 / File Name | 语言 / Language |
|--------------------|-----------------|
| `zh.yml` | 简体中文 / Simplified Chinese |
| `en.yml` | 英文 / English |

### 创建自定义语言包 / Creating Custom Language Packs

1. 进入 `config/fucksable/lang/` 目录
2. 复制 `en.yml` 或 `zh.yml` 作为模板
3. 重命名为你想要的语言文件名（例如 `ja.yml`、`es.yml`、`fr.yml`）
4. 翻译 YAML 文件中的值（键名不要改）
5. 在游戏中执行 `/fucksablelang <文件名>` 切换

```
1. Navigate to `config/fucksable/lang/`
2. Copy `en.yml` or `zh.yml` as a template
3. Rename it to your desired language file name (e.g., `ja.yml`, `es.yml`, `fr.yml`)
4. Translate the values in the YAML file (don't change the keys)
5. Run `/fucksablelang <filename>` in-game to switch
```

### 注意事项 / Notes

- FuckSable 的可打印信息**不是很多**，所有文本加起来也就几十行
- 你完全可以**直接丢给 GPT** 去翻译，几秒钟搞定
- 切换语言后立即生效，不需要重启服务器
- 如果语言文件格式错误（无效 YAML），会自动回退到默认语言（英文）

- FuckSable's printable messages are **not that many** — all text combined is only a few dozen lines
- You can **just throw it at GPT** for translation, takes a few seconds
- Language switching takes effect immediately, no server restart required
- If a language file has invalid YAML, it will automatically fall back to default language (English)

### 示例：自定义语言文件结构 / Example: Custom Language File Structure

```yaml
# zh.yml - 简体中文示例 / Simplified Chinese example

command:
  fucksable:
    title: "=== FuckSable 修复状态 ==="
    fix:
      enabled: "✓ %s 已启用"
      disabled: "✗ %s 已禁用"
      missing_dep: "⚠ %s 已禁用 (缺少依赖: %s)"
    invalid_fix: "无效的修复项: %s"
    set:
      enabled: "已将 %s 设置为 %s"
    lang:
      switched: "语言已切换至: %s"
      file_not_found: "语言文件不存在: %s"
      invalid_yml: "语言文件格式错误，已回退至默认语言"
```

```yaml
# en.yml - 英文示例 / English example

command:
  fucksable:
    title: "=== FuckSable Fix Status ==="
    fix:
      enabled: "✓ %s is enabled"
      disabled: "✗ %s is disabled"
      missing_dep: "⚠ %s is disabled (missing dependency: %s)"
    invalid_fix: "Invalid fix: %s"
    set:
      enabled: "Set %s to %s"
    lang:
      switched: "Language switched to: %s"
      file_not_found: "Language file not found: %s"
      invalid_yml: "Invalid YAML format, falling back to default language"
```

---

## 为什么是 All-in-One？/ Why All-in-One?

**Q: 为什么不写单独的 mod 逐个修复，而是全部打包在一起？**

**A:** 因为我讨厌为了修复几个问题还要到处找补丁、一个个安装、反复排查冲突。

**Q: Why not write separate mods for each fix?**

**A:** Because I'm tired of hunting down patches, installing them one by one, and debugging conflicts just to fix a few issues.

**FuckSable 是全部打包：**
- 一个 mod，一次安装，所有修复同时生效
- 每一个修复都在真实服务端环境测试过，不是纸上谈兵
- 配置文件可以单独开关，不需要就不开
- 不用记这个 bug 归哪个 mod 管，一个命令全看见

**FuckSable is all-in-one:**
- One mod, one installation, all fixes work together
- Every fix has been tested on a real server environment, not just theory
- Config file lets you toggle individual fixes on/off
- No need to remember which bug belongs to which mod — one command shows everything

简单说就是：**省事**。

Simply put: **Less hassle**.

---

## 吐槽 / Rant

Sable 的 Rust + Java 架构本身不是问题。问题在于两边几乎都没有错误处理：

Sable's Rust + Java architecture isn't the problem. The problem is the near-total lack of error handling on both sides:

- Rust 侧 panic → 直接带走 JVM / Rust-side panic → directly takes down JVM
- Java 侧对 Rust 返回值校验 ≈ 不存在 / Java-side validation of Rust return values ≈ nonexistent
- 服务端引用客户端类？这不是边缘 case，是专用服务器一启动就崩的程度 / Server referencing client classes? Not edge cases, they crash dedicated servers at startup

依赖管理也是一坨屎：API 类散落在各种包路径下 —— `SubLevelContainerHolder` 在 `mixinterface.plot` 而不是 `api.sublevel`，`JOMLConversion` 在 `companion.math`。你永远猜不到下一个类在哪个包里。

Dependency management is also a mess: API classes scattered across random package paths — `SubLevelContainerHolder` in `mixinterface.plot` instead of `api.sublevel`, `JOMLConversion` in `companion.math`. You never know where the next class will be hiding.

**但话说回来**，方块物理化这个想法确实很酷。只是希望作者能多花点时间在稳定性上，而不是只追求功能。

**But that said**, the idea of block physics is indeed really cool. I just wish the author would spend more time on stability instead of just chasing features.

---

## 附加说明 / Additional Note

自动更新走的是**中国 IP**，而且**没有 SSL 保护**。

Auto-updates come from a **Chinese IP** with **no SSL protection**.

默认是关闭的（除非你自己改了配置）。**不要开**。需要更新就去 Modrinth 下，防止供应链攻击。

It's disabled by default (unless you changed the config). **DON'T enable it**. If you need an update, download from Modrinth to prevent supply chain attacks.

> ~~"我暂时没想把它开源，但你们大可信任安全性 —— 毕竟 Modrinth 审核过了对吧？"~~
> ~~"I'm not planning to open-source it for now, but you can trust its security — after all, Modrinth reviewed it, right?"~~

现在开源了。

Now it's open source. 
---

## 许可证 / License

MIT License - 详见 [LICENSE](LICENSE) 文件。

MIT License - see [LICENSE](LICENSE) file for details.

---
