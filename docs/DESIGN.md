# taczbotai · TACZ Bot AI（Minecraft 1.20.1 / Forge）

前置：**Timeless and Classics Zero 1.1.8-hotfix**（`curse.maven:timeless-and-classics-zero-1028108:8141310`）

## 构建

- 需要 **JDK 17**（本仓库不附带 JDK；请把 `JAVA_HOME` 指向 JDK 17）
- `./gradlew build`（Windows：`gradlew.bat build`）
- 产物：`build/libs/taczbotai-<版本>.jar`

## 当前功能（1.3.8）

- **生物**：史蒂夫骨架 + 4 张内置迷彩皮肤（资源包可覆盖）+ 随机名字池
- **TACZ 集成**：原生 `IGunOperator` 射击链路、持枪/瞄准/换弹动画、预装任意配件（`GunItemBuilder` 或整段 SNBT）
- **按枪种**：交战距离 12/48/72，换弹 1/1.5/2.5/2.5/5/6 秒
- **索敌**：非透视（必须先看见）→ 追击最后看见的位置 → 跟丢 15 秒放弃
- **5 档精度**：档位 5 含 10% 锁头秒杀（排除 Boss 与创造玩家）
- **队伍**：原版计分板；`team set/clear/showname/namemode/color` 均支持 `[半径|all]`
- **路径**：`record cs|bf`；移动节点（`+`）/ 停留节点（`-` 弹窗设分秒）；cs=终点 5 格徘徊，bf=到头反向 + 停留时慢速徘徊
- **对局**：`/taczbotai <none|cs|bf> start <分钟>|pause|end|status`，超时或一方全灭即结算；结算行为（传送坐标 / 退队 / 清背包）可配置
- **配置**：SERVER 类型（每存档一份，仅房主/管理员可改），GUI 四页签 + `/taczbotai config set|get|list`
- **其它**：bot 击杀 bot 聊天播报、`summon` 定点生成、Tab 全指令补全、中/繁/英三语

> 详细设计说明见下方原文（原理、TACZ 接口细节、赔偿方案等）。
---

# taczbotai — TACZ 枪械 AI 生物模组（Minecraft 1.20.1 / Forge）

> 交付物：`build/libs/taczbotai-1.3.1.jar`（前置：Timeless and Classics Zero 1.1.8-hotfix）

---

## 1. 技术路线：Forge Java（不用 EntityJS）

**结论：原生 Forge Java 实现，零运行时脚本层。**

理由：

| 维度 | Forge Java（选用） | EntityJS |
|---|---|---|
| 与 TACZ 的耦合 | TACZ 本身就是 Java mod，直接 import 它的 API（`IGunOperator` / `GunItemBuilder`）编译期即校验，零反射/零桥接开销 | 需要通过 JS 绑定层调用，1.20.1 还存在 SRG 方法名（`m_xxxxx_`）问题，容易在运行期才炸 |
| 性能 | 战斗逻辑跑在实体 tick 里，纯 Java，没有脚本解释器参与 | 每个 AI 决策都要过一遍 JS 运行时 |
| 渲染（关键） | 可直接继承 `LivingEntityRenderer` + `PlayerModel` + `ItemInHandLayer`，TACZ 的第三人称手臂姿势由它自己的 `HumanoidModelMixin` 自动接管 | 需要覆盖渲染器为 `HumanoidRender` 再打 patcher 才能让自定义生物用类人模型，社区方案本身就是在给 EntityJS 打补丁 |
| 依赖体积 | 只依赖 Forge + TACZ | 再加 EntityJS / KubeJS / Rhino 三层前置 |

EntityJS 那条"HumanoidRender + patcher"的社区先例，本质上是在绕开"自定义生物没有类人骨架"的限制；Java 路线里这个问题根本不存在，所以没有必要走这条路。

---

## 2. 自定义生物

