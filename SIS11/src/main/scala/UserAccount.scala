import java.time.Instant

import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import sample.persistence.CborSerializable
import Node.{Command, GetBooks}

object UserAccount {
  final case class State(users: Map[String, User],books: Map[String, Book], checkoutDate: Option[Instant]) extends CborSerializable {
    def isCheckedOut: Boolean =
      checkoutDate.isDefined
    def hasUser(itemId: String): Boolean =
      users.contains(itemId)
    def isEmptyUser: Boolean =
      users.isEmpty
    def updateUser(itemId: String, user: User): State = {
      hasUser(itemId) match {
        case true =>
          copy(users = users - itemId)
          copy(users = users + (itemId -> user))
        case false =>
          copy(users = users + (user.id -> user))
      }
    }
    def removeUser(itemId: String): State =
      copy(users = users - itemId)
    def checkout(now: Instant): State =
      copy(checkoutDate = Some(now))
    def toSummaryUsers: SummaryUsers =
      SummaryUsers(users, isCheckedOut)
    def toSummaryUser(id: String): SummaryUser =
      SummaryUser(users.get(id), isCheckedOut)
    def hasBook(itemId: String): Boolean =
      users.contains(itemId)
    def isEmptyBooks: Boolean =
      books.isEmpty
    def updateBook(itemId: String, book: Book): State = {
      hasBook(itemId) match {
        case true =>
          copy(books = books - itemId)
          copy(books = books + (itemId -> book))
      }
    }
    def removeBook(itemId: String): State =
      copy(books = books - itemId)
    def toSummaryBooks: SummaryBooks =
      SummaryBooks(books, isCheckedOut)
    def toSummaryBook(id: String): SummaryBook =
      SummaryBook(books.get(id), isCheckedOut)
  }
  object State {
    val empty: State = State(users = Map.empty, books = Map.empty, checkoutDate = None)
  }
  final case class AddUser(userAccount: User, replyTo: ActorRef[Node.Command]) extends Command
  final case class RemoveUser(token : String, itemId: String, replyTo: ActorRef[Node.Command]) extends Command
  final case class AdjustUser(itemId: String, userAccount: User, replyTo: ActorRef[Node.Command]) extends Command
  final case class GetUser(token: String, id: String,replyTo: ActorRef[Node.Command]) extends Command
  final case class GetUsers(token: String, replyTo: ActorRef[Node.Command]) extends Command
  final case class SummaryUsers(users: Map[String, User], checkedOut: Boolean) extends Command
  final case class SummaryUser(user: Option[User], checkedOut: Boolean) extends Command
  final case class AddBook(book: Book, replyTo: ActorRef[Node.Command]) extends Command
  final case class RemoveBook(itemId: String, replyTo: ActorRef[Node.Command]) extends Command
  final case class AdjustBook(itemId: String, book: Book, replyTo: ActorRef[Node.Command]) extends Command
  final case class GetBook(id: String, replyTo: ActorRef[Node.Command]) extends Command
  final case class GetBooks(replyTo: ActorRef[Node.Command]) extends Command
  final case class SummaryBooks(books: Map[String, Book], checkedOut: Boolean) extends Command
  final case class SummaryBook(book: Option[Book], checkedOut: Boolean) extends Command
  final case class GetToken(email: String, password: String, replyTo: ActorRef[Node.Command]) extends Command
  final case class Checkout(replyTo: ActorRef[Node.Command]) extends Command
  final case class CheckoutBook(replyTo: ActorRef[Node.Command]) extends Command

  sealed trait Event extends CborSerializable {
    def cartId: String
  }
  final case class UserAdded(cartId: String, itemId: String, userAccount: User) extends Event
  final case class UserRemoved(cartId: String, itemId: String) extends Event
  final case class UserAdjusted(cartId: String, itemId: String, userAccount: User) extends Event

