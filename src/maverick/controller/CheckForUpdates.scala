package maverick.controller

import maverick.model.error.VersionNotFoundException
import maverick.model.{Module, ModuleExport, ModuleUpdate}
import utopia.flow.parse.string.{IterateLines, Regex}
import utopia.flow.collection.CollectionExtensions._
import utopia.flow.parse.file.FileExtensions._
import utopia.flow.time.Today
import utopia.flow.util.StringExtensions._
import utopia.flow.util.Version

/**
 * Checks for model-specific updates
 * @author Mikko Hilpinen
 * @since 3.10.2021, v0.1
 */
object CheckForUpdates
{
	private val versionLineRegex = Regex.escape('#') + Regex.any + Version.regex + Regex.any
	
	/**
	 * Finds the latest version or an update of the specified module
	 * @param module Module for which updates are searched
	 * @return Either Left: Latest module export or Right: Pending module update.
	 *         Failure if no version data was found or if no proper jar files were found.
	 */
	def apply(module: Module) = {
		// Reads the change list document and finds the latest version (line / range)
		IterateLines.fromPath(module.changeListPath) { linesIter =>
			linesIter.nextWhere { versionLineRegex(_) }
				.flatMap { line =>
					Version.findFrom(line).map { version =>
						// checks whether it's a development version or an existing version
						// Also counts today's releases as development versions (modified release use-case)
						// Case: A development version => checks for changes list and creates an update
						if (line.containsIgnoreCase("dev") ||
							line.contains(CloseChangeDocument.dateFormat.format(Today.toLocalDate)))
						{
							val changeLines = linesIter.takeWhile { !versionLineRegex(_) }.toVector
								.dropRightWhile { _.isEmpty }
							Right((version, changeLines))
						}
						// Case: An existing version, returns it
						else
							Left(version)
					}
				}
				// Fails if no version was listed
				.toTry { new VersionNotFoundException(
					s"${module.changeListPath.fileName} of ${module.name} doesn't list any module versions") }
		}.flatten.flatMap { latest =>
			// Creates a module export based on found version. In case of an update, wraps the export in an update.
			ModuleExport.findFor(module, latest.leftOrMap { _._1 })
				.map { export =>
					latest.mapBoth { _ => export } { case (_, changes) => ModuleUpdate(export, changes) }
				}
		}
	}
}
