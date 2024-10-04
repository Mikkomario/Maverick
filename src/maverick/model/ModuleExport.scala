package maverick.model

import utopia.flow.util.EitherExtensions._
import utopia.flow.util.Version

import scala.util.Success

object ModuleExport
{
	/**
	 * Creates a new module application export
	 * @param module Described module
	 * @param version Exported version
	 * @return A new module export that expects application format
	 */
	def application(module: Module, version: Version) = apply(module, Left(version))
	/**
	 * Creates a new jar -based module export
	 * @param module Described module
	 * @param jarPath Exported jar
	 * @return A new jar -based module export
	 */
	def jar(module: Module, jarPath: ArtifactJarPath) = apply(module, Right(jarPath))
	
	/**
	 * Creates a module export based on a specific version number and module artifact files
	 * @param module Described module
	 * @param version Exported version
	 * @return A module export. Failure if exported module jars were not found.
	 */
	def findFor(module: Module, version: Version) = {
		if (module.isApplication)
			Success(application(module, version))
		else
			ArtifactJarPath.findFrom(module.artifactDirectory, version).map { jar(module, _) }
	}
}

/**
 * Represents a specific exported module version
 * @author Mikko Hilpinen
 * @since 3.10.2021, v0.1
 * @param module Module being updated
 */
case class ModuleExport private(module: Module, private val versionOrJar: Either[Version, ArtifactJarPath])
{
	/**
	 * @return Whether this module represents an application (not a single jar)
	 */
	def isApplication = versionOrJar.isLeft
	/**
	 * @return Whether this module export consists of a single jar
	 */
	def isJar = versionOrJar.isRight
	
	/**
	 * @return Version of this export
	 */
	def version = versionOrJar.leftOrMap { _.version }
	/**
	 * @return Path to the artifact jar file. None if this is an application export and not a single jar export.
	 */
	def jarPath = versionOrJar.toOption
	/**
	 * @return Directory that stores the exported jars or application
	 */
	def artifactDirectory = module.artifactDirectory
}
