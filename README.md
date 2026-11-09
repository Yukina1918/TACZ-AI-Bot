# taczbotai · TACZ Bot AI

[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-blue.svg)](#兼容性)
[![Forge](https://img.shields.io/badge/Forge-47.x-orange.svg)](#兼容性)
[![TACZ](https://img.shields.io/badge/TACZ-1.2.0-red.svg)](#兼容性)

基于 **Timeless and Classics Zero (TACZ) 1.2.0** 的 AI 机器人模组。
机器人会拿枪、瞄准、开火、换弹，能沿你录制的路径巡逻，支持计分板队伍对抗、5 档精度与「对局」模式。

---

## 目录

- [特性](#特性)
- [安装](#安装)
- [快速上手](#快速上手)
- [指令](#指令)
- [配置文件](#配置文件)
- [玩法说明](#玩法说明)
- [性能](#性能)
- [兼容性](#兼容性)
- [构建](#构建)
- [版本历史](#版本历史)
- [常见问题](#常见问题)
- [许可证](#许可证)

---

## 特性

**战斗**

- 完整复用 TACZ 原生射击链路（`IGunOperator`）：弹道、下坠、穿透、枪声、枪口火光、爆头倍率
- 持枪 / 瞄准 / 换弹动画由 TACZ 自身驱动，第三人称姿态正确
- 支持任意枪械与预装配件（`GunItemBuilder` 或整段 SNBT）
- **5 档精度**：Ⅰ 级几乎打不中 → Ⅴ 级接近自瞄，Ⅴ 级附带 **10% 锁头秒杀**
- 按枪种区分的交战距离（12 / 48 / 72 格）与换弹时长（1 / 1.5 / 2.5 / 2.5 / 5 / 6 秒）

**AI 行为**

- **非透视索敌**：必须先看见才锁定；目标躲进掩体时追「最后看见的位置」，15 秒彻底跟丢
- **枪声惊动**：无消音器开枪 → 64 格内敌方 bot 无视视线追踪你 10 秒；带消音器仅 8 格
- 无目标时随机巡逻，或沿录制路径行走

**队伍与对局**

- 完全走原版计分板队伍，支持友军伤害开关、头顶名字模式（总是 / 仅同队 / 隐藏）与 16 种颜色
- 对局模式：`none`（瞎逛）/ `cs`（终点徘徊）/ `bf`（移动-停留-移动，到头反向）
- 超时或一方全灭自动结算，结算后可按配置传送玩家、退队、清空背包

**其它**

- 史蒂夫骨架 + 4 张内置皮肤（资源包可覆盖）
- 内置「测试人员」静止靶子：无 AI、不会移动、没有攻击性，名字与血量可配置
  - 默认皮肤 `test_subject.png`（BF4 皮肤），资源包用同名路径 `assets/taczbotai/textures/entity/test_subject.png` 即可覆盖
- 图形界面配置（四页签，支持滚轮翻页），SERVER 配置仅房主/管理员可改
- 简体中文 / 繁體中文 / English 三语

---

## 安装

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x（开发基线 47.3.0） |
| TACZ | **1.2.0 及以上** |
| Java | 17 |

1. 安装 Minecraft 1.20.1 + Forge 47.x
2. 安装 [Timeless and Classics Zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) 1.2.0
3. 把 `dist/taczbotai-2.0.0.jar` 放进 `.minecraft/mods/`

---

## 快速上手

```mcfunction
# 生成 3 个红队 bot（默认无配件 1911）
/taczbotai spawn 3 red

# 换成你手里那把枪（把整段 NBT 写进配置）
/taczbotai gun

# 重新给他们发这把枪
/taczbotai equip

# 查看最近一只的状态
/taczbotai info

# 放几个静止靶子测试伤害
/taczbotai testsub 3
```

路径巡逻：

```mcfunction
# 1. 开始录制，选择 cs 模式（到终点后 5 格徘徊）
/taczbotai record cs start lane1

# 2. 边走边按 +（小键盘加号）记移动节点；按 - 记停留节点（弹窗填分/秒）

# 3. 结束并保存
/taczbotai record cs stop

# 4. 分配给周围 32 格内的 bot
/taczbotai path assign lane1
```

---

## 指令

所有指令需要**权限等级 2**（单人开作弊 / 服务器 OP）。子指令可用 Tab 补全。

### 生成与装备

| 指令 | 说明 |
|---|---|
| `/taczbotai spawn [数量] [队伍] [武器]` | 在脚下生成（1~32）。队伍省略用配置默认队；武器可写枪械 id 或 SNBT |
| `/taczbotai summon <x> <y> <z> <数量> [队伍] [枪械]` | 在指定坐标生成（支持 `~` 相对坐标，数量 1~64） |
| `/taczbotai testsub [数量]` | 生成静止靶子「测试人员」（1~64 个，默认 1） |
| `/taczbotai equip` | 按当前配置重新给最近的 bot 发枪 |
| `/taczbotai skin <0-4>` | 设置最近一只的皮肤（0 = 原版史蒂夫，1~4 = 内置迷彩） |
| `/taczbotai tier <1-5>` | 全局攻击档位（写入该存档配置，立即生效） |
| `/taczbotai info` | 最近一只的详细状态：枪械、弹匣、队伍、是否视你为敌等 |

### 队伍与显示

| 指令 | 说明 |
|---|---|
| `/taczbotai team set <队伍> [半径\|all]` | 加入队伍（省略范围 = 最近一只，`all` = 全维度） |
| `/taczbotai team clear [半径\|all]` | 脱离队伍 |
| `/taczbotai team friendlyfire <true\|false>` | 全局友军伤害开关 |
| `/taczbotai team showname <true\|false> [半径\|all]` | 头顶名字显示开关 |
| `/taczbotai team namemode <always\|team\|hidden> [半径\|all]` | 名字模式（默认 `team`：仅同队可见） |
| `/taczbotai team color <颜色> [半径\|all]` | 名字颜色（原版 16 色） |

### 路径录制

| 指令 | 说明 |
|---|---|
| `/taczbotai record <cs\|bf> start <名字>` | 开始录制，当前位置记为起点 |
| `/taczbotai record <cs\|bf> stop` | 结束录制，当前位置记为终点并保存 |
| `/taczbotai record node` | 记一个**移动节点**（等价按 `+` / `=`） |
| `/taczbotai record node stop <秒>` | 记一个**停留节点**（等价按 `-`，弹窗确认后自动调用） |
| `/taczbotai record undo` / `status` | 撤销上一个节点 / 查看当前录制状态 |

### 路径管理

| 指令 | 说明 |
|---|---|
| `/taczbotai path list` | 列出该存档所有路径 |
| `/taczbotai path assign <名字> [半径]` | 分配给范围内全部 bot（默认 32 格，最大 256） |
| `/taczbotai path clear` | 取消最近一只的路径 |
| `/taczbotai path delete <名字>` | 删除路径文件 |

### 对局

| 指令 | 说明 |
|---|---|
| `/taczbotai <none\|cs\|bf> start [分钟]` | 开始对局并计时（0 / 省略 = 不限时） |
| `/taczbotai <none\|cs\|bf> pause` | 暂停（冻结计时，再执行一次恢复） |
| `/taczbotai <none\|cs\|bf> end` | 立即结束对局（不杀人） |
| `/taczbotai <none\|cs\|bf> status` | 查看对局状态与剩余时间 |

### 配置

| 指令 | 说明 |
|---|---|
| `/taczbotai config list` | 列出全部配置键值 |
| `/taczbotai config get <键>` | 读取单项 |
| `/taczbotai config set <键> <值>` | 写入单项（布尔 / 数字 / 字符串 / 逗号列表） |

### 枪械 NBT

| 指令 | 说明 |
|---|---|
| `/taczbotai gun` | 把主手 TACZ 枪械的整段 NBT 写入配置 `bot.gunNbt` |
| `/taczbotai gun show` | 打印当前 NBT 与字符数 |
| `/taczbotai gun clear` | 清空 NBT，回退到 `bot.gunId` + `bot.attachments` |

### 按键

| 按键 | 作用 |
|---|---|
| 小键盘 `+` / `=` | 录制时记移动节点 |
| 小键盘 `-` / `-` | 录制时弹出「停留节点时长」窗口（分、秒） |

---

## 配置文件

配置为 **SERVER 类型**，每个存档各一份：

```
saves/<世界名>/serverconfig/taczbotai-server.toml
```

专用服务器则为 `config/taczbotai-server.toml`。**只有房主 / 服务器管理员**能修改。
图形界面入口：模组列表 → TACZ Bot AI → 配置（四个页签：战斗 / 数值 / 装备队伍路径 / 结算）。

主要配置段：

```toml
[attack]
    attackTier = 3            # 1~5 攻击档位
    instantKillEnabled = true # 档位 5 的锁头秒杀开关
    instantKillChance = 10    # 秒杀概率（%）
    requireLineOfSight = true # 非透视索敌
    lostSightSeconds = 15     # 跟丢多久放弃
[attack.range]
    close = 12.0              # 手枪 / 冲锋枪
    mid = 48.0                # 步枪 / 机枪 / 霰弹 / 重武器
    far = 72.0                # 狙击 / 射手
[alert]
    enabled = true
    trackSeconds = 10         # 被枪声惊动后的追踪时长
    unsilencedRadius = 64.0
    silencedRadius = 8.0
[reload]
    pistolSeconds = 1.0
    rifleSeconds = 1.5
    marksmanSeconds = 2.5
    sniperSeconds = 2.5
    mgSeconds = 5.0
    shotgunSeconds = 6.0
    heavySeconds = 6.0
[team]
    friendlyFire = false
    defaultTeam = "red"
[bot]
    gunId = "tacz:m1911"
    gunNbt = ""               # 优先级高于 gunId + attachments
    attachments = ["SCOPE=tacz:scope_acog_ta31"]
    names = ["bot_A", "bot_B", "bot_C", "bot_D"]
    health = 20.0
    speed = 0.32
    infiniteAmmo = true
[path]
    patrolSpeed = 1.0
    recordMode = "node"
[dummy]
    name = "测试人员"          # 靶子头顶名字
    health = 20.0            # 最大生命值
    invulnerable = false     # 是否无敌
[match]
    endTeleport = true        # 对局结束传送玩家
    endX = 0.0
    endY = 64.0
    endZ = 0.0
    endLeaveTeam = false      # 结束时退队
    endClearInventory = false # 结束时清空背包
```

---

## 玩法说明

### 攻击档位

射击时以「目标眼高 -0.35 格」为瞄准点，叠加角度误差后交给 TACZ 走原生弹道：

| 档位 | 默认角度误差 σ | 表现 |
|---|---|---|
| Ⅰ | 7.0° | 描边严重，几乎打不中 |
| Ⅱ | 4.0° | 命中率低 |
| Ⅲ | 2.0° | 普通人水平 |
| Ⅳ | 0.8° | 命中率较高 |
| Ⅴ | 0.15° | 接近枪械自身散布，并启用 10% 锁头秒杀 |

锁头秒杀排除：`forge:bosses` 标签实体、配置黑名单、创造 / 旁观模式玩家。

### 按枪种的交战距离与换弹

| 类别 | 枪械类型 | 射程 | 换弹 |
|---|---|---|---|
| 近 | `pistol` / `smg` | 12 格 | 1.0 s |
| 中 | `rifle` / `mg` / `shotgun` / 重武器 / 其它 | 48 格 | 步枪 1.5 s、机枪 5.0 s、霰弹与重武器 6.0 s |
| 远 | `sniper` / `marksman` / `dmr` | 72 格 | 2.5 s |

### 枪声惊动

- 真人玩家开枪时检测枪口是否装了消音器（`SilenceModifier`）
- 无消音器 → 64 格内敌方 bot 被惊动，**无视视线**追踪开枪者 10 秒
- 有消音器 → 仅 8 格内会被听到

### 路径模式

| 模式 | 行为 |
|---|---|
| `cs` | 单向走到终点，之后在终点 5 格范围内徘徊 |
| `bf` | 沿节点行走，在停留节点慢速徘徊指定时长；到最后一个节点后**原路反向返回** |

停留节点在 JSON 中记为 `"stop": true, "stay": <秒>`；旧路径文件（无该字段）按移动节点处理。

### 对局结算

- **超时**：时间到，bot 全部死亡，玩家按 `[match]` 配置传送
- **提前结束**：某一阵营（同队的 bot + 玩家）全部阵亡 → 立即结束并公告胜方
- 阵营 = 有 bot 的队伍 ∪ 有玩家的队伍；存活 = 活着的 bot + 活着的玩家

---

## 性能

针对「多 bot 同屏卡顿」的优化（2.0.0）：

| 优化点 | 说明 |
|---|---|
| 索敌扫描 | 14~22 tick 随机错峰，避免同 tick 集中扫描 |
| 视线检测 | 只对最近的 3 个候选做射线，结果缓存 4 tick |
| 路径数据 | 路径点与模式缓存，100 tick 或换路径时才重读 |
| 寻路 | 仅在导航结束 / 卡住 / 目标点移动 >2 格时重算（冷却 40 tick） |
| 对局检测 | 世界实体扫描 5 → 20 tick |
| 枪声惊动 | 仅真人玩家触发 + 5 tick 节流 |

---

## 兼容性

- Minecraft **1.20.1** / Forge **47.x**
- TACZ **1.2.0+**（`mods.toml` 中 `versionRange = "[1.2.0,)"`，不支持 1.1.8）
- 不依赖 CustomNPCs / KubeJS / 假玩家，纯独立生物
- 已适配常见性能模组（ModernFix、Embeddium 等）；未修改 TACZ 任何代码，仅调用公开 API

---

## 构建

需要 **JDK 17** 与 TACZ 1.2.0 的 jar（第三方二进制，不随仓库分发）：

```bash
# 1. 准备依赖
mkdir -p libs
cp /path/to/tacz-forge-1.20.1-1.2.0.jar libs/tacz-1.2.0.jar

# 2. 构建
./gradlew build          # Windows: gradlew.bat build

# 3. 产物
build/libs/taczbotai-2.0.0.jar
```

---

## 版本历史

| 版本 | 变更 |
|---|---|
| **2.1.0** | 新增无 AI 静止靶子「测试人员」（名字/血量可配置，皮肤可在资源包覆盖） |
| 2.0.0 | 基线升级 TACZ 1.2.0；新增枪声惊动；性能优化；MIT 开源 |
| 1.3.8 | 对局结算（传送 / 退队 / 清背包）配置化，GUI 新增「结算」页 |
| 1.3.7 | 修正 PVE 结算（1 真人 vs N bot 可判胜负） |
| 1.3.6 | 修正阵营误判 |
| 1.3.5 | 杀光即结算、超时致死文案修正 |
| 1.3.4 | `summon`、对局指令、路径 cs/bf 节点模式、停留节点弹窗 |
| 1.3.3 | `team set/clear` 支持半径 |
| 1.3.2 | GUI 滚轮翻页 |
| 1.3.1 | SERVER 配置（仅管理员）、`config` 指令、Tab 补全 |
| 1.3.0 | 配置重构为世界配置 |
| 1.2.1 ~ 1.0.x | 早期迭代（生成、射击、路径、队伍、GUI） |

---

## 常见问题

**Q：bot 不拿枪 / 不开枪？**
检查 TACZ 是否为 1.2.0+，且整合包里该枪械 id 存在；用 `/taczbotai info` 看枪械与弹匣状态。

**Q：配置改了不生效？**
配置在 `saves/<世界>/serverconfig/`，每个存档独立；改完用 `/taczbotai config list` 确认，必要时重进世界。

**Q：路径走得很慢 / 卡住？**
检查节点间距（建议 5~20 格），调大 `[path] patrolSpeed`，确认脚下有可站立的方块。

**Q：对局结束没有结算？**
确认至少一方有 bot；结算按 bot 阵营判定，玩家单独一队不会成为阵营。

---

## 许可证

本项目以 **MIT License** 开源，详见 [LICENSE](LICENSE)。

```text
Copyright (c) 2026 Yukina_V
SPDX-License-Identifier: MIT
```

**第三方说明**

- 本项目**不包含** TACZ 的代码或资源。TACZ 是独立模组，版权归其作者所有，需自行从 CurseForge 下载
- `src/main/resources/assets/taczbotai/textures/entity/tba_1~4.png` 为本项目自制，随 MIT 授权
- 详细设计说明见 [`docs/DESIGN.md`](docs/DESIGN.md)
