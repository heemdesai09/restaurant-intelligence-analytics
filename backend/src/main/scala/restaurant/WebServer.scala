package restaurant

import com.sun.net.httpserver.{HttpServer, HttpHandler, HttpExchange}
import java.net.InetSocketAddress
import java.io.{OutputStream, InputStream, File}
import java.nio.file.{Files, Paths}
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.util.{Try, Success, Failure}
import upickle.default._

/**
 * Embedded HTTP Server using Java standard library HttpServer.
 * Serves both the REST API and the frontend web application with zero external framework overhead.
 */
class WebServer(
    port: Int,
    restaurantService: RestaurantService,
    analyticsService: AnalyticsService
) {
  private var serverOpt: Option[HttpServer] = None

  def start(): Unit = {
    val server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0)
    server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10))

    // Register API handler
    server.createContext("/api", new ApiHandler(restaurantService, analyticsService))

    // Register Static frontend handler
    server.createContext("/", new StaticFileHandler())

    server.start()
    serverOpt = Some(server)
    println(s"[Web Server] Started successfully on http://localhost:$port")
  }

  def stop(): Unit = {
    serverOpt.foreach(_.stop(0))
    serverOpt = None
    println("[Web Server] Stopped.")
  }
}

/**
 * Handles all REST API requests under /api/...
 */
