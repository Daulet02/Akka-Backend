case class User(id: String, email: String, password:String, books: Seq[Book])
case class CreateUser(email: String, password:String)
case class AddBook(id: String)

case class SignIn(email: String, password: String)
case class Token(token: String)