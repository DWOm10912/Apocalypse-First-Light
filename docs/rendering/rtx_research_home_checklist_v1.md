# RTX 渲染器调研：回家实测清单（R0 / S0）

- **状态**：Research Only，操作清单。只做验证，不改 AFL、Aureveil、Vextris 任何代码。
- **日期**：2026-10-26
- **配套报告**：`docs/rendering/afl_independent_renderer_research_v1.md`（§9 PoC 计划中的 R0、S0）、`docs/rendering/rtx_remix_forge_backport_research_v1.md`。
- **测试机**：家里的台式机，Windows，RTX 3080 Ti。
- 文中 `NV:`、`RAD:`、`MCVR:`、`DLSSPG:` 等来源前缀的含义，见报告二 §1。

**目的**：两份报告的所有结论都来自源码阅读，没有在 GPU 上跑过。这份清单把"能不能真正跑起来"拆成两步：

- **R0**：跑一遍已发布的 Radiance，作为 Forge 1.20.1 下 Vulkan 光追和 DLSS 的现成基准。
- **S0 预检**：用 NVIDIA 官方示例程序，确认这块 3080 Ti 在 Vulkan 下的硬件光追、DLSS SR 和 RR 都正常。**这一步不需要我们写任何代码。**

**原则**：

- 所有测试都在**新建的独立实例或独立目录**里做，不碰 AFL 的开发环境和存档。
- Radiance 只作为"黑盒"运行和观察。**不打开它的源码来照抄**（GPLv3，见报告二 §3.7、§8）。
- NVIDIA 的 DLL 和 SDK 只用于本机测试，不打包、不转发给别人。
- 每一步都要留下截图、日志和帧时间数据。"能启动"不算通过。

---

## 1. 一次性准备

| 软件 | 用途 | 说明 |
|---|---|---|
| NVIDIA 显卡驱动（最新版） | 全部 | DLSS 2.4.11+ 至少需要 2022-03-03 之后的驱动（`DLSSPG:` p.1）。NVIDIA RTX Remix for Minecraft 要求 616.92 及以上（`NV:README.md:50`）。直接装最新版即可 |
| Microsoft Visual C++ x64 Redistributable（最新版） | R0 | Radiance README 的安装第 4 步 |
| Minecraft 启动器：Prism Launcher 或官方启动器 | R0 | 新建一个**干净实例** |
| Forge 1.20.1 **47.4.22** | R0 | 与 AFL 相同。Radiance 的 `mods.toml` 要求 Forge 不低于 47.3.19 |
| JDK / JRE 17（x64） | R0 | Minecraft 1.20.1 用 Java 17 |
| Git | S0 预检 | — |
| Visual Studio 2022（"使用 C++ 的桌面开发"工作负载） | S0 预检 | 编译 NVIDIA 示例 |
| CMake 3.20 及以上 | S0 预检 | `Streamline_Sample` 要求 3.20+ |
| Windows SDK 10.0.22000 及以上 | S0 预检 | `Streamline_Sample` 要求 |
| Vulkan SDK（LunarG，最新 1.4.x） | S0 预检 | 安装时勾选 **Volk headers**（`vk_raytracing_tutorial_KHR` 要求）。把 SDK 的 `Bin` 目录加入 PATH，`Streamline_Sample` 需要其中支持 SPIR-V 的 `dxc.exe` |
| 帧时间工具：PresentMon、CapFrameX 或 NVIDIA FrameView 任选其一 | 全部 | 记录平均 FPS、1% Low 和帧时间；只看 F3 不够 |
| 可选：RenderDoc、Nsight Graphics | 排错 | 出问题时抓帧 |

**系统设置**：

- 在 **Windows 设置 → 系统 → 屏幕 → 显示卡** 中，把启动 Minecraft 用的 `javaw.exe` 设为"高性能"。Radiance FAQ 要求 Forge 必须在高性能 GPU 上启动；台式机只有一块独显时可以忽略。
- **实例路径只用英文和数字**。Radiance issue #271 报告游戏目录含非 ASCII 字符时会启动崩溃。