- 实体：`taczbotai:tacz_bot`（`TaczBotEntity extends PathfinderMob`），独立生物，**不依赖** CustomNPCs、假玩家、KubeJS。
- 骨架：渲染器直接使用原版 `PlayerModel`（经典 64x64 史蒂夫 UV 布局），因此皮肤是标准 Minecraft 皮肤格式。
- 皮肤：
  - 内置 4 张：`assets/taczbotai/textures/entity/tba_1.png` … `tba_4.png`，生成时随机挑一张（同步到客户端）。
  - 资源包优先：贴图路径就是标准的 `assets/taczbotai/textures/entity/...`，资源包同名覆盖即可生效。
  - jar 内兜底：客户端每帧检查资源是否存在；连 jar 内贴图都被删掉时回退到原版史蒂夫皮肤（`DefaultPlayerSkin.getDefaultSkin(uuid)`）。
- 名字：从配置 `[bot] names` 里**随机挑一个**作为头顶名字（默认 4 个：`bot_Yukina_1918` / `bot_Xiexiaoai_74` / `bot_Wuliyincanaiyi` / `Dafeiyu_2026`），
  名字存实体 NBT，死亡播报也会用它（例如「bot_Yukina_1918 被 bot_Xiexiaoai_74 击杀了。」）。
- 行为：无目标时随机闲逛（自研漫游逻辑，避免和巡逻抢导航）；交战距离内停下、瞄准、开火。
- 生成：`/taczbotai spawn` 或创造模式物品栏里的「TACZ 机器人生成蛋」。

---

## 3. TACZ 集成（核心）

### 3.1 射击：直接复用 TACZ 的原生链路

TACZ 用 mixin 把 `com.tacz.guns.api.entity.IGunOperator` 注入到了 **`LivingEntity`** 上（`com.tacz.guns.mixin.common.LivingEntityMixin` 实现该接口）。也就是说**任何生物**都能：

```java
IGunOperator op = IGunOperator.fromLivingEntity(bot);
op.initialData();                       // 初始化/切换枪械
op.draw(bot::getMainHandItem);          // 拔枪（开始 draw 冷却）
op.aim(true);                           // 瞄准（同步 aimingProgress，驱动第三人称动画）
op.shoot(() -> pitch, () -> yaw);       // 开火 -> TACZ 生成 EntityKineticBullet
op.reload();                            // 换弹
op.bolt();                              // 栓动上膛
```

生物因此获得和玩家完全一致的：弹道/下坠/穿透、枪声与枪口火光、散布、换弹状态机、栓动动作、爆头倍率（`head_shot_multiplier`）。

**弹药**：TACZ 的 `canReload` 会扫描实体的 `ForgeCapabilities.ITEM_HANDLER`，而生物没有物品栏。本模组通过 `AttachCapabilitiesEvent<Entity>` 给生物挂了一个动态弹药容器（`BotAmmoHandler`），按当前枪械的 `ammo` 字段即时生成对应弹药，换弹时扣减 `ammoReserve`。这样**弹匣制枪械和 inventory 供弹制枪械都能正常换弹**。

配置 `infiniteAmmo` 默认 **true**：备弹视为无限（仍然会正常打空弹匣、播换弹动画），避免生物站在那儿打空枪。若设为 false，则使用 `ammoReserve` 的备弹量。另有兜底：弹匣空且连续 4 秒没进入换弹状态时（某些第三方枪包的 Lua 换弹脚本在非玩家实体上会失败），直接把弹匣补满。

### 3.2 动画：白拿

- 持枪/瞄准姿势：TACZ 的 `HumanoidModelMixin` 在 `HumanoidModel.setupAnim` 的 TAIL 调用 `InnerThirdPersonManager`，对**任何**手持 TACZ 枪械的 `LivingEntity` 摆手臂。我们的 `PlayerModel` 继承自 `HumanoidModel`，所以姿势自动正确，瞄准进度由 TACZ 自己的 `SyncedEntityData`（`StartTracking` 时同步，非玩家实体同样同步）下发。
- 枪械模型：TACZ 通过 `IClientItemExtensions` 注册了自定义物品渲染器；`ItemInHandLayer` 渲染手持物时会走它，所以机器人手里的枪（含配件）能正常显示。
- 换弹/射击动画：由 TACZ 的 `ReloadState` / `GunFireEvent` 驱动，同样是原生表现。

### 3.3 预装改装件：**可行**（已从源码确认）

