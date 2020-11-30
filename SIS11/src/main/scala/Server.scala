import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Server {
  def startHttpServer(routes: Route, host: String, port: Int)(implicit system: ActorSystem[_], ex: ExecutionContext): Unit = {
    val futureBinding = Http().newServerAt(host, port).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
