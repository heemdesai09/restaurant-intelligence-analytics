package restaurant

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.{
  Aggregates,
  Accumulators,
  Filters,
  Indexes,
  IndexOptions,
  Sorts,
  Projections
}
import org.bson.Document
import org.bson.conversions.Bson

import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._

/**
 * Service dedicated to MongoDB Aggregation Pipelines and Index Management.
 * Implements real aggregation pipelines using official MongoDB aggregation stages ($match, $group, $sort, $unwind, $limit).
 */
class AnalyticsService(collection: MongoCollection[Document]) {

  /**
   * Aggregation 1: Restaurants by Cuisine
   * Pipeline:
   *   [
   *     { $match: { cuisine: { $ne: null, $ne: "" } } },
   *     { $group: { _id: "$cuisine", count: { $sum: 1 } } },
   *     { $sort: { count: -1 } },
   *     { $limit: limit }
   *   ]
   */
  def getRestaurantsByCuisine(limit: Int = 10): Either[String, List[CuisineStat]] = {
    Try {
      val pipeline = List(
        Aggregates.`match`(
          Filters.and(
            Filters.ne("cuisine", null),
            Filters.ne("cuisine", "")
          )
        ),
        Aggregates.group("$cuisine", Accumulators.sum("count", 1)),
        Aggregates.sort(Sorts.descending("count")),
        Aggregates.limit(limit)
      )

      collection.aggregate(pipeline.asJava)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .map { doc =>
          val cuisine = Option(doc.getString("_id")).getOrElse("Other")
          val count = Option(doc.getInteger("count")).map(_.toLong).getOrElse(0L)
          CuisineStat(cuisine, count)
        }
        .toList
    } match {
      case Success(stats) => Right(stats)
      case Failure(ex) => Left(s"Failed to aggregate restaurants by cuisine: ${ex.getMessage}")
    }
  }

  /**
   * Aggregation 2: Restaurants by Borough
   * Pipeline:
   *   [
   *     { $match: { borough: { $ne: null, $ne: "", $ne: "Missing" } } },
   *     { $group: { _id: "$borough", count: { $sum: 1 } } },
   *     { $sort: { count: -1 } }
   *   ]
   */
  def getRestaurantsByBorough(): Either[String, List[BoroughStat]] = {
    Try {
      val pipeline = List(
        Aggregates.`match`(
          Filters.and(
            Filters.ne("borough", null),
            Filters.ne("borough", ""),
            Filters.ne("borough", "Missing")
          )
        ),
        Aggregates.group("$borough", Accumulators.sum("count", 1)),
        Aggregates.sort(Sorts.descending("count"))
      )

      collection.aggregate(pipeline.asJava)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .map { doc =>
          val borough = Option(doc.getString("_id")).getOrElse("Unknown")
          val count = Option(doc.getInteger("count")).map(_.toLong).getOrElse(0L)
          BoroughStat(borough, count)
        }
        .toList
    } match {
      case Success(stats) => Right(stats)
      case Failure(ex) => Left(s"Failed to aggregate restaurants by borough: ${ex.getMessage}")
    }
  }