官方 API 就支持：

```java
GunItemBuilder.create()
    .setId(new ResourceLocation("tacz:ak47"))
    .setFireMode(FireMode.AUTO)
    .setAmmoCount(30)
    .putAttachment(AttachmentType.SCOPE, new ResourceLocation("tacz:scope_acog_ta31"))
    .putAttachment(AttachmentType.MUZZLE, new ResourceLocation("tacz:muzzle_silencer_phantom_s1"))
    .build();
```

底层 NBT 结构（`GunItemDataAccessor` 源码）：

```
tag: {
  GunId: "tacz:ak47",
  GunFireMode: "AUTO",
  GunCurrentAmmoCount: 30,
  HasBulletInBarrel: 1b,
  AttachmentSCOPE:        { id: "tacz:attachment", Count: 1b, tag: { AttachmentId: "tacz:scope_acog_ta31", ZoomNumber: 0 } },
  AttachmentMUZZLE:       { ... },
  AttachmentSTOCK:        { ... },
  AttachmentGRIP:         { ... },
  AttachmentLASER:        { ... },
  AttachmentEXTENDED_MAG: { ... }
}
```

- 键名 = `"Attachment" + AttachmentType.name()`，类型枚举：`SCOPE / MUZZLE / STOCK / GRIP / LASER / EXTENDED_MAG`。
- 值 = 一个完整 ItemStack 序列化（`ItemStack.save`），内层 `tag.AttachmentId` 才是真正的配件 id。
- 默认枪包（TACZ 自带 `tacz_default_gun`）实测 54 把枪、99 个配件；整合包里的其他枪包（lradd / 面包军火库 等）同样按这套结构，所以本模组不硬编码任何 id，全部走配置。
- 注意：`GunItemBuilder.build()` 会按枪械的 `item_type` 选择注册物品（TACZ 1.1.8 默认是 `tacz:modern_kinetic_gun`），并校验枪械 id 是否存在；配置里写错会在日志里告警而不是崩溃。

---

## 4. 队伍与敌友判定

- 完全走**原版计分板队伍**（`/team`）：`LivingEntity#getTeam()`，队友 = 同名 `PlayerTeam`。
- 生物的 `getScoreboardName()` 是它自己的 UUID 字符串，所以可以用 `Scoreboard#addPlayerToTeam` 把生物加进任意队伍（本模组在生成/指令时自动完成）。
- 索敌规则：
  - 玩家：创造/旁观模式永不攻击；同队不攻击。
  - 其他 `taczbotai` 生物：同队不攻击。
  - `Enemy`（原版敌对生物）：可配置是否攻击（默认开），同队不攻击。
- 同队误伤：两道闸，配置 `friendlyFire` 默认关（= 不伤同队）。
  1. `EntityHurtByGunEvent.Pre`（TACZ 命中前事件，可取消）—— 取消后整次命中，连击退/点燃都不发生；
  2. `LivingHurtEvent` 兜底 —— 拦截其它来源（含本模组的锁头伤害）；攻击者优先取伤害来源实体，取不到时用子弹实体 `getOwner()` 反推。
- **队伍标识 `teamId()`**：优先用生物 NBT 里记录的队伍名（`setBotTeam` 写入），没有记录才回退到计分板 `getTeam()`。
  早期版本只在「双方都能从计分板查到队伍」时才判同队，导致两个计分板反查失败的 bot 互相开火（实测日志里的 `TACZ 机器人 被 TACZ 机器人 击杀了。` 就是这个 bug）。现在两边都用记录值比较，与计分板是否反查成功无关。
  计分板登记仍然保留（方便 `/team` 查看、原版 `isAlliedTo` 等逻辑），只是不再作为唯一判据。
- `/taczbotai info` 会显示 `队伍=记录值 (scoreboard:实际值)`、「你的队伍」、「视你为敌」，可直接确认判定是否符合预期。

---

## 5. 攻击力度 5 档

射击时以「目标眼高 -0.35 格」（躯干偏上/头颈）为瞄准点，计算 pitch/yaw 后叠加角度误差，再交给 TACZ 走原生弹道：

误差为标准差（度），可在配置里逐档修改：

