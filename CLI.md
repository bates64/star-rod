# Star Rod Command-Line Interface

Star Rod provides a comprehensive CLI for automation, scripting, and headless builds.

## General Commands

### Help
```bash
java -jar StarRod.jar help
```
Display all available commands with usage examples.

### Version
```bash
java -jar StarRod.jar version
```
Show version information.

---

## Project Building

### Build Diorama Package
```bash
java -jar StarRod.jar build
```
Build a complete Diorama distribution package.

**Output:** `.starrod/build/<mod-id>.diorama`

**What it does:**
1. Compiles all project assets (maps, sprites, textures, etc.)
2. Compresses binary data using Yay0 compression
3. Packages into AssetsArchive format
4. Includes project.kdl manifest
5. Generates target.json with engine SHA and configuration
6. Packages everything into a TAR.GZ archive

**Use cases:**
- Standard build command for development
- Creating release packages for distribution
- Automated build pipelines
- Continuous integration

### Build AssetsArchive Only
```bash
java -jar StarRod.jar archive
```
Build only the AssetsArchive (assets.bin) without packaging into Diorama.

**Output:** `.starrod/build/assets.bin`

**What it does:**
1. Compiles all project assets (maps, sprites, textures, etc.)
2. Compresses binary data using Yay0 compression
3. Writes to `.starrod/build/assets.bin`

**Use cases:**
- Testing asset compilation without creating a full distribution
- Integration with custom build pipelines
- Rapid iteration during development

---

## Diorama Management

### Apply Diorama to ROM
```bash
java -jar StarRod.jar apply <pmdx-file> <rom-file> [output-rom]
```

Apply a Diorama package to a ROM file.

**Arguments:**
- `<pmdx-file>`: Path to the .diorama package
- `<rom-file>`: Path to the base ROM (papermario.z64)
- `[output-rom]`: Optional output path (defaults to modifying rom-file in-place)

**Examples:**
```bash
# Apply to a copy
java -jar StarRod.jar apply mymod.diorama baserom.z64 modded.z64

# Apply in-place (modifies baserom.z64)
java -jar StarRod.jar apply mymod.diorama baserom.z64
```

**Diorama Package Format:**
A Diorama package is a TAR.GZ archive containing:
- `project.kdl` - Project manifest with mod metadata
- `target.json` - Target configuration (engine SHA, ROM addresses)
- `assets.bin` - AssetsArchive binary

**target.json example:**
```json
{
  "papermario-dx": {
    "engine_sha": "a1b2c3d4e5f6...",
    "assets_archive_ROM_START": "0x1E40000"
  }
}
```

**What it does:**
1. Extracts assets.bin from the Diorama package
2. Copies the base ROM to the output location (if specified)
3. Validates that the ROM is a compatible papermario-dx build
4. Applies the mod using intelligent patching strategy (see below)

**ROM Patching Strategy:**

The patcher automatically detects if the mod already exists in the ROM (by project name):

- **If mod exists and new version fits:** Replaces in-place for optimal ROM size
- **If mod exists but new version is larger:** Removes old version, appends new one
- **If mod doesn't exist:** Appends to the end of the chain

This means you can repeatedly apply the same mod during development without the ROM growing unbounded. Each re-application replaces the previous version if possible.

**Technical Details:**
- Validates ROM has AssetsArchive chain (refuses vanilla PM64 ROMs)
- Aligns data to 16-byte boundaries (N64 requirement)
- Updates linked list to maintain chain integrity
- Supports chaining multiple different mods

### Inspect AssetsArchive
```bash
java -jar StarRod.jar inspect <archive-file>
```

Display the contents and metadata of an AssetsArchive file.

**Example:**
```bash
java -jar StarRod.jar inspect .starrod/build/assets.bin
```

