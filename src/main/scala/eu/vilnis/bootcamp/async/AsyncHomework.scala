package eu.vilnis.bootcamp.async

import java.net.URL
import java.util.concurrent.Executors

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success}

/**
  * Application:
  * - takes a web-page URL from arguments (args array)
  * - loads the web-page body, extracts HTTP links from it
  * - for all the found links, tries to fetch a server name header if there is one
  * - prints all the encountered unique server name values in alphabetical order
  *
  * Each link processing should be done in parallel.
  * Validation of arguments is not needed.
  *
  * Try to test it on http://google.com!
  */
object AsyncHomework extends App {
  private implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  /*
   * NOTES:
   * - requirements doesn't clearly state if / how to handle multiple site input, this code processes all in one batch
   * - assumed provided methods are 'immutable', i.e, not to be touched by me and to be used as is
   */

  // app entry point
  getRefServers(args.toSet) onComplete {
    case Success(serverNames) =>
      println((">> START:RESULTS <<" +: serverNames :+ ">> END:RESULTS <<").mkString("\n"))
      System.exit(0)
    case Failure(t)     =>
      println(s"Failed with $t")
      System.exit(1)
  }


  def getRefServers(sites:Set[String]): Future[List[String]] = {
    val cache = mutable.HashMap.empty[String, Future[Option[String]]]

    val processingTasks = sites map { s: String =>
      for {
        body <- fetchPageBody(s)
        links <- findLinkUrls(body)
        server <- Future.sequence(
          links.map(s =>
            synchronized { cache.getOrElseUpdate(s, fetchServerName(s)) }
          )
        )
      } yield server.flatten
    }
    Future.sequence(processingTasks).map( _.flatten.toList.sorted )
  }


  private def fetchPageBody(url: String): Future[String] = {
    println(f"Fetching $url")
    Future {
      val source = Source.fromURL(url)
      try {
        source.mkString
      } finally {
        source.close()
      }
    }
  }

  private def fetchServerName(url: String): Future[Option[String]] = {
    println(s"Fetching server name header for $url")
    Future {
      Option(new URL(url).openConnection().getHeaderField("Server"))
    }
  }

  private def findLinkUrls(html: String): Future[List[String]] =
    Future {
      val linkPattern = """href="(http[^"]+)"""".r
      linkPattern.findAllMatchIn(html).map(m => m.group(1)).toList
    }
}