  /**
   * Aggregation 3: Average Inspection Score by Cuisine
   * Pipeline:
   *   [
   *     { $unwind: "$grades" },
   *     { $match: { "grades.score": { $ne: null, $gte: 0 }, cuisine: { $ne: null, $ne: "" } } },
   *     { $group: { _id: "$cuisine", avgScore: { $avg: "$grades.score" }, count: { $sum: 1 } } },
   *     { $match: { count: { $gte: 20 } } },
   *     { $sort: { avgScore: -1 } },
   *     { $limit: limit }
   *   ]
   */
  def getAverageScoreByCuisine(limit: Int = 10): Either[String, List[AvgScoreStat]] = {
    Try {
      val pipeline = List(
        Aggregates.unwind("$grades"),
        Aggregates.`match`(
          Filters.and(
            Filters.ne("grades.score", null),
            Filters.gte("grades.score", 0),
            Filters.ne("cuisine", null),
            Filters.ne("cuisine", "")
          )
        ),
        Aggregates.group(
          "$cuisine",
          Accumulators.avg("avgScore", "$grades.score"),
          Accumulators.sum("count", 1)
        ),
        Aggregates.`match`(Filters.gte("count", 20)),
        Aggregates.sort(Sorts.descending("avgScore")),
        Aggregates.limit(limit)
      )

      collection.aggregate(pipeline.asJava)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .map { doc =>
          val cuisine = Option(doc.getString("_id")).getOrElse("Unknown")
          val rawAvg = Option(doc.getDouble("avgScore")).map(_.doubleValue()).getOrElse(0.0)
          val rounded = math.round(rawAvg * 100.0) / 100.0
          val count = Option(doc.getInteger("count")).map(_.toLong).getOrElse(0L)
          AvgScoreStat(cuisine, rounded, count)
        }
        .toList
    } match {
      case Success(stats) => Right(stats)
      case Failure(ex) => Left(s"Failed to aggregate average score by cuisine: ${ex.getMessage}")
    }
  }

  /**
   * Aggregation 4: Top Rated Restaurants
   * Pipeline:
   *   [
   *     { $unwind: "$grades" },
   *     { $match: { "grades.score": { $ne: null, $gte: 0 } } },
   *     { $group: {
   *         _id: "$restaurant_id",
   *         name: { $first: "$name" },
   *         cuisine: { $first: "$cuisine" },
   *         borough: { $first: "$borough" },
   *         avgScore: { $avg: "$grades.score" },
   *         reviewCount: { $sum: 1 }
   *       }
   *     },
   *     { $match: { reviewCount: { $gte: 2 } } },
   *     { $sort: { avgScore: -1 } },
   *     { $limit: limit }
   *   ]
   */
  def getTopRatedRestaurants(limit: Int = 10): Either[String, List[TopRestaurantStat]] = {
    Try {
      val pipeline = List(
        Aggregates.unwind("$grades"),
        Aggregates.`match`(
          Filters.and(
            Filters.ne("grades.score", null),
            Filters.gte("grades.score", 0)
          )
        ),
        Aggregates.group(
          "$restaurant_id",
          Accumulators.first("name", "$name"),
          Accumulators.first("cuisine", "$cuisine"),
          Accumulators.first("borough", "$borough"),
          Accumulators.avg("avgScore", "$grades.score"),
          Accumulators.sum("reviewCount", 1)
        ),
        Aggregates.`match`(Filters.gte("reviewCount", 2)),
        Aggregates.sort(Sorts.descending("avgScore")),
        Aggregates.limit(limit)
      )

      collection.aggregate(pipeline.asJava)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .map { doc =>
          val name = Option(doc.getString("name")).getOrElse("Unnamed")
          val cuisine = Option(doc.getString("cuisine")).getOrElse("Other")
          val borough = Option(doc.getString("borough")).getOrElse("Missing")
          val rawAvg = Option(doc.getDouble("avgScore")).map(_.doubleValue()).getOrElse(0.0)
          val rounded = math.round(rawAvg * 100.0) / 100.0
          val count = Option(doc.getInteger("reviewCount")).map(_.toLong).getOrElse(0L)
          TopRestaurantStat(name, cuisine, borough, rounded, count)
        }
        .toList
    } match {
      case Success(stats) => Right(stats)
      case Failure(ex) => Left(s"Failed to aggregate top-rated restaurants: ${ex.getMessage}")
    }
  }

