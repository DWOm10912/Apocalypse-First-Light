# AFL Renderer：第三方依赖与代码来源许可证清单 v1

- **状态**：Research Only，许可证初审清单。**不是法律意见**，正式发布前需要法务复核（用户原则第 10 条）。
- **日期**：2026-10-26
- **适用对象**：AFL Renderer 独立渲染器项目。架构见 `docs/rendering/afl_independent_renderer_research_v1.md` §8、§11；API 设计见 `docs/rendering/afl_renderer_api_design_v1.md`。
- **许可证原则**（用户 2026-10-26）：
  - 核心代码采用专有许可证。
  - 公开 API 与闭源实现分离。
  - GPLv3 代码只作参考，不复制、不改写进核心。
  - 引入任何外部代码前先检查许可证，并保留来源清单。
  - NVIDIA SDK 和运行库单独审核。
  - 需要 GPLv3 组件时先报告，不擅自引入。

**核对方法**：每一项都从上游仓库拉取当前提交的许可证文件，读取原文后记录"核对提交"。没有读到原文的项目会明确标注。"可用于专有核心"指按其条款可以与闭源代码一起分发，并列出须履行的义务。

---

## 1. 模块与许可证边界

| 模块 | 内容 | 建议许可证 | 说明 |
|---|---|---|---|
| `afl-renderer-api` | 纯 Java 接口和数据结构，供 AFL 与第三方 Mod 编译依赖 | **Apache-2.0**（推荐）或 MIT | Apache-2.0 带专利授权条款，更适合公开 API。AFL 可以 JarInJar 打包这一层 |
| `afl-renderer-core` | Forge 接入、捕获层、Mixin、JNI | 专有 | 只依赖下表中"可用于专有核心"的组件 |
| `afl-renderer-native` | C++ Vulkan、光追、降噪调度、上采样调度 | 专有 | 同上 |
| NVIDIA / AMD / Intel 运行库 | `nvngx_*.dll`、Streamline 插件、FSR、XeSS 二进制 | 各自厂商许可 | **动态加载，单独放置**，不与核心代码合并成一个许可证 |

---

## 2. 可用于专有核心的依赖（已核对原文）

### 2.1 Java 层

| 组件 | 核对提交 / 版本 | 许可证（原文文件） | 用途 | 义务 |
|---|---|---|---|---|
| LWJGL 3（`lwjgl-vulkan`、`lwjgl-vma`、`lwjgl-shaderc` 等绑定） | `LWJGL/lwjgl3` @8adda23 | **BSD-3-Clause**（`LICENSE.md`） | Java 端 Vulkan 绑定。MC 1.20.1 自带 LWJGL 3.3.1，新增模块需与其版本匹配（报告二 §5.2） | 保留版权声明与免责声明；不得用其名义背书。随附绑定的原生库（VMA、shaderc）另按其各自许可证 |
| JOML | `JOML-CI/JOML` @2d6c1df | **MIT**（`LICENSE`） | 矩阵与向量；MC 已自带 | 保留声明 |
| Mixin | `SpongePowered/Mixin` @4053421 | **MIT**（`LICENSE.txt`） | 由 Forge 提供 | 保留声明 |
| MixinExtras | `LlamaLad7/MixinExtras` @613b418 | **MIT**（`LICENSE`） | 如需 `@WrapOperation` 等注解，需 JarInJar 打包 | 保留声明 |
| NVIDIA `rtxremix-java` / `remixbridge` | `NVIDIAGameWorks/rtx-remix-minecraft` v0.1.0 | **Apache-2.0**（仓库 `LICENSE`） | 仅在选择报告一的 D 路线时使用 | 保留 LICENSE 和 NOTICE；在修改过的文件中注明修改 |

### 2.2 原生层（C++）

