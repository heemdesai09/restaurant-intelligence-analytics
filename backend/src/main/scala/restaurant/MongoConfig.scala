package restaurant

import com.mongodb.client.{MongoClient, MongoClients, MongoCollection, MongoDatabase}
import org.bson.Document
import java.io.File
import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters._

object MongoConfig {
  val DatabaseName: String = "sample_restaurants"
  val CollectionName: String = "restaurants"

  // Lazy initialization of MongoClient using MONGODB_URI environment variable
  private var clientInstance: Option[MongoClient] = None

  /**
   * Helper function to load environment variables from .env file
   * if not already present in system environment variables.
   */
  def loadEnv(): Map[String, String] = {
    val candidates = Seq(
      Paths.get(".env"),
      Paths.get("../.env"),
      Paths.get("../../.env")
    )
    val envFileOpt = candidates.find(p => Files.exists(p))
    envFileOpt match {
      case Some(path) =>
        try {
          Files.readAllLines(path).asScala
            .map(_.trim)
            .filter(line => line.nonEmpty && !line.startsWith("#") && line.contains("="))
            .map { line =>
              val idx = line.indexOf('=')
              val key = line.substring(0, idx).trim
              val value = line.substring(idx + 1).trim.stripPrefix("\"").stripSuffix("\"")
              key -> value
            }
            .toMap
        } catch {
          case _: Exception => Map.empty[String, String]
        }
      case None =>
        Map.empty[String, String]
    }
  }

  /**
   * Retrieves MONGODB_URI from system environment or .env file.
   */
  def getMongoUri(): Option[String] = {
    sys.env.get("MONGODB_URI")
      .filter(_.trim.nonEmpty)
      .orElse(loadEnv().get("MONGODB_URI").filter(_.trim.nonEmpty))
  }

  /**
   * Gets or initializes the MongoClient instance.
   */
  def getClient(): Either[String, MongoClient] = synchronized {
    clientInstance match {
      case Some(c) => Right(c)
      case None =>
        getMongoUri() match {
          case Some(uri) =>
            Try(MongoClients.create(uri)) match {
              case Success(client) =>
                clientInstance = Some(client)
                Right(client)
              case Failure(ex) =>
                Left(s"Failed to initialize MongoDB client: ${ex.getMessage}")
            }
          case None =>
            Left(
              "MONGODB_URI environment variable is missing!\n" +
              "Please set MONGODB_URI in your environment or in a .env file.\n" +
              "Example: MONGODB_URI=\"mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority\""
            )
        }
    }
  }

  /**
   * Retrieves the target MongoDB database.
   */
  def getDatabase(): Either[String, MongoDatabase] = {
    getClient().map(_.getDatabase(DatabaseName))
  }

  /**
   * Retrieves the target 'restaurants' collection.
   */
  def getCollection(): Either[String, MongoCollection[Document]] = {
    getDatabase().map(_.getCollection(CollectionName))
  }

  /**
   * Pings the database to verify active connection to MongoDB Atlas.
   */
  def testConnection(): Either[String, String] = {
    getDatabase().flatMap { db =>
      Try {
        val ping = db.runCommand(new Document("ping", 1))
        val count = db.getCollection(CollectionName).countDocuments()
        s"Connected successfully to MongoDB Atlas! Database: '$DatabaseName', Collection: '$CollectionName' (Found $count documents)."
      } match {
        case Success(msg) => Right(msg)
        case Failure(ex) =>
          Left(s"Database connection test failed: ${ex.getMessage}")
      }
    }
  }

  /**
   * Safely closes the MongoDB connection.
   */
  def close(): Unit = synchronized {
    clientInstance.foreach { client =>
      try {
        client.close()
      } catch {
        case _: Exception => ()
      }
    }
    clientInstance = None
  }
}