---

## 2. R0：Radiance 0.1.6 基准测试

### 2.1 安装

1. 新建实例：Minecraft 1.20.1 + Forge 47.4.22，命名如 `radiance-r0`，路径不含中文。
2. **只放 Radiance 一个 Mod**。不放 AFL，不放 Oculus、Embeddium、Rubidium 或其他渲染类 Mod。
3. 下载 Radiance 0.1.6-alpha 的 **Forge 1.20.1 Windows** 版本：
   - 来源三选一：Modrinth `radiance-mod-windows`、CurseForge `radiance`、GitHub Releases。
   - 按它的构建脚本，文件名应形如 `Radiance-0.1.6-alpha-forge-1.20.1-windows.jar`（`RAD:build.gradle:50`）。
   - 下载后核对文件名中的 `forge`、`1.20.1`、`windows` 三项。
4. 放入实例的 `mods/` 目录。
5. **DLSS 运行库**（Radiance 不自带）：
   - 从 NVIDIA/DLSS 仓库 **v310.9.1** 标签的 `lib/Windows_x86_64/rel/` 目录下载：
     - `nvngx_dlss.dll`（Super Resolution）
     - `nvngx_dlssd.dll`（Ray Reconstruction）
     - 3080 Ti 不支持帧生成，所以不需要 `nvngx_dlssg.dll`。
   - 放到 **`<实例目录>/radiance/`**，目录不存在就新建。保持原文件名。
   - 这个版本号来自 Radiance README 中给出的下载链接。
   - **只用 `rel/`，不用 `dev/`**：`dev/` 版带水印和调试叠加层（`DLSSPG:` §4.2）。
6. 如果启动时在 `std::mutex::lock` 处崩溃：按 Radiance README 的"Windows fix: JDK runtime libraries"一节，把 JDK `bin` 目录下的 `msvcp140.dll`、`vcruntime140.dll`、`vcruntime140_1.dll` 改名备份，再重装最新的 VC++ 运行库。

### 2.2 固定测试场景

为了让数据可比，每次都用同一个世界、同一个位置、同一个时间。

1. 新建单人世界：创造模式，固定种子（记下种子号），开启作弊。
2. 找一处有水、有树、有起伏地形的地方，记录坐标。依次执行：
   - `/gamerule doDaylightCycle false`
   - `/gamerule doWeatherCycle false`
   - `/time set noon`
   - `/weather clear`
3. 视距设为 12，分辨率设为 **2560×1440**，全屏。
4. 依次测下面 5 个场景，每个场景录 **30 秒**帧时间：

| 编号 | 场景 | 看什么 |
|---|---|---|
| A | 原地静止，看向远景 | 基础帧时间；画面是否稳定、有无闪烁 |
| B | 沿直线匀速行走（按住 W 走 30 秒） | 地形运动时的拖影、闪烁 |
| C | 生成约 10 个村民或僵尸，绕着它们走 | **实体运动矢量**：移动生物身后有没有拖影 |
| D | 看向水面，然后潜入水中 | 水面反射、折射，水下效果 |
| E | `/time set midnight`，在周围放约 20 支火把 | 光追点光源和阴影；夜间噪点 |

### 2.3 测试矩阵

在 Radiance 设置界面中，按可用选项依次切换：

| 管线 | 模式 |
|---|---|
| NRD + FSR（不需要 DLSS DLL，作对照） | Quality |
| NRD + DLSS SR | Quality、Performance |
| DLSS RR | Quality、Performance |
| 可选：以上任一，视距改为 16 | — |

每个组合把 A–E 都跑一遍。

### 2.4 记录模板

