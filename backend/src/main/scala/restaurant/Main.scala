package restaurant

import scala.io.StdIn
import scala.util.{Try, Success, Failure}

/**
 * Main application entry point for the Restaurant Intelligence & Analytics System.
 * Supports both interactive Menu-Driven CLI and Embedded Web Server modes.
 */
object Main {

  def main(args: Array[String]): Unit = {
    println("=" * 60)
    println("  Restaurant Intelligence & Analytics System")
    println("  Advanced Big Data Analytics (ABDA) - Scala + MongoDB Atlas")
    println("=" * 60)

    // 1. Check MongoDB Atlas connection
    println("\n[1/3] Connecting to MongoDB Atlas...")
    val collectionRes = MongoConfig.getCollection()

    collectionRes match {
      case Left(err) =>
        println(s"\n[Error] Database Connection Error:\n$err\n")
        println("Please ensure MONGODB_URI is exported or placed in .env file.")
        println("Example: MONGODB_URI=\"mongodb+srv://<user>:<password>@cluster0.mongodb.net/?retryWrites=true&w=majority\"")
        sys.exit(1)

      case Right(collection) =>
        MongoConfig.testConnection() match {
          case Right(msg) => println(s"[Success] $msg")
          case Left(err) =>
            println(s"[Warning] MongoDB connection test ping returned: $err")
            println("Proceeding with caution...")
        }

        // Initialize repository and service layers
        val repository: RestaurantRepository = new MongoRestaurantRepository(collection)
        val restaurantService = new RestaurantService(repository)
        val analyticsService = new AnalyticsService(collection)

        // Ensure assignment indexes on startup
        println("[2/3] Verifying and ensuring required MongoDB indexes...")
        analyticsService.ensureIndexes() match {
          case Right(idxs) => println(s"[Success] Indexes ready: ${idxs.mkString(", ")}")
          case Left(err) => println(s"[Warning] Index setup notice: $err")
        }

        val port = sys.env.get("PORT")
          .orElse(MongoConfig.loadEnv().get("PORT"))
          .flatMap(p => Try(p.toInt).toOption)
          .getOrElse(8080)
        val server = new WebServer(port, restaurantService, analyticsService)

        // Determine execution mode from args or run both
        val mode = args.headOption.map(_.toLowerCase).getOrElse("both")

        mode match {
          case "web" =>
            println(s"[3/3] Starting Web Server mode on port $port...")
            server.start()
            println(s"\nWeb Application is live at: http://localhost:$port")
            println("Press Ctrl+C to terminate.")
            // Keep process alive
            Thread.currentThread().join()

          case "cli" =>
            println("[3/3] Starting CLI Menu mode...")
            runCliMenu(restaurantService, analyticsService)
            MongoConfig.close()
            println("\nExiting application. Goodbye!")

          case _ =>
            // Default "both": start web server in background and run CLI menu
            println(s"[3/3] Starting Web Server on http://localhost:$port and launching CLI Menu...")
            server.start()
            println(s"-> Web interface accessible at: http://localhost:$port\n")
            runCliMenu(restaurantService, analyticsService)
            server.stop()
            MongoConfig.close()
            println("\nExiting application. Goodbye!")
        }
    }
  }

  private def readLineSafe(): Option[String] = Option(StdIn.readLine()).map(_.trim)

  /**
   * Interactive, menu-driven CLI interface satisfying assignment specifications.
   */
  def runCliMenu(
      restaurantService: RestaurantService,
      analyticsService: AnalyticsService
  ): Unit = {
    var running = true

    while (running) {
      printMainMenu()
      print("Enter your choice (1-7): ")
      readLineSafe() match {
        case None =>
          println("\nEOF received. Terminating CLI session...")
          running = false
        case Some("1") => handleAddRestaurant(restaurantService)
        case Some("2") => handleSearchRestaurants(restaurantService)
        case Some("3") => handleUpdateRestaurant(restaurantService)
        case Some("4") => handleDeleteRestaurant(restaurantService)
        case Some("5") => handleAnalyticsMenu(analyticsService)
        case Some("6") => handleIndexInformation(analyticsService)
        case Some("7") =>
          println("\nTerminating CLI session...")
          running = false
        case Some("") =>
          // Empty input, loop
        case Some(other) =>
          println(s"\n[Error] Invalid option '$other'. Please choose a number between 1 and 7.")
      }

      if (running) {
        println("\nPress Enter to return to main menu...")
        if (readLineSafe().isEmpty) running = false
      }
    }
  }

