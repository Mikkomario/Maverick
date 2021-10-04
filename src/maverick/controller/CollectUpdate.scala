package maverick.controller

import maverick.model.enumeration.UpdateType
import maverick.model.enumeration.UpdateType.{Breaking, Overhaul}
import maverick.model.{ModuleExport, ModuleUpdate}
import utopia.flow.util.CollectionExtensions._
import utopia.flow.util.FileExtensions._

import java.nio.file.Path

/**
 * Used for writing an update document and for collecting the updated files to a single place
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
object CollectUpdate
{
	// ATTRIBUTES   ------------------------------
	
	private val ignoredApplicationFileTypes = Set("7z", "zip", "rar")
	
	private val applicationOrdering = Ordering.by { update: ModuleUpdate => !update.isApplication }
	private val updateLevelOrdering = Ordering.by { update: ModuleUpdate => update.updateType }
	private val alphabeticalOrdering = Ordering.by { update: ModuleUpdate => update.module.name }
	
	
	// OTHER    ----------------------------------
	
	/**
	 * Collects the update into a single change list document + binary directory
	 * @param updatesDirectory Directory where the updates will be exported to
	 * @param updates Module updates
	 * @param notChanged Exports of modules which didn't have any updates
	 * @return Success or failure
	 */
	// TODO: Add support for patch-style update (only single module was updated)
	def apply(updatesDirectory: Path, updates: Seq[ModuleUpdate], notChanged: Seq[ModuleExport]) =
	{
		// Creates the update directory
		updatesDirectory.asExistingDirectory.flatMap { updateDir =>
			val changeDocumentPath = updateDir/"Changes.md"
			
			// In case there is only one updated module, goes into "patch mode", releasing that model separately
			if (updates.size == 1)
			{
				val update = updates.head
				changeDocumentPath.writeLines(s"# ${update.module.name} ${update.version}" +: update.changeDocLines)
					.flatMap { _ => collectArtifacts(updateDir, Vector(update.wrapped)) }
			}
			else
			{
				// Orders the modules based on update level and alphabetical order
				val orderedUpdates = updates.sortedWith(applicationOrdering, updateLevelOrdering, alphabeticalOrdering)
				val orderedNotChanged = notChanged.sortBy { _.isApplication }.sortBy { _.module.name }
				// Writes the change document, then collects the binaries
				writeChanges(changeDocumentPath, orderedUpdates, orderedNotChanged).flatMap { _ =>
					collectArtifacts(updateDir/"binaries", orderedUpdates.map { _.wrapped } ++ orderedNotChanged)
				}
			}
		}
	}
	
	private def writeChanges(path: Path, updates: Iterable[ModuleUpdate], notChanged: Iterable[ModuleExport]) =
		path.writeUsing { writer =>
			writer.println("# Summary")
			writer.println("TODO: Write summary")
			writer.println()
			
			writer.println("# Module Versions")
			writer.println()
			// Changed / Not Modified -headers are only written when both groups exist
			val includesBothUpdates = updates.nonEmpty && notChanged.nonEmpty
			if (includesBothUpdates)
				writer.println("## Changed")
			updates.foreach { update => writer.println(s"- ${update.module.name} ${update.version}${
				updateTypeDescription(update.updateType)}") }
			if (includesBothUpdates)
				writer.println("## Not Modified")
			notChanged.foreach { export => writer.println(s"- ${export.module.name} ${export.version}") }
			writer.println()
			
			writer.println("# Changes per Module")
			writer.println(
				"All module changes are listed below. Modules are described in the same order as in the list above.")
			updates.foreach { update =>
				writer.println()
				writer.println(s"## ${update.module.name} ${update.version}")
				update.changeDocLines.foreach(writer.println)
			}
		}
	
	private def collectArtifacts(path: Path, modules: Iterable[ModuleExport]) =
	{
		// Makes sure the targeted directory exists
		path.asExistingDirectory.flatMap { path =>
			// Attempts to copy each module
			modules.tryForeach { export =>
				export.jarPath match
				{
					// Case: Jar-based export => Copies the jar file
					case Some(jarPath) => jarPath.copyTo(path).map { _ => () }
					// Case: Application export =>
					// Creates a new directory for the application and copies application files to it
					case None =>
						path.resolve(s"${export.module.name}-${export.version}").asExistingDirectory
							.flatMap { dir =>
								export.artifactDirectory.tryIterateChildren {
									// Ignores zip files (expecting them to be zipped applications)
									_.filterNot { file => ignoredApplicationFileTypes.contains(file.fileType) }
										.tryForeach { _.copyTo(dir).map { _ => () } }
								}
							}
				}
			}
		}
	}
	
	private def updateTypeDescription(updateType: UpdateType) = updateType match
	{
		case Overhaul => "- major update"
		case Breaking => "- breaking changes"
		case _ => ""
	}
}
