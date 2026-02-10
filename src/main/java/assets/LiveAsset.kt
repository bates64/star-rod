package assets

/**
 * Base class for loaded, in-memory asset data.
 * Holds a reference back to the asset it was loaded from.
 *
 * The relationship:
 * - Asset (file on disk, cheap)
 *   ↓ load
 * - LiveAsset (parsed data in memory, editable)
 *   ↓ save
 * - Asset (written back to disk)
 */
abstract class LiveAsset(
	val asset: Asset,
)