| 组件 | 核对提交 / 版本 | 许可证（原文文件） | 用途 | 义务 / 备注 |
|---|---|---|---|---|
| Vulkan-Headers | `KhronosGroup/Vulkan-Headers` @73814d0 | **Apache-2.0 或 MIT**，按文件区分（`LICENSE.md`、`LICENSES/`） | Vulkan 头文件 | 保留声明 |
| volk | `zeux/volk` @b7764bc | **MIT**（`LICENSE.md`） | Vulkan 函数加载 | 保留声明 |
| Vulkan Memory Allocator | `GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator` @0f1a757 | **MIT**（`LICENSE.txt`） | 显存分配 | 保留声明 |
| glm | `g-truc/glm` @6f14f47 | **Happy Bunny License 或 MIT**，二选一（`copying.txt`） | 数学库 | 选 MIT 并保留声明 |
| shaderc | `google/shaderc` @170316e | **Apache-2.0**（`LICENSE`） | GLSL → SPIR-V 编译 | 保留 LICENSE 和 NOTICE。依赖 glslang，见下行 |
| glslang | `KhronosGroup/glslang` @a37cfbe | **多许可证**：主体为 BSD-3 / BSD-2 / MIT / Apache-2.0；`LICENSE.txt` 中另列有 **"GPL 3 with special bison exception"** 一节 | shaderc 的前端 | **需要单独确认 GPL 部分覆盖哪些文件、是否会进入构建产物**。上游说明 Bison 早已移除，可以不使用 Bison 文件构建。引入前按原则第 9 条报告 |
| SPIRV-Tools | `KhronosGroup/SPIRV-Tools` @066042e | **Apache-2.0**（`LICENSE`） | SPIR-V 优化与校验 | 保留声明 |
| SPIRV-Headers | `KhronosGroup/SPIRV-Headers` @fbf8683 | **MIT 类**（Khronos，`LICENSE`） | 头文件 | 保留声明 |
| DirectX Shader Compiler（如需 HLSL → SPIR-V） | `microsoft/DirectXShaderCompiler` @0d6c746 | **LLVM Release License（University of Illinois/NCSA）**（`LICENSE.TXT`） | 可选；`Streamline_Sample` 构建需要 `dxc.exe` | 保留声明 |
| stb | `nothings/stb` @003501f | **MIT 或 Public Domain**，二选一（`LICENSE`） | 图像读写 | 选 MIT 并保留声明 |
| nlohmann/json | `nlohmann/json` @24078c8 | **MIT**（`LICENSE.MIT`） | 配置解析 | 保留声明 |
| nvpro_core2（只作为示例或参考框架） | `nvpro-samples/nvpro_core2` @7cfe3ca | **Apache-2.0**（`LICENSE`） | S0 预检示例的依赖；可以参考 | 复制代码时保留声明并注明修改 |
| NVIDIA NRI | `NVIDIA-RTX/NRI` @83fd010 | **MIT**（`LICENSE.txt`，"Copyright (c) 2021, NVIDIA CORPORATION"） | 可选：NRD 所用的渲染接口抽象 | 保留声明 |
| NVAPI | `NVIDIA/nvapi` @70d337d | **MIT**（`License.txt`，覆盖 `nvapi.lib` / `nvapi64.lib` 等） | 可选：NVIDIA 驱动查询 | 保留声明；头文件的条款见同一文件 |

### 2.3 上采样与降噪 SDK