  private def printMainMenu(): Unit = {
    println("\n" + "=" * 50)
    println("        RESTAURANT INTELLIGENCE & ANALYTICS")
    println("=" * 50)
    println(" 1. Add Restaurant")
    println(" 2. Search / View Restaurants")
    println(" 3. Update Restaurant")
    println(" 4. Delete Restaurant")
    println(" 5. Restaurant Analytics (Aggregations)")
    println(" 6. Index Information")
    println(" 7. Exit")
    println("=" * 50)
  }

  private def handleAddRestaurant(service: RestaurantService): Unit = {
    println("\n--- [1] Add New Restaurant ---")
    print("Restaurant Name: ")
    val name = StdIn.readLine().trim

    print("Cuisine (e.g. American, Italian, Indian, Bakery): ")
    val cuisine = StdIn.readLine().trim

    print("Borough (e.g. Manhattan, Brooklyn, Queens, Bronx, Staten Island): ")
    val borough = StdIn.readLine().trim

    print("Building / House No: ")
    val building = StdIn.readLine().trim

    print("Street: ")
    val street = StdIn.readLine().trim

    print("ZIP Code: ")
    val zipcode = StdIn.readLine().trim

    print("Inspection Score (e.g. 10, or leave blank): ")
    val scoreInput = StdIn.readLine().trim
    val scoreOpt = if (scoreInput.nonEmpty) Try(scoreInput.toInt).toOption else None

    print("Grade (e.g. A, B, C, or leave blank): ")
    val gradeInput = StdIn.readLine().trim
    val gradeOpt = if (gradeInput.nonEmpty) Some(gradeInput) else None

    print("Custom Restaurant ID (leave blank to auto-generate): ")
    val customIdInput = StdIn.readLine().trim
    val customId = if (customIdInput.nonEmpty) Some(customIdInput) else None

    service.addRestaurant(name, cuisine, borough, building, street, zipcode, scoreOpt, gradeOpt, customId) match {
      case Right(r) =>
        println(s"\n[Success] Restaurant successfully added!")
        println(s"  MongoDB ID    : ${r.id.getOrElse("N/A")}")
        println(s"  Restaurant ID : ${r.restaurantId}")
        println(s"  Name          : ${r.name}")
        println(s"  Cuisine       : ${r.cuisine}")
        println(s"  Borough       : ${r.borough}")
        println(s"  Address       : ${r.address.building} ${r.address.street}, ${r.address.zipcode}")
        println(s"  Grades        : ${r.grades.map(g => s"${g.grade} (Score: ${g.score.getOrElse(0)})").mkString(", ")}")
      case Left(err) =>
        println(s"\n[Error] $err")
    }
  }

  private def handleSearchRestaurants(service: RestaurantService): Unit = {
    println("\n--- [2] Search / View Restaurants ---")
    println("(Leave any filter blank to skip it)")

    print("Filter by Name contains: ")
    val name = Option(StdIn.readLine().trim).filter(_.nonEmpty)

    print("Filter by Cuisine: ")
    val cuisine = Option(StdIn.readLine().trim).filter(_.nonEmpty)

    print("Filter by Borough: ")
    val borough = Option(StdIn.readLine().trim).filter(_.nonEmpty)

    print("Filter by ZIP Code: ")
    val zip = Option(StdIn.readLine().trim).filter(_.nonEmpty)

    print("Filter by Minimum Score (e.g. 10): ")
    val scoreStr = StdIn.readLine().trim
    val minScore = if (scoreStr.nonEmpty) Try(scoreStr.toInt).toOption else None

    val filter = SearchFilter(name, cuisine, borough, zip, minScore)

    service.searchRestaurants(filter, limit = 20) match {
      case Right(list) =>
        if (list.isEmpty) {
          println("\n[Result] No restaurants matched the given filter criteria.")
        } else {
          println(s"\nFound ${list.size} restaurant(s) (displaying up to 20):")
          println("-" * 95)
          printf("%-12s | %-28s | %-14s | %-12s | %-8s | %-6s\n", "ID", "Name", "Cuisine", "Borough", "Zipcode", "Score")
          println("-" * 95)
          list.foreach { r =>
            val latestScore = r.grades.headOption.flatMap(_.score).map(_.toString).getOrElse("-")
            val truncatedName = if (r.name.length > 27) r.name.take(24) + "..." else r.name
            val truncatedCuisine = if (r.cuisine.length > 13) r.cuisine.take(11) + ".." else r.cuisine
            printf(
              "%-12s | %-28s | %-14s | %-12s | %-8s | %-6s\n",
              r.restaurantId.take(12),
              truncatedName,
              truncatedCuisine,
              r.borough,
              r.address.zipcode,
              latestScore
            )
          }
          println("-" * 95)
        }
      case Left(err) =>
        println(s"\n[Error] Search failed: $err")
    }
  }

