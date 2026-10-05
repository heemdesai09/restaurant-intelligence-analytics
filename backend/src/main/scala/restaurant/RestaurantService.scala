package restaurant

import scala.util.{Try, Success, Failure}

/**
 * Service layer providing business logic, input validation, and orchestration.
 * Demonstrates separation of concerns, encapsulation, pattern matching, and Scala collection processing.
 */
class RestaurantService(repo: RestaurantRepository) {

  /**
   * Adds a new restaurant with comprehensive validation.
   * Demonstrates validation conditions, pattern matching, and default fallback handling.
   */
  def addRestaurant(
      name: String,
      cuisine: String,
      borough: String,
      building: String,
      street: String,
      zipcode: String,
      scoreOpt: Option[Int] = None,
      gradeOpt: Option[String] = None,
      customRestaurantId: Option[String] = None
  ): Either[String, Restaurant] = {
    // 1. Validation: Name check
    if (name.trim.isEmpty) {
      return Left("Validation Error: Restaurant name cannot be empty.")
    }

    // 2. Validation: Cuisine check
    val sanitizedCuisine = if (cuisine.trim.isEmpty) "Other" else cuisine.trim
    val sanitizedBorough = if (borough.trim.isEmpty) "Missing" else borough.trim

    // 3. Validation: Score range check
    scoreOpt match {
      case Some(score) if score < 0 =>
        return Left(s"Validation Error: Inspection score cannot be negative (got $score).")
      case _ => // Valid score or None
    }

    // 4. Generate unique restaurant_id if not provided
    val rId = customRestaurantId.filter(_.trim.nonEmpty).getOrElse {
      "HEEM_" + System.currentTimeMillis().toString.takeRight(8)
    }

    val gradesList = scoreOpt match {
      case Some(s) =>
        val gradeLetter = gradeOpt.filter(_.trim.nonEmpty).getOrElse {
          // Automatic grade estimation based on NYC health dept standards
          if (s <= 13) "A" else if (s <= 27) "B" else "C"
        }
        List(Grade(date = Some(java.time.LocalDate.now().toString), grade = gradeLetter, score = Some(s)))
      case None =>
        gradeOpt.filter(_.trim.nonEmpty).map(g => Grade(grade = g)).toList
    }

    val newRestaurant = Restaurant(
      restaurantId = rId,
      name = name.trim,
      borough = sanitizedBorough,
      cuisine = sanitizedCuisine,
      address = Address(
        building = building.trim,
        street = street.trim,
        zipcode = zipcode.trim
      ),
      grades = gradesList
    )

    repo.insert(newRestaurant)
  }

  /**
   * Searches restaurants with criteria filtering.
   * Demonstrates Option handling and collection mapping.
   */
  def searchRestaurants(filter: SearchFilter, limit: Int = 50): Either[String, List[Restaurant]] = {
    filter.minScore match {
      case Some(score) if score < 0 =>
        Left("Validation Error: Minimum score filter cannot be negative.")
      case _ =>
        repo.search(filter, limit)
    }
  }

  def getRestaurant(id: String): Either[String, Option[Restaurant]] = {
    if (id.trim.isEmpty) Left("Restaurant ID cannot be empty.")
    else repo.findById(id.trim)
  }

  /**
   * Updates an existing restaurant record with validation.
   */
  def updateRestaurant(id: String, updated: Restaurant): Either[String, Boolean] = {
    if (id.trim.isEmpty) {
      return Left("Validation Error: Restaurant ID is required for update.")
    }
    if (updated.name.trim.isEmpty) {
      return Left("Validation Error: Restaurant name cannot be empty.")
    }

    // Verify record exists before updating
    repo.findById(id.trim) match {
      case Right(Some(_)) =>
        repo.update(id.trim, updated)
      case Right(None) =>
        Left(s"Restaurant not found with ID '$id'.")
      case Left(err) =>
        Left(s"Error checking existing restaurant: $err")
    }
  }

  /**
   * Deletes a restaurant by its ObjectId or restaurant_id.
   */
  def deleteRestaurant(id: String): Either[String, Boolean] = {
    if (id.trim.isEmpty) {
      return Left("Validation Error: Restaurant ID is required for delete.")
    }

    repo.findById(id.trim) match {
      case Right(Some(_)) =>
        repo.delete(id.trim)
      case Right(None) =>
        Left(s"Restaurant not found with ID '$id'.")
      case Left(err) =>
        Left(s"Error checking existing restaurant: $err")
    }
  }

  /**
   * Groups a list of restaurants in-memory by borough.
   * Explicitly showcases Scala's groupBy, map, and collection operations.
   */
  def groupRestaurantsByBorough(restaurants: List[Restaurant]): Map[String, Int] = {
    restaurants
      .groupBy(_.borough)
      .view
      .mapValues(_.size)
      .toMap
  }

  /**
   * Filters restaurants in-memory that have a grade 'A'.
   * Showcases Scala's filter and Option pattern matching.
   */
  def filterGradeARestaurants(restaurants: List[Restaurant]): List[Restaurant] = {
    restaurants.filter { r =>
      r.grades.exists(_.grade.toUpperCase == "A")
    }
  }
}
