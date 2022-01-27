package maverick.controller

import maverick.model.enumeration.UpdateType
import maverick.model.enumeration.UpdateType.{Breaking, Overhaul}
import maverick.model.{ModuleExport, ModuleUpdate}
import utopia.flow.util.CollectionExtensions._
import utopia.flow.util.FileExtensions._

import java.nio.file.Path
import scala.io.Codec
import scala.util.Success

/**
 * Used for writing an update document and for collecting the updated files to a single place
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
object CollectUpdate
{
	// ATTRIBUTES   ------------------------------
	
	private val ignoredApplicationFileTypes = Set("7z", "zip", "rar")
	
	private val updateLevelOrdering = Ordering.by { update: ModuleUpdate => update.updateType }.reverse
	private val alphabeticalOrdering = Ordering.by { update: ModuleUpdate => update.module.name }
	
	
	// OTHER    ----------------------------------
	
	/**
	 * Collects the update into a single change list document + binary directory
	 * @param updatesDirectory Directory where the updates will be exported to
	 * @param updates Module updates
	 * @param notChanged Exports of modules which didn't have any updates
	 * @return Success or failure
	 */
	def apply(updatesDirectory: Path, updates: Seq[ModuleUpdate], notChanged: Seq[ModuleExport] = Vector(),
	          summaryAdditions: Map[ModuleUpdate, Seq[String]] = Map())
	         (implicit codec: Codec) =
	{
		// Creates the update directory
		updatesDirectory.asExistingDirectory.flatMap { updateDir =>
			
			// Divides the updates into application and module changes
			val (moduleUpdates, applicationUpdates) = updates.divideBy { _.isApplication }
			val unchangedModules = notChanged.filterNot { _.isApplication }
			
			// Writes the module changes, if there are any
			val moduleWrite = if (moduleUpdates.isEmpty) Success(()) else
				writeModules(updateDir, moduleUpdates, unchangedModules)
			moduleWrite.flatMap { _ =>
				// Writes the application updates, if there are any
				applicationUpdates.tryForeach { update =>
					applicationUpdate(updateDir, update, summaryAdditions.getOrElse(update, Vector()))
				}
			}
		}
	}
	
	// Call only if there are updates
	private def writeModules(updatesDirectory: Path, updates: Seq[ModuleUpdate],
	                         notChanged: Seq[ModuleExport] = Vector(),
	                         summaryAdditions: Map[ModuleUpdate, Seq[String]] = Map())
	                        (implicit codec: Codec) =
	{
		val changeDocumentPath = updatesDirectory/"Changes.md"
		
		// In case there is only one updated module, goes into "patch mode", releasing that model separately
		if (updates.size == 1)
		{
			val update = updates.head
			changeDocumentPath.writeLines(
				(s"# ${update.module.name} ${update.version}" +:
					summaryAdditions.getOrElse(update, Vector())) ++ update.changeDocLines
			).flatMap { _ => collectArtifacts(updatesDirectory, Vector(update.wrapped)) }
		}
		else
		{
			// Orders the modules based on update level and alphabetical order
			val orderedUpdates = updates.sortedWith(updateLevelOrdering, alphabeticalOrdering)
			val orderedNotChanged = notChanged.sortBy { _.module.name }
			// Writes the change document, then collects the binaries
			writeChanges(changeDocumentPath, orderedUpdates, orderedNotChanged, summaryAdditions).flatMap { _ =>
				collectArtifacts(updatesDirectory/"binaries", orderedUpdates.map { _.wrapped } ++ orderedNotChanged)
			}
		}
	}
	
	private def writeChanges(path: Path, updates: Iterable[ModuleUpdate], notChanged: Iterable[ModuleExport],
	                         summaryAdditions: Map[ModuleUpdate, Seq[String]])
	                        (implicit codec: Codec) =
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
				summaryAdditions.get(update).foreach(writer.println)
				update.changeDocLines.foreach(writer.println)
			}
		}
	
	private def collectArtifacts(path: Path, modules: Iterable[ModuleExport]) =
	{
		// Makes sure the targeted directory exists
		path.asExistingDirectory.flatMap { path =>
			// Attempts to copy each module
			modules.tryForeach { export => export.jarPath.tryForeach { _.copyTo(path).map { _ => () } } }
		}
	}
	
	private def applicationUpdate(outputRoot: Path, application: ModuleUpdate, summaryAdditions: Seq[String])
	                             (implicit codec: Codec) =
	{
		outputRoot.resolve(s"${application.module.name}-${application.version}").asExistingDirectory
			.flatMap { dir =>
				// Copies the application jars and other files to a new folder
				application.artifactDirectory.tryIterateChildren {
					// Ignores zip files (expecting them to be zipped applications)
					_.filterNot { file => ignoredApplicationFileTypes.contains(file.fileType) }
						.tryForeach { _.copyTo(dir).map { _ => () } }
				}.flatMap { _ =>
					// Writes a change summary
					dir.resolve("Changes.md").writeUsing { writer =>
						writer.println("# Summary")
						summaryAdditions.foreach(writer.println)
						application.changeDocLines.foreach(writer.println)
					}
				}
			}.map { _ => () }
	}
	
	private def updateTypeDescription(updateType: UpdateType) = updateType match
	{
		case Overhaul => "- major update"
		case Breaking => "- breaking changes"
		case _ => ""
	}
}
