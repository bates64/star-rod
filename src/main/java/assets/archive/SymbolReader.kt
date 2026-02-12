package assets.archive

import java.io.File

/**
 * Reads a linker symbols file (syms.ld) and extracts symbol addresses.
 */
fun readSymbolAddress(symsFile: File, symbolName: String): Int? {
	if (!symsFile.exists()) {
		return null
	}

	// syms.ld format:
	// symbolName = 0x12345678; /* optional comment */
	val pattern = Regex("""^\s*$symbolName\s*=\s*0x([0-9A-Fa-f]+)\s*;""")

	symsFile.useLines { lines ->
		for (line in lines) {
			val match = pattern.find(line)
			if (match != null) {
				val hexValue = match.groupValues[1]
				return hexValue.toIntOrNull(16)
			}
		}
	}

	return null
}

/**
 * Reads the mapfs_ROM_START symbol from a linker symbols file.
 */
fun readMapfsRomStart(symsFile: File): Int {
	return readSymbolAddress(symsFile, "mapfs_ROM_START")
		?: throw IllegalStateException("mapfs_ROM_START symbol not found in ${symsFile.absolutePath}")
}