| 档位 | 默认角度误差 (σ) | 效果 |
|---|---|---|
| 1 | 7.0° | 描边极其严重，几乎打不中 |
| 2 | 4.0° | 描边明显，命中率低 |
| 3 | 2.0° | 中等命中率 |
| 4 | 0.8° | 命中率较高 |
| 5 | 0.15° | 接近枪械自身散布，正常命中率；且启用锁头秒杀 |

档位 4/5 还会额外获得 TACZ 瞄准态（`aim(true)` 后 `aimingProgress=1` → `InaccuracyType.AIM`，散布 0.15）带来的精度收益。

### 交战距离：按枪种三档（可配置）

| 类别 | 枪械类型（TACZ `index.type`） | 默认射程 | 换弹耗时 |
|---|---|---|---|
| 近距离 | `pistol` / `smg` | 12 格 | 1.0 s |
| 中距离 | `rifle` / `mg` / `shotgun` / 重武器 / 其它 | 48 格 | 步枪 1.5 s、机枪 5.0 s、**霰弹与重武器 6.0 s**、其它 2.0 s |
| 远距离 | `sniper` / `marksman` / `dmr` | 72 格 | 2.5 s |

- 配置：`[attack.range] close/mid/far`、`[reload] pistolSeconds/rifleSeconds/marksmanSeconds/sniperSeconds/mgSeconds/shotgunSeconds/otherSeconds`。
- 旧配置项 `fireRange` 已废弃（保留字段但不再使用），射程完全按枪种走。
- 索敌半径 = `max(targetRange, 该枪射程 + 16)`，所以 72 格的狙击枪也能正常发现目标。

### 换弹：按枪种的精确时长

TACZ 的换弹动画时长写死在枪包里，无法按实体改，所以本模组用「换弹锁」控制实际时长：

1. 弹匣打空（或非战斗时未满）→ 调 TACZ 的 `reload()` 播放原生换弹动画；
2. 内部计时器按枪种时长倒计时（1 / 1.5 / 2.5 / 5 / 6 秒），期间**不移动、不开火**；
3. 计时归零 → 若 TACZ 动画还没播完就 `cancelReload()`，并把弹匣直接补满（用 `AttachmentDataUtils` 算上扩容弹匣）。
4. 另有兜底：弹匣空且 4 秒都没进入换弹状态 → 直接补满（防止第三方枪包的 Lua 换弹在非玩家实体上失败）。

### 索敌与追击：非透视，看见才打，跟丢 15 秒放弃

- **锁定目标必须"真的看见"**：扫描候选时对每个目标做 `hasLineOfSight` 判定，隔着墙的敌人不会被锁定（配置 `requireLineOfSight`，默认 true；关掉就退化成范围扫描）。
  这不是资源开销问题——每 8 tick 只对少数候选做一次射线检测。
- **看见后开始交火**：进入有效射程 + 有视线 → 停下、反应 12 tick、瞄准、开火。
- **目标躲进掩体 → 跟踪**：记住最后一次看见的位置（`lastSeenPos`），朝**那个位置**绕路过去（每 10 tick 或导航完成/卡住时重算路径）。
  注意追的是"最后看见的位置"，不是目标的实时坐标，所以不会出现隔着墙精准跟人的透视行为。
- **跟丢 15 秒放弃**：目标连续 `lostSightSeconds`（默认 15 秒）完全看不到 → 放弃该目标，并忽略它 6 秒，回到巡逻。
- **能看见但追不到**（比如隔了深沟）：连续追 400 tick（20 秒）仍未进入射击条件 → 同样放弃。
### 锁头：**弹道命中时干预**，不绕过弹道

- 监听 `EntityHurtByGunEvent.Post`（TACZ 子弹命中并结算伤害之后）。
- 仅当「攻击者是 taczbotai 生物 + 当前档位为 5 + 开关打开」时，按 `instantKillChance`（默认 10%）掷骰。
- 命中则补一份 `目标最大生命值` 的伤害，伤害类型是自定义的 `taczbotai:bot_headshot`（数据包 tag 里登记了 `bypasses_armor`），所以：
  - **不是** `/kill` 指令，走完整 `hurt()`/死亡流程，有正常死亡信息；
  - 无视护甲，正好等于最大生命值的 100%。
