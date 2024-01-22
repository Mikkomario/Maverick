package maverick.model.enumeration

import utopia.flow.operator.ordering.SelfComparable
import utopia.flow.util.Version

/**
 * An enumeration for different levels / types of update
 * @author Mikko Hilpinen
 * @since 4.10.2021, v0.1
 */
sealed trait UpdateType extends SelfComparable[UpdateType]
{
	def changedIndex: Int
	
	override def self = this
	
	override def compareTo(o: UpdateType) = o.changedIndex - changedIndex
}

object UpdateType
{
	// ATTRIBUTES   --------------------------
	
	/**
	 * All update type values / options
	 */
	val values = Vector[UpdateType](Overhaul, Breaking, Minor, Other)
	
	
	// OTHER    ------------------------------
	
	/**
	 * @param version A version number
	 * @return The type of update that caused that version
	 */
	def from(version: Version) =
	{
		if (version.hasSuffix)
			Other
		else
			values.find { _.changedIndex == version.numbers.size - 1 }.getOrElse(Other)
	}
	
	
	// NESTED   ------------------------------
	
	/**
	 * A very large update where the major (first) version number changes
	 */
	case object Overhaul extends UpdateType
	{
		override def changedIndex = 0
	}
	
	/**
	 * An update that causes breaking changes (second version number changes)
	 */
	case object Breaking extends UpdateType
	{
		override def changedIndex = 1
	}
	
	/**
	 * An update that doesn't cause breaking changes (third version number changes)
	 */
	case object Minor extends UpdateType
	{
		override def changedIndex = 2
	}
	
	/**
	 * A different kind of update, for example an alpha update (suffix changes)
	 */
	case object Other extends UpdateType
	{
		override def changedIndex = 3
	}
}
