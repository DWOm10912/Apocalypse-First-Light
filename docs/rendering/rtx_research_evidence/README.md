# RTX 渲染器调研：证据文件

本目录存放 `docs/rendering/rtx_remix_forge_backport_research_v1.md` 和 `docs/rendering/afl_independent_renderer_research_v1.md` 两份报告引用的原始证据。

这些文件都是调研产物，**不是 AFL 运行代码**，也不参与构建。全部结论均为源码阅读或映射比对的结果，未在 GPU 上运行验证。

## 文件

| 文件 | 内容 | 来源 |
|---|---|---|
| `nvidia_java_subsystems.md` | NVIDIA RTX Remix for Minecraft 0.1.0 Mod 侧的子系统映射，共 12 项（地形、实体、物品、材质、相机、运动矢量、灯光、天空、粒子、帧提交、水、蒙皮），外加 73 个 Mixin 完整表、Fabric API 清单、access widener、Java 21 特性统计、包体量。英文，每条带 file:line | 阅读 `NVIDIAGameWorks/rtx-remix-minecraft` 提交 `f2c6fda`（与 tag `v0.1.0` = `170197a` 只差 `build.gradle` 2 行）后整理 |
| `mixin_1.21.1_vs_1.20.1.txt` | NVIDIA Mod 每个 Mixin 的目标类和方法在 1.20.1 是否存在、签名是否变化 | `scripts/mixincheck2.py` 的输出 |
| `scripts/yarnparse.py` | 把 yarn 的 enigma `.mapping` 文件解析成 pickle | 本次调研编写 |
| `scripts/mixincheck2.py` | 用 yarn 1.21.1 名 → intermediary → 1.20.1 intermediary，比对类、方法和描述符 | 本次调研编写 |

路径约定：`nvidia_java_subsystems.md` 中的 `M/` 指 `src/main/java/com/nvidia/rtxremix/minecraft/`，`J/` 指 `rtxremix-java/src/com/nvidia/rtxremix/`，均相对于 NVIDIA 仓库根目录。

## 如何复现映射比对

需要 Python 3 和 git。以下仓库都可以通过 GitHub 获取；本次调研时 Mojang 和 Fabric 的 Maven 不可访问，所以改用 GitHub 上的映射源。

```sh
git clone --depth 1 -b 1.21.1 https://github.com/FabricMC/yarn map/yarn-1.21.1
git clone --depth 1 -b 1.20.1 https://github.com/FabricMC/yarn map/yarn-1.20.1
git clone --depth 1 https://github.com/FabricMC/intermediary map/intermediary
git clone https://github.com/NVIDIAGameWorks/rtx-remix-minecraft nv/rtx-remix-minecraft

python3 -I scripts/yarnparse.py map/yarn-1.21.1 map/y1.21.1.pkl
python3 -I scripts/yarnparse.py map/yarn-1.20.1 map/y1.20.1.pkl
python3 -I scripts/mixincheck2.py "$PWD" nv/rtx-remix-minecraft/src/main/java/com/nvidia/rtxremix/minecraft/mixin
```

脚本假定 `scripts/`、`map/`、`nv/` 都在同一个工作目录下，第一个参数就是该目录。

## 输出的读法

- `class@1.20.1=YES/NO`：目标类的 intermediary 名在 1.20.1 中是否存在。
- `SAME signature`：方法存在且描述符一致，只需重映射为 mojmap。
- `SIGNATURE CHANGED`：方法存在但参数变了，会同时列出两个版本的描述符。
- `MISSING in 1.20.1`：方法不存在。
- `UNRESOLVED`：继承或接口方法，脚本未解析，需在 Forge MDK 中人工核对。

**局限**：

- intermediary 只能证明类和方法的身份与签名，**不能证明方法体内的 `@At` 目标一致**，也不包含 Forge 补丁对方法体的修改。
- `com.mojang.*` 这类未混淆的类（`RenderSystem`、`GlStateManager`）不在 intermediary 中，所以显示为 `NO`；实际存在与否以报告正文为准。
- mojmap 类名没有映射文件可用，报告中的 mojmap 名是常识对照，需在 Forge 1.20.1 MDK 中核对。

## 未收录的内容

- Forge 1.20.1 的 `ChunkRenderDispatcher.java.patch`：公开文件，位于 `MinecraftForge/MinecraftForge` 分支 `1.20.1` 的 `patches/minecraft/net/minecraft/client/renderer/chunk/`。
- `rtxremix-java` 的 Java 17 / 21 编译产物：属于构建输出。复现命令为 `javac --release 17 -cp joml-1.10.5.jar -d out $(find rtxremix-java/src -name '*.java')`，结果是 1 个错误（`CommandLineRedaction.java:118`，`StringBuilder.repeat(char,int)`）；`--release 21` 编译成功。
- Radiance / MCVR（GPLv3）的源码或片段：按 AFL Renderer 许可证原则，只在报告中引用文件位置与公开事实，不入库任何代码。