| 组件 | 核对提交 / 版本 | 许可证（原文文件） | 用途 | 义务 / 备注 |
|---|---|---|---|---|
| **Streamline SDK** | `NVIDIA-RTX/Streamline` @625db07（SDK 2.14.16） | **MIT**（`license.txt:1-21`）。**例外**：`sl_nvperf.h`、`sl_nvperf.dll` 适用 Nsight Perf SDK License（`license.txt:22-32`）。第三方组件见 `3rd-party-licenses.md`（Premake、nlohmann/json、TinyEXR、OpenEXR 等） | DLSS SR 接入（推荐方式） | 保留 MIT 声明，附第三方声明。**不分发 `sl_nvperf.*`**，它只是性能分析工具。DLSS 功能插件内含 NGX，适用 NVIDIA 条款（见下行） |
| **DLSS SDK / NGX**（`nvngx_dlss.dll`、`nvngx_dlssd.dll`） | `NVIDIA/DLSS` @9981c63（310.9.16） | **NVIDIA RTX SDKs License**（`LICENSE.txt`） | DLSS SR / RR | 见 §3.1，义务最多 |
| NRD（降噪） | `NVIDIA-RTX/NRD` @e06852b | **NVIDIA RTX SDKs License**（`LICENSE.txt`） | 可选：非 RR 路径的光追降噪 | 同 §3.1。**禁止让它受开源许可证约束** |
| SHARC（辐射缓存） | `NVIDIA-RTX/SHARC` @4e21b58 | **NVIDIA RTX SDKs License**（`License.md`） | 可选：GI 缓存 | 同上 |
| RTXDI（ReSTIR 直接光照） | `NVIDIA-RTX/RTXDI` @a6efab9 | **NVIDIA RTX SDKs License**（`LICENSE.txt`） | 可选：大量光源的直接光照 | 同上 |
| Reflex | `NVIDIA-RTX/Reflex` @c2522ce | **"License Agreement for NVIDIA Software Development Kits"**（`license.txt`，与 RTX SDKs License 不是同一份） | 可选：低延迟。只有 DLSS FG 强制要求，3080 Ti 用不到 | 引入前另行审读全文（本次只读了开头） |
| **AMD FidelityFX SDK v1.1.4（FSR 3.1）** | `GPUOpen-LibrariesAndSDKs/FidelityFX-SDK` tag v1.1.4（c6efa6b，2025-05-08） | **MIT**（`LICENSE.txt`） | 非 NVIDIA 显卡的上采样回退 | 保留声明。可以源码集成 |
| **AMD FSR SDK 2.x（当前主线）** | 同仓库 @60f4ea8（"AMD FSR SDK 2.3.0"，2026-06-24） | **AMD 自有许可**（`docs/license.md`）：只允许以**二进制形式**再分发；**禁止逆向工程、反编译、反汇编**；须在文档中复制声明与免责条款；部分列明文件除外 | 同上 | 可用于专有产品，但**不是 MIT**。⚠️ 报告二 §8.2 原写"MIT（外部知识）"，只对 v1.1.x 成立，已在报告二更正 |
| Intel XeSS | `intel/xess`（2026-10-26 核对） | **Intel Simplified Software License**（`LICENSE.txt`）：只提供二进制，可以不加修改地使用和再分发 | 可选：Intel 显卡的上采样 | 按其条款再分发，不得修改二进制 |

---

## 3. NVIDIA 专项审核（原则第 8 条）

### 3.1 NVIDIA RTX SDKs License（DLSS、NGX、NRD、SHARC、RTXDI 共用）

来源：`NVIDIA/DLSS:LICENSE.txt`（提交 9981c63）。

| 条款 | 原文位置 | 对 AFL Renderer 的含义 |
|---|---|---|
| 只能"incorporated in object code format into a software application"分发 | `:12` | DLL 必须作为渲染器的一部分随包提供，不能单独提供下载 |
| 衍生的源码要带"This software contains source code provided by NVIDIA Corporation." | `:16` | 只有修改 NVIDIA 示例源码时才适用 |
| 分发条款至少同等保护，并保护用户隐私与权利 | `:17` | 渲染器的 EULA 中要包含相应条款 |
| 发现违规分发要书面通知 NVIDIA | `:18` | — |
| 禁止逆向，禁止移除版权声明 | `:23` | — |
| 禁止作为独立产品分发 | `:24` | 同 `:12` |
| **禁止使 SDK 受开源许可证约束**（例如要求以源码形式提供、可自由修改、免费再分发） | `:27` | **专有核心可以合规**。GPLv3 项目在这里存在张力（Radiance 的处理方式见报告二 §3.7） |
| 使用 DLSS 或 NGX 的应用须在**启动画面、关于页、制作人员名单**中署名并放置 NVIDIA 商标 | `:89` | 需要设计署名位置：Mod 加载画面或设置页 |
| NVIDIA 可以使用应用的截图做宣传 | `:92` | 知悉即可 |

### 3.2 DLSS 发布技术要求

来源：DLSS Programming Guide，报告二 §6.9。

- 只分发 `lib/<平台>_<架构>/rel/` 下的正式版 DLL，**禁止分发 `dev/` 版**（§4.2）。
- 不剥离 NVIDIA 签名；如需对发行包签名，只能**追加**签名（§4.2.2）。
- 产品公开文档中需收录 DLL 内第三方组件的版权与许可证全文：CURL、8x13 bitmap font、d3dx12.h、xml、npy、stb、DirectX-Graphics-Samples、Vulkan-Headers（§4.2.3、§9.6）。
- 可以不申请应用 ID，使用 GUID 形式的 Project ID 加 `CUSTOM` 引擎类型（§5.2.1）。部署检查表中"Game specific Application ID"的写法，正式发布前向 NVIDIA 确认。
- 卸载时要删除 DLL（§4.2.1）。

