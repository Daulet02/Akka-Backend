import java.util.concurrent.atomic.AtomicLong
import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Source}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import ch.qos.logback.classic.{Level,Logger}
import org.slf4j.LoggerFactory
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import scala.concurrent.Future
import scala.util.{Failure, Success}

object Main extends App {
  implicit val system = ActorSystem("Quickstart")
  implicit val ec = system.dispatcher

  val log = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
  val logger = log.asInstanceOf[Logger].setLevel(Level.OFF)

  val bootstrapServers = "localhost:9092"
  val config = system.settings.config.getConfig("akka.kafka.producer")

  def numberGenerator():Unit = {
    val topic = "number_generator"

    val producerSettings =
      ProducerSettings(config, new StringSerializer, new StringSerializer)
        .withBootstrapServers(bootstrapServers)
        .withBootstrapServers(bootstrapServers)


    val random = scala.util.Random
    val done = Source(Seq.fill(15)(random.nextInt(10)))
      .map(_.toString)
      .map(value => {
        val r = new ProducerRecord[String, String](topic, value)
        Thread.sleep(3000)
        r
      }).runWith(Producer.plainSink(producerSettings))
    done.onComplete {
      case Success(_)=>
      case Failure(exception) => println(exception)
    }
  }

  def publishServices(num:Int=0, service: String): Unit = {
    val topic = service

    val producerSettings =
      ProducerSettings(config, new StringSerializer, new StringSerializer)
        .withBootstrapServers(bootstrapServers)
    val done = Source(List(num))
      .map(_.toString)
      .map { elem =>
        new ProducerRecord[String, String](topic, elem)
      }
      .runWith(Producer.plainSink(producerSettings))
    done.onComplete {
      case Success(_)=>
      case Failure(exception) => println(exception)
    }
  }

  def double(num:Int):Int = {
    val result = num*2
    publishServices(result, "addition2")
    result
  }
  def triple(num:Int):Int={
    val result = num+3
    publishServices(result, "multiple2")
    result
  }

  def consumeServices(topic:String): Unit = {
    class OffsetStore {
      private val offset = new AtomicLong
      def businessLogicAndStoreOffset(record: ConsumerRecord[String, String]): Future[Done] = {
        if (topic=="number_generator") {
          println(s"Number generator : ${record.value}")
          double(record.value.toInt)
          triple(record.value.toInt)
        }
        else{
          println(s"$topic got: ${record.value}")
        }
        offset.set(record.offset)
        Future.successful(Done)
      }
      def loadOffset(): Future[Long] = {
        Future.successful(offset.get)
      }
    }

    val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withClientId("externalOffsetStorage")

    val db = new OffsetStore
    db.loadOffset().map { fromOffset =>
      Consumer
        .plainSource(
          consumerSettings,
          Subscriptions.assignmentWithOffset(
            new TopicPartition(topic, 0) -> fromOffset
          )
        )
        .mapAsync(1)(db.businessLogicAndStoreOffset)
        .toMat(Sink.seq)(DrainingControl.apply)
        .run()
    }
  }

  numberGenerator()
  consumeServices("number_generator")
  consumeServices("double")
  consumeServices("triple")
}
