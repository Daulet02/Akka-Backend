import Node.{Checked, Command}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import scala.concurrent.duration.DurationInt
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
case class PostText(email: String, password: String)
trait Router {
  def route: Route
}
class NodeRouter(users: UsersRepository, node:ActorRef[Node.Command])(implicit system: ActorSystem[_], ex:ExecutionContext)
  extends  Router
    with  Directives with UsersDirectives{
  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: Scheduler = system.scheduler
  override def route: Route =
    concat(
      pathPrefix("live") {
        get {
          val processFuture: Future[Checked] = node.ask(ref => Node.Check("userToken",ref))(timeout,scheduler).mapTo[Checked]
          onSuccess(processFuture){response =>
            complete(response)
          }
        }
      },
      pathPrefix("signin"){
        post{
          entity(as[PostText]) { token =>
            val processFuture: Future[Node.Token] = node.ask(
              ref => Node.GetToken(token.email, token.password,ref)
            )(timeout, scheduler).mapTo[Node.Token]
            println("send credentials")
            onSuccess(processFuture) { token =>
              complete(token)
            }
          }
        }
      }
      ,
      pathPrefix("signup"){
        post{
          entity(as[CreateUser]) { createUser =>
            val processFuture: Future[Node.SuccessUser] = node.ask(
              ref => Node.Create(
                User(UUID.randomUUID().toString, createUser.email, createUser.password, Seq.empty)
                ,ref)
            )(timeout, scheduler).mapTo[Node.SuccessUser]
            println("sended")
            onSuccess(processFuture) { response =>
              complete(response)
            }
          }
        }
      },
      pathPrefix("users") {
        concat(
          path(Segment){ id: String =>
            concat(
              get{
                val processFuture: Future[Node.SuccessUser] = node.ask(
                  ref => Node.GetAccount("user"+id, id, ref))(timeout, scheduler).mapTo[Node.SuccessUser]
                onSuccess(processFuture) { response =>
                  complete(response)
                }
              },
              delete{
                val processFuture: Future[Node.SuccessUser] = node.ask(
                  ref => Node.DeleteAccount("admin", id, ref))(timeout, scheduler).mapTo[Node.SuccessUser]
                onSuccess(processFuture) { response =>
                  complete(response)
                }
              },
              put{
//                entity(as[AddBook]) { updateUser =>
//                  val processFuture: Future[Node.SuccessUser] = node.ask(
//                    ref => Node.GetAccount("user" + id, id, ref))(timeout, scheduler).mapTo[Node.SuccessUser]
//                  onSuccess(processFuture) { response =>
//                    complete(response)
//                  }
//                }
                complete("ok")
              }
            )
          },
          get{
            val processFuture: Future[Node.Success] = node.ask(
              ref => Node.GetAccounts("admin", ref))(timeout, scheduler).mapTo[Node.Success]
            onSuccess(processFuture) { response =>
              complete(response)
            }
          },
          post{
            entity(as[CreateUser]) { createUser =>
              val processFuture: Future[Node.SuccessUser] = node.ask(
                ref => Node.Create(
                  User(UUID.randomUUID().toString, createUser.email, createUser.password, Seq.empty)
                  ,ref)
              )(timeout, scheduler).mapTo[Node.SuccessUser]
              println("send")
              onSuccess(processFuture) { response =>
                complete(response)
              }
            }
          },
        )
      },
      pathPrefix("books"){
        concat(
          pathPrefix("search"){
            path(Segment){ text: String =>
              get{
                handle(users.searchBook(text )) {
                  case BookNotFound(_) =>
                    ApiError.todoNotFound(text)
                  case _ =>
                    ApiError.generic
                } { books =>
                  complete(books)
                }
              }
            }
          },
          path(Segment){ id: String =>
            concat(
              get{
                val processFuture: Future[Node.SuccessBook] = node.ask(
                  ref => Node.GetBook(id, ref))(timeout, scheduler).mapTo[Node.SuccessBook]
                onSuccess(processFuture) { response =>
                  complete(response)
                }
              },
              delete{
                val processFuture: Future[Node.SuccessBook] = node.ask(
                  ref => Node.DeleteBook(id, ref))(timeout, scheduler).mapTo[Node.SuccessBook]
                onSuccess(processFuture) { response =>
                  complete(response)
                }
              },
              put{
                complete("ok")
              }
            )
          },
          concat(
            get{
              val processFuture: Future[Node.SuccessBooks] = node.ask(
                ref => Node.GetBooks( ref))(timeout, scheduler).mapTo[Node.SuccessBooks]
              onSuccess(processFuture) { response =>
                complete(response)
              }
            },
            post{
              entity(as[CreateBook]) { createBook =>
                val processFuture: Future[Node.SuccessBook] = node.ask(
                  ref => Node.CreateBook(
                    createBook.token,
                    Book(UUID.randomUUID().toString, createBook.name, createBook.description),ref
                  )
                )(timeout, scheduler).mapTo[Node.SuccessBook]
                println("send Book")
                onSuccess(processFuture) { response =>
                  complete(response)
                }
              }
            }
          ),
        )
      }
    )
}
