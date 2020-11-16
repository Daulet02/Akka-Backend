package Consumer_1

import java.util.concurrent.atomic.AtomicLong
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import scala.concurrent.Future
import akka.Done

class OffsetStore {
  private val offset = new AtomicLong

  def businessLogicAndStoreOffset(record: ConsumerRecord[String, String]): Future[Done] = {
    println(s"DB.save: ${record.value}")
    offset.set(record.offset)
    Future.successful(Done)
  }

  def loadOffset(): Future[Long] = {
    Future.successful(offset.get)
  }
}
