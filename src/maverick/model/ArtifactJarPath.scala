package maverick.model

import utopia.flow.util.CollectionExtensions._
import utopia.flow.util.FileExtensions._
import utopia.flow.util.StringExtensions._
import utopia.flow.util.{Equatable, Version}

import java.io.FileNotFoundException
import java.nio.file.Path

object ArtifactJarPath
{
	/**
	 * @param path Path to a jar file
	 * @param version Version of this jar
	 * @return An artifact jar path that contains both versionless and jar versioned references
	 */
	def versionless(path: Path, version: Version) = new ArtifactJarPath(path, version, originalIsVersioned = false)
	/**
	 * @param path Path to a jar file
	 * @param version Version of this jar
	 * @return An artifact jar path that only contains that versioned jar reference
	 */
	def versioned(path: Path, version: Version) = new ArtifactJarPath(path, version, originalIsVersioned = true)
	
	/**
	 * Searches for an artifact jar file path from the specified directory,
	 * preferring a versionless jar file if possible
	 * @param directory Module artifact jars -directory
	 * @param searchedVersion Version of the searched jar
	 * @param isLatest Whether searching for the latest version (versionless jar can be used) (default = true)
	 * @return A new artifact jar path based on a jar file in that directory.
	 *         Failure if file reading failed or no jar file was found.
	 */
	def findFrom(directory: Path, searchedVersion: Version, isLatest: Boolean = true) =
		directory.allRegularFileChildrenOfType("jar").flatMap { files =>
			// Tries to find a versionless jar first (only applicable if this is the latest version)
			val withoutVersion =
			{
				if (isLatest)
					files.find { file => !Version.regex.existsIn(file.fileName) }
						.map { versionless(_, searchedVersion) }
				else
					None
			}
			// Next searches for a versioned jar
			withoutVersion.orElse { files.find { _.fileName.contains(searchedVersion.toString) }
				.map { versioned(_, searchedVersion) } }
			// If neither was found, fails
			.toTry { new FileNotFoundException(
				s"No artifact jar in $directory was found suitable for version $searchedVersion") }
		}
}

/**
 * Represents a path to an exported jar file. Handles both versionless and versioned copy.
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
class ArtifactJarPath(original: Path, val version: Version, originalIsVersioned: Boolean) extends Equatable
{
	// ATTRIBUTES   -------------------------------
	
	/**
	 * Path to the artifact jar file before version number is applied. None if that jar was not available.
	 */
	val versionless = if (originalIsVersioned) None else Some(original)
	/**
	 * Name of the versioned jar file
	 */
	val versionedFileName = {
		if (originalIsVersioned)
			original.fileName
		else
		{
			val (namePart, extensionPart) = original.fileName.splitAtLast(".")
			s"$namePart-$version.$extensionPart"
		}
	}
	/**
	 * Path to the artifact jar file with the correct version number applied
	 */
	val versioned = if (originalIsVersioned) original else original.withFileName(versionedFileName)
	
	
	// COMPUTED ---------------------------------
	
	/**
	 * @return Whether jar file size changes between the versionless (new) and versioned (previous) copy.
	 *         If true, this would indicate a change between the two version exports.
	 *         Returns false when there are no two existing jar files to compare.
	 */
	def changesSize = versionless.exists { versionless =>
		versioned.exists && versionless.exists &&
			versioned.size.toOption.exists { versionedSize => versionless.size.toOption.exists { _ != versionedSize } }
	}
	
	
	// IMPLEMENTED  -----------------------------
	
	override def properties = Vector(versionless, versioned)
	
	
	// OTHER    ---------------------------------
	
	/**
	 * Copies this jar file to the specified directory
	 * @param directory Directory where this jar is copied to
	 * @return New jar path. Failure if file copy failed.
	 */
	def copyTo(directory: Path) = versionless.filter { _.exists } match
	{
		case Some(versionless) =>
			// Updates artifact jar name to include versioning, then copies it to the target directory
			versionless.rename(versionedFileName, allowOverwrite = true).flatMap { _.copyTo(directory) }
		case None => versioned.copyTo(directory)
	}
}