  private def handleUpdateRestaurant(service: RestaurantService): Unit = {
    println("\n--- [3] Update Restaurant ---")
    print("Enter Restaurant ID or MongoDB ObjectId to update: ")
    val id = StdIn.readLine().trim

    service.getRestaurant(id) match {
      case Right(Some(existing)) =>
        println(s"\nFound Restaurant: '${existing.name}' (${existing.restaurantId})")
        println("(Press Enter without typing to keep existing value)")

        print(s"New Name [${existing.name}]: ")
        val nameIn = StdIn.readLine().trim
        val newName = if (nameIn.nonEmpty) nameIn else existing.name

        print(s"New Cuisine [${existing.cuisine}]: ")
        val cuisineIn = StdIn.readLine().trim
        val newCuisine = if (cuisineIn.nonEmpty) cuisineIn else existing.cuisine

        print(s"New Borough [${existing.borough}]: ")
        val boroughIn = StdIn.readLine().trim
        val newBorough = if (boroughIn.nonEmpty) boroughIn else existing.borough

        print(s"New Building [${existing.address.building}]: ")
        val bldIn = StdIn.readLine().trim
        val newBld = if (bldIn.nonEmpty) bldIn else existing.address.building

        print(s"New Street [${existing.address.street}]: ")
        val strIn = StdIn.readLine().trim
        val newStr = if (strIn.nonEmpty) strIn else existing.address.street

        print(s"New ZIP Code [${existing.address.zipcode}]: ")
        val zipIn = StdIn.readLine().trim
        val newZip = if (zipIn.nonEmpty) zipIn else existing.address.zipcode

        val updatedRecord = existing.copy(
          name = newName,
          cuisine = newCuisine,
          borough = newBorough,
          address = existing.address.copy(
            building = newBld,
            street = newStr,
            zipcode = newZip
          )
        )

        service.updateRestaurant(id, updatedRecord) match {
          case Right(true) => println(s"\n[Success] Restaurant '$id' updated successfully!")
          case Right(false) => println(s"\n[Error] Update failed: record not found.")
          case Left(err) => println(s"\n[Error] $err")
        }

      case Right(None) =>
        println(s"\n[Error] No restaurant found with ID '$id'.")
      case Left(err) =>
        println(s"\n[Error] Failed to lookup restaurant: $err")
    }
  }

  private def handleDeleteRestaurant(service: RestaurantService): Unit = {
    println("\n--- [4] Delete Restaurant ---")
    print("Enter Restaurant ID or MongoDB ObjectId to delete: ")
    val id = StdIn.readLine().trim

    service.getRestaurant(id) match {
      case Right(Some(r)) =>
        print(s"Are you sure you want to delete '${r.name}' (ID: ${r.restaurantId})? (y/n): ")
        val confirm = StdIn.readLine().trim.toLowerCase
        if (confirm == "y" || confirm == "yes") {
          service.deleteRestaurant(id) match {
            case Right(true) => println(s"\n[Success] Restaurant '${r.name}' (ID: $id) deleted successfully.")
            case Right(false) => println(s"\n[Error] Delete operation reported no matching document.")
            case Left(err) => println(s"\n[Error] Delete failed: $err")
          }
        } else {
          println("\nDelete cancelled by user.")
        }
      case Right(None) =>
        println(s"\n[Error] No restaurant found with ID '$id'.")
      case Left(err) =>
        println(s"\n[Error] $err")
    }
  }