| 管线 | 模式 | 场景 | 平均 FPS | 1% Low | GPU 占用 | 显存 | 明显问题（拖影、闪烁、噪点、崩溃） |
|---|---|---|---|---|---|---|---|
| NRD+FSR | Quality | A | | | | | |
| … | | | | | | | |

另外记录：驱动版本号、Radiance JAR 文件名、DLSS DLL 版本，以及设置界面里**是否出现 DLSS 选项**（README 说缺少 DLL 或硬件不支持时选项不会出现）。

### 2.5 通过与失败

- **通过**：
  - DLSS SR 和 RR 选项可选。
  - 切换后 FPS 有可量化的变化。
  - 15 分钟内无崩溃。
  - 场景 C 中能明确判断实体有没有拖影，有或没有都算"已观察到"。
- **失败也有价值**：记录崩溃日志和出现时机。例如进世界即崩、切换管线时崩（对照 issue #277）、DLSS 选项不出现。

### 2.6 要带回来的东西

- 第 2.4 节填好的表格。
- 每个管线在场景 A 和 C 的截图各一张。
- `<实例目录>/logs/latest.log`。如有崩溃，再带上 `crash-reports/` 下的文件和 JVM 生成的 `hs_err_pid*.log`。
- **不要**把 `<实例目录>/radiance/` 里的 DLL 发给别人。

### 2.7 可选的下一步（需要另行批准）

在 Radiance 实例里加载 AFL，观察报告二 §3.3 预测的"不可见内容"：物理水、T15、枪械、道路、近处植物。

这需要用 AFL 的无光影包配置运行（不加载 Embeddium 和 Oculus），并且要接触 AFL 的开发环境，所以**本次不做**。

---

## 3. S0 预检：用 NVIDIA 官方示例验证 3080 Ti

这一步验证的是"显卡和驱动在 Vulkan 下能做到什么"。全部使用 NVIDIA 公开的示例，**不需要写代码**，结果直接决定后续自研 S0 值不值得启动。

### 3.1 检查 Vulkan 光追扩展

安装 Vulkan SDK 后，在命令行运行：

```
vulkaninfo --summary > vulkaninfo_summary.txt
vulkaninfo > vulkaninfo_full.txt
```

在 `vulkaninfo_full.txt` 中确认以下几项都存在：

- `VK_KHR_ray_tracing_pipeline`
- `VK_KHR_acceleration_structure`
- `VK_KHR_ray_query`
- `VK_KHR_deferred_host_operations`
- `VK_KHR_external_memory_win32`
- `VK_KHR_external_semaphore_win32`

前四个是硬件光追必需的。Radiance 要求其中三个，见 `MCVR:src/core/vulkan/physical_device.cpp:112-125`。后两个是 GL 与 Vulkan 共享图像所需的。

**通过**：以上 6 项全部存在，`apiVersion` 不低于 1.3。

### 3.2 硬件光追：`nvpro-samples/vk_raytracing_tutorial_KHR`

- 许可证 Apache-2.0；本次核对提交为 952a1a8。
- README 中的依赖：Vulkan 1.4+ SDK（勾选 Volk headers）、CMake 3.18+、支持 Vulkan 光追的 GPU 和驱动。

```
git clone https://github.com/nvpro-samples/vk_raytracing_tutorial_KHR
cd vk_raytracing_tutorial_KHR
cmake -B build -S .
cmake --build build -j 8 --config Release
```

运行教程最后阶段的光追程序，以及反射、ray query 两个样例。

**通过**：画面出现光追阴影和反射，窗口可以缩放且不崩溃。记录 1440p 下的帧时间。

### 3.3 DLSS Super Resolution（Vulkan + Streamline）：`NVIDIA-RTX/Streamline_Sample`

- 本次核对提交为 94da85b，仓库内附 `NVIDIA RTX SDKs License (12Apr2021).txt`。
- 依赖：CMake 3.20+、Windows SDK 10.0.22000+、Vulkan SDK 1.2.198.1+，PATH 中有支持 SPIR-V 的 `dxc.exe`。

