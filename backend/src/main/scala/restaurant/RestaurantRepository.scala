package restaurant

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.{Filters, Updates, Sorts}
import org.bson.Document
import org.bson.types.ObjectId
import org.bson.conversions.Bson

import java.util.regex.Pattern
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._

/**
 * Trait defining the Restaurant data access contract.
 * Demonstrates abstraction, traits, and interface segregation in Scala.
 */
trait RestaurantRepository {
  def insert(restaurant: Restaurant): Either[String, Restaurant]
  def findById(id: String): Either[String, Option[Restaurant]]
  def findByRestaurantId(restaurantId: String): Either[String, Option[Restaurant]]
  def search(filter: SearchFilter, limit: Int = 50): Either[String, List[Restaurant]]
  def update(id: String, updated: Restaurant): Either[String, Boolean]
  def delete(id: String): Either[String, Boolean]
  def count(): Either[String, Long]
}

/**
 * MongoDB implementation of RestaurantRepository.
 * Handles Document-to-CaseClass mapping and executes real MongoDB queries.
 */
class MongoRestaurantRepository(collection: MongoCollection[Document]) extends RestaurantRepository {

  /**
   * Helper function to convert a MongoDB Document into a Scala Restaurant case class.
   * Demonstrates Option handling, collection mapping, and safe type conversion.
   */
  private def documentToRestaurant(doc: Document): Restaurant = {
    val idOpt = Option(doc.getObjectId("_id")).map(_.toHexString)
    val restaurantId = Option(doc.getString("restaurant_id")).getOrElse(idOpt.getOrElse("unknown"))
    val name = Option(doc.getString("name")).getOrElse("Unnamed Restaurant")
    val borough = Option(doc.getString("borough")).getOrElse("Missing")
    val cuisine = Option(doc.getString("cuisine")).getOrElse("Other")

    // Extract address subdocument
    val address = Option(doc.get("address", classOf[Document])) match {
      case Some(addrDoc) =>
        val building = Option(addrDoc.getString("building")).getOrElse("")
        val street = Option(addrDoc.getString("street")).getOrElse("")
        val zipcode = Option(addrDoc.getString("zipcode")).getOrElse("")
        val coords = Option(addrDoc.getList("coord", classOf[java.lang.Double]))
          .map(_.asScala.map(_.doubleValue()).toList)
          .getOrElse(Nil)
        Address(building = building, street = street, zipcode = zipcode, coord = coords)
      case None =>
        Address()
    }

    // Extract grades array of subdocuments
    val grades = Option(doc.getList("grades", classOf[Document])) match {
      case Some(gradeDocs) =>
        gradeDocs.asScala.map { gDoc =>
          val dateStr = Option(gDoc.get("date")).map(_.toString)
          val grade = Option(gDoc.getString("grade")).getOrElse("Not Rated")
          val score = Option(gDoc.getInteger("score")).map(_.intValue())
          Grade(date = dateStr, grade = grade, score = score)
        }.toList
      case None =>
        Nil
    }

    Restaurant(
      id = idOpt,
      restaurantId = restaurantId,
      name = name,
      borough = borough,
      cuisine = cuisine,
      address = address,
      grades = grades
    )
  }

  /**
   * Helper function to convert a Scala Restaurant case class into a MongoDB Document.
   */
  private def restaurantToDocument(r: Restaurant): Document = {
    val doc = new Document()
    doc.append("restaurant_id", r.restaurantId)
    doc.append("name", r.name)
    doc.append("borough", r.borough)
    doc.append("cuisine", r.cuisine)

    val addrDoc = new Document()
      .append("building", r.address.building)
      .append("street", r.address.street)
      .append("zipcode", r.address.zipcode)
    if (r.address.coord.nonEmpty) {
      addrDoc.append("coord", r.address.coord.map(Double.box).asJava)
    }
    doc.append("address", addrDoc)

    val gradeDocs = r.grades.map { g =>
      val gd = new Document()
      g.date.foreach(d => gd.append("date", d))
      gd.append("grade", g.grade)
      g.score.foreach(s => gd.append("score", Integer.valueOf(s)))
      gd
    }.asJava
    doc.append("grades", gradeDocs)

    doc
  }

  /**
   * Builds a filter predicate that matches either MongoDB ObjectId or restaurant_id string.
   */
  private def buildIdFilter(id: String): Bson = {
    if (ObjectId.isValid(id)) {
      Filters.or(
        Filters.eq("_id", new ObjectId(id)),
        Filters.eq("restaurant_id", id)
      )
    } else {
      Filters.eq("restaurant_id", id)
    }
  }