  private def handleAnalyticsMenu(analyticsService: AnalyticsService): Unit = {
    var inAnalytics = true
    while (inAnalytics) {
      println("\n" + "-" * 45)
      println("         RESTAURANT ANALYTICS (AGGREGATIONS)")
      println("-" * 45)
      println(" 1. Restaurants by Cuisine ($group, $sort, $limit)")
      println(" 2. Restaurants by Borough ($group, $sort)")
      println(" 3. Average Score by Cuisine ($unwind, $group, $sort)")
      println(" 4. Top-Rated Restaurants ($unwind, $group, $sort)")
      println(" 5. Grade Distribution ($unwind, $match, $group)")
      println(" 6. Back to Main Menu")
      println("-" * 45)
      print("Select analytics pipeline (1-6): ")

      val choice = readLineSafe().getOrElse("6")
      choice match {
        case "1" =>
          println("\n=== 1. Restaurants by Cuisine (Top 10) ===")
          analyticsService.getRestaurantsByCuisine(10) match {
            case Right(list) =>
              printf("%-30s | %s\n", "Cuisine", "Restaurant Count")
              println("-" * 45)
              list.foreach(c => printf("%-30s | %d\n", c.cuisine, c.count))
            case Left(err) => println(s"[Error] $err")
          }

        case "2" =>
          println("\n=== 2. Restaurants by Borough ===")
          analyticsService.getRestaurantsByBorough() match {
            case Right(list) =>
              printf("%-20s | %s\n", "Borough", "Restaurant Count")
              println("-" * 35)
              list.foreach(b => printf("%-20s | %d\n", b.borough, b.count))
            case Left(err) => println(s"[Error] $err")
          }

        case "3" =>
          println("\n=== 3. Average Score by Cuisine (Higher is critical) ===")
          analyticsService.getAverageScoreByCuisine(10) match {
            case Right(list) =>
              printf("%-25s | %-12s | %s\n", "Cuisine", "Avg Score", "Sample Count")
              println("-" * 52)
              list.foreach(s => printf("%-25s | %-12.2f | %d\n", s.cuisine, s.avgScore, s.count))
            case Left(err) => println(s"[Error] $err")
          }

        case "4" =>
          println("\n=== 4. Top-Rated Restaurants ===")
          analyticsService.getTopRatedRestaurants(10) match {
            case Right(list) =>
              printf("%-28s | %-16s | %-12s | %-8s\n", "Name", "Cuisine", "Borough", "Score")
              println("-" * 72)
              list.foreach(r => printf("%-28s | %-16s | %-12s | %.2f\n", r.name.take(27), r.cuisine.take(15), r.borough, r.avgScore))
            case Left(err) => println(s"[Error] $err")
          }

        case "5" =>
          println("\n=== 5. Inspection Grade Distribution ===")
          analyticsService.getGradeDistribution() match {
            case Right(list) =>
              printf("%-15s | %s\n", "Grade", "Count")
              println("-" * 30)
              list.foreach(g => printf("%-15s | %d\n", g.grade, g.count))
            case Left(err) => println(s"[Error] $err")
          }

        case "6" =>
          inAnalytics = false

        case _ =>
          println("[Error] Invalid selection. Choose 1-6.")
      }

      if (inAnalytics) {
        println("\nPress Enter to continue analytics...")
        if (readLineSafe().isEmpty) inAnalytics = false
      }
    }
  }

  private def handleIndexInformation(analyticsService: AnalyticsService): Unit = {
    println("\n--- [6] MongoDB Index Information ---")
    analyticsService.listIndexes() match {
      case Right(idxs) =>
        println(s"Found ${idxs.size} index(es) on collection '${MongoConfig.CollectionName}':")
        println("-" * 75)
        printf("%-30s | %-32s | %-8s\n", "Index Name", "Keys", "Unique")
        println("-" * 75)
        idxs.foreach(i => printf("%-30s | %-32s | %-8s\n", i.name, i.keys, if (i.isUnique) "YES" else "NO"))
        println("-" * 75)
      case Left(err) =>
        println(s"[Error] Failed to fetch indexes: $err")
    }

    println("\nRequired Assignment Indexes:")
    println(" 1. name               (Single-field)")
    println(" 2. cuisine            (Single-field)")
    println(" 3. borough + cuisine  (Compound index)")

    print("\nDo you want to re-ensure these indexes now? (y/n): ")
    val ans = StdIn.readLine().trim.toLowerCase
    if (ans == "y" || ans == "yes") {
      analyticsService.ensureIndexes() match {
        case Right(created) => println(s"[Success] Indexes verified: ${created.mkString(", ")}")
        case Left(err) => println(s"[Error] Index verification failed: $err")
      }
    }
  }
}
