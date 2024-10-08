package maverick.main

import maverick.controller.{CheckForUpdates, CloseChangeDocument, CollectUpdate, FindModules}
import maverick.model.{ModuleExport, ModuleUpdate}
import utopia.flow.collection.CollectionExtensions._
import utopia.flow.parse.file.FileExtensions._
import utopia.flow.time.Today
import utopia.flow.util.EitherExtensions._
import utopia.flow.util.TryCatch
import utopia.flow.util.TryExtensions._
import utopia.flow.util.logging.{Logger, SysErrLogger}

import java.nio.file.Path
import scala.io.{Codec, StdIn}
import scala.util.{Failure, Success}

/**
 * The command line application representing this project
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
object Maverick extends App
{
	private implicit val log: Logger = SysErrLogger
	private implicit val codec: Codec = Codec.UTF8
	
	// Introduction
	println("Welcome to Maverick, a program for project exports")
	println("You can close the program at any time by writing 'exit' as input (without quotations)")
	println()
	
	private def ask(question: String) = {
		println(question)
		val result = StdIn.readLine()
		if (result.toLowerCase == "exit")
			System.exit(0)
		println()
		result
	}
	private def askBoolean(question: String, default: Boolean = false) = {
		val answer = ask(question + s" (y/n, default = ${if (default) "yes" else "no"})").toLowerCase
		if (answer.startsWith("y"))
			true
		else if (answer.startsWith("n"))
			false
		else
			default
	}
	
	// Queries the project directory and makes sure it exists
	val projectDirectory: Path = args.headOption
		.getOrElse[String] {
			println("Please specify the project directory to export")
			ask(s"Current directory: ${"".toAbsolutePath}")
		}
	if (!projectDirectory.isDirectory) {
		println(s"The project path you specified (${
			projectDirectory.toAbsolutePath}) is not an existing directory. Please try again.")
		System.exit(0)
	}
	
	// Finds the modules
	FindModules(projectDirectory) match {
		case Success((modules, missing)) =>
			// Checks whether there are incomplete modules and whether export should be terminated
			if (missing.nonEmpty) {
				if (modules.isEmpty) {
					println(s"It looks like all the ${missing.size} modules were missing an artifact directory.")
					println("Please build project artifacts and try again.")
					System.exit(0)
				}
				else {
					println(s"No artifact directory was found for the following modules: ${missing.mkString(", ")}")
					if (!askBoolean("Do you still want to continue the export process?"))
						System.exit(0)
				}
			}
			// Makes sure some modules were found
			else if (modules.isEmpty) {
				println("No modules could be found from that directory")
				println("Modules are directories which contain a change list document " +
					"(document must contain the word 'change' and be of type .md)")
				println("Please try again later or with another project")
				System.exit(0)
			}
			
			// Checks for module updates
			println(s"Found ${modules.size} modules: [${modules.map { _.name }.mkString(", ")}]")
			modules.map { CheckForUpdates(_) }.toTryCatch match {
				case TryCatch.Success(results: Seq[Either[ModuleExport, ModuleUpdate]], errors) =>
					errors.headOption.foreach { error =>
						error.printStackTrace()
						println(s"Failed to process ${ errors.size } of the ${ modules.size } modules")
					}
					
					val (notChanged, updated) = results.divided
					if (errors.nonEmpty && !askBoolean("Do you want to continue anyway?"))
						println("Export canceled")
					else if (updated.isEmpty)
						println("It looks like no module was updated (no 'dev' versions found). Please try again.")
					else {
						// Makes sure the "static" modules were not changed in their jar paths either
						val warningCases = notChanged.filter { _.jarPath.exists { _.changesSize } }
						if (warningCases.nonEmpty) {
							println(s"Following modules were not listed as changed but had their jar files updated anyway: ${
								warningCases.map { _.module.name }.mkString(", ") }")
							println("It is possible that some changes haven't been documented in these modules")
							if (!askBoolean(". Would you like to continue anyway?"))
								System.exit(0)
						}
						
						// Lists status and makes sure user wants to continue
						if (notChanged.nonEmpty) {
							println("Following modules were NOT changed:")
							notChanged.foreach { exp => println(s"- ${exp.module.name} ${exp.version}") }
						}
						println("Following modules were updated:")
						updated.foreach { update =>
							println(s"- ${update.module.name} ${update.version} (${update.updateType})")
						}
						println()
						
						// Checks whether any modules are missing a summary
						val missingSummaryModules = updated
							.filter { _.changeDocLines.headOption.forall { _.startsWith("#") } }
						if (missingSummaryModules.nonEmpty)
							println(s"The following ${missingSummaryModules.size} modules are missing a summary: ${
								missingSummaryModules.map { _.module.name }.mkString(", ")}")
						
						// Makes sure the user wants to complete the process
						if (askBoolean("Would you like to continue (finalize) the export process?",
							default = true))
						{
							// Checks whether the user would like to fill in the missing summaries (if there are any)
							val addedSummaries: Map[ModuleUpdate, Vector[String]] = {
								// Case: No summaries are missing or the user doesn't want to write any => continues
								if (missingSummaryModules.isEmpty || !askBoolean(s"Do you want to write the ${
									missingSummaryModules.size} summaries now?"))
									Map()
								// Case: User wants to write the missing summaries => collects them one by one
								else
									missingSummaryModules.flatMap { update =>
										// Prints update details before each question
										println(s"${update.module.name} ${update.version} (${update.updateType})")
										update.changeDocLines.foreach(println)
										println()
										println(s"${update.module.name} changes are listed above, please write the summary.")
										val summary = ask("Hint: £-signs will be replaced with line breaks")
										summary.notEmpty.map { summary =>
											update -> summary.split('£').toVector
										}
									}.toMap
							}
							
							// Collects the update files to a directory (user may choose)
							val defaultDirectory: Path = s"${projectDirectory.fileName}/$Today"
							val targetDirectory = ask(s"Please specify the directory where data should be collected (default = $defaultDirectory)")
								.notEmpty.map[Path] { s => s }.getOrElse(defaultDirectory)
							
							CollectUpdate(targetDirectory, updated, notChanged, addedSummaries) match {
								case Success(_) =>
									println("Update collected successfully")
									targetDirectory.openInDesktop()
									// "Closes" the export process (if user wants to)
									// NB: This process can't be cancelled if the user wrote summaries
									// (because data would otherwise be lost)
									if (addedSummaries.nonEmpty || askBoolean(
										"Do you want the change list documents edited with today as the latest release date (replacing 'dev' status)?",
										default = true))
									{
										val closeResults = updated.map { update =>
											update -> CloseChangeDocument(update.module,
												addedSummaries.getOrElse(update, Vector()))
										}
										val failureResults = closeResults
											.flatMap { case (update, result) => result.failure.map { update -> _ } }
										if (failureResults.nonEmpty) {
											failureResults.head._2.printStackTrace()
											println(s"Failed to update change list documents in following modules: [${
												failureResults.map { _._1.module.name }
													.mkString(", ")}] (see above error for details)")
										}
									}
									
									println("Update process complete. Thanks for using Maverick!")
								
								case Failure(error) =>
									error.printStackTrace()
									println(s"Update process failed (see more details above): ${error.getMessage}")
							}
						}
						else
							println("Okay. Please run this program again when you're ready to perform the export.")
					}
				case TryCatch.Failure(error) =>
					error.printStackTrace()
					println(s"An error occurred while checking for updates (more details above): ${error.getMessage}")
			}
		case Failure(error) =>
			error.printStackTrace()
			println(s"Failed to scan modules due to error described above (${error.getMessage})")
	}
}
