package maverick.controller

import maverick.model.Module
import utopia.flow.parse.Regex
import utopia.flow.time.Today
import utopia.flow.util.FileExtensions._
import utopia.flow.util.Version

import java.time.format.DateTimeFormatter

/**
 * Used for "closing" the development version in module change documents
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
object CloseChangeDocument
{
	// ATTRIBUTES   ----------------------------
	
	private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")
	
	private val developmentVersionLineRegex = Regex.escape('#') + Regex.any + Version.regex +
		Regex.any + Regex("dev") + Regex.any
	
	
	// OTHER    --------------------------------
	
	/**
	 * Closes the change list document of the specified module
	 * @param module Targeted module
	 * @return Path to the edited change list document. Failure if change list editing failed.
	 */
	def apply(module: Module) =
		module.changeListPath.edit { editor =>
			// Finds the development version line and overwrites it
			editor.mapNextWhere(developmentVersionLineRegex.apply) { original =>
				// Replaces the part after the version number with a release date
				val versionEndIndex = Version.regex.endIndexIteratorIn(original).next()
				original.take(versionEndIndex) + " - " + Today.toLocalDate.format(dateFormat)
			}
		}
}
