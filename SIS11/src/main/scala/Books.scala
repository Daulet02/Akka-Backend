case class Book(id: String, name: String, description:String)
case class CreateBook(token: String, name: String, description:String)
case class UpdateBook(name: Some[String], description: Some[String])
case class Delete(id: String)

case class Category(id: String, name: String, description:String)
case class UpdateCategory(name: Some[String], description: Some[String])
case class CreateCategory(name: String, description:String)
