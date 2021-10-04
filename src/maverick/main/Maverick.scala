package maverick.main

import maverick.controller.{CheckForUpdates, CloseChangeDocument, CollectUpdate, FindModules}
import utopia.flow.generic.DataType
import utopia.flow.time.Today
import utopia.flow.util.CollectionExtensions._
import utopia.flow.util.FileExtensions._
import utopia.flow.util.StringExtensions._

import java.nio.file.Path
import scala.io.StdIn
import scala.util.{Failure, Success}

/**
 * The command line application representing this project
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
object Maverick extends App
{
	DataType.setup()
	
	// Introduction
	println("Welcome to Maverick, a program for project exports")
	println("You can close the program at any time by writing 'exit' as input (without quotations)")
	println()
	
	def ask(question: String) =
	{
		println(question)
		val result = StdIn.readLine()
		if (result.toLowerCase == "exit")
			System.exit(0)
		println()
		result
	}
	def askBoolean(question: String, default: Boolean = false) =
	{
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
			ask(s"Current directory: ${".".toAbsolutePath}")
		}
	if (!projectDirectory.isDirectory)
	{
		println(s"The project path you specified (${
			projectDirectory.toAbsolutePath}) is not an existing directory. Please try again.")
		System.exit(0)
	}
	
	// Finds the modules
	FindModules(projectDirectory) match
	{
		case Success((modules, missing)) =>
			// Checks whether there are incomplete modules and whether export should be terminated
			if (missing.nonEmpty)
			{
				if (modules.isEmpty)
				{
					println(s"It looks like all the ${missing.size} modules were missing an artifact directory.")
					println("Please build project artifacts and try again.")
					System.exit(0)
				}
				else
				{
					println(s"No artifact directory was found for the following modules: ${missing.mkString(", ")}")
					if (!askBoolean("Do you still want to continue the export process?"))
						System.exit(0)
				}
			}
			// Makes sure some modules were found
			else if (modules.isEmpty)
			{
				println("No modules could be found from that directory")
				println("Modules are directories which contain a change list document " +
					"(document must contain the word 'change' and be of type .md)")
				println("Please try again later or with another project")
				System.exit(0)
			}
			
			// Checks for module updates
			println(s"Found ${modules.size} modules: [${modules.map { _.name }.mkString(", ")}]")
			modules.tryMap { CheckForUpdates(_) } match
			{
				case Success(results) =>
					val (notChanged, updated) = results.divided
					if (updated.isEmpty)
						println("It looks like no module was updated (no 'dev' versions found). Please try again.")
					else
					{
						// Makes sure the "static" modules were not changed in their jar paths either
						val warningCases = notChanged.filter { _.jarPath.exists { _.changesSize } }
						if (warningCases.nonEmpty)
						{
							println(s"Following modules were not listed as changed but had their jar files updated anyway: ${
								warningCases.map { _.module.name }.mkString(", ") }")
							println("It is possible that some changes haven't been documented in these modules")
							if (!askBoolean(". Would you like to continue anyway?"))
								System.exit(0)
						}
						
						// Lists status and makes sure user wants to continue
						if (notChanged.nonEmpty)
						{
							println("Following modules were NOT changed:")
							notChanged.foreach { exp => println(s"- ${exp.module.name} ${exp.version}") }
						}
						println("Following modules were updated:")
						updated.foreach { update =>
							println(s"- ${update.module.name} ${update.version} (${update.updateType})")
						}
						println()
						
						// TODO: Should make sure that the modules have a summary line
						if (askBoolean("Would you like to continue (finalize) the export process?",
							default = true))
						{
							// Collects the update files to a directory (user may choose)
							val defaultDirectory: Path = s"${projectDirectory.fileName}/$Today"
							val targetDirectory = ask(s"Please specify the directory where data should be collected (default = $defaultDirectory)")
								.notEmpty.map[Path] { s => s }.getOrElse(defaultDirectory)
							
							CollectUpdate(targetDirectory, updated, notChanged) match
							{
								case Success(_) =>
									println("Update collected successfully")
									// "Closes" the export process (if user wants to)
									if (askBoolean("Do you want the change list documents edited with today as the latest release date (replacing 'dev' status)?",
										default = true))
									{
										val closeResults = updated.map { update =>
											update -> CloseChangeDocument(update.module)
										}
										val failureResults = closeResults
											.flatMap { case (update, result) => result.failure.map { update -> _ } }
										if (failureResults.nonEmpty)
										{
											failureResults.head._2.printStackTrace()
											println(s"Failed to update change list documents in following modules: [${
												failureResults.map { _._1.module.name }
													.mkString(", ")}] (see above error for details)")
										}
									}
									
									println("Update process complete. Thanks for using Maverick!")
									targetDirectory.openInDesktop()
								
								case Failure(error) =>
									error.printStackTrace()
									println(s"Update process failed (see more details above): ${error.getMessage}")
							}
						}
						else
							println("Okay. Please run this program again when you're ready to perform the export.")
					}
				case Failure(error) =>
					error.printStackTrace()
					println(s"An error occurred while checking for updates (more details above): ${error.getMessage}")
			}
		case Failure(error) =>
			error.printStackTrace()
			println(s"Failed to scan modules due to error described above (${error.getMessage})")
	}
}