步骤（按其 README）：

1. `git clone https://github.com/NVIDIA-RTX/Streamline_Sample`，然后执行 `git submodule update --init --recursive`。
2. 从 **NVIDIA-RTX/Streamline 的 GitHub Releases** 下载 Streamline SDK 发行包（本次核对的源码为 2.14.16），把其内容复制到示例的 `streamline/` 目录。2.7.32 之后，DLL 不在 git 仓库里，只在 Release 包中提供。
3. 保持默认的 `-DUSE_SL=1`，运行 `make.bat` 生成工程，再运行 `build.bat` 编译。
4. **用 Vulkan 运行**：在 `run.bat` 或命令行中加 `-vk`。可以用 `-DLSS_mode 1`（MaxPerf）等参数设定初始模式：0 关闭、1 性能、2 平衡、3 质量、4 超性能、5 DLAA。
5. 在示例 UI 中切换 DLSS 模式。

**通过**：

- Vulkan 模式下能开启 DLSS SR。
- 切换模式时内部渲染分辨率变化，画面无水印。如有水印，说明用了开发版 DLL。
- 记录 Off、Quality、Performance 三档的帧时间。
- 对照 `DLSSPG:` 的数字：3080 Ti 在 1440p Performance 下，DLSS 自身开销约 1.02 ms（预设 J/K）到 2.04 ms（预设 M）。

这一步同时验证 Streamline 用 `sl.interposer.dll` 接管 Vulkan 的加载方式（示例 README "Integration notes"），也就是报告二 §6 第 1 条推荐的接入方式。

### 3.4 可选：DLSS Ray Reconstruction（Vulkan + NGX 直连）：`nvpro-samples/vk_denoise_dlssrr`

- 许可证 Apache-2.0；本次核对提交为 f8113a8。
- README 说明 CMake 配置时会自动从 NVIDIA/DLSS 下载 SDK。

按常规 CMake 流程编译并运行，切换 RR 开关。

**通过**：路径追踪画面经 RR 去噪后稳定，没有明显的噪点闪烁。

这一步验证的是"NGX 直连"方式。MCVR 的 DLSS 封装也是基于这个示例修改的（`MCVR:LICENSE.md`），自研时可以对照两种接入方式。

### 3.5 S0 预检的结论怎么用

| 结果 | 含义 | 下一步 |
|---|---|---|
| 3.1–3.3 全部通过 | 显卡、驱动、Vulkan 光追、Streamline DLSS SR 都可用 | 可以申请启动**自研 S0**（报告二 §9）。这需要写代码，**要先批准** |
| 3.2 通过，3.3 失败 | 光追可用，DLSS 接入有问题 | 先尝试 3.4 的 NGX 直连，再排查 Streamline 版本和 DLL |
| 3.1 或 3.2 失败 | 驱动或环境有问题 | 更新驱动后重试；仍然失败就停止，整个 RTX 方向暂停 |

### 3.6 要带回来的东西

- `vulkaninfo_summary.txt`，以及 `vulkaninfo_full.txt` 中上述 6 个扩展所在的行。
- 3.2、3.3（3.4）各自的截图和帧时间。
- 编译失败时，带上 CMake 或编译器的完整报错。

---

## 4. 自研 S0（**未批准，本次不做**）

S0 预检通过后，下一步才是报告二 §9 中的自研 S0：一个与 Minecraft 无关的小型 C++ Vulkan 程序（立方体场景 → BLAS/TLAS → 光追阴影与反射 → jitter + 运动矢量 → DLSS SR）。

- 它是 AFL Renderer 专有原生核心的第一块代码，需要你另外批准后再开始。
- 写代码时，只参考 Apache-2.0 和 MIT 许可的 NVIDIA 示例、Khronos 规范和 Streamline 文档。
- 不参考 Radiance / MCVR（GPLv3）的实现。
- 每个引入的依赖都先登记许可证（报告二 §8.2）。
