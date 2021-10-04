package maverick.controller

import maverick.model.Module
import utopia.flow.datastructure.immutable.Pair
import utopia.flow.parse.Regex
import utopia.flow.util.CollectionExtensions._
import utopia.flow.util.FileExtensions._

import java.nio.file.Path

/**
 * Used for finding project modules that use versioning
 * @author Mikko Hilpinen
 * @since 3.10.2021, v0.1
 */
object FindModules
{
	private val changeListDocumentNameRegex = Regex.any + (Regex("c") || Regex("C")).withinParenthesis +
		Regex("hange") + Regex.any + Regex.escape('.') + Regex("md")
	private val nameSplitterRegex = Regex.escape('-') || Regex.escape('_')
	
	/**
	 * Finds project modules from the specified project directory
	 * @param projectDirectory Project's directory
	 * @return Modules under that project, followed by modules that are missing an artifact directory.
	 *         Failure if file reading failed at some point
	 */
	def apply(projectDirectory: Path) =
	{
		// Finds directories which contain a change list document
		projectDirectory.tryIterateChildren { iter =>
			// Possible modules
			iter.filter { _.isDirectory }.tryFlatMap { dir =>
				// Finds the changes document from module files
				dir.iterateChildren { iter =>
					iter.find { file =>
						file.isRegularFile && changeListDocumentNameRegex(file.fileName)
					}.map { dir -> _ }
				}
			// Combines the results
			}.map { _.toVector }
		}.flatMap { modulePaths =>
			// Next, makes sure the modules have an associated artifact
			val artifactsDirectory = projectDirectory/"out/artifacts"
			// Finds all possible specific artifact directories
			artifactsDirectory.iterateChildren { _.filter { _.isDirectory }.toVector }.map { artifactDirectories =>
				// Finds, which artifact directory matches with which module
				modulePaths.dividedWith { case (moduleDir, changeListPath) =>
					// Splits the module name so that searching is more flexible
					val moduleNameParts = nameSplitterRegex.split(moduleDir.fileName).toVector.flatMap { part =>
						// Also splits the module name based on camel casing (e.g. TestModule -> Test & Module)
						val upperCaseLocations = Regex.upperCaseLetter.startIndexIteratorIn(part)
							.toVector.dropWhile { _ == 0 }
						if (upperCaseLocations.isEmpty)
							Vector(part)
						else
							part.substring(0, upperCaseLocations.head) +:
								upperCaseLocations.paired.map { case Pair(start, end) => part.substring(start, end) } :+
								part.substring(upperCaseLocations.last)
					}
					val lowerModuleNameParts = moduleNameParts.map { _.toLowerCase }
					val moduleName = moduleNameParts.mkString(" ")
					// The matching is performed based on module name vs. directory name
					artifactDirectories.filter { dir =>
						val dirNameParts = nameSplitterRegex.split(dir.fileName).toVector.map { _.toLowerCase }
						lowerModuleNameParts.forall(dirNameParts.contains)
					// In case of multiple possible results, the shortest name is selected
					}.minByOption { _.fileName.length } match
					{
						// Case: Matching directory found
						case Some(artifactDirectory) =>
							Left(Module(moduleName, changeListPath, artifactDirectory))
						// Case: No matching directory found
						case None => Right(moduleName)
					}
				}
			}
		}
	}
}
