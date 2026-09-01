import os
import zipfile
import shutil
from pathlib import Path

targets = [
    "assets/minecraft/textures/entity/chest/normal.png",
    "assets/minecraft/textures/entity/chest/normal_left.png",
    "assets/minecraft/textures/entity/chest/normal_right.png",
]

search_roots = [
    Path(r"D:\Minecraft Modding\Apocalypse First Light\.gradle-user"),
    Path(r"D:\Minecraft Modding\Apocalypse First Light\build"),
    Path.home() / ".gradle" / "caches",
]

output = Path(
    r"D:\Minecraft Modding\Apocalypse First Light\vanilla_chest_textures"
)
output.mkdir(parents=True, exist_ok=True)

found_jar = None

print("Searching for vanilla chest textures...")
print()

for root in search_roots:
    if not root.exists():
        continue

    print(f"Scanning: {root}")

    for jar_path in root.rglob("*.jar"):
        try:
            with zipfile.ZipFile(jar_path, "r") as jar:
                names = set(jar.namelist())

                if targets[0] in names:
                    found_jar = jar_path

                    print()
                    print("[FOUND]")
                    print(jar_path)
                    print()

                    for target in targets:
                        if target not in names:
                            print(f"[MISSING IN JAR] {target}")
                            continue

                        filename = Path(target).name
                        destination = output / filename

                        with jar.open(target) as src:
                            with destination.open("wb") as dst:
                                shutil.copyfileobj(src, dst)

                        print(f"[EXTRACTED] {filename}")
                        print(f"            {destination}")

                    break

        except (zipfile.BadZipFile, PermissionError, OSError):
            continue

    if found_jar:
        break

if not found_jar:
    print()
    print("[NOT FOUND]")
    print("No scanned JAR contains:")
    print(targets[0])
else:
    print()
    print("Done.")