  final case class BookAdded(cartId: String, itemId: String, book: Book) extends Event
  final case class BookRemoved(cartId: String, itemId: String) extends Event
  final case class BookAdjusted(cartId: String, itemId: String, book: Book) extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event
  def apply(cartId: String): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, State](
      PersistenceId("ShoppingCart", cartId),
      State.empty,
      (state, command) =>
        if (state.isCheckedOut) checkedOutShoppingCart(cartId, state, command)
        else openShoppingCart(cartId, state, command),
      (state, event) => handleEvent(state, event))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }
  private def openShoppingCart(cartId: String, state: State, command: Command): Effect[Event, State] =
    command match {
      case GetUsers(token,replyTo) =>
        if (token == "admin")
          replyTo ! Node.Success(state.toSummaryUsers)
        Effect.none
      case AddUser(userAccount, replyTo) =>
        if (state.hasUser(userAccount.id)) {
          replyTo ! Node.Error(s"Item '${userAccount.id}' was already added")
          Effect.none
        } else if (userAccount.email=="" && userAccount.password=="") {
          replyTo ! Node.Error("Name or Pass mustn't be empty")
          Effect.none
        } else {
          Effect
            .persist(UserAdded(cartId, userAccount.id, userAccount))
            .thenRun(updatedCart => replyTo ! Node.SuccessUser(updatedCart.toSummaryUser(userAccount.id)))
        }
      case RemoveUser(token, itemId, replyTo) =>
        if (state.hasUser(itemId) && token == "user" + itemId) {
          Effect.persist(UserRemoved(cartId, itemId)).thenRun(updatedCart => replyTo ! Node.SuccessUser(updatedCart.toSummaryUser(itemId)))
        } else {
          replyTo ! Node.Success(state.toSummaryUsers) // removing an item is idempotent
          Effect.none
        }
      case AdjustUser(itemId, userAccount, replyTo) =>
        if (userAccount.email=="" && userAccount.password == "") {
          replyTo ! Node.Error("Name or Pass mustn't be empty")
          Effect.none
        } else if (state.hasUser(itemId)) {
          Effect
            .persist(UserAdjusted(cartId, itemId, userAccount))
            .thenRun(updatedCart => replyTo ! Node.Success(updatedCart.toSummaryUsers))
        } else {
          replyTo ! Node.Error(s"Cannot adjust quantity for item '$itemId'. Item not present on cart")
          Effect.none
        }
      case Checkout(replyTo) =>
        if (state.isEmptyUser) {
          replyTo ! Node.Error("Cannot checkout an empty shopping cart")
          Effect.none
        } else {
          Effect
            .persist(CheckedOut(cartId, Instant.now()))
            .thenRun(updatedCart => replyTo ! Node.Success(updatedCart.toSummaryUsers))
        }
      case GetUser(token,id,replyTo) =>
        if (token == "user"+id && state.hasUser(id))
          replyTo ! Node.SuccessUser(state.toSummaryUser(id))
        Effect.none
      case GetBook(id,replyTo) =>
        if (state.hasBook(id))
          replyTo ! Node.SuccessBook(state.toSummaryBook(id))
        Effect.none
      case GetBooks(replyTo)=>
        replyTo ! Node.SuccessBooks(state.toSummaryBooks)
        Effect.none
      case AddBook(book, replyTo) =>
        if (state.hasBook(book.id)) {
          replyTo ! Node.Error(s"Item '${book.id}' was already added")
          Effect.none
        } else if (book.name=="" && book.description=="") {
          replyTo ! Node.Error("Name or description mustn't be empty")
          Effect.none
        } else {
          Effect
            .persist(BookAdded(cartId, book.id, book))
            .thenRun(updatedCart => replyTo ! Node.SuccessBooks(updatedCart.toSummaryBooks))
        }
      case RemoveBook(itemId, replyTo) =>
        if (state.hasBook(itemId)) {
          Effect.persist(
            BookRemoved(cartId, itemId)
          ).thenRun(updatedCart => replyTo ! Node.SuccessBook(updatedCart.toSummaryBook(itemId)))
        } else {
          replyTo ! Node.SuccessBooks(state.toSummaryBooks) // removing an item is idempotent
          Effect.none
        }
      case AdjustBook(itemId, book, replyTo) =>
        if (book.name=="" && book.description == "") {
          replyTo ! Node.Error("Name or description mustn't be empty")
          Effect.none
        } else if (state.hasBook(itemId)) {
          Effect
            .persist(BookAdjusted(cartId, itemId, book))
            .thenRun(updatedCart => replyTo ! Node.SuccessBooks(updatedCart.toSummaryBooks))
        } else {
          replyTo ! Node.Error(s"Cannot adjust quantity for item '$itemId'. Item not present on cart")
          Effect.none
        }
      case CheckoutBook(replyTo) =>
        if (state.isEmptyBooks) {
          replyTo ! Node.Error("Cannot checkout an empty book cart")
          Effect.none
        } else {
          Effect
            .persist(CheckedOut(cartId, Instant.now()))
            .thenRun(updatedCart => replyTo ! Node.SuccessBooks(updatedCart.toSummaryBooks))
        }
      case GetToken(email, password, replyTo) =>
        state.users.foreach(u => {
          if (u._2.email == email && u._2.password == password)
            replyTo ! Node.Token("user" + u._1)
        })
        Effect.none
    }
  private def checkedOutShoppingCart(cartId: String, state: State, command: Command): Effect[Event, State] =
    command match {
      case GetUser(id, token,replyTo) =>
        if (token == "user"+id && state.hasUser(id))
          replyTo ! Node.SuccessUser(state.toSummaryUser(id))
        Effect.none
      case cmd: AddUser =>
        cmd.replyTo ! Node.Error("Can't add an item to an already checked out account")
        Effect.none
      case cmd: RemoveUser =>
        cmd.replyTo ! Node.Error("Can't remove an item from an already checked out account")
        Effect.none
      case cmd: AdjustUser =>
        cmd.replyTo ! Node.Error("Can't adjust item on an already checked out account")
        Effect.none
      case cmd: Checkout =>
        cmd.replyTo ! Node.Error("Can't checkout already checked out account")
        Effect.none
      case GetBook(id,replyTo) =>
        if (state.hasBook(id))
          replyTo ! Node.SuccessBook(state.toSummaryBook(id))
        Effect.none
      case GetBooks(replyTo)=>
        replyTo ! Node.SuccessBooks(state.toSummaryBooks)
        Effect.none
      case cmd: AddBook =>
        cmd.replyTo ! Node.Error("Can't add an item to an already checked out account")
        Effect.none
      case cmd: RemoveBook =>
        cmd.replyTo ! Node.Error("Can't remove an item from an already checked out account")
        Effect.none
      case cmd: AdjustBook =>
        cmd.replyTo ! Node.Error("Can't adjust item on an already checked out account")
        Effect.none
      case cmd: CheckoutBook =>
        cmd.replyTo ! Node.Error("Can't checkout already checked out account")
        Effect.none
    }
  private def handleEvent(state: State, event: Event) = {
    event match {
      case UserAdded(_, itemId, quantity) => state.updateUser(itemId, quantity)
      case UserRemoved(_, itemId)         => state.removeUser(itemId)
      case UserAdjusted(_, itemId, quantity) =>
        state.updateUser(itemId, quantity)
      case CheckedOut(_, eventTime) => state.checkout(eventTime)
      case BookAdded(_, itemId, quantity) => state.updateBook(itemId, quantity)
      case BookRemoved(_, itemId)         => state.removeBook(itemId)
      case BookAdjusted(_, itemId, quantity) =>
        state.updateBook(itemId, quantity)
    }
  }
}
