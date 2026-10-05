package restaurant

import upickle.default.{ReadWriter, macroRW, readwriter, writeJs, read}

object CodecHelpers {
  implicit def optionRW[T: ReadWriter]: ReadWriter[Option[T]] =
    readwriter[ujson.Value].bimap[Option[T]](
      opt => opt.map(v => writeJs(v)).getOrElse(ujson.Null),
      {
        case ujson.Null => None
        case ujson.Arr(items) if items.isEmpty => None
        case ujson.Arr(items) => Some(read[T](items.head))
        case other => Some(read[T](other))
      }
    )
}
import CodecHelpers._

// Address case class representing embedded address subdocument in MongoDB
case class Address(
    building: String = "",
    street: String = "",
    zipcode: String = "",
    coord: List[Double] = Nil
)

object Address {
  implicit val rw: ReadWriter[Address] = macroRW
}

// Grade case class representing inspection grades array element
case class Grade(
    date: Option[String] = None,
    grade: String = "Not Rated",
    score: Option[Int] = None
)

object Grade {
  implicit val rw: ReadWriter[Grade] = macroRW
}

// Restaurant case class representing the main document in sample_restaurants.restaurants
case class Restaurant(
    id: Option[String] = None, // MongoDB ObjectId hex string
    restaurantId: String,
    name: String,
    borough: String,
    cuisine: String,
    address: Address,
    grades: List[Grade] = Nil
)

object Restaurant {
  implicit val rw: ReadWriter[Restaurant] = macroRW
}

// Search filter criteria model
case class SearchFilter(
    name: Option[String] = None,
    cuisine: Option[String] = None,
    borough: Option[String] = None,
    zipcode: Option[String] = None,
    minScore: Option[Int] = None
)

object SearchFilter {
  implicit val rw: ReadWriter[SearchFilter] = macroRW
}

// Analytics result models
case class CuisineStat(cuisine: String, count: Long)
object CuisineStat {
  implicit val rw: ReadWriter[CuisineStat] = macroRW
}

case class BoroughStat(borough: String, count: Long)
object BoroughStat {
  implicit val rw: ReadWriter[BoroughStat] = macroRW
}

case class AvgScoreStat(cuisine: String, avgScore: Double, count: Long)
object AvgScoreStat {
  implicit val rw: ReadWriter[AvgScoreStat] = macroRW
}

case class TopRestaurantStat(
    name: String,
    cuisine: String,
    borough: String,
    avgScore: Double,
    reviewCount: Long
)
object TopRestaurantStat {
  implicit val rw: ReadWriter[TopRestaurantStat] = macroRW
}

case class GradeStat(grade: String, count: Long)
object GradeStat {
  implicit val rw: ReadWriter[GradeStat] = macroRW
}

case class IndexInfo(name: String, keys: String, isUnique: Boolean)
object IndexInfo {
  implicit val rw: ReadWriter[IndexInfo] = macroRW
}

case class SystemOverview(
    totalRestaurants: Long,
    totalCuisines: Long,
    totalBoroughs: Long,
    avgScore: Double
)
object SystemOverview {
  implicit val rw: ReadWriter[SystemOverview] = macroRW
}
