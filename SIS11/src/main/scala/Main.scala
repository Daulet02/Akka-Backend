import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

object Main {
  val config: Config = ConfigFactory.load()
  val address = config.getString("http.ip")
  val port = config.getInt("http.port")
  val nodeId = config.getString("clustering.ip")

  def main(args: Array[String]): Unit = {

    var bk = Book("1","title1","description1")
    var bk2 = Book("2","title2","description2")
    var us1 = User("1","user1","asdf", Seq(bk,bk2))
    var us2 = User("2","user12","asdfasd", Seq(bk2))
    var users:Seq[User] = Seq(
      us1, us2
    )
    val books:Seq[Book] = Seq(
      bk,bk2
    )
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    val rootBehavior = Behaviors.setup[Node.Command] { context =>
      context.spawnAnonymous(Node())
      val group = Routers.group(Node.NodeServiceKey)
      val node = context.spawnAnonymous(group)
      val repo = new InMemoryRepository(users,books)(context.executionContext)
      val router = new NodeRouter(repo,node)(context.system, context.executionContext)
      Server.startHttpServer(router.route, address, port)(context.system, context.executionContext)
      Behaviors.empty
    }
    ActorSystem[Node.Command](rootBehavior, "cluster-playground")

    //    val text = List("this is a test", "of some very naive word counting", "but what can you say", "it is what it is")
    //    receptionist ! JobRequest("the first job", (1 to 100000).flatMap(i => text ++ text).toList)
    //    system.actorOf(Props(new ClusterDomainEventListener), "cluster-listener")

  }



}
