package maverick.model

import maverick.model.Module.scalaJarRegex
import utopia.flow.parse.Regex
import utopia.flow.util.FileExtensions._

import java.nio.file.Path

object Module
{
	private val scalaJarRegex = Regex("scala-library") + Regex.any + Regex.escape('.') + Regex("jar")
}

/**
 * Represents a project module
 * @author Mikko Hilpinen
 * @since 3.10.2021, v0.1
 */
case class Module(name: String, projectName: String, changeListPath: Path, artifactDirectory: Path)
{
	/**
	 * @return Whether this module exports full applications and not just individual jar files
	 */
	// Checks whether the export directory contains a scala-library jar file
	def isApplication = artifactDirectory
		.iterateChildren { _.exists { p => p.isRegularFile && scalaJarRegex(p.fileName) } }
		.getOrElse(false)
}