class ApiHandler(
    restaurantService: RestaurantService,
    analyticsService: AnalyticsService
) extends HttpHandler {

  override def handle(exchange: HttpExchange): Unit = {
    val method = exchange.getRequestMethod.toUpperCase
    val uri = exchange.getRequestURI
    val path = uri.getPath
    val queryParams = parseQueryParams(Option(uri.getRawQuery).getOrElse(""))

    // Set CORS headers
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization")

    if (method == "OPTIONS") {
      exchange.sendResponseHeaders(204, -1)
      return
    }

    try {
      (method, path) match {
        // Health check
        case ("GET", "/api/health") =>
          MongoConfig.testConnection() match {
            case Right(msg) => sendJson(exchange, 200, ujson.Obj("status" -> "ok", "message" -> msg))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("status" -> "error", "error" -> err))
          }

        // Overview metrics
        case ("GET", "/api/overview") =>
          analyticsService.getSystemOverview() match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Search / List restaurants
        case ("GET", "/api/restaurants") =>
          val filter = SearchFilter(
            name = queryParams.get("name"),
            cuisine = queryParams.get("cuisine"),
            borough = queryParams.get("borough"),
            zipcode = queryParams.get("zipcode"),
            minScore = queryParams.get("minScore").flatMap(s => Try(s.toInt).toOption)
          )
          val limit = queryParams.get("limit").flatMap(s => Try(s.toInt).toOption).getOrElse(50)
          restaurantService.searchRestaurants(filter, limit) match {
            case Right(list) => sendJson(exchange, 200, write(list))
            case Left(err) => sendJson(exchange, 400, ujson.Obj("error" -> err))
          }

        // Get single restaurant by ID
        case ("GET", p) if p.startsWith("/api/restaurants/") =>
          val id = p.stripPrefix("/api/restaurants/")
          restaurantService.getRestaurant(id) match {
            case Right(Some(r)) => sendJson(exchange, 200, write(r))
            case Right(None) => sendJson(exchange, 404, ujson.Obj("error" -> s"Restaurant '$id' not found"))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Add Restaurant
        case ("POST", "/api/restaurants") =>
          val body = readBody(exchange.getRequestBody)
          Try(ujson.read(body)) match {
            case Success(json) =>
              val name = json.obj.get("name").map(_.str).getOrElse("")
              val cuisine = json.obj.get("cuisine").map(_.str).getOrElse("")
              val borough = json.obj.get("borough").map(_.str).getOrElse("")
              val building = json.obj.get("building").map(_.str).getOrElse("")
              val street = json.obj.get("street").map(_.str).getOrElse("")
              val zipcode = json.obj.get("zipcode").map(_.str).getOrElse("")
              val scoreOpt = json.obj.get("score").flatMap(v => Try(v.num.toInt).toOption)
              val gradeOpt = json.obj.get("grade").map(_.str)
              val customId = json.obj.get("restaurantId").map(_.str)

              restaurantService.addRestaurant(name, cuisine, borough, building, street, zipcode, scoreOpt, gradeOpt, customId) match {
                case Right(created) => sendJson(exchange, 201, write(created))
                case Left(err) => sendJson(exchange, 400, ujson.Obj("error" -> err))
              }
            case Failure(ex) =>
              sendJson(exchange, 400, ujson.Obj("error" -> s"Invalid JSON body: ${ex.getMessage}"))
          }

        // Update Restaurant
        case ("PUT", p) if p.startsWith("/api/restaurants/") =>
          val id = p.stripPrefix("/api/restaurants/")
          val body = readBody(exchange.getRequestBody)
          Try(ujson.read(body)) match {
            case Success(json) =>
              val name = json.obj.get("name").map(_.str).getOrElse("")
              val cuisine = json.obj.get("cuisine").map(_.str).getOrElse("")
              val borough = json.obj.get("borough").map(_.str).getOrElse("")
              val building = json.obj.get("building").map(_.str).getOrElse("")
              val street = json.obj.get("street").map(_.str).getOrElse("")
              val zipcode = json.obj.get("zipcode").map(_.str).getOrElse("")
              val scoreOpt = json.obj.get("score").flatMap(v => Try(v.num.toInt).toOption)
              val gradeOpt = json.obj.get("grade").map(_.str)

              val grades = scoreOpt match {
                case Some(s) =>
                  List(Grade(date = Some(java.time.LocalDate.now().toString), grade = gradeOpt.getOrElse("A"), score = Some(s)))
                case None =>
                  gradeOpt.map(g => Grade(grade = g)).toList
              }

              val updated = Restaurant(
                restaurantId = id,
                name = name,
                borough = borough,
                cuisine = cuisine,
                address = Address(building, street, zipcode),
                grades = grades
              )

              restaurantService.updateRestaurant(id, updated) match {
                case Right(true) => sendJson(exchange, 200, ujson.Obj("success" -> true, "message" -> s"Restaurant '$id' updated successfully."))
                case Right(false) => sendJson(exchange, 404, ujson.Obj("error" -> s"Restaurant '$id' not found."))
                case Left(err) => sendJson(exchange, 400, ujson.Obj("error" -> err))
              }
            case Failure(ex) =>
              sendJson(exchange, 400, ujson.Obj("error" -> s"Invalid JSON body: ${ex.getMessage}"))
          }

        // Delete Restaurant
        case ("DELETE", p) if p.startsWith("/api/restaurants/") =>
          val id = p.stripPrefix("/api/restaurants/")
          restaurantService.deleteRestaurant(id) match {
            case Right(true) => sendJson(exchange, 200, ujson.Obj("success" -> true, "message" -> s"Restaurant '$id' deleted successfully."))
            case Right(false) => sendJson(exchange, 404, ujson.Obj("error" -> s"Restaurant '$id' not found."))
            case Left(err) => sendJson(exchange, 400, ujson.Obj("error" -> err))
          }

        // Analytics: Cuisines
        case ("GET", "/api/analytics/cuisines") =>
          val limit = queryParams.get("limit").flatMap(s => Try(s.toInt).toOption).getOrElse(10)
          analyticsService.getRestaurantsByCuisine(limit) match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Analytics: Boroughs
        case ("GET", "/api/analytics/boroughs") =>
          analyticsService.getRestaurantsByBorough() match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Analytics: Average score by cuisine
        case ("GET", "/api/analytics/avg-score") =>
          val limit = queryParams.get("limit").flatMap(s => Try(s.toInt).toOption).getOrElse(10)
          analyticsService.getAverageScoreByCuisine(limit) match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Analytics: Top rated
        case ("GET", "/api/analytics/top-rated") =>
          val limit = queryParams.get("limit").flatMap(s => Try(s.toInt).toOption).getOrElse(10)
          analyticsService.getTopRatedRestaurants(limit) match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Analytics: Grades
        case ("GET", "/api/analytics/grades") =>
          analyticsService.getGradeDistribution() match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Indexes: List
        case ("GET", "/api/indexes") =>
          analyticsService.listIndexes() match {
            case Right(data) => sendJson(exchange, 200, write(data))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        // Indexes: Ensure
        case ("POST", "/api/indexes/ensure") =>
          analyticsService.ensureIndexes() match {
            case Right(created) => sendJson(exchange, 200, ujson.Obj("success" -> true, "indexes" -> created))
            case Left(err) => sendJson(exchange, 500, ujson.Obj("error" -> err))
          }

        case _ =>
          sendJson(exchange, 404, ujson.Obj("error" -> s"API route not found: $method $path"))
      }
    } catch {
      case ex: Exception =>
        sendJson(exchange, 500, ujson.Obj("error" -> s"Internal server error: ${ex.getMessage}"))
    }
  }

  private def sendJson(exchange: HttpExchange, statusCode: Int, json: ujson.Value): Unit = {
    sendJson(exchange, statusCode, json.render(indent = 2))
  }

  private def sendJson(exchange: HttpExchange, statusCode: Int, responseBody: String): Unit = {
    val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json; charset=UTF-8")
    exchange.sendResponseHeaders(statusCode, bytes.length)
    val os = exchange.getResponseBody
    os.write(bytes)
    os.close()
  }

  private def readBody(is: InputStream): String = {
    val scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name())
    val body = if (scanner.hasNextLine) scanner.useDelimiter("\\A").next() else ""
    scanner.close()
    body
  }

  private def parseQueryParams(query: String): Map[String, String] = {
    if (query.isEmpty) Map.empty
    else {
      query.split("&").flatMap { pair =>
        val parts = pair.split("=", 2)
        if (parts.length == 2) {
          val key = URLDecoder.decode(parts(0), StandardCharsets.UTF_8.name())
          val value = URLDecoder.decode(parts(1), StandardCharsets.UTF_8.name())
          Some(key -> value)
        } else None
      }.toMap
    }
  }
}