- 排除：`forge:bosses` 标签实体 + 配置黑名单（默认末影龙/凋灵/监守者）+ 创造/旁观模式玩家。

为什么选"命中时干预"而不是"独立命中判定绕过 TACZ"：后者等于自己重写弹道、弹道下坠、穿透、命中判定，既容易和 TACZ 的爆头/护甲穿透逻辑打架，也会让 TACZ 的枪声/击中反馈与实际伤害对不上；前者只是"命中之后再补一刀"，兼容性最好。

---

## 6. 路径预设

- **录制（默认节点模式）**：
  1. `/taczbotai record start <名字>` —— 自动把当前位置记成**起点**；
  2. 边走边在**拐点/关键位置**按下记录键（默认小键盘 `+`，备用 `=` 键；可在「选项 → 控制 → 按键绑定 → TACZ 机器人」里改成任意键），每按一次记一个节点；
  3. `/taczbotai record stop` —— 自动把当前位置记成**终点**并保存。
  - 配套指令：`/taczbotai record node`（手动记节点）、`record undo`（撤销上一个点）、`record status`（看当前名字/点数）。
  - 一条路通常只有 **5~20 个节点**，文件小、导航也不会在密集点之间反复重新规划。
  - 旧行为（每 4 tick 自动采样）保留为 `recordMode = "dense"`，默认是 `"node"`。
- **存储：自定义 JSON 文件读写，位置 `<存档文件夹>/taczbotaidata/<名字>.json`。**
  - 明确回答需求里的二选一：**没有用 SavedData**。Forge 的 `SavedData` 固定写入 `<存档>/data/*.dat`（NBT），做不到独立 `taczbotaidata` 文件夹，也不方便外部查看/编辑，所以这里走自定义文件 IO。
  - 每个存档一个目录，缓存按存档根路径隔离，**不跨存档串用**；服务器停止时清空缓存。
- 使用：`/taczbotai path assign <名字>` 把路径分配给范围内的生物；`/taczbotai path clear` 取消。
- **行走模式（`[path] pathMode`，也可在 GUI「装备/队伍/路径」页点击切换）**：
  - `loop`（默认）：按顺序走完，回到第 1 个点继续（终点离起点远时会横穿场地走回去）；
  - `pingpong`：走到终点后原地掉头，沿原路返回（不会横穿场地，巡逻更自然）；
  - `once`：只走一趟，到终点后原地待命（继续索敌开火，不再移动）；`path assign` 会重置它。
  模式存在实体 NBT 里，重载区块不会丢进度方向。
- 移动速度（两种录制模式都适用）：
  - 到达判定 **2 格**，并一次性跳过所有已经贴脸的连续点（dense 模式必需，node 模式无副作用）；
  - 导航 `isDone()`/`isStuck()` 时**立刻**重新下目标（不再固定等 30 tick 冷却）；
  - 距离目标 > 6 格时用 `patrolSpeed × 1.5`，接近后回落到 `patrolSpeed`；
  - 目标 Y 取目标点下方方块的 Y，避免点在空中（跳跃/上下坡）导致路径反复重算。
  - `patrolSpeed`（默认 1.0）可再调大；实体基础速度是 `bot.speed`（默认 0.32）。
- 管理：`/taczbotai path list`、`/taczbotai path delete <名字>`。

文件格式（可直接手改）：

```json
{
  "name": "squad_a",
  "dimension": "minecraft:overworld",
  "points": [ { "x": 12.5, "y": 70.0, "z": -3.25 }, ... ]
}
```

---

## 7. 击杀播报

- `LivingDeathEvent`：仅当**死者是 taczbotai 生物**且**伤害来源实体也是 taczbotai 生物**时，向全服广播：
  `%s 被 %s 击杀了。`（走语言文件，三语都有）
- 玩家击杀玩家、生物杀玩家、玩家杀生物：都不播报（类型判定直接把它们排除掉）。

---

## 8. GUI 配置

