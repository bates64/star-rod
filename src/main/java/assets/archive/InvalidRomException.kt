package assets.archive

/**
 * Exception thrown when attempting to patch a ROM that is not a valid papermario-dx build.
 */
class InvalidRomException(message: String, cause: Throwable? = null) : Exception(message, cause)
