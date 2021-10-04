package maverick.model.error

/**
 * Thrown when a version is not found where expected
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
class VersionNotFoundException(message: String, cause: Throwable = null) extends Exception(message, cause)