  override def insert(restaurant: Restaurant): Either[String, Restaurant] = {
    Try {
      val doc = restaurantToDocument(restaurant)
      collection.insertOne(doc)
      val generatedId = Option(doc.getObjectId("_id")).map(_.toHexString)
      restaurant.copy(id = generatedId)
    } match {
      case Success(saved) => Right(saved)
      case Failure(ex) => Left(s"Failed to insert restaurant: ${ex.getMessage}")
    }
  }

  override def findById(id: String): Either[String, Option[Restaurant]] = {
    Try {
      val doc = collection.find(buildIdFilter(id)).first()
      Option(doc).map(documentToRestaurant)
    } match {
      case Success(res) => Right(res)
      case Failure(ex) => Left(s"Error retrieving restaurant by id '$id': ${ex.getMessage}")
    }
  }

  override def findByRestaurantId(restaurantId: String): Either[String, Option[Restaurant]] = {
    Try {
      val doc = collection.find(Filters.eq("restaurant_id", restaurantId)).first()
      Option(doc).map(documentToRestaurant)
    } match {
      case Success(res) => Right(res)
      case Failure(ex) => Left(s"Error retrieving restaurant_id '$restaurantId': ${ex.getMessage}")
    }
  }

  /**
   * Multi-filter search querying MongoDB directly using Bson Filters.
   * Supports combining: Name, Cuisine, Borough, ZIP code, Minimum Score.
   */
  override def search(filter: SearchFilter, limit: Int = 50): Either[String, List[Restaurant]] = {
    Try {
      var filterClauses = List.empty[Bson]

      // Name regex filter (case-insensitive substring match)
      filter.name.filter(_.trim.nonEmpty).foreach { name =>
        val pattern = "(?i)" + Pattern.quote(name.trim)
        filterClauses = Filters.regex("name", pattern) :: filterClauses
      }

      // Cuisine regex filter
      filter.cuisine.filter(_.trim.nonEmpty).foreach { cuisine =>
        val pattern = "(?i)" + Pattern.quote(cuisine.trim)
        filterClauses = Filters.regex("cuisine", pattern) :: filterClauses
      }

      // Borough regex filter
      filter.borough.filter(_.trim.nonEmpty).foreach { borough =>
        val pattern = "(?i)" + Pattern.quote(borough.trim)
        filterClauses = Filters.regex("borough", pattern) :: filterClauses
      }

      // ZIP code exact match
      filter.zipcode.filter(_.trim.nonEmpty).foreach { zip =>
        filterClauses = Filters.eq("address.zipcode", zip.trim) :: filterClauses
      }

      // Minimum Score filter ($gte on grades.score)
      filter.minScore.foreach { minScore =>
        filterClauses = Filters.gte("grades.score", minScore) :: filterClauses
      }

      // Combine multiple filters using $and
      val queryBson = if (filterClauses.isEmpty) {
        new Document()
      } else if (filterClauses.size == 1) {
        filterClauses.head
      } else {
        Filters.and(filterClauses.asJava)
      }

      val docs = collection.find(queryBson)
        .sort(Sorts.descending("grades.score"))
        .limit(limit)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .toList

      docs.map(documentToRestaurant)
    } match {
      case Success(list) => Right(list)
      case Failure(ex) => Left(s"Search query failed: ${ex.getMessage}")
    }
  }

  override def update(id: String, updated: Restaurant): Either[String, Boolean] = {
    Try {
      val updateDoc = new Document()
        .append("name", updated.name)
        .append("borough", updated.borough)
        .append("cuisine", updated.cuisine)
        .append("address.building", updated.address.building)
        .append("address.street", updated.address.street)
        .append("address.zipcode", updated.address.zipcode)

      if (updated.grades.nonEmpty) {
        val gradeDocs = updated.grades.map { g =>
          val gd = new Document()
          g.date.foreach(d => gd.append("date", d))
          gd.append("grade", g.grade)
          g.score.foreach(s => gd.append("score", Integer.valueOf(s)))
          gd
        }.asJava
        updateDoc.append("grades", gradeDocs)
      }

      val result = collection.updateOne(buildIdFilter(id), new Document("$set", updateDoc))
      result.getMatchedCount > 0
    } match {
      case Success(matched) => Right(matched)
      case Failure(ex) => Left(s"Update failed for '$id': ${ex.getMessage}")
    }
  }

  override def delete(id: String): Either[String, Boolean] = {
    Try {
      val result = collection.deleteOne(buildIdFilter(id))
      result.getDeletedCount > 0
    } match {
      case Success(deleted) => Right(deleted)
      case Failure(ex) => Left(s"Delete failed for '$id': ${ex.getMessage}")
    }
  }

  override def count(): Either[String, Long] = {
    Try(collection.countDocuments()) match {
      case Success(cnt) => Right(cnt)
      case Failure(ex) => Left(s"Failed to count documents: ${ex.getMessage}")
    }
  }
}