  /**
   * Aggregation 5: Grade Distribution
   * Pipeline:
   *   [
   *     { $unwind: "$grades" },
   *     { $match: { "grades.grade": { $in: ["A", "B", "C", "P", "Z", "Not Yet Graded"] } } },
   *     { $group: { _id: "$grades.grade", count: { $sum: 1 } } },
   *     { $sort: { count: -1 } }
   *   ]
   */
  def getGradeDistribution(): Either[String, List[GradeStat]] = {
    Try {
      val validGrades = List("A", "B", "C", "P", "Z", "Not Yet Graded").asJava
      val pipeline = List(
        Aggregates.unwind("$grades"),
        Aggregates.`match`(Filters.in("grades.grade", validGrades)),
        Aggregates.group("$grades.grade", Accumulators.sum("count", 1)),
        Aggregates.sort(Sorts.descending("count"))
      )

      collection.aggregate(pipeline.asJava)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .map { doc =>
          val grade = Option(doc.getString("_id")).getOrElse("Unknown")
          val count = Option(doc.getInteger("count")).map(_.toLong).getOrElse(0L)
          GradeStat(grade, count)
        }
        .toList
    } match {
      case Success(stats) => Right(stats)
      case Failure(ex) => Left(s"Failed to aggregate grade distribution: ${ex.getMessage}")
    }
  }

  /**
   * Aggregates key system metrics for the Overview section.
   */
  def getSystemOverview(): Either[String, SystemOverview] = {
    Try {
      val totalRestaurants = collection.countDocuments()
      val totalCuisines = collection.distinct("cuisine", classOf[String]).into(new java.util.ArrayList[String]()).size().toLong
      val totalBoroughs = collection.distinct("borough", classOf[String]).into(new java.util.ArrayList[String]()).asScala.filter(_ != "Missing").size.toLong

      // Calculate global average score via aggregation
      val avgPipeline = List(
        Aggregates.unwind("$grades"),
        Aggregates.`match`(Filters.gte("grades.score", 0)),
        Aggregates.group(null, Accumulators.avg("globalAvg", "$grades.score"))
      )
      val avgDoc = collection.aggregate(avgPipeline.asJava).first()
      val globalAvg = Option(avgDoc).flatMap(d => Option(d.getDouble("globalAvg"))).map(_.doubleValue()).getOrElse(0.0)
      val roundedAvg = math.round(globalAvg * 100.0) / 100.0

      SystemOverview(totalRestaurants, totalCuisines, totalBoroughs, roundedAvg)
    } match {
      case Success(overview) => Right(overview)
      case Failure(ex) => Left(s"Failed to compute system overview: ${ex.getMessage}")
    }
  }

  /**
   * Ensures the 3 required assignment indexes:
   * 1. name (Single field index)
   * 2. cuisine (Single field index)
   * 3. borough + cuisine (Compound index)
   */
  def ensureIndexes(): Either[String, List[String]] = {
    Try {
      val idx1 = collection.createIndex(Indexes.ascending("name"))
      val idx2 = collection.createIndex(Indexes.ascending("cuisine"))
      val idx3 = collection.createIndex(
        Indexes.compoundIndex(
          Indexes.ascending("borough"),
          Indexes.ascending("cuisine")
        )
      )
      List(idx1, idx2, idx3)
    } match {
      case Success(names) => Right(names)
      case Failure(ex) => Left(s"Failed to create indexes: ${ex.getMessage}")
    }
  }

  /**
   * Lists all existing indexes on the collection.
   */
  def listIndexes(): Either[String, List[IndexInfo]] = {
    Try {
      collection.listIndexes()
        .into(new java.util.ArrayList[Document]())
        .asScala
        .map { doc =>
          val name = Option(doc.getString("name")).getOrElse("unnamed")
          val keyDoc = Option(doc.get("key", classOf[Document])).map(_.toJson()).getOrElse("{}")
          val unique = Option(doc.getBoolean("unique")).map(_.booleanValue()).getOrElse(false)
          IndexInfo(name, keyDoc, unique)
        }
        .toList
    } match {
      case Success(indexes) => Right(indexes)
      case Failure(ex) => Left(s"Failed to list indexes: ${ex.getMessage}")
    }
  }
}