- 入口：**模组列表 → TACZ Bot AI → 配置**（Forge `ConfigScreenHandler` 扩展点），不需要按键、不需要额外 API 模组。
- **读取手持枪械 NBT**：「装备/队伍/路径」页的 `NBT` 行 + 「读取」按钮 —— 会把主手 TACZ 枪械的整段 SNBT 填进输入框**并复制到剪贴板**，保存后写进配置 `gunNbt`。
  拿一把改好配件的枪 → 点「读取」→「保存并关闭」→ 之后 `/taczbotai spawn` / `/taczbotai equip` / 生成蛋都会用这把枪的完整配置（省得手写 NBT，也绕开指令 256 字符长度限制）。
  - 输入框上限已放宽到 64k 字符（`EditBox` 默认 512 会截断，早期版本就是踩了这个坑），旁边有「清空」按钮。
  - 也可以直接用 `/taczbotai gun` 写入配置（完全不受输入框长度影响），`/taczbotai gun show` 可核对字符数。
  - 配置里若残留被截断的非法 SNBT，解析失败只会告警并回退到 `gunId` + `attachments`，不会崩。
- 布局为**三个分页**（避免字段过多导致控件重叠）：
  - **战斗**：攻击档位、同队误伤、五档锁头秒杀、击杀播报、是否攻击敌对生物；
  - **数值**：三档交战距离（近/中/远，格）＋ 七类换弹时间（手枪/步枪/射手/狙击/机枪/霰弹与重武器/其它，秒）；
  - **装备/队伍/路径**：枪械 ID、预装配件（`类型=配件ID` 逗号分隔）、默认队伍、路径名，以及「设为队伍 / 清空队伍 / 分配路径 / 路径列表 / 按配置重新装备 / 生成一个生物」快捷按钮。
- 切页时会先把当前页输入框的值回读进内存，不会丢改动；「保存并关闭」直接把值写进 `ForgeConfigSpec` 并保存到 `config/taczbotai-common.toml`。
- 队伍 / 路径 / 换枪 / 生成 这类**服务器侧操作**通过 GUI 直接发送指令给服务端执行（`/taczbotai ...`），客户端不维护状态，避免不同步。
- 为什么不用 Cloth Config / YACL：零前置依赖，手写 `Screen` 足够，且能直接复用现有的指令逻辑。

---

## 9. 多语言

`assets/taczbotai/lang/`：

- `en_us.json`（英文）
- `zh_cn.json`（简体中文）
- `zh_tw.json`（繁体中文）

覆盖：实体名、生成蛋、GUI 全部标签与开关状态、所有指令反馈、击杀播报、锁头死亡信息。

---

## 10. 指令表

| 指令 | 说明 |
|---|---|
| `/taczbotai spawn [数量] [队伍] [武器]` | 生成生物。队伍省略则用配置 `defaultTeam`；武器可写枪械 id（`tacz:m1911`，无配件）或 SNBT（`{GunId:"tacz:ak47",AttachmentSCOPE:{...}}`），省略则为无配件 1911 |
| `/taczbotai info` | 打印最近生物的状态：枪械 / 弹匣 / 备弹 / 队伍 / 你的队伍 / 友伤 / 档位 / 是否在换弹 / 是否视你为敌（排查用） |
| `/taczbotai record start <名字>` / `record stop` | 开始 / 结束录制路径（节点模式：start 记起点，stop 记终点并保存） |
| `/taczbotai record node` / `record undo` / `record status` | 手动记一个节点 / 撤销上一个点 / 查看当前录制名字与点数（默认按键 + 等价于 `record node`） |
| `/taczbotai path list` | 列出存档内的路径 |
| `/taczbotai path assign <名字> [半径]` | 把路径分配给**范围内全部**生物（默认 32 格） |
| `/taczbotai path clear` | 取消最近生物的路径 |
| `/taczbotai path delete <名字>` | 删除路径文件 |
| `/taczbotai team set <队伍>` / `team clear` | 设置最近生物的计分板队伍 |
| `/taczbotai team friendlyfire <true\|false>` | 全局友军伤害开关（等同配置 `friendlyFire`） |
| `/taczbotai team showname <true\|false> [all]` | 头顶名字是否显示（`all` = 附近 64 格全部生物） |
| `/taczbotai team color <颜色> [all]` | 头顶名字改色，颜色用 MC 标准名（`red` / `gold` / `aqua` / `light_purple` …） |
| `/taczbotai tier <1-5>` | 快速切换攻击档位 |
| `/taczbotai skin <0-4>` | 设置最近生物皮肤（0 = 原版史蒂夫） |
| `/taczbotai config list` / `get <key>` / `set <key> <value>` | 查看 / 读取 / 修改配置（SERVER 配置，仅房主与管理员；键名与候选值都支持 Tab 补全） |
| `/taczbotai equip` | 按当前配置重新给最近的生物发枪（`gunNbt` 优先，其次 `gunId` + `attachments`） |
| `/taczbotai gun` | **把主手 TACZ 枪械的整段 NBT 存进配置 `gunNbt`**；之后 `equip` / 生成蛋 / `spawn`（不写武器参数时）都用它 |
| `/taczbotai gun show` / `gun clear` | 打印当前 `gunNbt` / 清空回退到 `gunId` + `attachments` |