**Output:**
```
AssetsArchive Information:
  Magic:        MAPFS
  Project:      MyMod
  File size:    245760 bytes

Table of Contents:
  Name                                                         Offset   Comp. Size Decomp. Size
  ----------------------------------------------------------------------------------------------------
  kmr_20_shape.bin                                             184      12345      15000
  kmr_20_collision.bin                                         12529    8192       10240
  custom_texture.bin                                           20721    4096       4096
  END DATA                                                     -        -          -

  Total: 3 entries
  Compressed:   24633 bytes
  Decompressed: 29336 bytes
  Compression:  84.0%
```

**Use cases:**
- Debugging archive contents
- Verifying compression ratios
- Checking which assets are included
- Inspecting downloaded Diorama packages

---

## AssetsArchive Format

The AssetsArchive format is based on Paper Mario's internal "mapfs" filesystem:

### Binary Structure
```
[Header: 32 bytes]
  - Magic: "MAPFS " (6 bytes)
  - Project name: (16 bytes, null-padded)
  - Reserved: (10 bytes)

[Table of Contents: N × 76 bytes]
  For each entry:
    - Name: (64 bytes, null-terminated)
    - Data offset: (4 bytes, big-endian)
    - Compressed size: (4 bytes, big-endian)
    - Decompressed size: (4 bytes, big-endian)

[Sentinel Entry: 76 bytes]
  - Name: "END DATA\0"
  - Next node offset: (4 bytes) - 0 = end of chain
  - Compressed size: 0
  - Decompressed size: 0

[Data Section]
  - Raw or Yay0-compressed binary data
```

### Compression
- Files ≥64 bytes are compressed using Yay0 (LZSS variant)
- Files <64 bytes stored uncompressed
- Binary artifacts (BINARY, SHAPE, COLLISION types) are compressed
- Headers and small files stored uncompressed

### ROM Integration
- AssetsArchive uses a linked list in ROM
- Multiple mods can chain together
- Default ROM address: 0x1E40000
- Each node can point to the next via sentinel entry

---

## Automation Examples

### CI/CD Build Pipeline
```bash
#!/bin/bash
# Build and release a mod

set -e

# Build the Diorama package
java -jar StarRod.jar pmdx

# Verify the archive
java -jar StarRod.jar inspect .starrod/build/assets.bin

# Upload to release
MOD_ID=$(grep 'id =' project.kdl | cut -d'"' -f2)
cp .starrod/build/${MOD_ID}.diorama releases/
```

### Rapid Asset Testing
```bash
#!/bin/bash
# Quick rebuild and inspect

java -jar StarRod.jar archive
java -jar StarRod.jar inspect .starrod/build/assets.bin
```

### Iterative Development
```bash
#!/bin/bash
# Repeatedly apply your mod during development
# The ROM won't grow each time - your mod gets replaced in-place

while true; do
  # Make changes to your project...
  java -jar StarRod.jar pmdx
  java -jar StarRod.jar apply mymod.diorama papermario-dx.z64 test.z64
  # Test in emulator...
done

# Result: test.z64 stays roughly the same size because your mod
# is replaced each iteration rather than appended
```

### Multi-Mod ROM Creation
```bash
#!/bin/bash
# Apply multiple mods to a base ROM

BASE_ROM="papermario.z64"
OUTPUT_ROM="modded.z64"

# Copy base
cp "$BASE_ROM" "$OUTPUT_ROM"

# Apply mods in sequence
java -jar StarRod.jar apply mod1.diorama "$OUTPUT_ROM"
java -jar StarRod.jar apply mod2.diorama "$OUTPUT_ROM"
java -jar StarRod.jar apply mod3.diorama "$OUTPUT_ROM"

echo "Multi-mod ROM created: $OUTPUT_ROM"
```

---

## Exit Codes

- `0`: Success
- `1`: Error (build failed, file not found, etc.)

---

## Notes

- All commands must be run from a valid project directory (except -HELP and -VERSION)
- Paths can be relative or absolute
- File extensions are important (.diorama, .z64, .bin)
- Build output goes to `.starrod/build/` directory
- ROM files are modified in-place unless an output path is specified
