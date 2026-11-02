import zipfile, re, sys
jar = "build/libs/sunlitmarket-1.20.1-0.1.0.jar"
z = zipfile.ZipFile(jar)
bad = []
n = 0
for name in z.namelist():
    if not name.endswith(".class"): continue
    if "/client/" in name: continue
    n += 1
    data = z.read(name)
    if b"net/minecraft/client" in data:
        bad.append(name)
print("non-client classes:", n)
print("leaks:", len(bad))
for b in bad: print("  ", b)