/**
 * Serves static HTML, CSS, and JS frontend files from frontend directory.
 */
class StaticFileHandler extends HttpHandler {
  private val frontendCandidates = Seq(
    Paths.get("frontend"),
    Paths.get("../frontend"),
    Paths.get(System.getProperty("user.dir"), "frontend"),
    Paths.get(System.getProperty("user.dir"), "..", "frontend"),
    Paths.get("/Users/heemdesai/YEAR 4/ABDA/ASSIGNMENT/frontend"),
    Paths.get("src/main/resources/frontend")
  )

  private def resolveFrontendRoot(): Option[java.nio.file.Path] = {
    frontendCandidates.find(Files.isDirectory(_))
  }

  override def handle(exchange: HttpExchange): Unit = {
    val rawPath = exchange.getRequestURI.getPath
    val sanitizedPath = if (rawPath == "/" || rawPath.isEmpty) "/index.html" else rawPath

    resolveFrontendRoot() match {
      case Some(root) =>
        val filePath = root.resolve(sanitizedPath.stripPrefix("/")).normalize()
        if (Files.exists(filePath) && !Files.isDirectory(filePath) && filePath.startsWith(root)) {
          val contentType = getContentType(filePath.getFileName.toString)
          exchange.getResponseHeaders.set("Content-Type", contentType)
          if (exchange.getRequestMethod.equalsIgnoreCase("HEAD")) {
            exchange.sendResponseHeaders(200, -1)
            exchange.getResponseBody.close()
          } else {
            val bytes = Files.readAllBytes(filePath)
            exchange.sendResponseHeaders(200, bytes.length)
            val os = exchange.getResponseBody
            os.write(bytes)
            os.close()
          }
        } else {
          val notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(404, notFound.length)
          val os = exchange.getResponseBody
          os.write(notFound)
          os.close()
        }
      case None =>
        val msg = "Frontend directory not found on server.".getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(500, msg.length)
        val os = exchange.getResponseBody
        os.write(msg)
        os.close()
    }
  }

  private def getContentType(fileName: String): String = {
    if (fileName.endsWith(".html")) "text/html; charset=UTF-8"
    else if (fileName.endsWith(".css")) "text/css; charset=UTF-8"
    else if (fileName.endsWith(".js")) "application/javascript; charset=UTF-8"
    else if (fileName.endsWith(".json")) "application/json; charset=UTF-8"
    else if (fileName.endsWith(".svg")) "image/svg+xml"
    else if (fileName.endsWith(".png")) "image/png"
    else "text/plain; charset=UTF-8"
  }
}
