# Highway V2 M1-A 开发测试场工具

完整命令、碰撞限制、指标和用户实机验收：[开发测试场报告](../../docs/worldgen/highway_v2_m1a_mesh_sandbox.md)。

```powershell
node tools/highway-v2-m1a/prepare.mjs
node tools/highway-v2-m1a/prepare.mjs --check
# 设置为本机已有JDK17；只需现有Gson缓存，无需网络。
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
node tools/highway-v2-m1a/verify.mjs
# 已compileJava后直接核对其纯几何编译类：
node tools/highway-v2-m1a/verify.mjs --compiled
```

prepare直接复制四件已导出的V2-0资源，不调用新道路生成器；scene.json包含原点、中心线与源文件SHA。更改runtime几何/碰撞契约时升级runtimeContract。资源不是全国注册模型，不要手工编辑生成物。

verify运行真实RoadMeshAsset、ChunkMeshGeometry和原AflMeshLoader，写validation.json；检查16格裁剪、UV/源法线、真实接缝采样、碰撞高度/台阶、负坐标、独立重复构建。源码模式临时合并纯Java源码，编译产物模式直接读取Gradle输出；不运行Minecraft、世界生成或全套测试。耗时只输出控制台，确定性报告不含机器相关时间。

游戏中在专用开发创造世界使用 `/afl dev highway_mesh preview 0 80 0`，观察后依次dry_run/create同原点。预览无碰撞；移除用remove。原点XZ须16对齐，包络须全空气且chunk已加载；任何既有方块都拒绝覆盖。详情及PASS门槛以报告为准。

本轮compileJava与离线检查通过；实际客户端、保存重进、行走、光照、Oculus/Embeddium均未测试。