### 3.3 NVIDIA RTX Remix Runtime（仅报告一 D 路线）

- `NV:packaging/licenses/remix-runtime/ThirdPartyLicenses.txt`：同样是"作为应用一部分以目标码分发、不得单独分发"的条款（`:122-177`）。
- DLSS / NGX 只能在 NVIDIA GPU 上使用（`:351-354`）。
- runtime 是私有构建（`minecraft-rtx2-71a581da`），版本更新由 NVIDIA 控制。

---

## 4. 只作参考、不进入核心的项目

| 项目 | 核对提交 | 许可证 | 处理方式 |
|---|---|---|---|
| Radiance（Java） | `Minecraft-Radiance/Radiance` 787670a（`release-0.1.6-1.20.1-forge`） | **GPL-3.0**（`LICENCE`；`mods.toml` `license="GPL-3.0"`） | 只引用文件位置和公开事实；不复制、不改写；R0 只作为黑盒运行 |
| MCVR（C++） | `Minecraft-Radiance/MCVR` 6719d74 | **GPLv3 为主**；少数文件为 Apache-2.0 或 MIT（`LICENSE.md`） | 同上。其中 Apache 和 MIT 文件也是改自 nvpro-samples，需要时**直接参考上游原版**（`vk_denoise_nrd`、`vk_denoise_dlssrr`），不从 MCVR 取 |
| Shaderpack-Advanced | `Minecraft-Radiance/Shaderpack-Advanced` | **GPL-3.0**（`LICENSE`） | 同上 |
| VulkanMod | `xCollateral/VulkanMod` 14efa9d（`1.20.x`） | **LGPL-3.0**（`LICENSE`） | 只借鉴思路；修改并分发 LGPL 部分需要开放该部分源码，因此不并入核心 |
| Oculus、Embeddium | AFL 运行时依赖（未拉取源码核对） | 本次**未核对原文** | 与 AFL Renderer 互斥，不链接、不复制 |

### 参考记录要求

阅读上面任一项目时，在"参考记录"中写明：日期、项目与提交、阅读的文件、得出的结论（只写事实或架构结论）。**不保存代码片段，不对照抄写。**

---

## 5. 平台与游戏层

| 组件 | 许可证 / 条款 | 说明 |
|---|---|---|
| Minecraft Forge 1.20.1 | **LGPL-2.1**（`MinecraftForge/MinecraftForge` 分支 1.20.1 `LICENSE.txt`："Unless noted below, Minecraft Forge, Forge Mod Loader, and all parts herein are licensed under the terms of the LGPL 2.1"） | Mod 依赖 Forge 运行，不需要以 LGPL 发布；不修改、不分发 Forge 本体 |
| Minecraft（Mojang） | Minecraft EULA 与使用准则 | **本次未核对原文**。准则对 Mod 的商业化有限制，正式发布和任何收费方案都要复核 |

---

## 6. 发布前合规检查表

- [ ] 依赖清单与实际构建产物一致：用工具列出 JAR 和 DLL 中的全部第三方文件，逐一对照本清单。
- [ ] glslang 的 GPL 部分没有进入产物，或者已按原则第 9 条报告并得到批准。
- [ ] 所有宽松许可证（MIT / BSD / Apache / NCSA）的声明，汇总到渲染器的 `THIRD_PARTY_NOTICES`。
- [ ] Apache-2.0 组件附带了 NOTICE 文件（如有），修改过的文件已注明修改。
- [ ] NVIDIA：只带 `rel` 版 DLL、签名完整、署名与商标位置符合要求、DLL 内第三方声明已收录、EULA 含同等保护条款。
- [ ] AMD：FSR 的版本线（v1.1.x MIT 或 2.x AMD 许可）已确定，对应声明已收录。
- [ ] Intel XeSS（如使用）：二进制未修改，声明已收录。
- [ ] `afl-renderer-api` 已附 Apache-2.0（或选定许可证）文本，且不含任何专有实现。
- [ ] 参考记录完整，核心代码中没有来自 GPL 或 LGPL 项目的片段。
- [ ] Minecraft 使用准则已复核。
- [ ] 法务复核完成。