---

## 11. 已知限制 / 说明

1. **枪械与配件 id 来自枪包**：TACZ 直属枪包、以及整合包里额外装的枪包（lradd、面包军火库、superbwarfare 等）id 各不相同。配置里写错只会在日志告警并保留空枪，不会崩档。
2. **换弹依赖自建弹药容器**：生物没有原版物品栏，弹药由 `BotAmmoHandler` 动态生成并记录剩余量。若某些第三方枪包用了非标弹药 id，只要 TACZ 能解析 gun index 就仍然有效。
3. **锁头是概率秒杀，不是每枪必杀**：默认 10%，可按配置改。
4. **Boss 判定**：以 `forge:bosses` 标签 + 配置黑名单为准（原版没有统一的 boss 标签，模组 boss 需要自己加进黑名单）。
5. **武器切换**：目前支持"配置一把枪 + 换枪指令"；生物不会自动从地上捡枪。

---

## 12. 构建

```bash
# 需要 JDK 17（本工程已内置便携 JDK 17）
./gradlew build
# 产物：build/libs/taczbotai-1.3.1.jar
```

- Gradle 8.8 + ForgeGradle 6（Gradle 9 与 ForgeGradle 6 不兼容）
- Minecraft 1.20.1 / Forge 47.x
- TACZ 依赖：`curse.maven:timeless-and-classics-zero-1028108:8141310`（1.1.8-hotfix），通过 `fg.deobf` 反混淆后编译
## 13. 版本号规则

- 当前版本：**1.3.1**（`mod_version` in `gradle.properties`，jar 名与 `mods.toml` 同步；纯数字，不带字母前缀，避免任何版本解析兼容性问题）。
- **十进制迭代**：末位只在 0~9 之间递增，满 10 进一位。
  例：`1.0.9` → `1.1.0`，`1.9.9` → `2.0.0`。
## 14. 配置归属（仅房主 / 服务器管理员）

- 配置类型已从 **COMMON 改为 SERVER**：文件位于存档侧的
  `saves\<世界名>\serverconfig\taczbotai-server.toml`（专用服务器则在 `config\taczbotai-server.toml`）。
  **每个存档各有一份**，客户端拿到的是只读同步副本，玩家无法在本地改规则。
- 所有写入都必须经过服务端：GUI 的「保存并关闭」逐条发送 `/taczbotai config set <key> <value>`，
  `/taczbotai` 整条指令要求权限等级 2，所以**只有房主或服务器管理员**能改；普通成员点保存会收到权限拒绝提示。
- 相关指令：
  - `/taczbotai config list` 列出全部键值；
  - `/taczbotai config get <key>` 读取单项（Tab 可补全键名）；
  - `/taczbotai config set <key> <value>` 写入（布尔/数字/字符串/逗号分隔列表均可，Tab 会按 key 给出候选值）。
- 从 1.2.1 升级时，脚本已把旧的 `config\taczbotai-common.toml` 内容迁移进每个存档的 serverconfig（旧文件改名 `.bak`）。
