import java.util.concurrent.atomic.AtomicLong

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.ClosedShape
import akka.stream.scaladsl.GraphDSL.Implicits.{SourceArrow, port2flow}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer, StringSerializer}
import org.apache.kafka.common.serialization.{Deserializer, Serializer, StringDeserializer, StringSerializer}

import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

object Main extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val ec = system.dispatcher


  val config = system.settings.config.getConfig("akka.kafka.producer")
  val server = system.settings.config.getString("akka.kafka.producer.kafka-clients.server")
  val topic = system.settings.config.getString("akka.kafka.producer.kafka-clients.topic")
  val topic2 = system.settings.config.getString("akka.kafka.producer.kafka-clients.topic2")

  val producerSettings =
    ProducerSettings(config, new StringSerializer, new StringSerializer)
      .withBootstrapServers(server)
  val kafkaProducer = producerSettings.createKafkaProducer()

  val done: Future[Done] =
    Source(1 to 100)
      .map(_ => Random.nextInt())
      .map(value => new ProducerRecord[String, String](topic, value.toString))
      .runWith(Producer.plainSink(producerSettings))

  done.onComplete {
    case Success(value) => {
      println(value)
      system.terminate()
    }
    case Failure(exception) => {
      println(exception)
    }
  }

//  val done1: Future[Done] =
//    Source(101 to 200)
//      .map(_ => Random.nextInt())
//      .map(value => new ProducerRecord[String, String](topic2, value.toString))
//      .runWith(Producer.plainSink(producerSettings))
//
//  done1.onComplete {
//    case Success(value) => {
//      println(value)
//      system.terminate()
//    }
//    case Failure(exception) => {
//      println(exception)
//    }
//  }

}
