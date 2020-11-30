import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait UsersRepository {
  def allUsers(): Future[Seq[User]]
  def createUser(createUser:CreateUser): Future[User]
  def login(signIn: SignIn): Future[Token]
  def updateUser(id: String, addBook: AddBook): Future[User]
  def deleteUser(id: String): Future[User]
  def getUser(id: String): Future[User]

  def allBooks(): Future[Seq[Book]]
  def createBook(createBook:CreateBook): Future[Book]
  def deleteBook(id: String): Future[Book]
  def getBook(id: String): Future[Book]
  def searchBook(name: String): Future[Seq[Book]]
}
final case class UserNotFound(id: String) extends Exception("User " + id + " not found")
final case class BookNotFound(id: String) extends Exception("Book " + id + " not found")
final case class BookFound(id: String) extends Exception("Book " + id + "  is already in list")
class InMemoryRepository(usersList:Seq[User] = Seq.empty, booksList: Seq[Book] = Seq.empty
                             )(implicit  ex:ExecutionContext) extends UsersRepository {
  private var users: Vector[User] = usersList.toVector
  private var books: Vector[Book] = booksList.toVector
  override def allUsers(): Future[Seq[User]] = Future.successful(users)

  override def createUser(createUser: CreateUser): Future[User] = Future.successful {
    val user = User(
      id = UUID.randomUUID().toString,
      email = createUser.email,
      password = createUser.password,
      books = Vector.empty[Book]
    )
    users = users :+ user
    user
  }
  override def updateUser(id: String, addBook: AddBook): Future[User] = {
    users.find(_.id == id) match {
      case Some(user) =>
        if (user.books.find(_.id == addBook.id ) != null){
          val book_tmp = books.find(_.id==addBook.id)
          book_tmp match {
            case Some(b) =>
              val books_tmp = user.books :+ b
              val tmp = user.copy(books = books_tmp)
              users = users.map(user => if (user.id == id) tmp else user)
              Future.successful(tmp)
          }
        }else{
          Future.failed(BookFound(addBook.id))
        }
      case None =>
        Future.failed(UserNotFound(id))
    }
  }

  override def login(signIn: SignIn): Future[Token] = {
    users.foreach(us=>{
      if (us.email == signIn.email && us.password == signIn.password)
        Future.successful(Token("user" + us.id))
      else
        Future.failed(UserNotFound(signIn.email))
    })
    Future.successful(Token("authorization Failed: No user Exists"))
  }

  override def deleteUser(id: String): Future[User] = {
    users.find(_.id == id) match {
      case Some(todo) =>
        users = users.filter(_.id != id)
        Future.successful(todo)
      case None =>
        Future.failed(UserNotFound(id))
    }
  }
  override def getUser(id: String): Future[User] = {
    users.find(_.id == id) match {
      case Some(todo) =>
        Future.successful(todo)
      case None =>
        Future.failed(UserNotFound(id))
    }
  }

  override def allBooks(): Future[Seq[Book]] = {
    Future.successful(books)
  }

  override def createBook(createBook: CreateBook): Future[Book] = {
    val book = Book(
      id = UUID.randomUUID().toString,
      name = createBook.name,
      description = createBook.description,
    )
    books = books :+ book
    Future.successful(book)
  }

  override def deleteBook(id: String): Future[Book] = {
    books.find(_.id == id) match {
      case Some(book) =>
        books = books.filter(_.id != id)
        Future.successful(book)
      case None =>
        Future.failed(BookNotFound(id))
    }
  }

  override def getBook(id: String): Future[Book] = {
    books.find(_.id == id) match {
      case Some(book) =>
        Future.successful(book)
      case None =>
        Future.failed(BookNotFound(id))
    }
  }

  override def searchBook(name: String): Future[Seq[Book]] = {
    var bs: Seq[Book] = Seq.empty
    books.foreach(b => if (b.name.contains(name)) bs = bs :+ b)
    Future.successful(bs)
  }
}
import akka.http.scaladsl.server.{Directive1, Directives}
import scala.concurrent.Future
import scala.util.Success
trait UsersDirectives extends Directives {
  def handle[T](f: Future[T])(e: Throwable => ApiError): Directive1[T] = onComplete(f) flatMap {
    case Success(t) =>
      provide(t)
//    case Failure(error) =>
//      val apiError = e(error)
//      complete(apiError.statusCode, apiError.message)
  }
}

import akka.http.scaladsl.model.{StatusCode, StatusCodes}

final case class ApiError private(statusCode: StatusCode, message: String)
object ApiError {
  private def apply(statusCode: StatusCode, message: String): ApiError = new ApiError(statusCode, message)

  val generic: ApiError = new ApiError(StatusCodes.InternalServerError, "Unknown error.")
  val emptyTitleField: ApiError = new ApiError(StatusCodes.BadRequest, "The title field must not be empty.")
  val emptyNameField: ApiError = new ApiError(StatusCodes.BadRequest, "The Name field must not be empty.")
  val emptyEmailField: ApiError = new ApiError(StatusCodes.BadRequest, "The Email field must not be empty.")
  val emptyPhoneField: ApiError = new ApiError(StatusCodes.BadRequest, "The Phone field must not be empty.")
  val emptyDescriptionField: ApiError = new ApiError(StatusCodes.BadRequest, "The description field must not be empty.")
  val emailWrongFormat: ApiError = new ApiError(StatusCodes.BadRequest, "The email field is wrong.")
  val phoneWrongFormat: ApiError = new ApiError(StatusCodes.BadRequest, "The phone number field is wrong.")

  def todoNotFound(id: String): ApiError =
    new ApiError(StatusCodes.NotFound, s"The todo with id $id could not be found.")

  def addressNotFound(id: String): ApiError =
    new ApiError(StatusCodes.NotFound, s"The addressBook with id $id could not be found.")
}
