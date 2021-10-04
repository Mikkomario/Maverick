package maverick.model

import maverick.model.enumeration.UpdateType
import utopia.flow.util.Extender

/**
 * Represents a module update
 * @author Mikko Hilpinen
 * @since 3.10.2021, v0.1
 * @param newState Module version after update
 * @param changeDocLines Lines describing changes introduced in this update
 */
case class ModuleUpdate(newState: ModuleExport, changeDocLines: Vector[String]) extends Extender[ModuleExport]
{
	// ATTRIBUTES   -----------------------------
	
	/**
	 * How big this update is / type of this update
	 */
	lazy val updateType = UpdateType.from(newState.version)
	
	
	// IMPLEMENTED  -----------------------------
	
	override def wrapped = newState
}
